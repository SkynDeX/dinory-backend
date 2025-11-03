package com.sstt.dinory.domain.child.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@lombok.Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmotionLogDto {
    private Long id;
    private Long childId;
    private String emotion;
    private String sentiment;
    private String source;
    private String context;
    private List<String> interests;     // 추가
    private LocalDateTime recordedAt;
}
