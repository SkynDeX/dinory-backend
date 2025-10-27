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
    private String choiceId;  // AI 서버에서 "c11", "c12" 등의 String 반환
    private String abilityType;
    private Integer abilityPoints;   
    
}
