package com.sstt.dinory.domain.tts.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class TtsRequest {
    private String text;
    private String voiceName;
    private Double speakingRate;
    private Double pitch;
}