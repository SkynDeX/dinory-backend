package com.sstt.dinory.domain.image.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageGenerationRequest {

    private Long sceneId;
    private String prompt;
    private String style;
}