package com.sstt.dinory.domain.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * [2025-11-04 김민중 추가] AI 기반 동적 선택지 생성 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenerateChoicesRequest {
    private Long sessionId;
    private Long childId;
    private String lastMessage;
}
