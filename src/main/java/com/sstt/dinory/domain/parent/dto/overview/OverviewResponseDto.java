package com.sstt.dinory.domain.parent.dto.overview;

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
public class OverviewResponseDto {

    private Map<String, Double> abilities;    // 부모용 5개 전문 영역
    private Integer totalStories;            // 총 완료 동화 수
    private Integer totalTime;              // 총 학습 시간 (초)
    private List<Map<String, Object>> emotions; // 감정 변화 추이
    private List<Map<String, Object>> choices; // 선택 패턴
    private List<Map<String, Object>> topics; // 대화 주제
    private List<Map<String, Object>> recentStories; // 최근 동화

}
