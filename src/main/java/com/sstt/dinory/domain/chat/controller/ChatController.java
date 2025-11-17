package com.sstt.dinory.domain.chat.controller;

import com.sstt.dinory.domain.chat.dto.ChatInitFromStoryRequest;
import com.sstt.dinory.domain.chat.dto.ChatInitRequest;
import com.sstt.dinory.domain.chat.dto.ChatMessageDto;
import com.sstt.dinory.domain.chat.dto.ChatMessageRequest;
import com.sstt.dinory.domain.chat.dto.ChatResponseDto;
import com.sstt.dinory.domain.chat.dto.GenerateChoicesRequest;
import com.sstt.dinory.domain.chat.dto.GenerateChoicesResponse;
import com.sstt.dinory.domain.chat.dto.NavigationIntentRequest;
import com.sstt.dinory.domain.chat.dto.NavigationIntentResponse;
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

    /**
     * [2025-11-07 추가] 대화 종료 버튼 클릭 기록 (세션은 활성 유지)
     * - last_closed_at 필드만 업데이트
     * - ended_at은 null로 유지하여 세션 활성 상태 유지
     */
    @PostMapping("/{sessionId}/close")
    public ResponseEntity<Void> recordChatClose(@PathVariable Long sessionId) {
        chatService.recordChatClose(sessionId);
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
     * [2025-11-07 추가] DinoCharacter용 활성 세션 조회 또는 생성
     * - 아이별로 하나의 메인 세션을 유지
     * - 과거 대화 내역 포함
     */
    @GetMapping("/child/{childId}/active-session")
    public ResponseEntity<ChatResponseDto> getOrCreateActiveSession(@PathVariable Long childId) {
        ChatResponseDto response = chatService.getOrCreateActiveSession(childId);
        return ResponseEntity.ok(response);
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

    /**
     * [2025-11-14 추가] 사용자 메시지에서 페이지 이동 의도 분석
     */
    @PostMapping("/analyze-navigation")
    public ResponseEntity<NavigationIntentResponse> analyzeNavigationIntent(@RequestBody NavigationIntentRequest request) {
        NavigationIntentResponse response = chatService.analyzeNavigationIntent(request);
        return ResponseEntity.ok(response);
    }

    /**
     * [2025-11-17 추가] 특정 패턴을 포함하는 메시지 삭제 (과거 잘못된 응답 정리용)
     */
    @DeleteMapping("/session/{sessionId}/messages/pattern")
    public ResponseEntity<?> deleteMessagesWithPattern(
            @PathVariable Long sessionId,
            @RequestParam String pattern) {
        Map<String, Object> result = chatService.deleteMessagesWithPattern(sessionId, pattern);
        return ResponseEntity.ok(result);
    }

    /**
     * [2025-11-17 추가] 세션의 모든 메시지 삭제
     */
    @DeleteMapping("/session/{sessionId}/messages")
    public ResponseEntity<?> clearSessionMessages(@PathVariable Long sessionId) {
        Map<String, Object> result = chatService.clearSessionMessages(sessionId);
        return ResponseEntity.ok(result);
    }
}