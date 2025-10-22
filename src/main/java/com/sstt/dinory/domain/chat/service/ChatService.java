package com.sstt.dinory.domain.chat.service;

import com.sstt.dinory.domain.chat.dto.ChatInitRequest;
import com.sstt.dinory.domain.chat.dto.ChatMessageRequest;
import com.sstt.dinory.domain.chat.dto.ChatResponseDto;
import com.sstt.dinory.domain.chat.entity.ChatMessage;
import com.sstt.dinory.domain.chat.entity.ChatSession;
import com.sstt.dinory.domain.chat.repository.ChatMessageRepository;
import com.sstt.dinory.domain.chat.repository.ChatSessionRepository;
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