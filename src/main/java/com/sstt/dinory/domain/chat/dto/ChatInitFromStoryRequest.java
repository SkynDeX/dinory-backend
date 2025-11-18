package com.sstt.dinory.domain.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatInitFromStoryRequest {
    private Long completionId;  // StoryCompletion ID
    private Long childId;       // [2025-11-17 추가] 보안 검증용 Child ID
}