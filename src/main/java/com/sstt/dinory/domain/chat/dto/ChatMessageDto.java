package com.sstt.dinory.domain.chat.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RAG 메모리용 간단한 메시지 DTO
 */
@Data
@Builder
public class ChatMessageDto {
    private Long sessionId;
    private String message;
    private String sender;  // "USER" or "AI"
    private LocalDateTime createdAt;
}
