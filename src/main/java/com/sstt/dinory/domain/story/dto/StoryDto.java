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
public class StoryDto {
    
    private String id;
    private String title;
    private String description;
    private String category;
    private String theme;
    private Integer ageMin;
    private Integer ageMax;
    private Integer estimatedTime;
    private String coverImageUrl;

}

