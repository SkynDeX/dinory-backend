package com.sstt.dinory.domain.parent.service;

import com.sstt.dinory.domain.story.entity.StoryCompletion;
import com.sstt.dinory.domain.story.repository.StoryCompletionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OverviewService {

    private final StoryCompletionRepository storyCompletionRepository;

    // 부모 대시보드 데이터 조회
    public Map<String, Object> getOverview(Long childId, String period) {
        // 1. 기간별 완료된 동화 조회
        LocalDateTime startDate = calculateStartDate(period);
        LocalDateTime endDate = LocalDateTime.now();

        List<StoryCompletion> completions = storyCompletionRepository
                .findByChildIdAndCompletedAtBetween(childId, startDate, endDate);

        // 2. 아이 능력치 집계 (용기, 친절, 공감, 우정, 자존감)
        Map<String, Double> childAbilities = calculateChildAbilities(completions);

        // 3. 부모용 전문 영역으로 변환
        Map<String, Double> parentAbilities = convertToParentAbilities(childAbilities);

        // 4. 기타 통계 데이터
        Map<String, Object> result = new HashMap<>();
        result.put("abilities", parentAbilities);
        result.put("totalStories", completions.size());
        result.put("totalTime", completions.stream().mapToInt(c -> c.getTotalTime() != null ? c.getTotalTime() : 0).sum());

        return result;
    }

    // choicesJson 분석하여 아이 능력치 집계
    private Map<String, Double> calculateChildAbilities(List<StoryCompletion> completions) {
        Map<String, Integer> abilityPoints = new HashMap<>();
        Map<String, Integer> abilityCount = new HashMap<>();

        // 초기화
        List<String> abilities = Arrays.asList("용기", "친절", "공감", "우정", "자존감");
        for (String ability : abilities) {
            abilityPoints.put(ability, 0);
            abilityCount.put(ability, 0);
        }

        // choicesJson 에서 능력치 추출
        for (StoryCompletion completion : completions) {
            List<StoryCompletion.ChoiceRecord> choices = completion.getChoicesJson();
            if (choices != null) {
                for (StoryCompletion.ChoiceRecord choice : choices) {
                    String abilityType = choice.getAbilityType();
                    Integer points = choice.getAbilityPoints();

                    if (abilityType != null && points != null && abilityPoints.containsKey(abilityType)) {
                        abilityPoints.put(abilityType, abilityPoints.get(abilityType) + points);
                        abilityCount.put(abilityType, abilityCount.get(abilityType) + 1);
                    }
                }
            }
        }

        // 평균 점수 계산 (0-100 스케일로 정규화)
        Map<String, Double> result = new HashMap<>();
        for (String ability : abilities) {
            int count = abilityCount.get(ability);
            if (count > 0) {
                double avgPoints = (double) abilityPoints.get(ability) / count;
                // 점수를 0-100 범위로 정규화 (가정: 선택 당 최대 10점)
                result.put(ability, Math.min(avgPoints * 10, 100.0));
            } else {
                result.put(ability, 0.0);
            }
        }

        return result;
    }

    // 아이 능력치 > 부모님 전문 영역으로 변환
    private Map<String, Double> convertToParentAbilities(Map<String, Double> childAbilities) {
        Map<String, Double> result = new LinkedHashMap<>();

        // 정서 인식 및 조절 = 공감(70%) + 자존감(30%)
        result.put("정서 인식 및 조절",
                childAbilities.get("공감") * 0.7 + childAbilities.get("자존감") * 0.3);

        // 사회적 상호작용 = 친절(50%) + 우정(50%)
        result.put("사회적 상호작용",
                childAbilities.get("친절") * 0.5 + childAbilities.get("우정") * 0.5);

        // 자아 개념 = 자존감(60%) + 용기(40%)
        result.put("자아 개념",
                childAbilities.get("자존감") * 0.6 + childAbilities.get("용기") * 0.4);

        // 도전 및 적응력 = 용기(100%)
        result.put("도전 및 적응력",
                childAbilities.get("용기"));

        // 공감 및 친사회성 = 공감(60%) + 친절(40%)
        result.put("공감 및 친사회성",
                childAbilities.get("공감") * 0.6 + childAbilities.get("친절") * 0.4);

        return result;
    }

    // 기간별 시작일 계산
    private LocalDateTime calculateStartDate(String period) {
        LocalDateTime now = LocalDateTime.now();
        switch (period) {
            case "day":
                return now.minusDays(1);
            case "week":
                return now.minusWeeks(1);
            case "month":
                return now.minusMonths(1);
            default:
                return now.minusDays(1); // 기본값: 일간
        }
    }
}
