package com.sstt.dinory.domain.image.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageGenerationResponse {

    private Long id;
    private Long sceneId;
    private String prompt;
    private String style;
    private String status;
    private String imageUrl;
    private String errorMessage;
    private LocalDateTime requestedAt;
    private LocalDateTime completedAt;
}