package com.sstt.dinory.domain.child.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmotionInterestRequest {
    private Long childId;
    private String emotion;
    private List<String> interests;
    private String source;
    private String context;
}