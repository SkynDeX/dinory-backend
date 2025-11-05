package com.sstt.dinory.domain.chat.controller;

import com.sstt.dinory.domain.chat.dto.ChatInitFromStoryRequest;
import com.sstt.dinory.domain.chat.dto.ChatInitRequest;
import com.sstt.dinory.domain.chat.dto.ChatMessageDto;
import com.sstt.dinory.domain.chat.dto.ChatMessageRequest;
import com.sstt.dinory.domain.chat.dto.ChatResponseDto;
import com.sstt.dinory.domain.chat.dto.GenerateChoicesRequest;
import com.sstt.dinory.domain.chat.dto.GenerateChoicesResponse;
import com.sstt.dinory.domain.chat.entity.ChatMessage;
import com.sstt.dinory.domain.chat.repository.ChatMessageRepository;
import com.sstt.dinory.domain.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatService chatService;
    private final ChatMessageRepository chatMessageRepository;

    @PostMapping("/init")
    public ResponseEntity<ChatResponseDto> initChatSession(@RequestBody ChatInitRequest request) {
        ChatResponseDto response = chatService.initChatSession(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/init-from-story")
    public ResponseEntity<ChatResponseDto> initChatSessionFromStory(@RequestBody ChatInitFromStoryRequest request) {
        ChatResponseDto response = chatService.initChatSessionFromStory(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/message")
    public ResponseEntity<ChatResponseDto> sendMessage(@RequestBody ChatMessageRequest request) {
        ChatResponseDto response = chatService.sendMessage(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{sessionId}/end")
    public ResponseEntity<Void> endChatSession(@PathVariable Long sessionId) {
        chatService.endChatSession(sessionId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<ChatResponseDto> getChatSession(@PathVariable Long sessionId) {
        ChatResponseDto response = chatService.getChatSession(sessionId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/child/{childId}")
    public ResponseEntity<List<ChatResponseDto>> getChatSessionsByChild(@PathVariable Long childId) {
        List<ChatResponseDto> responses = chatService.getChatSessionsByChild(childId);
        return ResponseEntity.ok(responses);
    }

    /**
     * RAG: 특정 아이의 최근 대화 기록 조회
     * FastAPI의 MemoryService가 호출
     */
    @GetMapping("/history/child/{childId}")
    public ResponseEntity<List<ChatMessageDto>> getChatHistoryByChild(
            @PathVariable Long childId,
            @RequestParam(defaultValue = "10") int limit) {

        Pageable pageable = PageRequest.of(0, limit);
        List<ChatMessage> messages = chatMessageRepository.findRecentMessagesByChildId(childId, pageable);

        List<ChatMessageDto> dtos = messages.stream()
                .map(msg -> ChatMessageDto.builder()
                        .sessionId(msg.getChatSession().getId())
                        .message(msg.getMessage())
                        .sender(msg.getSender())
                        .createdAt(msg.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * [2025-11-04 김민중 추가] AI 기반 동적 선택지 생성
     */
    @PostMapping("/generate-choices")
    public ResponseEntity<GenerateChoicesResponse> generateChoices(@RequestBody GenerateChoicesRequest request) {
        GenerateChoicesResponse response = chatService.generateChoices(request);
        return ResponseEntity.ok(response);
    }

    /**
     * [2025-11-05 추가] 세션의 동화 완료 정보 조회 (AI 서버용)
     */
    @GetMapping("/{sessionId}/story-completion")
    public ResponseEntity<?> getStoryCompletionBySession(@PathVariable Long sessionId) {
        return ResponseEntity.ok(chatService.getStoryCompletionBySession(sessionId));
    }
}