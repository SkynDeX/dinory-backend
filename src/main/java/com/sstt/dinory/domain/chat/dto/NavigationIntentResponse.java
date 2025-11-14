package com.sstt.dinory.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NavigationIntentResponse {
    @JsonProperty("has_navigation_intent")
    private Boolean hasNavigationIntent;

    @JsonProperty("target_path")
    private String targetPath;

    @JsonProperty("confidence")
    private Double confidence;

    @JsonProperty("reason")
    private String reason;
}
