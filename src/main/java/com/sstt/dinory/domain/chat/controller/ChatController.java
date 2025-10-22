package com.sstt.dinory.domain.chat.controller;

import com.sstt.dinory.domain.chat.dto.ChatInitRequest;
import com.sstt.dinory.domain.chat.dto.ChatMessageRequest;
import com.sstt.dinory.domain.chat.dto.ChatResponseDto;
import com.sstt.dinory.domain.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/init")
    public ResponseEntity<ChatResponseDto> initChatSession(@RequestBody ChatInitRequest request) {
        ChatResponseDto response = chatService.initChatSession(request);
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
}