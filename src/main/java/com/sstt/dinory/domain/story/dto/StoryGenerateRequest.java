package com.sstt.dinory.domain.story.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryGenerateRequest {
    
    private String storyId;
    private Long childId;
    private String childName;
    private String emotion;
    private List<String> interests;
    
}