package com.sstt.dinory.domain.chat.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatResponseDto {
    private Long sessionId;
    private Long childId;
    private String aiResponse;
    private List<ChatMessageDto> messages;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ChatMessageDto {
        private Long id;
        private String sender;
        private String message;
        private LocalDateTime createdAt;
    }
}