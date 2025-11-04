package com.sstt.dinory.domain.story.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.sstt.dinory.domain.story.dto.RecommendedStoryDto;
import com.sstt.dinory.domain.story.dto.StoryChoiceRequest;
import com.sstt.dinory.domain.story.dto.StoryCompleteRequest;
import com.sstt.dinory.domain.story.dto.StoryCompletionSummaryDto;
import com.sstt.dinory.domain.story.dto.StoryGenerateRequest;
import com.sstt.dinory.domain.story.entity.StoryCompletion;
import com.sstt.dinory.domain.story.repository.StoryCompletionRepository;
import com.sstt.dinory.domain.story.service.StoryRecommendationService;
import com.sstt.dinory.domain.story.service.StoryService;

import java.util.stream.Collectors;


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
    private final StoryCompletionRepository storyCompletionRepository;

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

    /**
     * [2025-10-28 신규 추가] 다음 씬 생성 API (분기형 스토리)
     *
     * 변경 사유: 기존 8개 씬 미리 생성 방식에서 사용자 선택에 따른 분기형 스토리로 전환
     * - 사용자가 선택지를 선택하면 해당 선택을 저장하고 다음 씬을 AI 서버에 요청
     * - AI 서버는 이전 선택 히스토리를 기반으로 맥락에 맞는 다음 씬 생성
     * - 최대 8개 씬까지 생성 가능하며, 8번째 씬에서 이야기 종료
     *
     * @param completionId 현재 진행 중인 스토리의 completion ID
     * @param request 사용자의 선택 정보 (sceneNumber, choiceId, abilityType, abilityPoints)
     * @return 다음 씬 정보 (scene 객체와 isEnding 플래그 포함)
     */
    @PostMapping("/completion/{completionId}/next-scene")
    public ResponseEntity<Map<String, Object>> getNextScene(
        @PathVariable Long completionId,
        @RequestBody StoryChoiceRequest request
    ) {
        log.info("=== 다음 씬 요청 ===");
        log.info("completionId: {}", completionId);
        log.info("sceneNumber: {}", request.getSceneNumber());
        log.info("choiceId: {}", request.getChoiceId());

        // 선택 저장
        storyService.saveChoice(completionId, request);

        // 다음 씬 생성 (AI 서버에 이전 선택 히스토리 전달)
        Map<String, Object> nextScene = storyService.generateNextScene(completionId, request.getSceneNumber() + 1);

        return ResponseEntity.ok(nextScene);
    }

    // 선택지 저장하기 (기존 API - 호환성 유지)
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

    // 동화 완료 요약 조회 - 
    @GetMapping("/completion/{completionId}/summary")
    public ResponseEntity<StoryCompletionSummaryDto> getStoryCompletionSummary(
        @PathVariable Long completionId
    ) {
        log.info("=== 동화 완료 요약 조회 요청 ===");
        log.info("completionId: {}", completionId);

        StoryCompletionSummaryDto summary = storyService.getStoryCompletionSummary(completionId);

        return ResponseEntity.ok(summary);
    }

    // 커스텀 선택지
    @PostMapping("/analyze-custom-choice")
    public ResponseEntity<Map<String, Object>> analyzeCustomChoice(
        @RequestBody Map<String, Object> request
    ) {
        log.info("=== 커스텀 선택지 분석 요청 ===");

        Long completionId = null;
        Integer sceneNumber = null;
        String text = null;

        try {
            // completionId 파싱
            if(request.get("completionId") != null) {
                Object completionIdObj = request.get("completionId");
                if(completionIdObj instanceof Number) {
                    completionId = ((Number) completionIdObj).longValue();
                } else {
                    completionId = Long.valueOf(completionIdObj.toString());
                }
            }

            // sceneNumber 파싱
            if (request.get("sceneNumber") != null) {
                Object sceneNumberObj = request.get("sceneNumber");
                if (sceneNumberObj instanceof Number) {
                    sceneNumber = ((Number) sceneNumberObj).intValue();
                } else {
                    sceneNumber = Integer.valueOf(sceneNumberObj.toString());
                }
            }

            // text 파싱
            if (request.get("text") != null) {
                text = request.get("text").toString();
            }

            log.info("completionId: {}, sceneNumber: {}, text: {}", completionId, sceneNumber, text);

            // 입력 검증
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("text는 필수입니다.");
            }

            // StoryService를 통해 AI 분석 수행
            Map<String, Object> analysisResult = storyService.analyzeCustomChoice(completionId, sceneNumber, text);

            log.info("AI 분석 결과: {}", analysisResult);
            return ResponseEntity.ok(analysisResult);
                
        } catch (Exception e) {
            log.error("커스텀 선택지 분석 실패: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "분석 실패",
                "message", e.getMessage()
            ));
        }
    }   


    // 헬스체크용
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Story Controller is working!");
    }

      // 완성된 동화 전체 조회 - 다시보기
    @GetMapping("/completion/{completionId}/full-story")
    public ResponseEntity<Map<String, Object>> getFullStory(
        @PathVariable Long completionId
    ){
        log.info("==== 완성 된 동화 조회(다시보기용) ====");
        log.info("completionId: {}" , completionId);

        Map<String, Object> fullStory = storyService.getFullStory(completionId);

        return ResponseEntity.ok(fullStory);
    }
    
    /**
     * 2025-11-04 김민중 추가
     * RAG: 특정 아이의 최근 동화 완료 기록 조회
     * FastAPI의 MemoryService가 호출
     */
    @GetMapping("/completions/child/{childId}")
    public ResponseEntity<List<StoryCompletionSummaryDto>> getStoryCompletionsByChild(
            @PathVariable Long childId,
            @RequestParam(defaultValue = "5") int limit) {

        log.info("=== RAG: 동화 완료 기록 조회 ===");
        log.info("childId: {}, limit: {}", childId, limit);

        Pageable pageable = PageRequest.of(0, limit);
        List<StoryCompletion> completions = storyCompletionRepository.findRecentCompletionsByChildId(childId, pageable);

        List<StoryCompletionSummaryDto> dtos = completions.stream()
                .map(StoryCompletionSummaryDto::from)
                .collect(Collectors.toList());

        log.info("조회된 동화 완료 기록: {}개", dtos.size());

        return ResponseEntity.ok(dtos);
    }

}
