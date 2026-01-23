package aicc.bot.michat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import aicc.bot.ChatBot;
import aicc.bot.dto.ChatBotRequest;
import aicc.bot.dto.MiChatAskRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MiChatBotImpl implements ChatBot {
    
    private final WebClient chatWebClient;
    private final ObjectMapper objectMapper;
    
    @Value("${app.ai-bot.ai-end-point}")
    private String aiEndPoint;
    
    @Value("${app.ai-bot.company-id:apt001}")
    private String companyId;
    
    @Value("${app.ai-bot.default-user-id:manager}")
    private String defaultUserId;
    
    @Value("${app.ai-bot.rag-sys-info:DEFAULT_RAG}")
    private String ragSysInfo;
    
    private static final String ASK_ENDPOINT = "/v1/chatbot/ask";
    private static final String SSE_DATA_PREFIX = "data:";
    private static final String SSE_DONE_MESSAGE = "[DONE]";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(65);

    @Override
    public void ask(ChatBotRequest requests, Consumer<String> onChunk, Runnable onComplete) {
        if (requests == null || requests.getMessage() == null) {
            log.warn("ChatBot 요청이 null이거나 메시지가 없습니다.");
            onChunk.accept("잘못된 요청입니다.");
            if (onComplete != null) onComplete.run();
            return;
        }

        try {
            MiChatAskRequest askRequest = buildAskRequest(requests);
            String requestBody = objectMapper.writeValueAsString(askRequest);
            
            log.info("ChatBot API 호출 시작 - SessionId: {}, CompanyId: {}", 
                askRequest.getMeta().getSessionId(), askRequest.getMeta().getCompanyId());
            
            chatWebClient.post()
                .uri(aiEndPoint + ASK_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(REQUEST_TIMEOUT)
                .subscribe(
                    line -> processStreamLine(line, onChunk),
                    error -> {
                        handleDetailedError(error, onChunk);
                        if (onComplete != null) onComplete.run();
                    },
                    () -> {
                        log.info("ChatBot Stream 완료 - SessionId: {}", askRequest.getMeta().getSessionId());
                        if (onComplete != null) onComplete.run();
                    }
                );

        } catch (JsonProcessingException e) {
            log.error("ChatBot 요청 JSON 생성 실패", e);
            onChunk.accept("요청 처리 중 오류가 발생했습니다.");
            if (onComplete != null) onComplete.run();
        } catch (Exception e) {
            log.error("ChatBot Stream 호출 중 예상치 못한 오류 발생", e);
            onChunk.accept("시스템 오류가 발생했습니다.");
            if (onComplete != null) onComplete.run();
        }
    }
    
    /**
     * 표준화된 Request DTO 빌드
     */
    private MiChatAskRequest buildAskRequest(ChatBotRequest requests) {
        return MiChatAskRequest.builder()
            .chat(MiChatAskRequest.ChatConfig.builder()
                .message(requests.getMessage())
                .stream(true)
                .useHistory(true)
                .build())
            .meta(MiChatAskRequest.MetaConfig.builder()
                .companyId(requests.getCompanyId() != null && !requests.getCompanyId().isEmpty() 
                    ? requests.getCompanyId() : this.companyId)
                .sessionId(requests.getSessionId())
                .userId(requests.getUserId() != null && !requests.getUserId().isEmpty() 
                    ? requests.getUserId() : this.defaultUserId)
                .category1(requests.getCategory1() != null ? requests.getCategory1() : "")
                .category2(requests.getCategory2() != null ? requests.getCategory2() : "")
                .ragSysInfo(this.ragSysInfo)
                .build())
            .build();
    }
    
    /**
     * SSE 스트림 라인을 처리합니다.
     */
    private void processStreamLine(String line, Consumer<String> onChunk) {
        // 디버깅을 위해 수신된 라인을 INFO 레벨로 출력
        if (log.isDebugEnabled()) {
             log.debug("SSE Raw Line 수신: '{}'", line);
        }
        
        if (line == null || line.trim().isEmpty()) {
            return;
        }
        
        // [DONE] 메시지 처리
        if (SSE_DONE_MESSAGE.equals(line.trim()) || line.contains(SSE_DONE_MESSAGE)) {
            log.debug("ChatBot Stream [DONE] 수신");
            return;
        }

        // "data:" 접두어가 있다면 제거, 없다면 그대로 사용
        String raw = line;
        if (line.startsWith(SSE_DATA_PREFIX)) {
            raw = line.substring(SSE_DATA_PREFIX.length()).trim();
        }
        
        try {
            JsonNode node = objectMapper.readTree(raw);
            String delta = null;
            
            // 1. 'delta' 필드 확인
            if (node.has("delta")) {
                delta = node.get("delta").asText();
            } 
            // 2. 'content' 필드 확인 (OpenAI 스타일)
            else if (node.has("content")) {
                delta = node.get("content").asText();
            }
            // 3. 'message' 필드 확인
            else if (node.has("message")) {
                delta = node.get("message").asText();
            }
            // 4. 'text' 필드 확인
            else if (node.has("text")) {
                delta = node.get("text").asText();
            }
            // 5. 'answer' 필드 확인
            else if (node.has("answer")) {
                delta = node.get("answer").asText();
            }

            if (delta != null && !delta.isEmpty()) {
                // 수신된 청크 데이터 로그 출력
                if (log.isDebugEnabled()) log.debug("Stream Chunk: {}", delta);
                onChunk.accept(delta);
            } else {
                log.warn("Stream 데이터에 유효한 텍스트 필드(delta, content, message, text, answer)가 없음: {}", raw);
            }
        } catch (JsonProcessingException e) {
            log.warn("ChatBot Stream 응답 파싱 실패 - Raw: {}", raw, e);
        }
    }
    
    /**
     * 상세 에러 처리 (표준 에러 응답 구조 대응)
     */
    private void handleDetailedError(Throwable error, Consumer<String> onChunk) {
        if (error instanceof WebClientResponseException responseEx) {
            String errorBody = responseEx.getResponseBodyAsString(StandardCharsets.UTF_8);
            try {
                JsonNode errorNode = objectMapper.readTree(errorBody);
                if (errorNode.has("error")) {
                    JsonNode errorDetail = errorNode.get("error");
                    String errorCode = errorDetail.get("error_code").asText();
                    String errorMessage = errorDetail.get("error_message").asText();
                    
                    log.error("AI 엔진 오류 발생 - Code: {}, Message: {}, RequestId: {}", 
                        errorCode, errorMessage, errorDetail.has("requestId") ? errorDetail.get("requestId").asText() : "N/A");
                    
                    if (errorCode.startsWith("MAI-4")) {
                        onChunk.accept("요청이 올바르지 않습니다. (" + errorMessage + ")");
                    } else {
                        onChunk.accept("AI 서버 처리 중 오류가 발생했습니다.");
                    }
                    return;
                }
            } catch (Exception e) {
                log.warn("에러 응답 파싱 실패: {}", errorBody);
            }
        }
        log.error("ChatBot Stream 처리 중 예외 발생", error.getMessage());
        onChunk.accept("서비스 연결에 실패했습니다.");
    }

}
