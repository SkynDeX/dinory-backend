package com.sstt.dinory.domain.child.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
        
}
