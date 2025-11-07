package com.sstt.dinory.domain.story.service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;

import com.sstt.dinory.domain.child.entity.ChildRewardEntity;
import com.sstt.dinory.domain.child.service.ChildRewardService;
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
import com.sstt.dinory.domain.image.dto.ImageGenerationRequest;
import com.sstt.dinory.domain.image.dto.ImageGenerationResponse;
import com.sstt.dinory.domain.image.entity.ImageGeneration;
import com.sstt.dinory.domain.image.repository.ImageGenerationRepository;
import com.sstt.dinory.domain.image.service.ImageService;
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
    private final ImageService imageService;
    private final ImageGenerationRepository imageGenerationRepository;
    private final ChildRewardService childRewardService;  // [2025-11-07 김광현] 공룡알 보상


    @Value("${ai.server.url:http://localhost:8000}")
    private String aiServerUrl;

    /** 첫 번째 씬 생성 */
    @Transactional
    public Map<String, Object> generateStory(StoryGenerateRequest request) {
        // [2025-11-04 김민중 수정] childId 필수 검증 추가
        if (request.getChildId() == null) {
            throw new IllegalArgumentException("childId는 필수입니다. 프론트에서 childId를 전송해주세요.");
        }

        Child child = childRepository.findById(request.getChildId())
            .orElseThrow(() -> new RuntimeException("Child 못 찾음: " + request.getChildId()));
        
        // Pinecone ID로 story 조회/생성
        Story story = getOrCreateStory(request.getStoryId());

        // interests 가져오기: request의 interests 우선, 없으면 child의 interests 사용
        List<String> interests = (request.getInterests() != null && !request.getInterests().isEmpty())
            ? request.getInterests()
            : (child.getInterests() != null ? child.getInterests() : new ArrayList<>());

        // [2025-10-28 김민중 수정] Story의 title과 description을 AI 서버로 전송
        Map<String, Object> aiRequest = new HashMap<>();
        aiRequest.put("storyId", request.getStoryId());
        aiRequest.put("storyTitle", story.getTitle());
        aiRequest.put("storyDescription", story.getDescription());
        aiRequest.put("childId", request.getChildId());
        aiRequest.put("emotion", request.getEmotion());
        aiRequest.put("interests", interests);
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

        story.setTitle(finalTitle);

        // [2025-11-03 김광현] Pinecone에서 가져온 description 업데이트
        // Pinecone 검색 결과에서 description을 가져와야 하는데, 현재는 없음
        // 일단 AI가 생성한 첫 씬 내용을 description으로 사용
        if (firstSceneResponse.containsKey("scene")) {
            Map<String, Object> sceneData = (Map<String, Object>) firstSceneResponse.get("scene");
            String sceneContent = null;
            
            if (sceneData.containsKey("content")) {
                sceneContent = (String) sceneData.get("content");
            } else if (sceneData.containsKey("text")) {
                sceneContent = (String) sceneData.get("text");
            }
            
            if (sceneContent != null && !sceneContent.isBlank()) {
                // 첫 씬 내용을 description으로 사용 (100자 정도)
                String description = sceneContent.length() > 100 
                    ? sceneContent.substring(0, 100) + "..." 
                    : sceneContent;
                story.setDescription(description);
                log.info("Story description 업데이트: {}", description);
            }
        }

        // 감정 기반 카테고리 설정
        String category = mapEmotionToCategory(request.getEmotion());
        story.setCategory(category);
        storyRepository.save(story);
        log.info("Story 업데이트 완료 - title: {}, description: {}, category: {}", 
            story.getTitle(), story.getDescription(), category);

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


        // [2025-11-04 김광현] Scene 먼저 저장 후 이미지 비동기로 진행 화면 렌더링 속도 잡기
        // saveSceneWithImage(story, firstSceneResponse, 1);
        // 이미지 없이 Scene 먼저 저장
        saveSceneTextOnly(story, firstSceneResponse, 1);

        // [2025-11-03 김광현] 저장된 Scene에서 imageUrl 가져오기
        Scene savedScene = sceneRepository.findByStoryAndSceneNumber(story, 1)
                                        .orElse(null);

        // [2025-11-05 김민중 수정] 이미지 비동기 생성 - Story not found 오류 방지를 위한 재시도 로직 추가
        final Long storyId = story.getId();
        final Long sceneId = savedScene != null ? savedScene.getId() : null;

        if (sceneId == null) {
            log.warn("Scene이 저장되지 않아 비동기 이미지 생성을 건너뜁니다.");
        } else {
            CompletableFuture.runAsync(() -> {
                try {
                    // [2025-11-05 김민중 수정] 트랜잭션 커밋을 위해 대기 시간 증가 (100ms -> 500ms)
                    Thread.sleep(500);

                    log.info("== 비동기 이미지 생성 시작 : sceneNumber=1, storyId={}, sceneId={} ==", storyId, sceneId);

                    // [2025-11-05 김민중 수정] Story 조회 재시도 로직 추가 (최대 3회)
                    Story freshStory = null;
                    for (int i = 0; i < 3; i++) {
                        freshStory = storyRepository.findById(storyId).orElse(null);
                        if (freshStory != null) {
                            break;
                        }
                        log.warn("Story 조회 실패, 재시도 {}/3", i + 1);
                        Thread.sleep(200);
                    }

                    if (freshStory == null) {
                        log.error("❌ Story not found after 3 retries: storyId={}", storyId);
                        return;
                    }

                    generateImageForScene(freshStory, 1);
                    log.info("✅ 비동기 이미지 생성 완료: sceneNumber=1, storyId={}", storyId);

                } catch(InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("비동기 이미지 생성 중단: {}", ie.getMessage());
                } catch(Exception e) {
                    log.error("❌ 비동기 이미지 생성 실패(Scene은 저장완료): storyId={}, error={}", storyId, e.getMessage(), e);
                }
            });
        }

        Map<String, Object> response = new HashMap<>(firstSceneResponse);
        response.put("completionId", completion.getId());
        response.put("storyId", story.getId());  // [2025-10-29 김광현] 변경
        response.put("pineconeId", story.getPineconeId());  // [2025-10-29 김광현] 추가

        // [2025-11-03 김광현] imageUrl 추가
//        if(savedScene != null && savedScene.getImageUrl() != null) {
//            response.put("imageUrl", savedScene.getImageUrl());
//            log.info("응답에 imageUrl 추가: {}",
//                        savedScene.getImageUrl().substring(0, Math.min(80, savedScene.getImageUrl().length())));
//        }
        // [2025-11-04 김광현] scene 객체에 sceneId 추가
        if (savedScene != null) {
            // scene 객체 가져오기
            @SuppressWarnings("unchecked")
            Map<String, Object> sceneData = (Map<String, Object>) response.get("scene");
            if (sceneData != null) {
                sceneData.put("sceneId", savedScene.getId());
                log.info("scene에 sceneId 추가: {}", savedScene.getId());
            }

            // imageUrl 추가
            if (savedScene.getImageUrl() != null) {
                response.put("imageUrl", savedScene.getImageUrl());
                log.info("응답에 imageUrl 추가: {}",
                        savedScene.getImageUrl().substring(0, Math.min(80, savedScene.getImageUrl().length())));
            }
        }
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

        // 동화 완료 시 별 1개 추가하기
        try {
            Long childId = completion.getChild().getId();
            ChildRewardEntity reward = childRewardService.addStar(childId);
            log.info("동화 완료 보상 지급: completionId={}, childId={}, 현재별={}/5, 보유알={}",
                    completionId, childId, reward.getStars(), reward.getEggs());
        } catch (Exception e) {
            log.error("보상 지급 실패: completionId={}, error={}", completionId, e.getMessage());
            // 보상 지금 실패해도 동화 완료는 정상
        }
    }

    
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

    /**
     * [2025-11-04 김민중 수정] Scene 정보도 함께 조회
     */
    @Transactional(readOnly = true)
    public StoryCompletionSummaryDto getStoryCompletionSummary(Long completionId) {
        StoryCompletion completion = storyCompletionRepository.findById(completionId)
            .orElseThrow(() -> new RuntimeException("StoryCompletion 없음: " + completionId));

        // Scene 정보 조회
        List<Scene> scenes = sceneRepository.findByStoryIdOrderBySceneNumberAsc(completion.getStory().getId());

        // SceneDto 변환
        List<StoryCompletionSummaryDto.SceneDto> sceneDtos = scenes.stream()
            .map(scene -> StoryCompletionSummaryDto.SceneDto.builder()
                .sceneNumber(scene.getSceneNumber())
                .content(scene.getContent())
                .imageUrl(scene.getImageUrl())
                .build())
            .collect(Collectors.toList());

        // StoryCompletionSummaryDto 생성 후 Scene 정보 추가
        StoryCompletionSummaryDto summary = StoryCompletionSummaryDto.from(completion);
        return StoryCompletionSummaryDto.builder()
            .completionId(summary.getCompletionId())
            .childId(summary.getChildId())
            .childName(summary.getChildName())
            .storyId(summary.getStoryId())
            .pineconeId(summary.getPineconeId())
            .storyTitle(summary.getStoryTitle())
            .totalTime(summary.getTotalTime())
            .completedAt(summary.getCompletedAt())
            .choices(summary.getChoices())
            .scenes(sceneDtos)  // Scene 정보 추가
            .totalCourage(summary.getTotalCourage())
            .totalEmpathy(summary.getTotalEmpathy())
            .totalCreativity(summary.getTotalCreativity())
            .totalResponsibility(summary.getTotalResponsibility())
            .totalFriendship(summary.getTotalFriendship())
            .build();
    }

    /** 다음 씬 생성 */
    @Transactional
    public Map<String, Object> generateNextScene(Long completionId, int nextSceneNumber) {
        StoryCompletion completion = storyCompletionRepository.findById(completionId)
            .orElseThrow(() -> new RuntimeException("StoryCompletion 없음: " + completionId));

        Child child = completion.getChild();
        Story story = completion.getStory();

        // [2025-10-28 김민중 수정] interests 우선순위: completion에 저장된 값 > child의 interests
        List<String> interests = (completion.getInterests() != null && !completion.getInterests().isEmpty())
            ? completion.getInterests()
            : (child.getInterests() != null ? child.getInterests() : new ArrayList<>());

        // [2025-10-28 김민중 수정] Story의 title과 description을 AI 서버로 전송
        // emotion과 interests를 completion에서 가져옴
        Map<String, Object> aiRequest = new HashMap<>();
        aiRequest.put("storyId", story.getPineconeId());
        aiRequest.put("storyTitle", story.getTitle());
        aiRequest.put("storyDescription", story.getDescription());
        aiRequest.put("childId", child.getId());
        aiRequest.put("emotion", completion.getEmotion() != null ? completion.getEmotion() : "중립");
        aiRequest.put("interests", interests);
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

        saveSceneTextOnly(story, aiResponse, nextSceneNumber);

        // [2025-11-03 김광현] 저장된 Scene에서 imageUrl 가져오기
        //  Scene savedScene = sceneRepository.findByStoryAndSceneNumber(story, nextSceneNumber)
        //    .orElse(null);
        // [2025-11-04 김광현] 이미지는 비동기로
        // [2025-11-05 김민중 수정] Scene 없음 오류 방지를 위한 재시도 로직 추가
        final Long storyId = story.getId();
        final int finalSceneNumber = nextSceneNumber;
        CompletableFuture.runAsync(() -> {
            try {
                // [2025-11-05 김민중 수정] 트랜잭션 커밋을 위해 대기 시간 증가 (100ms -> 500ms)
                Thread.sleep(500);

                log.info("=== 비동기 이미지 생성 시작: sceneNumber={}, storyId={} ===", finalSceneNumber, storyId);

                // [2025-11-05 김민중 수정] Story 조회 재시도 로직 추가 (최대 3회)
                Story freshStory = null;
                for (int i = 0; i < 3; i++) {
                    freshStory = storyRepository.findById(storyId).orElse(null);
                    if (freshStory != null) {
                        break;
                    }
                    log.warn("Story 조회 실패, 재시도 {}/3", i + 1);
                    Thread.sleep(200);
                }

                if (freshStory == null) {
                    log.error("❌ Story not found after 3 retries: storyId={}", storyId);
                    return;
                }

                // [2025-11-05 김민중 수정] Scene 조회 재시도 로직 추가 (최대 3회)
                Scene scene = null;
                for (int i = 0; i < 3; i++) {
                    scene = sceneRepository.findByStoryAndSceneNumber(freshStory, finalSceneNumber).orElse(null);
                    if (scene != null) {
                        break;
                    }
                    log.warn("Scene 조회 실패, 재시도 {}/3: sceneNumber={}", i + 1, finalSceneNumber);
                    Thread.sleep(300);
                }

                if (scene == null) {
                    log.error("❌ Scene not found after 3 retries: sceneNumber={}, storyId={}", finalSceneNumber, storyId);
                    return;
                }

                generateImageForScene(freshStory, finalSceneNumber);
                log.info("✅ 비동기 이미지 생성 완료: sceneNumber={}, storyId={}", finalSceneNumber, storyId);

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("비동기 이미지 생성 중단: {}", ie.getMessage());
            } catch (Exception e) {
                log.error("❌ 비동기 이미지 생성 실패 (Scene은 저장됨): sceneNumber={}, error={}", finalSceneNumber, e.getMessage(), e);
            }
        });

        // [2025-11-03 김광현] imageUrl을 응답에 추가
//        if (savedScene != null && savedScene.getImageUrl() != null) {
//            aiResponse.put("imageUrl", savedScene.getImageUrl());
//            log.info("응답에 imageUrl 추가: {}", savedScene.getImageUrl().substring(0, Math.min(80, savedScene.getImageUrl().length())));
//        }
        // [2025-11-04 김광현] 저장된 Scene에서 sceneId 가져오기
        Scene savedScene = sceneRepository.findByStoryAndSceneNumber(story, nextSceneNumber)
                .orElse(null);

        // scene 객체에 sceneId 추가
        if (savedScene != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> sceneData = (Map<String, Object>) aiResponse.get("scene");
            if (sceneData != null) {
                sceneData.put("sceneId", savedScene.getId());
                log.info("scene에 sceneId 추가: {}", savedScene.getId());
            }
        }

        // 즉시 응답 반환 (imageUrl 없이)
        aiResponse.put("imageUrl", null);
        log.info("다음 장면 응답 반환 (이미지는 백그라운드 생성 중)");

        return aiResponse;
    }

    /** [2025-11-03 김광현] AI 응답으로 Scene 저장 + 이미지 생성/캐싱 */
    private void saveSceneWithImage(Story story, Map<String, Object> aiResponse, int sceneNumber) {
        log.info("=== saveSceneWithImage 호출됨: sceneNumber={} ===", sceneNumber);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> sceneData = (Map<String, Object>) aiResponse.get("scene");
            if (sceneData == null) {
                return;
            }

            // [2025-11-03 김광현] Scene이 이미 존재하는지 확인
            Optional<Scene> existingSceneOpt = sceneRepository.findByStoryAndSceneNumber(story, sceneNumber);
            Scene scene;
            
            if (existingSceneOpt.isPresent()) {
                scene = existingSceneOpt.get();
                log.info("Scene이 이미 존재함: sceneNumber={}, sceneId={}", sceneNumber, scene.getId());
            } else {
                // Scene 생성
                String content = (String) sceneData.get("text");
                if (content == null) {
                    content = (String) sceneData.get("content");
                }
                if (content == null) {
                    content = "";
                }

                scene = Scene.builder()
                        .story(story)
                        .sceneNumber(sceneNumber)
                        .content(content)
                        .imageUrl(null)
                        .imagePrompt(null)
                        .build();
                
                scene = sceneRepository.save(scene);
                log.info("Scene 생성 완료: sceneNumber={}, sceneId={}", sceneNumber, scene.getId());
            }

            // [2025-11-03 김광현] 이미지가 없으면 생성 또는 캐시에서 가져오기
            if (scene.getImageUrl() == null && !scene.getContent().isEmpty()) {
                try {
                    // [2025-11-05 수정] storyId 전달하여 캐릭터 설명 자동 조회
                    String imagePrompt = translateToEnglishImagePrompt(scene.getContent(), story.getPineconeId());
                    log.info("씬 {} 이미지 프롬프트: {}", sceneNumber, imagePrompt);
                    
                    // [2025-11-03 김광현] 동일한 프롬프트로 생성된 이미지가 있는지 확인
                    Optional<ImageGeneration> cachedImage = imageGenerationRepository
                        .findFirstByPromptAndStatusOrderByCompletedAtDesc(imagePrompt, "completed");
                    
                    if (cachedImage.isPresent() && cachedImage.get().getImageUrl() != null) {
                        // 캐시된 이미지 사용
                        String cachedUrl = cachedImage.get().getImageUrl();
                        scene.setImageUrl(cachedUrl);
                        scene.setImagePrompt(imagePrompt);
                        sceneRepository.save(scene);
                        log.info("씬 {} 캐시된 이미지 사용: {}", sceneNumber, 
                            cachedUrl.substring(0, Math.min(80, cachedUrl.length())));
                    } else {
                        // 새로 이미지 생성
                        log.info("씬 {} 이미지 생성 시작...", sceneNumber);
                        
                        ImageGenerationRequest imageRequest = ImageGenerationRequest.builder()
                            .sceneId(scene.getId())
                            .prompt(imagePrompt)
                            .style("fantasy-art")
                            .build();
                        
                        ImageGenerationResponse imageResponse = imageService.generateImage(imageRequest);
                        
                        if ("completed".equals(imageResponse.getStatus()) && imageResponse.getImageUrl() != null) {
                            scene.setImageUrl(imageResponse.getImageUrl());
                            scene.setImagePrompt(imagePrompt);
                            sceneRepository.save(scene);
                            log.info("씬 {} 이미지 생성 완료: {}", sceneNumber, 
                                imageResponse.getImageUrl().substring(0, Math.min(80, imageResponse.getImageUrl().length())));
                        } else {
                            log.warn("씬 {} 이미지 생성 실패: status={}", sceneNumber, imageResponse.getStatus());
                        }
                    }
                } catch (Exception e) {
                    log.warn("씬 {} 이미지 생성/캐싱 실패 (Scene은 저장됨): {}", sceneNumber, e.getMessage());
                }
            } else if (scene.getImageUrl() != null) {
                log.info("씬 {} 이미지가 이미 존재함", sceneNumber);
            }

        } catch (Exception e) {
            log.error("Scene 저장 오류: {}", e.getMessage(), e);
        }
    }

    // Scene 텍스트 번저 저장(이미지는 나중에)
    private void saveSceneTextOnly(Story story, Map<String, Object> aiResponse, int sceneNumber) {
        log.info("=== saveSceneTextOnly 호출: sceneNumber={} ===", sceneNumber);
        try {
            @SuppressWarnings("unchecked")
                    Map<String, Object> sceneData = (Map<String, Object>) aiResponse.get("scene");
            if(sceneData == null) {
                log.warn("scene 데이터가 없음");
                return;
            }

            // scene이 이미 존재하는지 확인
            Optional<Scene> existingSceneOpt = sceneRepository.findByStoryAndSceneNumber(story, sceneNumber);

            if(existingSceneOpt.isPresent()) {
                log.info("Scene이 이미 존재함: sceneNumber={}", sceneNumber);
                return;
            }

            // Scene 텍스트 추출
            String content = (String) sceneData.get("text");
            if(content == null) {
                content = (String) sceneData.get("content");
            }

            if (content == null) {
                content = "";
            }

            // Scene 생성(이미지 없이)
            Scene scene = Scene.builder()
                    .story(story)
                    .sceneNumber(sceneNumber)
                    .content(content)
                    .imageUrl(null)
                    .imagePrompt(null)
                    .build();

            sceneRepository.save(scene);
            log.info("Scene 텍스트 저장 완료: sceneNumber={}, sceneId={}", sceneNumber, scene.getId());
        } catch (Exception e) {
            log.error("Scene 텍스트 저장 오류: {}", e.getMessage(), e);
        }
    }

    /** 저장된 Scene에 이미지만 추가 */
    private void generateImageForScene(Story story, int sceneNumber) {
        log.info("=== generateImageForScene 호출: sceneNumber={} ===", sceneNumber);
        try {
            // Scene 조회
            Scene scene = sceneRepository.findByStoryAndSceneNumber(story, sceneNumber)
                    .orElseThrow(() -> new RuntimeException("Scene 없음: sceneNumber=" + sceneNumber));

            // 이미지가 이미 있으면 스킵
            if (scene.getImageUrl() != null) {
                log.info("씬 {} 이미지가 이미 존재함", sceneNumber);
                return;
            }

            // 이미지가 없으면 생성
            if (!scene.getContent().isEmpty()) {
                // [2025-11-05 수정] storyId 전달하여 캐릭터 설명 자동 조회
                String imagePrompt = translateToEnglishImagePrompt(scene.getContent(), story.getPineconeId());
                log.info("씬 {} 이미지 프롬프트: {}", sceneNumber, imagePrompt);

                // 캐시 확인
                Optional<ImageGeneration> cachedImage = imageGenerationRepository
                        .findFirstByPromptAndStatusOrderByCompletedAtDesc(imagePrompt, "completed");

                if (cachedImage.isPresent() && cachedImage.get().getImageUrl() != null) {
                    // 캐시된 이미지 사용
                    String cachedUrl = cachedImage.get().getImageUrl();
                    scene.setImageUrl(cachedUrl);
                    scene.setImagePrompt(imagePrompt);
                    sceneRepository.save(scene);
                    log.info("씬 {} 캐시된 이미지 사용: {}", sceneNumber,
                            cachedUrl.substring(0, Math.min(80, cachedUrl.length())));
                } else {
                    // 새로 이미지 생성
                    log.info("씬 {} 이미지 생성 시작...", sceneNumber);

                    ImageGenerationRequest imageRequest = ImageGenerationRequest.builder()
                            .sceneId(scene.getId())
                            .prompt(imagePrompt)
                            .style("fantasy-art")
                            .build();

                    ImageGenerationResponse imageResponse = imageService.generateImage(imageRequest);

                    if ("completed".equals(imageResponse.getStatus()) && imageResponse.getImageUrl() != null) {
                        scene.setImageUrl(imageResponse.getImageUrl());
                        scene.setImagePrompt(imagePrompt);
                        sceneRepository.save(scene);
                        log.info("씬 {} 이미지 생성 완료: {}", sceneNumber,
                                imageResponse.getImageUrl().substring(0, Math.min(80, imageResponse.getImageUrl().length())));
                    } else {
                        log.warn("씬 {} 이미지 생성 실패: status={}", sceneNumber, imageResponse.getStatus());
                    }
                }
            }

        } catch (Exception e) {
            log.error("씬 {} 이미지 생성 실패: {}", sceneNumber, e.getMessage(), e);
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

    // 완성된 동화 전체 조회(다시보기용)
    @Transactional(readOnly = true)
    public Map<String, Object> getFullStory(Long completionId) {
        // StoryCompletion 조회
        StoryCompletion completion = storyCompletionRepository.findById(completionId)
            .orElseThrow(() -> new RuntimeException("StoryCompletion 없음: " + completionId));

        // 동화가 완료 되지 않으면 조회 불가
        if(completion.getCompletedAt() == null) {
            throw new RuntimeException("완료되지 않은 동화입니다!: " + completionId);
        }

        Story story = completion.getStory();

        // 모든 씬 조회
        List<Scene> scenes = sceneRepository.findByStoryOrderBySceneNumber(story);
        
        //  각 씬 선택지 조회
        List<Map<String, Object>> sceneDataList = new ArrayList<>();
        for (Scene scene : scenes) {
            Map<String, Object> sceneData = new HashMap<>();
            sceneData.put("sceneNumber", scene.getSceneNumber());
            sceneData.put("content", scene.getContent());
            sceneData.put("imageUrl", scene.getImageUrl());
            sceneData.put("imagePrompt", scene.getImagePrompt());

            // 해당 씬 모든 선택지 조회
            List<Choice> choices = choiceRepository.findByScene(scene);
            List<Map<String, Object>> choiceDataList = new ArrayList<>();

            for(Choice choice: choices) {
                Map<String, Object> choiceData = new HashMap<>();
                choiceData.put("id", choice.getId());
                choiceData.put("text", choice.getChoiceText());
                choiceData.put("abilityType", choice.getAbilityType());
                choiceData.put("abilityPoints", choice.getAbilityPoints());
                choiceDataList.add(choiceData);
            }

            sceneData.put("choices", choiceDataList);

            // 사용자가 선택한 선택지 찾기
            String selectedChoiceText = null;
            for(StoryCompletion.ChoiceRecord record : completion.getChoicesJson()) {
                if(record.getSceneNumber().equals(scene.getSceneNumber())) {
                    selectedChoiceText = record.getChoiceText();
                    break;
                }
            }

            sceneData.put("selectedChoiceText", selectedChoiceText);

            sceneDataList.add(sceneData);
        }
        // 5. 응답 구성
        Map<String, Object> response = new HashMap<>();
        response.put("completionId", completion.getId());
        response.put("storyTitle", completion.getStoryTitle());
        response.put("emotion", completion.getEmotion());
        response.put("interests", completion.getInterests());
        response.put("totalTime", completion.getTotalTime());
        response.put("completedAt", completion.getCompletedAt());
        response.put("scenes", sceneDataList);
        response.put("totalAbilityScore", completion.getAbilityScore());

        return response;
    }

    /**
     * [2025-11-04 김민중 수정] 한글 동화 내용을 AI 서버에서 영어 이미지 프롬프트로 변환
     * [2025-11-05 수정] storyId 파라미터 추가하여 캐릭터 설명 자동 조회
     * FastAPI 서버의 /ai/create-image-prompt를 호출하여 핵심 시각적 요소만 추출
     */
    private String translateToEnglishImagePrompt(String koreanText, String storyId) {
        // 입력 검증
        if (koreanText == null || koreanText.trim().isEmpty()) {
            log.warn("동화 내용이 비어있음, 기본 프롬프트 사용");
            // [2025-11-05 김민중 수정] 캐릭터 일관성을 위한 기본 프롬프트
            return "A cute child character, consistent anime art style, Studio Ghibli inspired, same character design, kawaii, soft pastel color palette";
        }

        try {
            // FastAPI 서버에 프롬프트 생성 요청
            Map<String, Object> request = new HashMap<>();
            request.put("koreanText", koreanText);
            request.put("maxLength", 200);  // [2025-11-05 수정] 70 → 200자로 증가 (충분한 설명 보장)
            request.put("storyId", storyId);  // [2025-11-05 추가] 캐릭터 설명 자동 조회용

            log.debug("AI 프롬프트 생성 요청: {}자", koreanText.length());

            Map<String, Object> response = webClientBuilder.build()
                .post()
                .uri(aiServerUrl + "/ai/create-image-prompt")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

            if (response != null && response.containsKey("imagePrompt")) {
                String imagePrompt = (String) response.get("imagePrompt");

                // 프롬프트 검증
                if (imagePrompt != null && !imagePrompt.trim().isEmpty()) {
                    log.info("AI 프롬프트 생성 완료: {}자 → {}자 ({})",
                        koreanText.length(),
                        imagePrompt.length(),
                        imagePrompt.substring(0, Math.min(50, imagePrompt.length())));
                    return imagePrompt;
                }
            }

            log.warn("AI 프롬프트 생성 응답이 비어있음, 폴백 사용");
            return createFallbackPrompt(koreanText);

        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            log.error("AI 서버 응답 오류 ({}): {}, 폴백 사용", e.getStatusCode(), e.getMessage());
            return createFallbackPrompt(koreanText);
        } catch (Exception e) {
            log.error("AI 프롬프트 생성 실패: {}, 폴백 사용", e.getMessage());
            return createFallbackPrompt(koreanText);
        }
    }

    /**
     * [2025-11-04 김민중 추가] 폴백 프롬프트 생성
     * [2025-11-05 김민중 수정] 캐릭터 일관성을 위한 프롬프트 강화
     */
    private String createFallbackPrompt(String koreanText) {
        // [2025-11-05 김민중 수정] 캐릭터 일관성 키워드를 기본으로 설정
        String basePrompt = "A cute child character, consistent anime art style, Studio Ghibli inspired, same character design, kawaii, soft pastel color palette";

        // 동화 내용에서 간단한 키워드 추출
        if (koreanText.contains("숲") || koreanText.contains("나무")) {
            basePrompt += ", magical forest scene";
        } else if (koreanText.contains("바다") || koreanText.contains("물고기")) {
            basePrompt += ", peaceful ocean scene";
        } else if (koreanText.contains("하늘") || koreanText.contains("구름")) {
            basePrompt += ", dreamy sky scene";
        }

        if (koreanText.contains("친구") || koreanText.contains("함께")) {
            basePrompt += ", friendship theme";
        }

        if (koreanText.contains("용기") || koreanText.contains("용감")) {
            basePrompt += ", brave character";
        }

        // [2025-11-05 김민중 수정] 따뜻한 분위기 추가
        basePrompt += ", warm and friendly atmosphere";

        log.info("폴백 프롬프트 생성: {}", basePrompt);
        return basePrompt;
    }
}
