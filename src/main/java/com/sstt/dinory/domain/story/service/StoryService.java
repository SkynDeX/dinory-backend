package com.sstt.dinory.domain.story.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
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

import jakarta.persistence.EntityManager;
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
    private final EntityManager entityManager;


    @Value("${ai.server.url:http://localhost:8000}")
    private String aiServerUrl;

    /** 첫 번째 씬 생성 */
    @Transactional
    public Map<String, Object> generateStory(StoryGenerateRequest request) {
        Child child = childRepository.findById(request.getChildId())
            .orElseThrow(() -> new RuntimeException("Child 못 찾음: " + request.getChildId()));
        
        // Pinecone ID로 story 조회/생성
        Story story = getOrCreateStory(request.getStoryId());

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
                .uri(aiServerUrl + "/ai/generate-next-scene")
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

        // AI 응답 전체 로그 추가
        log.info("===== AI 응답 전체 =====");
        log.info("AI Response Keys: {}", firstSceneResponse.keySet());
        log.info("storyTitle in response: {}", firstSceneResponse.get("storyTitle"));
        log.info("scene in response: {}", firstSceneResponse.get("scene"));
        log.info("======================");

        // AI 응답 검증
        if (firstSceneResponse == null) {
            throw new RuntimeException("AI 서버로부터 응답을 받지 못했습니다.");
        }

        // [2025-10-30 김광현] 동화 제목 생성 로직
        String finalTitle = "동화 생성중..."; // 기본값
        if(firstSceneResponse.containsKey("storyTitle")) {
            String aiGeneratedTitle = (String) firstSceneResponse.get("storyTitle");
            
            if(aiGeneratedTitle != null && !aiGeneratedTitle.isBlank()) {
                finalTitle = aiGeneratedTitle;
                log.info("AI가 생성한 동화 제목 업데이트 : {}", aiGeneratedTitle);
            }
        }

        // ai가 storyTitle을 생성을 안하면 첫번째 씬으로 생성
        if(finalTitle.equals("동화 생성중...") && firstSceneResponse.containsKey("scene")) {
            Map<String, Object> sceneData = (Map<String, Object>) firstSceneResponse.get("scene");
            String sceneContent = null;

            if(sceneData.containsKey("content")) {
                sceneContent = (String) sceneData.get("content");
            } else if (sceneData.containsKey(("text"))) {
                sceneContent = (String) sceneData.get("text");
            }

            // 첫 30자를 제목으로 사용
            if(sceneContent != null && !sceneContent.isBlank()) {
                finalTitle = sceneContent.length() > 30 
                    ? sceneContent.substring(0, 30) + "..." 
                    : sceneContent;
                log.info("첫 번째 씬 내용 기반 제목 생성: {}", finalTitle);
            }

        }
        story.setTitle(finalTitle);

        // 감정 기반 카테고리 설정
        String category = mapEmotionToCategory(request.getEmotion());
        story.setCategory(category);
        storyRepository.save(story);
        log.info("Story 업데이트 완료 - title: {}, category: {}", story.getTitle(), category);

        // [2025-10-28 김민중 수정] emotion 저장 -> [2025-10-29 김광현] 위치 변경 AI응답성공해야만 DB에 저장
        StoryCompletion completion = StoryCompletion.builder()
            .child(child)
            .story(story)
            .emotion(request.getEmotion())
            .interests(request.getInterests())  // [2025-10-28 김광현] 추가
            .storyTitle(story.getTitle())       // [2025-10-29 김광현] 제목 저장 추가
            .choicesJson(new ArrayList<>())
            .build();

        storyCompletionRepository.save(completion);
        log.info("[StoryCompletion] AI 응답성공 후 저장완료 : completionId={}, completionTitle={}", completion.getId(), completion.getStoryTitle());



        saveSceneOnly(story, firstSceneResponse, 1);

        Map<String, Object> response = new HashMap<>(firstSceneResponse);
        response.put("completionId", completion.getId());
        response.put("storyId", story.getId());  // [2025-10-29 김광현] 변경
        response.put("pineconeId", story.getPineconeId());  // [2025-10-29 김광현] 추가
        return response;
    }

    // 감정을 카테고리로 저장
    private String mapEmotionToCategory(String emotion) {
        if(emotion == null) {
            return "일반";
        }

        return switch (emotion.toLowerCase()) {
            case "happy", "기뻐요", "행복해요" -> "행복";
            case "sad", "슬퍼요" -> "위로";
            case "angry", "화나요", "화가 나요" -> "감정조절";
            case "scared", "무서워요", "worried", "걱정돼요" -> "용기";
            case "excited", "신나요" -> "모험";
            case "tired", "피곤해요", "sleepy", "졸려요" -> "휴식";
            case "lonely", "외로워요" -> "우정";
            default -> "일반";
        };
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

    // @Transactional(propagation = Propagation.REQUIRES_NEW)
    // public Story getOrCreateStory(String pineconeId) {
    //     return storyRepository.findByPineconeId(pineconeId).orElseGet(() -> {

    //         // [2025-10-29 김광현] sql 에러 방지
    //         Optional<Story> existing = storyRepository.findByPineconeId(pineconeId);
    //         if(existing.isPresent()) {
    //             log.info("기존 Story 재사용: pineconeId={}, storyId={}\", pineconeId, existing.get().getId()");
    //             return existing.get();
    //         }

    //         // 없으면 생성
    //         Story newStory = Story.builder()
    //             .pineconeId(pineconeId)
    //             .title("동화 생성중...")
    //             .category("미분류")
    //             .description("AI가 동화를 생성중입니다.")
    //             .build();

    //         try {
    //             Story saved = storyRepository.save(newStory);
    //             log.info("새로운 story 생성: pineconeId={}, storyId={}", pineconeId, saved.getId()); 
    //             return saved;

    //         } catch (Exception e) {
    //             // [2025-10-29 김광현] 동시성 문제에서 insert시 다시 조회
    //             // UNIQUE 제약조건 위반 (동시성 문제로 다른 트랜잭션에서 이미 생성)
    //             log.warn("동시성 문제로 Story 중복 생성 시도 감지: pineconeId={}", pineconeId);

    //             // 실패한 엔티티 세션 제거(Hibernate 세션 정리)
    //             entityManager.clear();

    //             // 대기 후 재시도하기
    //             try {
    //                 Thread.sleep(100);
    //             } catch (InterruptedException ie) {
    //                 Thread.currentThread().interrupt();
    //             }

    //             // 다시 시도 (3번시도)
    //             for (int i = 0; i < 3; i++) {
    //                 Optional<Story> retryResult = storyRepository.findByPineconeId(pineconeId);
    //                 if(retryResult.isPresent()) {
    //                     log.info("재시도 {}번째 성공 : pineconeId={}", i + 1, pineconeId );
    //                     return retryResult.get();
    //                 }

    //                 // 재시도 대기
    //                 try {
    //                     Thread.sleep(50 * (i +1));
    //                 } catch (InterruptedException ie) {
    //                     Thread.currentThread().interrupt();
    //                 }
    //             }
                
    //             throw new RuntimeException("Story 조회 실패(재시도 3번 실패):" + pineconeId);
    //             // 다시 조회 (다른 트랜잭션에서 생성된 것 사용)
    //             // return storyRepository.findByPineconeId(pineconeId)
    //             //     .orElseThrow(() -> new RuntimeException("Story 조회 실패: " + pineconeId));
    //         }
    //     });
    // }
    @Transactional(propagation = Propagation.REQUIRES_NEW,
                    isolation = Isolation.READ_COMMITTED)
    public Story getOrCreateStory(String pineconeId) {
        Optional<Story> existing = storyRepository.findByPineconeIdWithLock(pineconeId);

        if (existing.isPresent()) {
            log.info("기존 Story 재사용: pineconeId={}", pineconeId);
            return existing.get();
        }
        
        Story newStory = Story.builder()
            .pineconeId(pineconeId)
            .title("동화 생성중...")
            .category("미분류")
            .description("AI가 동화를 생성중입니다.")
            .build();

        try {
            Story saved = storyRepository.save(newStory);
            log.info("새로운 Story 생성: pineconeId={}, id={}", pineconeId, saved.getId());
            return saved;
        } catch (DataIntegrityViolationException e) {
            // UNIQUE 제약조건 위반 - 동시 요청으로 다른 트랜잭션에서 이미 생성됨
            log.warn("동시성 문제 감지, Story 재조회 시도 시작: pineconeId={}", pineconeId);

            entityManager.clear();
            
            // 재시도 로직 (최대 5번)
            for (int i = 0; i < 5; i++) {
                // 점진적으로 대기 시간 증가 (100ms, 200ms, 300ms, 400ms, 500ms)
                try {
                    Thread.sleep(200 * (i + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("재조회 대기 중 인터럽트 발생", ie);
                }

                entityManager.flush();
                entityManager.clear();
                
                // 재조회 시도
                Optional<Story> retryStory = storyRepository.findByPineconeId(pineconeId);

                if (retryStory.isPresent()) {
                    log.info("✓ 재조회 성공 ({}번째 시도, {}ms 대기): pineconeId={}", 
                        i + 1, 200 * (i + 1), pineconeId);
                    return retryStory.get();
                }
                
                log.warn("✗ 재조회 실패 ({}번째 시도): pineconeId={}, 다음 시도 대기중...", 
                    i + 1, pineconeId);
            }
            
            // 5번 재시도 후에도 실패
            log.error("Story 조회 최종 실패 (5번 재시도 완료): pineconeId={}", pineconeId);
            throw new RuntimeException("Story 조회 실패 (5번 재시도 후): " + pineconeId);
        }

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
        aiRequest.put("storyId", story.getPineconeId());
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

    /** 커스텀 선택지 분석 */
    public Map<String, Object> analyzeCustomChoice(Long completionId, Integer sceneNumber, String text) {
        log.info("커스텀 선택지 AI 분석 요청: completionId={}, sceneNumber={}, text={}", 
            completionId, sceneNumber, text);

        // FastAPI로 전달할 요청 생성
        Map<String, Object> aiRequest = new HashMap<>();
        aiRequest.put("completionId", completionId);
        aiRequest.put("sceneNumber", sceneNumber);
        aiRequest.put("text", text);

        // FastAPI 호출
        Map<String, Object> aiResponse = webClientBuilder.build()
            .post()
            .uri(aiServerUrl + "/ai/analyze-custom-choice")
            .bodyValue(aiRequest)
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .block();

        log.info("AI 분석 응답: {}", aiResponse);
        return aiResponse;
    }
}
