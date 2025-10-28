package com.sstt.dinory.domain.child.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sstt.dinory.domain.child.dto.EmotionInterestRequest;
import com.sstt.dinory.domain.child.dto.EmotionLogDto;
import com.sstt.dinory.domain.child.service.EmotionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/emotion")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
@Slf4j
public class EmotionController {
    
    private final EmotionService emotionService;

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("Emotion Controller is working!");
    }

    @PostMapping("/test-json")
    public ResponseEntity<String> testJson(@RequestBody String body) {
        log.info("Received body: {}", body);
        return ResponseEntity.ok("Received: " + body);
    }

    @PostMapping("/check-simple")
    public ResponseEntity<String> checkEmotionSimple(
        @RequestBody String rawBody
    ) {
        log.info("Received raw body: {}", rawBody);
        return ResponseEntity.ok("OK");
    }

    @PostMapping("/check")
    public ResponseEntity<EmotionLogDto> checkEmotion(
        @RequestBody EmotionInterestRequest request) {

        log.info("=== Emotion Check Request ===");
        log.info("Raw request object: {}", request);
        log.info("childId: {}", request.getChildId());
        log.info("emotion: {}", request.getEmotion());
        log.info("interests: {}", request.getInterests());
        log.info("source: {}", request.getSource());
        log.info("context: {}", request.getContext());

        EmotionLogDto result = emotionService.saveEmotion(request);
        return ResponseEntity.ok(result);
        
    }

    // //  관심사 업데이트 
    // @PutMapping("/{emotionLogId}/interests")
    // public ResponseEntity<EmotionLogDto> updateInterests(
    //     @PathVariable Long emotionLogId,
    //     @RequestBody List<String> interests
    // ) {
    //     log.info("==Interests 업데이트==");
    //     log.info("emotionLogId: {}, interests: {}", emotionLogId, interests);

    //     EmotionLogDto result = emotionService.updateInterests(emotionLogId, interests);
    //     return ResponseEntity.ok(result);
    // }

    // // 최근 EmotionLog 조회
    // @GetMapping("/latest/{childId}")
    // public ResponseEntity<EmotionLogDto> getLatestEmotion(@PathVariable Long childId) {
    //     log.info("==최신 감정 얻기==");
    //     log.info("childId: {}", childId);
        
    //     EmotionLogDto result = emotionService.getLatestEmotion(childId);
    //     return ResponseEntity.ok(result);
    // }
        
}
