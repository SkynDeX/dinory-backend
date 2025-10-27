package com.sstt.dinory.domain.chat.service;

import com.sstt.dinory.domain.chat.dto.ChatInitFromStoryRequest;
import com.sstt.dinory.domain.chat.dto.ChatInitRequest;
import com.sstt.dinory.domain.chat.dto.ChatMessageRequest;
import com.sstt.dinory.domain.chat.dto.ChatResponseDto;
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
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${ai.server.url:http://localhost:8000}")
    private String aiServerUrl;

    @Transactional
    public ChatResponseDto initChatSession(ChatInitRequest request) {
        // 새로운 채팅 세션 생성
        ChatSession session = ChatSession.builder()
                .childId(request.getChildId())
                .build();

        session = chatSessionRepository.save(session);

        log.info("Chat session created: {}", session.getId());

        return ChatResponseDto.builder()
                .sessionId(session.getId())
                .childId(session.getChildId())
                .startedAt(session.getStartedAt())
                .messages(List.of())
                .build();
    }

    @Transactional
    public ChatResponseDto initChatSessionFromStory(ChatInitFromStoryRequest request) {
        log.info("=== 동화 기반 챗봇 세션 시작 ===");
        log.info("completionId: {}", request.getCompletionId());

        // StoryCompletion 조회
        StoryCompletion completion = storyCompletionRepository.findById(request.getCompletionId())
                .orElseThrow(() -> new RuntimeException("StoryCompletion을 찾을 수 없습니다: " + request.getCompletionId()));

        // StoryCompletion 요약 정보 생성
        StoryCompletionSummaryDto summary = StoryCompletionSummaryDto.from(completion);

        // 새로운 채팅 세션 생성 (동화와 연결)
        ChatSession session = ChatSession.builder()
                .childId(summary.getChildId())
                .storyCompletionId(request.getCompletionId())
                .build();

        session = chatSessionRepository.save(session);

        log.info("Chat session created from story: sessionId={}, storyId={}",
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

    public ChatResponseDto getChatSession(Long sessionId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Chat session not found: " + sessionId));

        List<ChatMessage> messages = chatMessageRepository.findByChatSessionIdOrderByCreatedAtAsc(sessionId);

        return ChatResponseDto.builder()
                .sessionId(session.getId())
                .childId(session.getChildId())
                .messages(convertToMessageDtos(messages))
                .startedAt(session.getStartedAt())
                .endedAt(session.getEndedAt())
                .build();
    }

    public List<ChatResponseDto> getChatSessionsByChild(Long childId) {
        List<ChatSession> sessions = chatSessionRepository.findByChildIdOrderByStartedAtDesc(childId);

        return sessions.stream()
                .map(session -> {
                    List<ChatMessage> messages = chatMessageRepository.findByChatSessionIdOrderByCreatedAtAsc(session.getId());
                    return ChatResponseDto.builder()
                            .sessionId(session.getId())
                            .childId(session.getChildId())
                            .messages(convertToMessageDtos(messages))
                            .startedAt(session.getStartedAt())
                            .endedAt(session.getEndedAt())
                            .build();
                })
                .collect(Collectors.toList());
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
            requestBody.put("story_id", summary.getStoryId());
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
}