package com.sstt.dinory.domain.story.dto;

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
public class StoryChoiceRequest {
    
    private Long childId;
    private String storyId;
    private Integer sceneNumber;
    private Long choiceId;
    private String abilityType;      
    private Integer abilityPoints;   
    
}
