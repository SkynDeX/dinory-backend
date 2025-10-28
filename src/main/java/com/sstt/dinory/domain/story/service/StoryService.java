package com.sstt.dinory.domain.story.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.sstt.dinory.domain.child.entity.Child;
import com.sstt.dinory.domain.child.repository.ChildRepository;
import com.sstt.dinory.domain.story.dto.StoryChoiceRequest;
import com.sstt.dinory.domain.story.dto.StoryCompleteRequest;
import com.sstt.dinory.domain.story.dto.StoryCompletionSummaryDto;
import com.sstt.dinory.domain.story.dto.StoryGenerateRequest;
import com.sstt.dinory.domain.story.entity.Choice;
import com.sstt.dinory.domain.story.entity.Scene;
import com.sstt.dinory.domain.story.entity.Story;
import com.sstt.dinory.domain.story.entity.StoryCompletion;
import com.sstt.dinory.domain.story.repository.ChoiceRepository;
import com.sstt.dinory.domain.story.repository.SceneRepository;
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
    private final SceneRepository sceneRepository;
    private final ChoiceRepository choiceRepository;

    @Value("${ai.server.url:http://localhost:8000}")
    private String aiServerUrl;

    /** 첫 번째 씬 생성 */
    @Transactional
    public Map<String, Object> generateStory(StoryGenerateRequest request) {
        Child child = childRepository.findById(request.getChildId())
            .orElseThrow(() -> new RuntimeException("Child 못 찾음: " + request.getChildId()));
        Story story = getOrCreateStory(request.getStoryId());

        // [2025-10-28 김민중 수정] emotion 저장
        StoryCompletion completion = StoryCompletion.builder()
            .child(child)
            .story(story)
            .emotion(request.getEmotion())
            .interests(request.getInterests()) // [2025-10-28 김광현] 추가
            .choicesJson(new ArrayList<>())
            .build();

        storyCompletionRepository.save(completion);

        // [2025-10-28 김민중 수정] Story의 title과 description을 AI 서버로 전송
        // childName은 동화 주인공 이름이 아닌, 개인화를 위한 참고용으로만 사용
        Map<String, Object> aiRequest = new HashMap<>();
        aiRequest.put("storyId", request.getStoryId());
        aiRequest.put("storyTitle", story.getTitle());
        aiRequest.put("storyDescription", story.getDescription());
        aiRequest.put("childId", request.getChildId());
        aiRequest.put("emotion", request.getEmotion());
        aiRequest.put("interests", request.getInterests() != null ? request.getInterests() : new ArrayList<>());
        aiRequest.put("sceneNumber", 1);
        aiRequest.put("previousChoices", new ArrayList<>());

        Map<String, Object> firstSceneResponse;
        try {
            firstSceneResponse = webClientBuilder.build()
                .post()
                .uri(aiServerUrl + "/ai/generate-first-scene")
                .bodyValue(aiRequest)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
        } catch (WebClientResponseException.NotFound nf) {
            firstSceneResponse = webClientBuilder.build()
                .post()
                .uri(aiServerUrl + "/ai/generate-next-scene")
                .bodyValue(aiRequest)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
        }

        saveSceneOnly(story, firstSceneResponse, 1);

        Map<String, Object> response = new HashMap<>(firstSceneResponse);
        response.put("completionId", completion.getId());
        response.put("storyId", request.getStoryId());
        return response;
    }

    /** 사용자가 고른 선택 저장: StoryCompletion + choice 테이블 */
    @Transactional
    public void saveChoice(Long completionId, StoryChoiceRequest request) {
        // [2025-10-28 김민중 수정] choiceText 필수 검증 추가
        if (request.getChoiceText() == null || request.getChoiceText().isBlank()) {
            throw new IllegalArgumentException("choiceText는 필수입니다. 프론트에서 선택지 텍스트를 전송해주세요.");
        }

        StoryCompletion completion = storyCompletionRepository.findById(completionId)
            .orElseThrow(() -> new RuntimeException("StoryCompletion 없음: " + completionId));

        // [2025-10-28 김민중 추가] 중복 요청 방지: choicesJson에서 이미 동일한 sceneNumber + choiceText가 있는지 체크
        boolean isDuplicate = completion.getChoicesJson().stream()
            .anyMatch(c -> c.getSceneNumber().equals(request.getSceneNumber())
                && c.getChoiceText().equals(request.getChoiceText()));

        if (isDuplicate) {
            log.warn("중복된 선택 요청 무시: completionId={}, sceneNumber={}, choiceText={}",
                completionId, request.getSceneNumber(), request.getChoiceText());
            return;
        }

        // 1) 진행 경로 누적(JSON)
        StoryCompletion.ChoiceRecord rec = new StoryCompletion.ChoiceRecord(
            request.getSceneNumber(),
            request.getChoiceId(),
            request.getChoiceText(),
            request.getAbilityType(),
            request.getAbilityPoints()
        );

        // [2025-10-28 김광현]능력치 합산 
        int points = (request.getAbilityPoints() == null) ? 0 : request.getAbilityPoints();
        int currentScore = completion.getAbilityScore() != null ? completion.getAbilityScore() : 0;
        int newScore = currentScore + points;
        completion.setAbilityScore(newScore);
        log.info("능력치 업데이트: {} -> {} ({}점 추가)", currentScore, newScore, points);

        completion.getChoicesJson().add(rec);
        storyCompletionRepository.save(completion);

        // 2) choice 테이블에 단일 선택 기록
        Story story = completion.getStory();
        Scene scene = sceneRepository.findByStoryAndSceneNumber(story, request.getSceneNumber())
            .orElseThrow(() -> new RuntimeException("Scene 없음: story=" + story.getId()
                + ", sceneNumber=" + request.getSceneNumber()));

        // [2025-10-28 김민중 수정] 중복 저장 방지
        Optional<Choice> existingChoice = choiceRepository.findBySceneAndChoiceText(scene, request.getChoiceText());
        if (existingChoice.isPresent()) {
            log.warn("중복된 선택지 저장 시도 방지: completionId={}, sceneNumber={}, choiceText={}",
                completionId, request.getSceneNumber(), request.getChoiceText());
            return;
        }

        String abilityType = (request.getAbilityType() == null || request.getAbilityType().isBlank())
            ? "기타" : request.getAbilityType();
        Integer abilityPoints = (request.getAbilityPoints() == null) ? 0 : request.getAbilityPoints();

        Choice choice = Choice.builder()
            .scene(scene)
            .choiceText(request.getChoiceText())
            .abilityType(abilityType)
            .abilityPoints(abilityPoints)
            .nextScene(null)             // 분기 연결 필요 시 추후 업데이트
            .build();

        choiceRepository.save(choice);
        log.info("선택지 저장 완료: completionId={}, sceneNumber={}, choiceText={}",
            completionId, request.getSceneNumber(), request.getChoiceText());
    }

    @Transactional
    public void completeStory(Long completionId, StoryCompleteRequest request) {
        StoryCompletion completion = storyCompletionRepository.findById(completionId)
            .orElseThrow(() -> new RuntimeException("StoryCompletion 없음: " + completionId));
        completion.setTotalTime(request.getTotalTime());
        completion.setCompletedAt(LocalDateTime.now());
        storyCompletionRepository.save(completion);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Story getOrCreateStory(String storyId) {
        return storyRepository.findById(storyId).orElseGet(() -> {
            Story newStory = Story.builder()
                .id(storyId)
                .title("생성된 동화")
                .category("임시")
                .build();
            try {
                return storyRepository.save(newStory);
            } catch (Exception e) {
                return storyRepository.findById(storyId)
                    .orElseThrow(() -> new RuntimeException("Story 조회 실패"));
            }
        });
    }

    @Transactional(readOnly = true)
    public StoryCompletionSummaryDto getStoryCompletionSummary(Long completionId) {
        StoryCompletion completion = storyCompletionRepository.findById(completionId)
            .orElseThrow(() -> new RuntimeException("StoryCompletion 없음: " + completionId));
        return StoryCompletionSummaryDto.from(completion);
    }

    /** 다음 씬 생성 */
    @Transactional
    public Map<String, Object> generateNextScene(Long completionId, int nextSceneNumber) {
        StoryCompletion completion = storyCompletionRepository.findById(completionId)
            .orElseThrow(() -> new RuntimeException("StoryCompletion 없음: " + completionId));

        Child child = completion.getChild();
        Story story = completion.getStory();

        // [2025-10-28 김민중 수정] Story의 title과 description을 AI 서버로 전송
        // emotion을 completion에서 가져옴
        Map<String, Object> aiRequest = new HashMap<>();
        aiRequest.put("storyId", story.getId());
        aiRequest.put("storyTitle", story.getTitle());
        aiRequest.put("storyDescription", story.getDescription());
        aiRequest.put("childId", child.getId());
        aiRequest.put("emotion", completion.getEmotion() != null ? completion.getEmotion() : "중립");
        aiRequest.put("interests", child.getInterests() != null ? child.getInterests() : new ArrayList<>());
        aiRequest.put("sceneNumber", nextSceneNumber);

        java.util.List<Map<String, Object>> prev = new java.util.ArrayList<>();
        for (StoryCompletion.ChoiceRecord c : completion.getChoicesJson()) {
            Map<String, Object> m = new HashMap<>();
            m.put("sceneNumber", c.getSceneNumber());
            m.put("choiceId", c.getChoiceId());
            m.put("choiceText", c.getChoiceText());
            m.put("abilityType", c.getAbilityType());
            m.put("abilityScore", c.getAbilityPoints());
            prev.add(m);
        }
        aiRequest.put("previousChoices", prev);

        Map<String, Object> aiResponse = webClientBuilder.build()
            .post()
            .uri(aiServerUrl + "/ai/generate-next-scene")
            .bodyValue(aiRequest)
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .block();

        saveSceneOnly(story, aiResponse, nextSceneNumber);
        return aiResponse;
    }

    /** AI 응답으로 Scene만 저장 */
    private void saveSceneOnly(Story story, Map<String, Object> aiResponse, int sceneNumber) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> sceneData = (Map<String, Object>) aiResponse.get("scene");
            if (sceneData == null) return;

            if (sceneRepository.findByStoryAndSceneNumber(story, sceneNumber).isPresent()) return;

            String content = (String) sceneData.get("text");
            if (content == null) content = (String) sceneData.get("content");
            if (content == null) content = "";

            Scene scene = Scene.builder()
                .story(story)
                .sceneNumber(sceneNumber)
                .content(content)
                .imageUrl(null)
                .imagePrompt(null)
                .build();
            sceneRepository.save(scene);
        } catch (Exception e) {
            log.error("Scene 저장 오류: {}", e.getMessage(), e);
        }
    }
}
