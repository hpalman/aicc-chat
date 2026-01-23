package aicc.bot.web;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import aicc.bot.service.ChatAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 채팅 내용 분석(요약, 키워드 추출, 카테고리 분류)을 제공하는 REST 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class ChatAnalysisController {

    private final ChatAnalysisService chatAnalysisService;

    /**
     * 상담 내용을 요약합니다.
     */
    @PostMapping("/summarize")
    public ResponseEntity<String> summarize(@RequestBody Map<String, Object> request) {
        log.info("요약 요청 수신");
        String result = chatAnalysisService.summarize(request);
        return ResponseEntity.ok(result);
    }

    /**
     * 상담 키워드를 추출합니다.
     */
    @PostMapping("/keywords")
    public ResponseEntity<String> extractKeywords(@RequestBody Map<String, Object> request) {
        log.info("키워드 추출 요청 수신");
        String result = chatAnalysisService.keyword(request);
        return ResponseEntity.ok(result);
    }

    /**
     * 상담 카테고리를 분류합니다.
     */
    @PostMapping("/classify")
    public ResponseEntity<String> classify(@RequestBody Map<String, Object> request) {
        log.info("카테고리 분류 요청 수신");
        String result = chatAnalysisService.category(request);
        return ResponseEntity.ok(result);
    }
}

