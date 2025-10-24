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
public class RecommendedStoryDto {
    
    private String storyId;
    private String title;
    private String coverImageUrl;
    private List<String> themes;  // ["우정", "용기"]
    private Integer estimatedTime;  // 분
    private String description;
    private Integer matchingScore;  // 매칭 점수 0-100
    
}
