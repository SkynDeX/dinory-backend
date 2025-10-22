package com.sstt.dinory.domain.child.dto;

import java.time.LocalDateTime;


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
    private LocalDateTime recordedAt;
}
