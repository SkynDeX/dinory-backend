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
public class StoryCompleteRequest {
    
    private Long childId;
    private String storyId;
    private Integer totalTime;  // 초
    private List<ChoiceRecord> choices;  // 선택 경로
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChoiceRecord {

        private Integer sceneNumber;
        private Long choiceId;
        private String abilityType;
        private Integer abilityPoints;
        
    }
}
