package com.sstt.dinory.domain.story.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

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

    /**
     * [2025-10-28 변경] 동화 생성 - 첫 번째 씬만 생성 (분기형 스토리)
     *
     * 변경 사유: 기존에는 8개 씬을 한 번에 생성했으나, 사용자 선택에 따라 분기하는 스토리로 변경
     * - 이제 첫 번째 씬만 생성하고 StoryCompletion 레코드를 생성
     * - 이후 씬은 사용자가 선택할 때마다 generateNextScene()으로 하나씩 생성
     * - AI 서버에 previousChoices를 빈 배열로 전달 (첫 씬이므로 이전 선택 없음)
     *
     * @param request 동화 생성 요청 (storyId, childId, childName, emotion, interests)
     * @return 첫 번째 씬 정보와 completionId를 포함한 응답
     */
    @Transactional
    public Map<String, Object> generateStory(StoryGenerateRequest request) {
        log.info("== 동화 생성 시작 (첫 씬) ==");
        log.info("storyId: {}, childId: {}, childName: {}", request.getStoryId(), request.getChildId(), request.getChildName());

        // child 조회
        Child child = childRepository.findById(request.getChildId())
            .orElseThrow(() -> new RuntimeException("Child 못 찾음: " + request.getChildId()));
        log.info("Child 조회 성공: {}", child.getName());

        // story 조회/생성
        Story story = getOrCreateStory(request.getStoryId());
        log.info("Story 준비 완료: {}", story.getTitle());

        // 진행 기록 생성
        StoryCompletion completion = StoryCompletion.builder()
            .child(child)
            .story(story)
            .choicesJson(new ArrayList<>())
            // 시작 시점에 completedAt 세팅은 비정상. 완료 시점에 설정.
            .build();
        storyCompletionRepository.save(completion);
        log.info("StoryCompletion 생성 완료: ID={}", completion.getId());

        // [변경] AI 요청 바디 - previousChoices 추가 (분기형 스토리)
        Map<String, Object> aiRequest = new HashMap<>();
        aiRequest.put("storyId", request.getStoryId());
        aiRequest.put("childId", request.getChildId());
        aiRequest.put("childName", request.getChildName());
        aiRequest.put("emotion", request.getEmotion());
        aiRequest.put("interests", request.getInterests());
        aiRequest.put("sceneNumber", 1);
        aiRequest.put("previousChoices", new ArrayList<>()); // 첫 씬이므로 빈 배열

        log.info("AI 서버에 첫 번째 씬 생성 요청...");

        Map<String, Object> firstSceneResponse;
        try {
            // 정상 경로
            firstSceneResponse = webClientBuilder.build()
                .post()
                .uri(aiServerUrl + "/ai/generate-first-scene")
                .bodyValue(aiRequest)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
            log.info("AI 서버 첫 씬 생성 완료");
        } catch (WebClientResponseException.NotFound nf) {
            // 레거시/호환 경로 fallback
            log.warn("first-scene 404 → fallback: /ai/generate-next-scene");
            firstSceneResponse = webClientBuilder.build()
                .post()
                .uri(aiServerUrl + "/ai/generate-next-scene")
                .bodyValue(aiRequest)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
            log.info("AI 서버 첫 씬 생성 완료(fallback)");
        } catch (Exception e) {
            log.error("AI 서버 호출 실패: {}", e.getMessage());
            throw new RuntimeException("AI 서버에서 첫 번째 씬 생성 실패: " + e.getMessage());
        }

        // 응답 구성
        Map<String, Object> response = new HashMap<>(firstSceneResponse);
        response.put("completionId", completion.getId());
        response.put("storyId", request.getStoryId());
        log.info("=== 첫 번째 씬 생성 완료 === completionId={}", completion.getId());
        return response;
    }

    @Transactional
    public void saveChoice(Long completionId, StoryChoiceRequest request) {
        log.info("== 선택지 저장 시작 ==");
        StoryCompletion completion = storyCompletionRepository.findById(completionId)
            .orElseThrow(() -> new RuntimeException("StoryCompletion을 찾을 수 없습니다: " + completionId));

        StoryCompletion.ChoiceRecord choiceRecord = new StoryCompletion.ChoiceRecord(
            request.getSceneNumber(),
            request.getChoiceId(),
            request.getAbilityType(),
            request.getAbilityPoints() // DTO는 abilityPoints
        );

        completion.getChoicesJson().add(choiceRecord);
        storyCompletionRepository.save(completion);
        log.info("선택지 저장 완료 - Scene: {}, Choice: {}", request.getSceneNumber(), request.getChoiceId());
    }

    @Transactional
    public void completeStory(Long completionId, StoryCompleteRequest request) {
        log.info("=== 동화 완료 처리 시작 ===");
        StoryCompletion completion = storyCompletionRepository.findById(completionId)
            .orElseThrow(() -> new RuntimeException("StoryCompletion을 찾을 수 없습니다: " + completionId));

        completion.setTotalTime(request.getTotalTime());
        completion.setCompletedAt(LocalDateTime.now());
        storyCompletionRepository.save(completion);

        log.info("동화 완료 처리 완료 - 총 소요시간: {}초", request.getTotalTime());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Story getOrCreateStory(String storyId) {
        return storyRepository.findById(storyId)
            .orElseGet(() -> {
                log.info("story가 DB에 없어 임시 생성: {}", storyId);
                Story newStory = Story.builder()
                    .id(storyId)
                    .title("생성된 동화")
                    .category("임시")
                    .build();
                try {
                    return storyRepository.save(newStory);
                } catch (Exception e) {
                    log.warn("Story 저장 중 에러, 재조회: {}", e.getMessage());
                    return storyRepository.findById(storyId)
                        .orElseThrow(() -> new RuntimeException("Story 조회 실패"));
                }
            });
    }

    @Transactional(readOnly = true)
    public StoryCompletionSummaryDto getStoryCompletionSummary(Long completionId) {
        log.info("=== 동화 완료 요약 조회 시작 === completionId={}", completionId);
        StoryCompletion completion = storyCompletionRepository.findById(completionId)
            .orElseThrow(() -> new RuntimeException("StoryCompletion을 찾을 수 없습니다: " + completionId));
        StoryCompletionSummaryDto summary = StoryCompletionSummaryDto.from(completion);
        log.info("동화 완료 요약 조회 완료 - 제목: {}, 아이: {}", summary.getStoryTitle(), summary.getChildName());
        return summary;
    }

    /**
     * [2025-10-28 신규 추가] 다음 씬 생성 (분기형 스토리)
     *
     * 변경 사유: 사용자 선택에 따라 동적으로 다음 씬을 생성하는 분기형 스토리 시스템 구현
     * - DB에서 이전 선택 히스토리를 조회하여 AI 서버에 전달
     * - AI 서버는 이전 선택들을 바탕으로 맥락에 맞는 다음 씬 생성
     * - 최대 8개 씬까지 생성 가능 (8번째 씬에서 isEnding=true)
     *
     * 주요 로직:
     * 1. StoryCompletion에서 이전 선택 히스토리 조회
     * 2. 이전 선택들을 previousChoices 배열로 변환 (choiceId, choiceText, abilityType, abilityScore 포함)
     * 3. AI 서버 /ai/generate-next-scene 엔드포인트 호출
     * 4. AI가 생성한 다음 씬 정보 반환
     *
     * @param completionId 현재 진행 중인 스토리의 completion ID
     * @param nextSceneNumber 생성할 다음 씬 번호 (2~8)
     * @return AI가 생성한 다음 씬 정보 (scene 객체와 isEnding 플래그)
     */
    @Transactional
    public Map<String, Object> generateNextScene(Long completionId, int nextSceneNumber) {
        log.info("=== 다음 씬 생성 시작 === completionId={}, nextSceneNumber={}", completionId, nextSceneNumber);

        StoryCompletion completion = storyCompletionRepository.findById(completionId)
            .orElseThrow(() -> new RuntimeException("StoryCompletion을 찾을 수 없습니다: " + completionId));

        Child child = completion.getChild();
        Story story = completion.getStory();

        // 이전 선택들 조회
        java.util.List<StoryCompletion.ChoiceRecord> previousChoices = completion.getChoicesJson();

        // AI 요청 바디 구성
        Map<String, Object> aiRequest = new HashMap<>();
        aiRequest.put("storyId", story.getId());
        aiRequest.put("childId", child.getId());
        aiRequest.put("childName", child.getName());
        aiRequest.put("emotion", "화남"); // TODO: 초기 감정 저장 후 사용
        aiRequest.put("interests", child.getInterests()); // TODO: null 방어
        aiRequest.put("sceneNumber", nextSceneNumber);

        // [중요] 이전 선택 히스토리를 AI 서버 형식으로 변환
        java.util.List<Map<String, Object>> previousChoicesData = new java.util.ArrayList<>();
        for (StoryCompletion.ChoiceRecord c : previousChoices) {
            Map<String, Object> m = new HashMap<>();
            m.put("sceneNumber", c.getSceneNumber());
            m.put("choiceId", c.getChoiceId());
            m.put("choiceText", "선택 " + c.getChoiceId()); // TODO: 실제 선택 텍스트 저장
            m.put("abilityType", c.getAbilityType());
            m.put("abilityScore", c.getAbilityPoints()); // AI 서버는 abilityScore 명칭 사용
            previousChoicesData.add(m);
        }
        aiRequest.put("previousChoices", previousChoicesData);

        log.info("AI 서버에 다음 씬 생성 요청... (scene {})", nextSceneNumber);

        try {
            Map<String, Object> aiResponse = webClientBuilder.build()
                .post()
                .uri(aiServerUrl + "/ai/generate-next-scene")
                .bodyValue(aiRequest)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

            log.info("AI 서버에서 씬 {} 생성 완료", nextSceneNumber);
            return aiResponse;
        } catch (Exception e) {
            log.error("AI 서버 호출 실패: {}", e.getMessage());
            throw new RuntimeException("AI 서버에서 다음 씬 생성 실패: " + e.getMessage());
        }
    }
}
