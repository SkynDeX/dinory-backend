package com.sstt.dinory.domain.parent.service;

import com.sstt.dinory.domain.story.entity.StoryCompletion;
import com.sstt.dinory.domain.story.repository.StoryCompletionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cglib.core.Local;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;


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

        List<Map<String, Object>> emotions = calculateEmotions(completions, period);
        List<Map<String, Object>> choices = calculateChoices(completions);
        List<Map<String, Object>> topics = calculateTopics(completions);
        List<Map<String, Object>> recentStories = getRecentStories(completions);

        System.out.println("emotions size: " + emotions.size());
        System.out.println("choices size: " + choices.size());
        System.out.println("topics size: " + topics.size());
        System.out.println("recentStories size: " + recentStories.size());
        System.out.println("=========================");

        // 4. 기타 통계 데이터
        Map<String, Object> result = new HashMap<>();
        result.put("abilities", parentAbilities);
        result.put("totalStories", completions.size());
        result.put("totalTime", completions.stream().mapToInt(c -> c.getTotalTime() != null ? c.getTotalTime() : 0).sum());
        result.put("emotions", emotions);
        result.put("choices", choices);
        result.put("topics", topics);
        result.put("recentStories", recentStories);

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

                    if (abilityType != null && points != null) {
                        // 5가지 핵심 능력치만 수집
                        if (abilityPoints.containsKey(abilityType)) {
                            abilityPoints.put(abilityType, abilityPoints.get(abilityType) + points);
                            abilityCount.put(abilityType, abilityCount.get(abilityType) + 1);
                        }
                    }
                }
            }
        }

        // 평균 점수 계산 (0-100 스케일로 정규화)
        // DB 점수 범위: 10-15점 → 0-100으로 정규화
        Map<String, Double> result = new HashMap<>();
        for (String ability : abilities) {
            int count = abilityCount.get(ability);
            if (count > 0) {
                double avgPoints = (double) abilityPoints.get(ability) / count;
                // 10점 = 0점, 15점 = 100점으로 정규화
                double normalized = ((avgPoints - 10.0) / 5.0) * 100.0;
                result.put(ability, Math.max(0.0, Math.min(normalized, 100.0)));
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
                // 오늘 00:00:00부터
                return now.toLocalDate().atStartOfDay();
            case "week":
                return now.minusWeeks(1);
            case "month":
                return now.minusMonths(1);
            default:
                return now.toLocalDate().atStartOfDay(); // 기본값: 오늘 00시
        }
    }

    // 감정 변화 추이 데이터 계산
    private List<Map<String, Object>> calculateEmotions(List<StoryCompletion> completions, String period) {
        // 긍정 감정 목록
        Set<String> positiveEmotions = Set.of("기뻐요", "신나요", "happy", "excited");
        // 부정 감정 목록
        Set<String> negativeEmotions = Set.of("슬퍼요", "화가나요", "걱정돼요", "sad", "angry", "worried");

        // 날짜별로 그룹화
        Map<String, int[]> emotionsByDate = new LinkedHashMap<>();

        for (StoryCompletion completion : completions) {
            if (completion.getCompletedAt() == null) continue;

            String dateKey;
            LocalDateTime completedAt = completion.getCompletedAt();

            if ("day".equals(period)) {
                // 일간: 시간대별 (데이터가 있는 시간만)
                dateKey = completion.getCompletedAt().format(DateTimeFormatter.ofPattern("HH:00"));
            } else if ("week".equals(period)) {
                // 주간: 주차별
                int weekOfMonth = (completedAt.getDayOfMonth() -1) / 7 + 1;
                dateKey = weekOfMonth + "주차";
            } else {
                // 월간: 월별
                dateKey = completedAt.getMonthValue() + "월";
            }

            emotionsByDate.putIfAbsent(dateKey, new int[]{0, 0}); // [positive, negative]

            String emotion = completion.getEmotion();
            if (emotion != null) {
                if (positiveEmotions.contains(emotion)) {
                    emotionsByDate.get(dateKey)[0]++;
                } else if (negativeEmotions.contains(emotion)) {
                    emotionsByDate.get(dateKey)[1]++;
                }
            }
        }

        // 결과 리스트 생성
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, int[]> entry : emotionsByDate.entrySet()) {

            Map<String, Object> item = new HashMap<>();
            item.put("label", entry.getKey());
            item.put("positive", entry.getValue()[0]);
            item.put("negative", entry.getValue()[1]);

            result.add(item);
        }

        return result;
    }

    // 선택 패턴 데이터 계산
    private List<Map<String, Object>> calculateChoices(List<StoryCompletion> completions) {
        Map<String, Integer> abilityCounts = new HashMap<>();

        abilityCounts.put("용기", 0);
        abilityCounts.put("친절", 0);
        abilityCounts.put("공감", 0);
        abilityCounts.put("우정", 0);
        abilityCounts.put("자존감", 0);

        int totalChoices = 0;
        for (StoryCompletion completion : completions) {
            List<StoryCompletion.ChoiceRecord> choices = completion.getChoicesJson();
            if (choices != null) {
                for (StoryCompletion.ChoiceRecord choice : choices) {
                    String abilityType = choice.getAbilityType();
                    if (abilityType != null && abilityCounts.containsKey(abilityType)) {
                        abilityCounts.put(abilityType, abilityCounts.get(abilityType) + 1);
                        totalChoices++;
                    }
                }
            }
        }

        if (totalChoices == 0) {
            return new ArrayList<>();
        }

        Map<String, Double> abilityRatios = new HashMap<>();
        for (Map.Entry<String, Integer> entry : abilityCounts.entrySet()) {
            abilityRatios.put(entry.getKey(), (entry.getValue() * 100.0) / totalChoices);
        }

        Map<String, Integer> styleCounts = new HashMap<>();

        for (StoryCompletion completion : completions) {
            List<StoryCompletion.ChoiceRecord> choices = completion.getChoicesJson();
            if (choices != null) {
                for (StoryCompletion.ChoiceRecord choice : choices) {
                    String abilityType = choice.getAbilityType();
                    if (abilityType == null) continue;

                    String style = determineChoiceStyle(abilityType, abilityRatios);
                    styleCounts.put(style, styleCounts.getOrDefault(style, 0) + 1);
                }
            }
        }

        Map<String, String> styleColors = Map.of(
                "용감한 선택", "#2fa36b",
                "배려하는 선택", "#87ceeb",
                "협력하는 선택", "#ffd166",
                "자신있는 선택", "#9b59b6",
                "도전적인 선택", "#ff9b7a",
                "신중한 선택", "#95a5a6"
        );

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : styleCounts.entrySet()) {
            int count = entry.getValue();
            double percentage = (count * 100.0) / totalChoices;

            Map<String, Object> item = new HashMap<>();
            item.put("name", entry.getKey());
            item.put("value", Math.round(percentage));
            item.put("count", count);
            item.put("color", styleColors.getOrDefault(entry.getKey(), "#cccccc"));

            result.add(item);
        }

        return result;

    }

    // 능력 타입과 전체 비율을 기반으로 선택 스타일 결정
    private String determineChoiceStyle(String abilityType, Map<String, Double> ratios) {
        double courage = ratios.get("용기");
        double kindness = ratios.get("친절");
        double empathy = ratios.get("공감");
        double friendship = ratios.get("우정");
        double confidence = ratios.get("자존감");

        if (courage >= 25 && confidence >= 25 && (abilityType.equals("용기") || abilityType.equals("자존감"))) {
            return "도전적인 선택";
        }

        if ((kindness + empathy) >= 50 && (abilityType.equals("친절") || abilityType.equals("공감"))) {
            return "배려하는 선택";
        }

        if (empathy >= 30 && courage <= 20 && abilityType.equals("공감")) {
            return "신중한 선택";
        }

        switch (abilityType) {
            case "용기":
                return courage >= 35 ? "용감한 선택" : "도전적인 선택";
            case "우정":
                return "협력하는 선택";
            case "자존감":
                return confidence >= 30 ? "자신있는 선택" : "도전적인 선택";
            case "친절":
            case "공감":
                return "배려하는 선택";
            default:
                return "용감한 선택";
        }
    }
    
    // 대화 주제 (관심사) 데이터 계산
    private List<Map<String, Object>> calculateTopics(List<StoryCompletion> completions) {
        Map<String, Integer> topicCounts = new HashMap<>();

        for (StoryCompletion completion : completions) {
            List<String> interests = completion.getInterests();
            if (interests != null) {
                for (String interest : interests) {
                    topicCounts.put(interest, topicCounts.getOrDefault(interest, 0) + 1);
                }
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : topicCounts.entrySet()) {
            // 빈도에 따라 크기 조정 (12 ~ 24px)
            int size = Math.min(24, 12 + entry.getValue() * 2);

            Map<String, Object> item = new HashMap<>();
            item.put("text", entry.getKey());
            item.put("size", size);
            item.put("count", entry.getValue());

            result.add(item);
        }

        return result;
    }

    // 최근 동화 목록
    private List<Map<String, Object>> getRecentStories(List<StoryCompletion> completions) {
        return completions.stream()
                .filter(c -> c.getCompletedAt() != null)
                .sorted((a, b) -> b.getCompletedAt().compareTo(a.getCompletedAt()))
                .limit(5)
                .map(c -> {
                    Map<String, Object> story = new HashMap<>();
                    story.put("id", c.getId());
                    story.put("title", c.getStoryTitle() != null ? c.getStoryTitle() : "제목 없음");
                    story.put("emotion", c.getEmotion() != null ? c.getEmotion() : "😊");
                    story.put("date", c.getCompletedAt().toLocalDate().toString());
                    return story;
                })
                .collect(Collectors.toList());
    }


}
