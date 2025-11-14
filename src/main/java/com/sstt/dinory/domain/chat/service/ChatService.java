package com.sstt.dinory.domain.chat.service;

import com.sstt.dinory.domain.chat.dto.ChatInitFromStoryRequest;
import com.sstt.dinory.domain.chat.dto.ChatInitRequest;
import com.sstt.dinory.domain.chat.dto.ChatMessageRequest;
import com.sstt.dinory.domain.chat.dto.ChatResponseDto;
import com.sstt.dinory.domain.chat.dto.GenerateChoicesRequest;
import com.sstt.dinory.domain.chat.dto.GenerateChoicesResponse;
import com.sstt.dinory.domain.chat.dto.NavigationIntentRequest;
import com.sstt.dinory.domain.chat.dto.NavigationIntentResponse;
import com.sstt.dinory.domain.chat.entity.ChatMessage;
import com.sstt.dinory.domain.chat.entity.ChatSession;
import com.sstt.dinory.domain.chat.repository.ChatMessageRepository;
import com.sstt.dinory.domain.chat.repository.ChatSessionRepository;
import com.sstt.dinory.domain.story.dto.StoryCompletionSummaryDto;
import com.sstt.dinory.domain.story.entity.StoryCompletion;
import com.sstt.dinory.domain.story.repository.StoryCompletionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final StoryCompletionRepository storyCompletionRepository;
    private final com.sstt.dinory.domain.story.repository.SceneRepository sceneRepository;  // Scene 조회용
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${ai.server.url:http://localhost:8000}")
    private String aiServerUrl;

    @Transactional
    public ChatResponseDto initChatSession(ChatInitRequest request) {
        // [2025-11-07 수정] 기존 활성 세션 재사용 (없으면 생성)
        ChatSession session = chatSessionRepository
                .findTopByChildIdAndEndedAtIsNullOrderByStartedAtDesc(request.getChildId())
                .orElseGet(() -> {
                    log.info("✅ 활성 세션 없음 - 새 세션 생성");
                    ChatSession newSession = ChatSession.builder()
                            .childId(request.getChildId())
                            .build();
                    return chatSessionRepository.save(newSession);
                });

        log.info("Chat session initialized: sessionId={} (기존 세션 재사용)", session.getId());

        // 기존 메시지 조회
        List<ChatMessage> messages = chatMessageRepository.findByChatSessionIdOrderByCreatedAtAsc(session.getId());

        return ChatResponseDto.builder()
                .sessionId(session.getId())
                .childId(session.getChildId())
                .startedAt(session.getStartedAt())
                .messages(convertToMessageDtos(messages))
                .build();
    }

    @Transactional
    public ChatResponseDto initChatSessionFromStory(ChatInitFromStoryRequest request) {
        log.info("=== 동화 기반 챗봇 세션 시작 ===");
        log.info("completionId: {}", request.getCompletionId());
        log.info("★★★ initChatSessionFromStory 호출됨! ★★★");

        // StoryCompletion 조회
        StoryCompletion completion = storyCompletionRepository.findById(request.getCompletionId())
                .orElseThrow(() -> new RuntimeException("StoryCompletion을 찾을 수 없습니다: " + request.getCompletionId()));

        // StoryCompletion 요약 정보 생성
        StoryCompletionSummaryDto summary = StoryCompletionSummaryDto.from(completion);

        // Scene 정보 조회 및 추가
        List<com.sstt.dinory.domain.story.entity.Scene> scenes =
                sceneRepository.findByStoryIdOrderBySceneNumberAsc(completion.getStory().getId());

        log.info("★ Scene 조회 시작: story_id={}", completion.getStory().getId());
        log.info("★ 조회된 Scene 개수: {}", scenes.size());

        List<StoryCompletionSummaryDto.SceneDto> sceneDtos = scenes.stream()
                .map(scene -> {
                    log.info("★ Scene {}: content length = {}", scene.getSceneNumber(),
                             scene.getContent() != null ? scene.getContent().length() : 0);
                    return StoryCompletionSummaryDto.SceneDto.builder()
                            .sceneNumber(scene.getSceneNumber())
                            .content(scene.getContent())
                            .imageUrl(scene.getImageUrl())
                            .build();
                })
                .collect(Collectors.toList());

        summary = summary.toBuilder()
                .scenes(sceneDtos)
                .build();

        log.info("★ Scene 정보 추가 완료: {} 개", sceneDtos.size());
        if (!sceneDtos.isEmpty()) {
            log.info("★ 첫 번째 scene preview: sceneNumber={}, content 앞 100자={}",
                     sceneDtos.get(0).getSceneNumber(),
                     sceneDtos.get(0).getContent() != null && sceneDtos.get(0).getContent().length() > 100
                         ? sceneDtos.get(0).getContent().substring(0, 100)
                         : sceneDtos.get(0).getContent());
        }

        // [2025-11-07 수정] 기존 활성 세션 재사용 (없으면 생성)
        final Long childId = summary.getChildId(); // 람다 표현식을 위해 final 변수로 추출
        ChatSession session = chatSessionRepository
                .findTopByChildIdAndEndedAtIsNullOrderByStartedAtDesc(childId)
                .orElseGet(() -> {
                    log.info("✅ 활성 세션 없음 - 새 세션 생성");
                    ChatSession newSession = ChatSession.builder()
                            .childId(childId)
                            .build();
                    return chatSessionRepository.save(newSession);
                });

        // [2025-11-07 추가] 가장 최근 읽은 동화 정보 업데이트
        session.setStoryCompletionId(request.getCompletionId());
        session = chatSessionRepository.save(session);

        log.info("Chat session from story: sessionId={}, storyId={} (기존 세션 재사용, 최근 동화 업데이트)",
                 session.getId(), summary.getStoryId());

        // AI에게 동화 기반 첫 인사 메시지 생성 요청
        String firstAiMessage = generateFirstMessageFromStory(session.getId(), summary);

        // AI의 첫 메시지 저장
        ChatMessage aiMessage = ChatMessage.builder()
                .chatSession(session)
                .sender("AI")
                .message(firstAiMessage)
                .build();

        chatMessageRepository.save(aiMessage);

        // 전체 메시지 조회
        List<ChatMessage> allMessages = chatMessageRepository.findByChatSessionIdOrderByCreatedAtAsc(session.getId());

        return ChatResponseDto.builder()
                .sessionId(session.getId())
                .childId(session.getChildId())
                .aiResponse(firstAiMessage)
                .messages(convertToMessageDtos(allMessages))
                .startedAt(session.getStartedAt())
                .build();
    }

    @Transactional
    public ChatResponseDto sendMessage(ChatMessageRequest request) {
        // 세션 조회
        ChatSession session = chatSessionRepository.findById(request.getSessionId())
                .orElseThrow(() -> new RuntimeException("Chat session not found: " + request.getSessionId()));

        // 사용자 메시지 저장
        ChatMessage userMessage = ChatMessage.builder()
                .chatSession(session)
                .sender("USER")
                .message(request.getMessage())
                .build();

        chatMessageRepository.save(userMessage);

        // AI 응답 생성
        String aiResponse = generateAIResponse(request.getSessionId(), request.getMessage(), session.getChildId());

        // AI 메시지 저장
        ChatMessage aiMessage = ChatMessage.builder()
                .chatSession(session)
                .sender("AI")
                .message(aiResponse)
                .build();

        chatMessageRepository.save(aiMessage);

        // [2025-11-04 김민중 추가] Pinecone에 대화 동기화 (비동기, 실패해도 무시)
        syncConversationToPinecone(
                session.getId(),
                session.getChildId(),
                request.getMessage(),
                aiResponse,
                aiMessage.getId()
        );

        // 전체 메시지 조회
        List<ChatMessage> allMessages = chatMessageRepository.findByChatSessionIdOrderByCreatedAtAsc(session.getId());

        return ChatResponseDto.builder()
                .sessionId(session.getId())
                .childId(session.getChildId())
                .aiResponse(aiResponse)
                .messages(convertToMessageDtos(allMessages))
                .startedAt(session.getStartedAt())
                .endedAt(session.getEndedAt())
                .build();
    }

    @Transactional
    public void endChatSession(Long sessionId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Chat session not found: " + sessionId));

        session.setEndedAt(LocalDateTime.now());
        chatSessionRepository.save(session);

        log.info("Chat session ended: {}", sessionId);
    }

    /**
     * [2025-11-07 추가] 대화 종료 버튼 클릭 기록 (세션은 활성 유지)
     * - last_closed_at 필드만 업데이트
     * - ended_at은 null로 유지하여 세션 활성 상태 유지
     * - 다음에 디노 클릭 시 이전 대화 이어서 가능
     */
    @Transactional
    public void recordChatClose(Long sessionId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Chat session not found: " + sessionId));

        session.setLastClosedAt(LocalDateTime.now());
        chatSessionRepository.save(session);

        log.info("Chat close button clicked (session remains active): sessionId={}, lastClosedAt={}",
                 sessionId, session.getLastClosedAt());
    }

    /**
     * [2025-11-04 김민중 수정] Pinecone 우선 조회, 실패 시 MySQL 사용
     */
    public ChatResponseDto getChatSession(Long sessionId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Chat session not found: " + sessionId));

        List<ChatResponseDto.ChatMessageDto> messageDtos;

        // Pinecone에서 대화 조회 시도
        List<Map<String, Object>> pineconeConvs = getConversationHistoryFromPinecone(sessionId, 100);

        if (!pineconeConvs.isEmpty()) {
            log.info("✅ Pinecone에서 대화 조회 성공: {} 메시지", pineconeConvs.size());
            messageDtos = pineconeConvs.stream()
                    .flatMap(conv -> {
                        // Pinecone은 대화 쌍(message + response)으로 저장됨
                        java.util.List<ChatResponseDto.ChatMessageDto> pair = new java.util.ArrayList<>();

                        // [2025-11-10 수정] 빈 메시지 필터링
                        String userMessage = (String) conv.get("message");
                        String aiResponse = (String) conv.get("response");

                        if (userMessage != null && !userMessage.trim().isEmpty()) {
                            pair.add(ChatResponseDto.ChatMessageDto.builder()
                                    .id(Long.parseLong(conv.get("message_id").toString().replace("msg_", "")))
                                    .sender("USER")
                                    .message(userMessage)
                                    .createdAt(java.time.LocalDateTime.parse((String) conv.get("created_at")))
                                    .build());
                        }

                        if (aiResponse != null && !aiResponse.trim().isEmpty()) {
                            pair.add(ChatResponseDto.ChatMessageDto.builder()
                                    .id(Long.parseLong(conv.get("message_id").toString().replace("msg_", "")) + 1)
                                    .sender("AI")
                                    .message(aiResponse)
                                    .createdAt(java.time.LocalDateTime.parse((String) conv.get("created_at")))
                                    .build());
                        }

                        return pair.stream();
                    })
                    .collect(Collectors.toList());
            log.info("📊 Pinecone 메시지 파싱 완료: {} 개 메시지", messageDtos.size());
        } else {
            // Fallback: MySQL에서 조회
            log.warn("⚠️ Pinecone 조회 실패, MySQL 사용");
            List<ChatMessage> messages = chatMessageRepository.findByChatSessionIdOrderByCreatedAtAsc(sessionId);
            messageDtos = convertToMessageDtos(messages);
            log.info("📊 MySQL에서 {} 개 메시지 로드", messageDtos.size());
        }

        return ChatResponseDto.builder()
                .sessionId(session.getId())
                .childId(session.getChildId())
                .messages(messageDtos)
                .startedAt(session.getStartedAt())
                .endedAt(session.getEndedAt())
                .build();
    }

    /**
     * [2025-11-04 김민중 수정] Pinecone에서 아이별 대화 세션 조회
     */
    public List<ChatResponseDto> getChatSessionsByChild(Long childId) {
        List<ChatSession> sessions = chatSessionRepository.findByChildIdOrderByStartedAtDesc(childId);

        return sessions.stream()
                .map(session -> {
                    List<ChatResponseDto.ChatMessageDto> messageDtos;

                    // Pinecone에서 조회 시도
                    List<Map<String, Object>> pineconeConvs = getConversationHistoryFromPinecone(session.getId(), 100);

                    if (!pineconeConvs.isEmpty()) {
                        log.debug("✅ Pinecone에서 세션 {} 조회 성공", session.getId());
                        messageDtos = pineconeConvs.stream()
                                .flatMap(conv -> {
                                    java.util.List<ChatResponseDto.ChatMessageDto> pair = new java.util.ArrayList<>();

                                    // [2025-11-10 수정] 빈 메시지 필터링
                                    String userMessage = (String) conv.get("message");
                                    String aiResponse = (String) conv.get("response");

                                    if (userMessage != null && !userMessage.trim().isEmpty()) {
                                        pair.add(ChatResponseDto.ChatMessageDto.builder()
                                                .id(Long.parseLong(conv.get("message_id").toString().replace("msg_", "")))
                                                .sender("USER")
                                                .message(userMessage)
                                                .createdAt(java.time.LocalDateTime.parse((String) conv.get("created_at")))
                                                .build());
                                    }

                                    if (aiResponse != null && !aiResponse.trim().isEmpty()) {
                                        pair.add(ChatResponseDto.ChatMessageDto.builder()
                                                .id(Long.parseLong(conv.get("message_id").toString().replace("msg_", "")) + 1)
                                                .sender("AI")
                                                .message(aiResponse)
                                                .createdAt(java.time.LocalDateTime.parse((String) conv.get("created_at")))
                                                .build());
                                    }

                                    return pair.stream();
                                })
                                .collect(Collectors.toList());
                    } else {
                        // Fallback: MySQL 사용
                        List<ChatMessage> messages = chatMessageRepository.findByChatSessionIdOrderByCreatedAtAsc(session.getId());
                        messageDtos = convertToMessageDtos(messages);
                    }

                    return ChatResponseDto.builder()
                            .sessionId(session.getId())
                            .childId(session.getChildId())
                            .messages(messageDtos)
                            .startedAt(session.getStartedAt())
                            .endedAt(session.getEndedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * [2025-11-04 김민중 수정] Pinecone 기반 대화 히스토리 조회
     * MySQL 대신 Pinecone을 사용하여 더 빠르고 시맨틱 검색 가능
     */
    private List<Map<String, Object>> getConversationHistoryFromPinecone(Long sessionId, int limit) {
        try {
            String url = aiServerUrl + "/api/memory/conversations/session/" + sessionId + "?limit=" + limit;

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response != null && response.containsKey("conversations")) {
                return (List<Map<String, Object>>) response.get("conversations");
            }

            log.warn("Pinecone 조회 실패, 빈 목록 반환");
            return new java.util.ArrayList<>();

        } catch (Exception e) {
            log.warn("Pinecone에서 대화 기록 조회 실패, MySQL 사용: {}", e.getMessage());
            // Fallback: MySQL 사용
            return new java.util.ArrayList<>();
        }
    }

    private String generateAIResponse(Long sessionId, String userMessage, Long childId) {
        try {
            // AI 서버에 요청 전송
            String url = aiServerUrl + "/api/chat";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("session_id", sessionId.intValue());
            requestBody.put("message", userMessage);
            if (childId != null) {
                requestBody.put("child_id", childId.intValue());
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);

            if (response != null && response.containsKey("ai_response")) {
                return (String) response.get("ai_response");
            }

            log.warn("No AI response received, using fallback");
            return "죄송해요, 지금은 대답하기 어려워요. 다시 말씀해주시겠어요?";

        } catch (Exception e) {
            log.error("Failed to get AI response: ", e);
            return "죄송해요, 잠시 후에 다시 이야기해요!";
        }
    }

    private String generateFirstMessageFromStory(Long sessionId, StoryCompletionSummaryDto summary) {
        try {
            // AI 서버에 동화 기반 첫 메시지 요청
            String url = aiServerUrl + "/api/chat/init-from-story";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("session_id", sessionId.intValue());
            requestBody.put("child_id", summary.getChildId().intValue());
            requestBody.put("child_name", summary.getChildName());
            requestBody.put("story_id", String.valueOf(summary.getStoryId())); // 문자열로 변환
            requestBody.put("story_title", summary.getStoryTitle());
            requestBody.put("total_time", summary.getTotalTime());

            // 능력치 정보 추가
            Map<String, Integer> abilities = new HashMap<>();
            abilities.put("courage", summary.getTotalCourage());
            abilities.put("empathy", summary.getTotalEmpathy());
            abilities.put("creativity", summary.getTotalCreativity());
            abilities.put("responsibility", summary.getTotalResponsibility());
            abilities.put("friendship", summary.getTotalFriendship());
            requestBody.put("abilities", abilities);

            // 선택 정보 추가
            requestBody.put("choices", summary.getChoices());

            // [2025-11-04 김민중 추가] Scene 정보 추가
            requestBody.put("scenes", summary.getScenes());

            log.info("Requesting first AI message from story: {}", requestBody);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);

            if (response != null && response.containsKey("ai_response")) {
                return (String) response.get("ai_response");
            }

            log.warn("No AI response received, using fallback");
            return String.format("%s야, 동화 어땠어? 재미있었니? 지금 기분이 어때?", summary.getChildName());

        } catch (Exception e) {
            log.error("Failed to get first AI message from story: ", e);
            // AI 서버 실패 시 기본 메시지 반환
            return String.format("%s야, 동화 '%s' 어땠어? 이야기 들으면서 어떤 생각이 들었어?",
                               summary.getChildName(), summary.getStoryTitle());
        }
    }

    /**
     * [2025-11-04 김민중 추가] AI 기반 동적 선택지 생성
     */
    public GenerateChoicesResponse generateChoices(GenerateChoicesRequest request) {
        try {
            // AI 서버에 선택지 생성 요청
            String url = aiServerUrl + "/api/chat/generate-choices";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            if (request.getSessionId() != null) {
                requestBody.put("session_id", request.getSessionId().intValue());
            }
            if (request.getChildId() != null) {
                requestBody.put("child_id", request.getChildId().intValue());
            }
            if (request.getLastMessage() != null) {
                requestBody.put("last_message", request.getLastMessage());
            }

            log.info("Requesting dynamic choices from AI: {}", requestBody);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);

            if (response != null) {
                List<String> choices = (List<String>) response.get("choices");
                String emotion = (String) response.getOrDefault("emotion", "neutral");

                log.info("AI generated choices: {}, emotion: {}", choices, emotion);

                return GenerateChoicesResponse.builder()
                        .choices(choices != null ? choices : List.of())
                        .emotion(emotion)
                        .build();
            }

            log.warn("No AI response for choices, using fallback");
            return GenerateChoicesResponse.builder()
                    .choices(List.of("더 알려줘", "다른 이야기"))
                    .emotion("neutral")
                    .build();

        } catch (Exception e) {
            log.error("Failed to generate choices from AI: ", e);
            return GenerateChoicesResponse.builder()
                    .choices(List.of("더 알려줘", "다른 이야기"))
                    .emotion("neutral")
                    .build();
        }
    }

    /**
     * [2025-11-04 김민중 추가] Pinecone에 대화 동기화 (비동기)
     * 실패해도 채팅 기능에는 영향 없음
     */
    @Async
    public void syncConversationToPinecone(
            Long sessionId,
            Long childId,
            String userMessage,
            String aiResponse,
            Long messageId
    ) {
        try {
            String url = aiServerUrl + "/api/memory/sync/conversation";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("session_id", sessionId.intValue());
            requestBody.put("child_id", childId.intValue());
            requestBody.put("user_message", userMessage);
            requestBody.put("ai_response", aiResponse);
            requestBody.put("message_id", messageId.intValue());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);

            if (response != null && "success".equals(response.get("status"))) {
                log.info("✅ Pinecone sync successful: msg_{}", messageId);
            } else {
                log.warn("⚠️ Pinecone sync skipped or failed: {}", response);
            }

        } catch (Exception e) {
            // 실패해도 무시 (채팅 기능에 영향 없음)
            log.warn("❌ Pinecone sync failed (non-critical): {}", e.getMessage());
        }
    }

    private List<ChatResponseDto.ChatMessageDto> convertToMessageDtos(List<ChatMessage> messages) {
        return messages.stream()
                .map(msg -> ChatResponseDto.ChatMessageDto.builder()
                        .id(msg.getId())
                        .sender(msg.getSender())
                        .message(msg.getMessage())
                        .createdAt(msg.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * [2025-11-05 추가] 세션의 동화 완료 정보 조회 (AI 서버가 story_context 복원용)
     */
    public StoryCompletionSummaryDto getStoryCompletionBySession(Long sessionId) {
        // 1. ChatSession 조회
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Chat session not found: " + sessionId));

        // 2. story_completion_id 확인
        Long storyCompletionId = session.getStoryCompletionId();
        if (storyCompletionId == null) {
            throw new RuntimeException("This session is not linked to a story completion");
        }

        // 3. StoryCompletion 조회
        StoryCompletion completion = storyCompletionRepository.findById(storyCompletionId)
                .orElseThrow(() -> new RuntimeException("Story completion not found: " + storyCompletionId));

        // 4. StoryCompletionSummaryDto 생성 및 반환
        return StoryCompletionSummaryDto.fromEntity(completion, sceneRepository);
    }

    /**
     * [2025-11-07 수정] DinoCharacter용 활성 세션 조회 또는 생성
     * - 아이별로 하나의 메인 세션을 계속 유지 (동화 완료 여부 무관)
     * - ended_at = null인 세션만 조회 (story_completion_id 조건 제거)
     * - 과거 대화 내역도 함께 반환
     */
    @Transactional
    public ChatResponseDto getOrCreateActiveSession(Long childId) {
        log.info("=== DinoCharacter 활성 세션 조회/생성: childId={} ===", childId);

        // 1. 활성 세션 조회 (ended_at = null) - story_completion_id 조건 제거!
        java.util.Optional<ChatSession> existingSession =
                chatSessionRepository.findTopByChildIdAndEndedAtIsNullOrderByStartedAtDesc(childId);

        if (existingSession.isPresent()) {
            // 기존 세션 있음 - 대화 내역과 함께 반환
            ChatSession session = existingSession.get();
            log.info("✅ 기존 활성 세션 발견: sessionId={}, 시작 시간={}", session.getId(), session.getStartedAt());

            // [2025-11-10 수정] 일반 대화에서는 동화 정보를 제거 (Pinecone만 사용)
            if (session.getStoryCompletionId() != null) {
                log.info("🔄 일반 대화 모드로 전환: storyCompletionId 초기화 (기존: {})", session.getStoryCompletionId());
                session.setStoryCompletionId(null);
                chatSessionRepository.save(session);
            }

            // [2025-11-07 수정] Pinecone에서 대화 내역 조회 (최근 20개만 - UX 개선)
            List<ChatResponseDto.ChatMessageDto> messageDtos;
            List<Map<String, Object>> pineconeConvs = getConversationHistoryFromPinecone(session.getId(), 20);

            if (!pineconeConvs.isEmpty()) {
                log.info("✅ Pinecone에서 {} 개 대화 로드", pineconeConvs.size());
                messageDtos = pineconeConvs.stream()
                        .flatMap(conv -> {
                            java.util.List<ChatResponseDto.ChatMessageDto> pair = new java.util.ArrayList<>();

                            // [2025-11-10 수정] 빈 메시지 필터링 및 로그 추가
                            String userMessage = (String) conv.get("message");
                            String aiResponse = (String) conv.get("response");

                            if (userMessage != null && !userMessage.trim().isEmpty()) {
                                pair.add(ChatResponseDto.ChatMessageDto.builder()
                                        .id(Long.parseLong(conv.get("message_id").toString().replace("msg_", "")))
                                        .sender("USER")
                                        .message(userMessage)
                                        .createdAt(java.time.LocalDateTime.parse((String) conv.get("created_at")))
                                        .build());
                            } else {
                                log.warn("⚠️ 빈 사용자 메시지 발견: message_id={}", conv.get("message_id"));
                            }

                            if (aiResponse != null && !aiResponse.trim().isEmpty()) {
                                pair.add(ChatResponseDto.ChatMessageDto.builder()
                                        .id(Long.parseLong(conv.get("message_id").toString().replace("msg_", "")) + 1)
                                        .sender("AI")
                                        .message(aiResponse)
                                        .createdAt(java.time.LocalDateTime.parse((String) conv.get("created_at")))
                                        .build());
                            } else {
                                log.warn("⚠️ 빈 AI 응답 발견: message_id={}", conv.get("message_id"));
                            }

                            return pair.stream();
                        })
                        .collect(Collectors.toList());

                log.info("📊 Pinecone 메시지 파싱 완료: {} 개 메시지 (USER + AI)", messageDtos.size());
            } else {
                // Fallback: MySQL
                log.info("⚠️ Pinecone 데이터 없음, MySQL에서 조회");
                List<ChatMessage> messages = chatMessageRepository.findByChatSessionIdOrderByCreatedAtAsc(session.getId());
                messageDtos = convertToMessageDtos(messages);
                log.info("📊 MySQL에서 {} 개 메시지 로드", messageDtos.size());
            }

            return ChatResponseDto.builder()
                    .sessionId(session.getId())
                    .childId(session.getChildId())
                    .messages(messageDtos)
                    .startedAt(session.getStartedAt())
                    .build();

        } else {
            // 활성 세션 없음 - 새로 생성
            log.info("🆕 활성 세션 없음, 새로운 세션 생성");

            ChatSession newSession = ChatSession.builder()
                    .childId(childId)
                    .build();

            newSession = chatSessionRepository.save(newSession);

            log.info("✅ 새 세션 생성 완료: sessionId={}", newSession.getId());

            return ChatResponseDto.builder()
                    .sessionId(newSession.getId())
                    .childId(newSession.getChildId())
                    .messages(List.of())
                    .startedAt(newSession.getStartedAt())
                    .build();
        }
    }

    /**
     * [2025-11-14 추가] 사용자 메시지에서 페이지 이동 의도 분석
     */
    public NavigationIntentResponse analyzeNavigationIntent(NavigationIntentRequest request) {
        try {
            String url = aiServerUrl + "/api/chat/analyze-navigation";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("message", request.getMessage());

            log.info("Analyzing navigation intent for message: {}", request.getMessage());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            NavigationIntentResponse response = restTemplate.postForObject(url, entity, NavigationIntentResponse.class);

            if (response != null) {
                log.info("Navigation intent analysis result: hasIntent={}, targetPath={}, confidence={}",
                        response.getHasNavigationIntent(), response.getTargetPath(), response.getConfidence());
                return response;
            }

            log.warn("No navigation intent response from AI, returning default");
            return NavigationIntentResponse.builder()
                    .hasNavigationIntent(false)
                    .targetPath(null)
                    .confidence(0.0)
                    .reason("AI 서버 응답 없음")
                    .build();

        } catch (Exception e) {
            log.error("Failed to analyze navigation intent: ", e);
            return NavigationIntentResponse.builder()
                    .hasNavigationIntent(false)
                    .targetPath(null)
                    .confidence(0.0)
                    .reason("분석 실패: " + e.getMessage())
                    .build();
        }
    }
}