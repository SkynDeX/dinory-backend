package com.sstt.dinory.domain.story.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.reactive.function.client.WebClient;

import com.sstt.dinory.domain.child.entity.Child;
import com.sstt.dinory.domain.child.repository.ChildRepository;
import com.sstt.dinory.domain.story.dto.StoryChoiceRequest;
import com.sstt.dinory.domain.story.dto.StoryCompleteRequest;
import com.sstt.dinory.domain.story.dto.StoryGenerateRequest;
import com.sstt.dinory.domain.story.entity.Story;
import com.sstt.dinory.domain.story.entity.StoryCompletion;
import com.sstt.dinory.domain.story.repository.StoryCompletionRepository;
import com.sstt.dinory.domain.story.repository.StoryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoryService {
    
    private final StoryRepository storyRepository;
    private final StoryCompletionRepository storyCompletionRepository;
    private final ChildRepository childRepository;
    private final WebClient.Builder webClientBuilder;

    @Value("${ai.server.url:http://localhost:8000}")
    private String aiServerUrl;

    // 동화 생성(AI 서버 호출)
    @Transactional
    public Map<String, Object> generateStory(StoryGenerateRequest request) {
        log.info("==동화 생성 시작==");
        log.info("storyId: {}, childId: {}, childName: {}", request.getStoryId(), request.getChildId(), request.getChildName());

        // child 조회
        Child child = childRepository.findById(request.getChildId())
                .orElseThrow(() -> new RuntimeException("Child 못 찾음: " + request.getChildId()));
        log.info("Child 조회 성공: {}", child.getName());

        // story 조회 or 생성
        Story story = storyRepository.findById(request.getStoryId())
            .orElseGet(() -> {
                log.info("story가 db에 없어서 임시 생성 : {}" , request.getStoryId());
                Story newStory = Story.builder()
                    .id(request.getStoryId())
                    .title("생성된 동화")
                    .category("임시")
                    .build();
                return storyRepository.save(newStory);
            });
        log.info("Story 준비 완료: {}", story.getTitle());

        // AI 서버 호출 준비
        Map<String, Object> aiRequest = new HashMap<>();
        aiRequest.put("storyId", request.getStoryId());
        aiRequest.put("childId", request.getChildId());
        aiRequest.put("childName", request.getChildName());
        aiRequest.put("emotion", request.getEmotion());
        aiRequest.put("interests", request.getInterests());

        log.info("AI 서버에 동화 생성 요청 중...");
        
        // 4. AI 서버 호출
        Map<String, Object> generatedStory;
        try {
            generatedStory = webClientBuilder.build()
                    .post()
                    .uri(aiServerUrl + "/ai/generate-story")
                    .bodyValue(aiRequest)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();
            
            log.info("AI 서버에서 동화 생성 완료!");
            
        } catch (Exception e) {
            log.error("AI 서버 호출 실패: {}", e.getMessage());
            throw new RuntimeException("AI 서버에서 동화 생성 실패: " + e.getMessage());
        }
        
        // 5. StoryCompletion 생성 (진행 기록 시작)
        StoryCompletion completion = StoryCompletion.builder()
                .child(child)
                .story(story)
                .choicesJson(new ArrayList<>())
                .completedAt(LocalDateTime.now())
                .build();
        storyCompletionRepository.save(completion);
        log.info("StoryCompletion 생성 완료: ID={}", completion.getId());
        
        // 6. 응답에 completionId 추가
        generatedStory.put("completionId", completion.getId());
        
        log.info("=== 동화 생성 완료 ===");
        return generatedStory;
    }

    @Transactional
    public void saveChoice(Long completionId, StoryChoiceRequest request) {
        log.info("==선택지 저장 시작==");

        // StoryCompletion 조회
        StoryCompletion completion = storyCompletionRepository.findById(completionId)
            .orElseThrow(() -> new RuntimeException("StoryCompletion을 찾을 수 없습니다:" + completionId));
        
        // 선택 기록 추가
        StoryCompletion.ChoiceRecord choiceRecord = new StoryCompletion.ChoiceRecord(
            request.getSceneNumber(),
            request.getChoiceId(),
            request.getAbilityType(),       
            request.getAbilityPoints() 
        );

        completion.getChoicesJson().add(choiceRecord);
        storyCompletionRepository.save(completion);

        log.info("선택지 저장 완료 - Scene: {}, Choice: {}", request.getSceneNumber(), request.getChoiceId());

    }

    @Transactional
    public void completeStory(Long completionId, StoryCompleteRequest request) {
        log.info("=== 동화 완료 처리 시작 ===");
    
        // StoryCompletion 조회
        StoryCompletion completion = storyCompletionRepository.findById(completionId)
            .orElseThrow(() -> new RuntimeException("StoryCompletion을 찾을 수 없습니다: " + completionId));
        
        // 총 소요 시간 업데이트
        completion.setTotalTime(request.getTotalTime());
        completion.setCompletedAt(LocalDateTime.now());
        
        storyCompletionRepository.save(completion);
        
        log.info("동화 완료 처리 완료 - 총 소요시간: {}초", request.getTotalTime());
        
    }


}
