package aicc.bot.web;


import aicc.chat.domain.ChatMessage;
import aicc.chat.domain.MessageType;
import aicc.chat.domain.UserRole;
import aicc.chat.service.MessageBroker;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import aicc.bot.ChatBot;
import aicc.bot.dto.ChatBotRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/bot")
@RequiredArgsConstructor
public class BotApiController {
    
    private final ChatBot chatBot;
    private final MessageBroker messageBroker;

    @PostMapping("/ask")
    public ResponseEntity<Map<String, String>> askBot(@RequestBody Map<String, String> payload) {
        String userMessage = payload.get("text");
        
        // Botpress는 Converse API 호출 시 사용했던 식별자를 "userId" 필드에 담아 보냅니다.
        // 우리는 ChatController에서 roomId를 식별자로 보냈으므로, 여기서 받는 값은 roomId입니다.
        String receivedRoomId = payload.get("userId"); 
        
        // AI 엔진(MiChatBotImpl)은 대화 맥락 유지를 위해 sessionId를 요구합니다.
        // roomId를 기반으로 sessionId를 생성하여 AI 엔진에 전달합니다.
        String aiSessionId;
        try {
            // roomId가 UUID 형식이면 그대로 사용
            UUID.fromString(receivedRoomId);
            aiSessionId = receivedRoomId;
        } catch (IllegalArgumentException e) {
            // UUID 형식이 아니더라도 roomId가 같으면 항상 같은 SessionId가 나오도록
            // roomId를 기반으로 UUID 생성 (대화 맥락 유지)
            aiSessionId = UUID.nameUUIDFromBytes(receivedRoomId.getBytes()).toString();
            log.info("RoomID({})가 UUID 형식이 아니어서 변환된 SessionID({})를 사용합니다.", receivedRoomId, aiSessionId);
        }

        log.info("AI 요청 수신: text={}, roomId={}, aiSessionId={}", userMessage, receivedRoomId, aiSessionId);
        
        final String finalSessionId = aiSessionId;
        final String targetRoomId = receivedRoomId;

        CompletableFuture.runAsync(() -> {
            try {
                ChatBotRequest request = ChatBotRequest.builder()
                        .message(userMessage)
                        .sessionId(finalSessionId) // 검증된 SessionId 사용
                        .build();

                StringBuilder responseBuilder = new StringBuilder();
                
                chatBot.ask(request, 
                    // onChunk: 데이터가 올 때마다 빌더에 추가
                    chunk -> responseBuilder.append(chunk),
                    // onComplete: 모든 데이터 수신 완료 후 실행
                    () -> {
                        String aiReply = responseBuilder.toString();
                        log.info("AI 답변 생성 완료 (길이: {}): {}", aiReply.length(), aiReply);

                        // 채팅방으로 발송 시에는 원래 roomId 사용
                        ChatMessage botMessage = ChatMessage.builder()
                                .roomId(targetRoomId)
                                .sender("Bot")
                                .senderRole(UserRole.BOT)
                                .message(aiReply)
                                .type(MessageType.TALK)
                                .build();
                        
                        log.info("채팅방 발송 직전 확인: roomId={}", targetRoomId);
                        messageBroker.publish(botMessage);
                        log.info("채팅방({})으로 AI 답변 전송 완료", targetRoomId);
                    }
                );
                 
            } catch (Exception e) {
                log.error("AI 처리 중 오류", e);
            }
        });

        return ResponseEntity.ok(Collections.singletonMap("reply", "")); 
    }
}
