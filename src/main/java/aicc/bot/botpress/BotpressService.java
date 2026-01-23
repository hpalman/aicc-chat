package aicc.bot.botpress;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class BotpressService {

    private final WebClient chatWebClient;
    
    // Botpress 서버 정보
    private static final String BOTPRESS_URL = "http://192.168.133.132:3000";
    // Bot ID는 Botpress Admin 패널에서 생성한 봇 ID와 일치해야 합니다. 기본값으로 'bot'을 사용합니다.
    private static final String BOT_ID = "bot"; 

    // Botpress의 Converse API는 userId를 기준으로 대화를 식별합니다.
    // 우리 시스템에서는 roomId를 userId 자리에 넣어 대화방 단위로 봇과 소통합니다.
    public void sendMessage(String targetId, String text, Consumer<String> onReply) {
        String url = BOTPRESS_URL + "/api/v1/bots/" + BOT_ID + "/converse/" + targetId;
        
        Map<String, String> body = new HashMap<>();
        body.put("type", "text");
        body.put("text", text);

        chatWebClient.post()
            .uri(url)
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(BotpressResponse.class)
            .subscribe(response -> {
                if (response != null && response.getResponses() != null) {
                    response.getResponses().forEach(r -> {
                        if ("text".equals(r.getType())) {
                            onReply.accept(r.getText());
                        } else {
                            onReply.accept("[" + r.getType() + "] 메시지가 도착했습니다.");
                        }
                    });
                }
            }, error -> {
                if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                    org.springframework.web.reactive.function.client.WebClientResponseException ex = (org.springframework.web.reactive.function.client.WebClientResponseException) error;
                    log.error("Botpress API Error: Status={}, Body={}", ex.getStatusCode(), ex.getResponseBodyAsString());
                } else {
                    log.error("Botpress API call failed", error);
                }
            });
    }

    @Data
    public static class BotpressResponse {
        private List<BotResponse> responses;
    }
    
    @Data
    public static class BotResponse {
        private String type;
        private String text;
        // 필요한 필드 추가 가능 (image, choices 등)
    }
}

