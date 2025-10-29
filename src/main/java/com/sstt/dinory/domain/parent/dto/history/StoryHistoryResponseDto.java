package com.sstt.dinory.domain.parent.dto.history;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryHistoryResponseDto {

    private List<StoryCompletionDto> completions;
    private Map<String, Object> statistics;
    private Map<String, Object> pagination;
}
