package com.sstt.dinory.domain.parent.service;

import com.sstt.dinory.domain.chat.entity.ChatMessage;
import com.sstt.dinory.domain.chat.repository.ChatMessageRepository;
import com.sstt.dinory.domain.story.entity.StoryCompletion;
import com.sstt.dinory.domain.story.repository.StoryCompletionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class OverviewService {

    private final StoryCompletionRepository storyCompletionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final WebClient.Builder webClientBuilder;

    @Value("${ai.server.url:http://localhost:8000}")
    private String aiServerUrl;

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
        List<Map<String, Object>> recentStories = getRecentStories(completions);

        // AI 기반 기능은 별도 엔드포인트로 분리 (성능 최적화)
        // - Topics: /api/parent/dashboard/overview/topics
        // - AI Insights: /api/parent/dashboard/overview/insights

        System.out.println("emotions size: " + emotions.size());
        System.out.println("choices size: " + choices.size());
        System.out.println("recentStories size: " + recentStories.size());
        System.out.println("=========================");

        // 4. 기타 통계 데이터
        Map<String, Object> result = new HashMap<>();
        result.put("abilities", parentAbilities);
        result.put("totalStories", completions.size());
        result.put("totalTime", completions.stream().mapToInt(c -> c.getTotalTime() != null ? c.getTotalTime() : 0).sum());
        result.put("emotions", emotions);
        result.put("choices", choices);
        result.put("topics", new ArrayList<>());  // 비동기 로딩
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

    // 선택 패턴 데이터 계산 (점수 기반)
    private List<Map<String, Object>> calculateChoices(List<StoryCompletion> completions) {
        // 능력별 점수 합계 및 카운트
        Map<String, Integer> abilityPoints = new HashMap<>();
        Map<String, Integer> abilityCounts = new HashMap<>();

        abilityPoints.put("용기", 0);
        abilityPoints.put("친절", 0);
        abilityPoints.put("공감", 0);
        abilityPoints.put("우정", 0);
        abilityPoints.put("자존감", 0);

        abilityCounts.put("용기", 0);
        abilityCounts.put("친절", 0);
        abilityCounts.put("공감", 0);
        abilityCounts.put("우정", 0);
        abilityCounts.put("자존감", 0);

        int totalChoices = 0;
        int minPoints = Integer.MAX_VALUE;
        int maxPoints = Integer.MIN_VALUE;

        for (StoryCompletion completion : completions) {
            List<StoryCompletion.ChoiceRecord> choices = completion.getChoicesJson();
            if (choices != null) {
                for (StoryCompletion.ChoiceRecord choice : choices) {
                    String abilityType = choice.getAbilityType();
                    Integer points = choice.getAbilityPoints();

                    if (abilityType != null && points != null && abilityPoints.containsKey(abilityType)) {
                        abilityPoints.put(abilityType, abilityPoints.get(abilityType) + points);
                        abilityCounts.put(abilityType, abilityCounts.get(abilityType) + 1);
                        totalChoices++;

                        // 디버그: 점수 범위 확인
                        minPoints = Math.min(minPoints, points);
                        maxPoints = Math.max(maxPoints, points);
                    }
                }
            }
        }

        log.info("=== DB 점수 범위 확인 === min: {}, max: {}, totalChoices: {}", minPoints, maxPoints, totalChoices);

        if (totalChoices == 0) {
            return new ArrayList<>();
        }

        // 능력별 평균 점수 계산 (8-15 범위를 0-100으로 정규화)
        // 일반 선택지: 10-15점, 커스텀 선택지: 8-15점 (직접 입력이라 AI 분석 불확실성 고려)
        Map<String, Double> abilityScores = new HashMap<>();
        for (String ability : abilityPoints.keySet()) {
            int count = abilityCounts.get(ability);
            if (count > 0) {
                double avgPoints = (double) abilityPoints.get(ability) / count;
                // 8점 = 0점, 15점 = 100점으로 정규화
                double normalized = ((avgPoints - 8.0) / 7.0) * 100.0;
                abilityScores.put(ability, Math.max(0.0, Math.min(normalized, 100.0)));
            } else {
                abilityScores.put(ability, 0.0);
            }
        }

        // 점수 합계 계산
        double totalScore = abilityScores.values().stream().mapToDouble(Double::doubleValue).sum();

        if (totalScore == 0) {
            return new ArrayList<>();
        }

        // 점수 기반 비율 계산
        Map<String, Double> abilityRatios = new HashMap<>();
        for (Map.Entry<String, Double> entry : abilityScores.entrySet()) {
            abilityRatios.put(entry.getKey(), (entry.getValue() / totalScore) * 100.0);
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

    // 능력 타입과 전체 비율을 기반으로 선택 스타일 결정 (AI 기반)
    private String determineChoiceStyle(String abilityType, Map<String, Double> ratios) {
        // AI 분석은 너무 느리므로, 스타일 매핑을 개선된 규칙 기반으로 처리
        // 능력치 분포를 보고 더 자연스러운 스타일 결정

        double courage = ratios.getOrDefault("용기", 0.0);
        double kindness = ratios.getOrDefault("친절", 0.0);
        double empathy = ratios.getOrDefault("공감", 0.0);
        double friendship = ratios.getOrDefault("우정", 0.0);
        double confidence = ratios.getOrDefault("자존감", 0.0);

        // 전체 패턴을 고려한 스타일 결정
        switch (abilityType) {
            case "용기":
                // 용기 + 자존감이 높으면 도전적, 용기만 높으면 용감한
                if (courage >= 25 && confidence >= 25) {
                    return "도전적인 선택";
                } else if (courage >= 35) {
                    return "용감한 선택";
                } else {
                    return "도전적인 선택";
                }

            case "친절":
                // 친절 + 공감이 높으면 배려하는
                if ((kindness + empathy) >= 50) {
                    return "배려하는 선택";
                } else {
                    return "배려하는 선택";
                }

            case "공감":
                // 공감이 높고 용기가 낮으면 신중한, 그 외는 배려하는
                if (empathy >= 30 && courage <= 20) {
                    return "신중한 선택";
                } else if ((kindness + empathy) >= 50) {
                    return "배려하는 선택";
                } else {
                    return "배려하는 선택";
                }

            case "우정":
                // 우정은 항상 협력하는
                return "협력하는 선택";

            case "자존감":
                // 자존감이 높으면 자신있는, 용기도 높으면 도전적인
                if (courage >= 25 && confidence >= 25) {
                    return "도전적인 선택";
                } else if (confidence >= 30) {
                    return "자신있는 선택";
                } else {
                    return "도전적인 선택";
                }

            default:
                // 기타 능력치는 용감한 선택
                return "용감한 선택";
        }
    }
    
    // 대화 주제 (관심사) 데이터 계산 - AI 기반
    private List<Map<String, Object>> calculateTopics(Long childId, LocalDateTime startDate, LocalDateTime endDate) {
        // 기간 내 아이의 대화 메시지 조회
        List<ChatMessage> chatMessages = chatMessageRepository
                .findByChildIdAndCreatedAtBetween(childId, startDate, endDate);

        if (chatMessages.isEmpty()) {
            log.info("기간 내 대화 메시지 없음, 빈 주제 리스트 반환");
            return new ArrayList<>();
        }

        // AI로 주제 추출
        try {
            Map<String, Object> requestBody = new HashMap<>();

            // 메시지 리스트 변환
            List<Map<String, String>> messageList = chatMessages.stream()
                    .map(msg -> {
                        Map<String, String> m = new HashMap<>();
                        m.put("sender", msg.getSender());
                        m.put("message", msg.getMessage());
                        return m;
                    })
                    .collect(Collectors.toList());

            requestBody.put("messages", messageList);

            log.info("AI 대화 주제 추출 요청: messageCount={}", chatMessages.size());

            Map<String, Object> response = webClientBuilder.build()
                    .post()
                    .uri(aiServerUrl + "/ai/extract-chat-topics")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response != null && response.containsKey("topics")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> aiTopics = (List<Map<String, Object>>) response.get("topics");

                if (aiTopics != null && !aiTopics.isEmpty()) {
                    // size 계산 추가 (워드클라우드용)
                    List<Map<String, Object>> result = new ArrayList<>();
                    for (Map<String, Object> topic : aiTopics) {
                        Map<String, Object> item = new HashMap<>();
                        item.put("text", topic.get("text"));

                        // count를 Integer로 변환
                        Integer count = topic.get("count") instanceof Integer
                            ? (Integer) topic.get("count")
                            : Integer.parseInt(topic.get("count").toString());

                        item.put("count", count);

                        // 빈도에 따라 크기 조정 (12 ~ 30px)
                        int size = Math.min(30, 12 + count * 2);
                        item.put("size", size);

                        result.add(item);
                    }

                    log.info("AI 대화 주제 추출 성공: {}개", result.size());
                    return result;
                }
            }

        } catch (Exception e) {
            log.error("AI 대화 주제 추출 실패: {}", e.getMessage());
        }

        // 폴백: 빈 리스트 반환
        log.warn("AI 주제 추출 실패, 빈 리스트 반환");
        return new ArrayList<>();
    }

    // 최근 동화 목록
    private List<Map<String, Object>> getRecentStories(List<StoryCompletion> completions) {
        return completions.stream()
                .filter(c -> c.getCompletedAt() != null)
                .sorted((a, b) -> b.getCompletedAt().compareTo(a.getCompletedAt()))
                .limit(10)  // 최대 10개로 증가
                .map(c -> {
                    Map<String, Object> story = new HashMap<>();
                    story.put("id", c.getId());
                    story.put("title", c.getStoryTitle() != null ? c.getStoryTitle() : "제목 없음");
                    story.put("emotion", c.getEmotion() != null ? c.getEmotion() : "😊");
                    story.put("completedAt", c.getCompletedAt().toString());  // 전체 날짜/시간
                    story.put("date", c.getCompletedAt().toLocalDate().toString());
                    story.put("totalTime", c.getTotalTime() != null ? c.getTotalTime() : 0);  // 소요 시간 추가
                    return story;
                })
                .collect(Collectors.toList());
    }

    // Topics만 별도 조회 (비동기 로딩용)
    public List<Map<String, Object>> getTopics(Long childId, String period) {
        LocalDateTime startDate = calculateStartDate(period);
        LocalDateTime endDate = LocalDateTime.now();

        return calculateTopics(childId, startDate, endDate);
    }

    // AI 인사이트만 별도 조회 (비동기 로딩용)
    public Map<String, Object> getAIInsights(Long childId, String period) {
        // 1. 기간별 완료된 동화 조회
        LocalDateTime startDate = calculateStartDate(period);
        LocalDateTime endDate = LocalDateTime.now();

        List<StoryCompletion> completions = storyCompletionRepository
                .findByChildIdAndCompletedAtBetween(childId, startDate, endDate);

        // 2. 아이 능력치 집계
        Map<String, Double> childAbilities = calculateChildAbilities(completions);
        Map<String, Double> parentAbilities = convertToParentAbilities(childAbilities);

        // 3. 선택 패턴 계산
        List<Map<String, Object>> choices = calculateChoices(completions);

        // 4. AI 인사이트 생성
        return generateAIInsights(parentAbilities, choices, completions.size(), period);
    }

    // AI 인사이트 생성 (Quick 인사이트 + 추천 활동)
    private Map<String, Object> generateAIInsights(
            Map<String, Double> abilities,
            List<Map<String, Object>> choices,
            int totalStories,
            String period) {

        Map<String, Object> defaultResult = new HashMap<>();
        defaultResult.put("quickInsight", "아이와 함께 동화를 읽으며 성장해보세요!");
        defaultResult.put("recommendation", Map.of(
            "ability", "용기",
            "message", "용기 관련 동화를 함께 읽어보세요."
        ));

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("abilities", abilities);
            requestBody.put("choices", choices);
            requestBody.put("totalStories", totalStories);
            requestBody.put("period", period);

            log.info("AI 인사이트 요청 시작");
            Map<String, Object> response = webClientBuilder.build()
                    .post()
                    .uri(aiServerUrl + "/ai/generate-dashboard-insights")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response != null) {
                log.info("AI 인사이트 생성 성공");
                return response;
            }
        } catch (Exception e) {
            log.error("AI 인사이트 생성 실패: {}", e.getMessage());
        }

        return defaultResult;
    }

}
