package com.sstt.dinory.domain.parent.dto.history;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryCompletionDto {

    private Long completionId;
    private String storyId;
    private String storyTitle;
    private String storyTheme;
    private LocalDateTime completedAt;
    private Integer duration;                   // 초 단위
    private String emotion;
    private List<String> interests;
    private Map<String, Integer> choicesSummary; // ability type별 선택 횟수

}
