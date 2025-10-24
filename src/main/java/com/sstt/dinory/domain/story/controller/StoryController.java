package com.sstt.dinory.domain.story.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.sstt.dinory.domain.story.dto.RecommendedStoryDto;
import com.sstt.dinory.domain.story.dto.StoryChoiceRequest;
import com.sstt.dinory.domain.story.dto.StoryCompleteRequest;
import com.sstt.dinory.domain.story.dto.StoryGenerateRequest;
import com.sstt.dinory.domain.story.service.StoryRecommendationService;
import com.sstt.dinory.domain.story.service.StoryService;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/story")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
@Slf4j
public class StoryController {
    
    private final StoryRecommendationService recommendationService;
    private final StoryService storyService;

    // 동화추천
    @PostMapping("/recommended")
    public ResponseEntity<List<RecommendedStoryDto>> getRecommendations(
        @RequestParam(required = false) String emotion,
        @RequestParam(required = false) List<String> interests,
        @RequestParam(required = false) Long childId,
        @RequestParam(defaultValue = "5") Integer limit) {

            log.info("=== 동화 추천 요청 ===");
            log.info("emotion: {}", emotion);
            log.info("interests: {}", interests);
            log.info("childId: {}", childId);
            log.info("limit: {}", limit);

            List<RecommendedStoryDto> recommendations = recommendationService.getRecommendations(emotion, interests, childId, limit);

            return ResponseEntity.ok(recommendations);
        }

    //  동화생성
    @PostMapping("/{storyId}/generate")
    public ResponseEntity<Map<String, Object>> generateStory(
        @PathVariable String storyId,
        @RequestBody StoryGenerateRequest request
    ) {
        log.info("=== 동화 생성 요청 ===");
        log.info("storyId: {}", storyId);
        log.info("childId: {}", request.getChildId());
        log.info("childName: {}", request.getChildName());
        log.info("emotion: {}", request.getEmotion());
        log.info("interests: {}", request.getInterests());

        // storyId를 request에 설정
        request.setStoryId(storyId);

        Map<String, Object> generatedStory = storyService.generateStory(request);

        return ResponseEntity.ok(generatedStory);
    }

    // 선택지 저장하기
    @PostMapping("/completion/{completionId}/choice")
    public ResponseEntity<Map<String, Object>> saveChoice(
        @PathVariable Long completionId,
        @RequestBody StoryChoiceRequest request
    ) {
        log.info("=== 선택지 저장 요청 ===");
        log.info("completionId: {}", completionId);
        log.info("sceneNumber: {}", request.getSceneNumber());
        log.info("choiceId: {}", request.getChoiceId());

        storyService.saveChoice(completionId, request);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "선택지가 저장되었습니다.");

        return ResponseEntity.ok(response);
    }

    // 동화 완료
@PostMapping("/completion/{completionId}/complete")
public ResponseEntity<Map<String, Object>> completeStory(
    @PathVariable Long completionId,
    @RequestBody StoryCompleteRequest request
) {
    log.info("=== 동화 완료 요청 ===");
    log.info("completionId: {}", completionId);
    log.info("totalTime: {}", request.getTotalTime());
    
    storyService.completeStory(completionId, request);
    
    Map<String, Object> response = new HashMap<>();
    response.put("success", true);
    response.put("message", "동화가 완료되었습니다.");
    
    return ResponseEntity.ok(response);
}

    // 헬스체크용
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Story Controller is working!");
    }
    
}
