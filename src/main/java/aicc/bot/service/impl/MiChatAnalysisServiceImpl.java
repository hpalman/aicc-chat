package aicc.bot.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import aicc.bot.service.ChatAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MiChatAnalysisServiceImpl implements ChatAnalysisService {

    private final WebClient chatWebClient;
    private final ObjectMapper objectMapper;

    @Value("${app.ai-bot.ai-end-point}")
    private String aiEndPoint;

    @Value("${app.ai-bot.analysis.summary-uri}")
    private String summaryUri;

    @Value("${app.ai-bot.analysis.keyword-uri}")
    private String keywordUri;

    @Value("${app.ai-bot.analysis.category-uri}")
    private String categoryUri;

    @Value("${app.ai-bot.analysis.company-id:apt001}")
    private String defaultCompanyId;

    @Value("${app.ai-bot.analysis.default-user-id:manager}")
    private String defaultUserId;

    @Override
    public String summarize(Map<String, Object> request) {
        return callApi(aiEndPoint + summaryUri, "요약", request);
    }

    @Override
    public String keyword(Map<String, Object> request) {
        return callApi(aiEndPoint + keywordUri, "키워드 추출", request);
    }

    @Override
    public String category(Map<String, Object> request) {
        return callApi(aiEndPoint + categoryUri, "카테고리 분류", request);
    }

    private String callApi(String url, String taskName, Map<String, Object> request) {
        if (request == null || request.isEmpty()) {
            log.warn("{} 요청이 비어있습니다.", taskName);
            return "잘못된 요청입니다.";
        }

        try {
            String sessionId = extractSessionId(request);
            Map<String, Object> requestMap = buildRequestMap(taskName, request, sessionId);
            
            String requestBody = objectMapper.writeValueAsString(requestMap);
            log.info("{} API 호출 시작 - SessionId: {}", taskName, sessionId);
            
            if (log.isDebugEnabled()) {
                log.debug("{} 요청 Body: {}", taskName, requestBody);
            }
            
            String response = chatWebClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .doOnError(error -> logError(taskName, error))
                .block();
            
            log.info("{} API 호출 완료 - SessionId: {}", taskName, sessionId);
            
            if (log.isDebugEnabled()) {
                log.debug("{} 응답: {}", taskName, response);
            }
            
            return response != null ? response : "응답이 없습니다.";
            
        } catch (JsonProcessingException e) {
            log.error("{} 요청 JSON 생성 실패", taskName, e);
            return "요청 처리 중 오류가 발생했습니다.";
        } catch (WebClientResponseException e) {
            log.error("{} API 응답 오류 - Status: {}, Body: {}", 
                taskName, e.getStatusCode(), 
                e.getResponseBodyAsString(StandardCharsets.UTF_8));
            return "API 호출 중 오류가 발생했습니다.";
        } catch (Exception e) {
            log.error("{} API 호출 중 예상치 못한 오류 발생", taskName, e);
            return "시스템 오류가 발생했습니다.";
        }
    }

    private Map<String, Object> buildRequestMap(String taskName, Map<String, Object> request, String sessionId) {
        Map<String, Object> requestMap = new LinkedHashMap<>();
        
        // meta 설정
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("companyId", request.getOrDefault("companyId", defaultCompanyId));
        meta.put("consultantId", defaultUserId);
        meta.put("sessionId", sessionId);
        meta.put("userId", defaultUserId);
        
        requestMap.put("meta", meta);
        requestMap.put("messages", request.get("messages"));

        // task별 추가 설정
        if ("요약".equals(taskName)) {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("maxLength", 300);
            config.put("stream", false);
            config.put("summaryType", "general");
            requestMap.put("config", config);
        } else if ("키워드 추출".equals(taskName)) {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("includeFrequency", false);
            config.put("keywordType", "general");
            config.put("maxKeywords", 8);
            requestMap.put("config", config);
        } else if ("카테고리 분류".equals(taskName)) {
            if (request.containsKey("categories")) {
                requestMap.put("categories", request.get("categories"));
            }
            requestMap.put("maxCategories", request.getOrDefault("maxCategories", 1));
        }
        
        return requestMap;
    }

    private void logError(String taskName, Throwable error) {
        log.error("{} API 호출 중 오류 발생: {}", taskName, error.getMessage());
    }

    @SuppressWarnings("unchecked")
    private String extractSessionId(Map<String, Object> request) {
        String sessionId = null;
        if (request.containsKey("meta")) {
            Object metaObj = request.get("meta");
            if (metaObj instanceof Map) {
                sessionId = (String) ((Map<String, Object>) metaObj).get("sessionId");
            }
        }
        if (sessionId == null) {
            sessionId = (String) request.get("sessionId");
        }
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = "chat-" + System.currentTimeMillis();
        }
        return sessionId;
    }
}

