package com.sstt.dinory.domain.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * [2025-11-04 김민중 추가] AI 기반 동적 선택지 생성 응답 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenerateChoicesResponse {
    private List<String> choices;    // AI가 생성한 선택지 목록
    private String emotion;           // Dino의 감정 (happy, sad, angry, neutral)
}
