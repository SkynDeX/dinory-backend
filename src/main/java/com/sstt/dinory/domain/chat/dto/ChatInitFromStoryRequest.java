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
}