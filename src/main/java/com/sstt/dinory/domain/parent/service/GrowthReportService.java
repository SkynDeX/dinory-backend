package com.sstt.dinory.domain.parent.service;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class GrowthReportService {

    private final StoryCompletionRepository storyCompletionRepository;
    private final WebClient.Builder webClientBuilder;
    
    @Value("${ai.server.url:http://localhost:8000}")
    private String aiServerUrl;
    
    // 성장 리포트 데이터 조회
    public Map<String, Object> getGrowthReport(Long childId, String period) {
        log.info("성장 리포트 생성 시작: childId={}, period={}", childId, period);

        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = calculateStartDate(period);
        LocalDateTime midDate = calculateMidDate(startDate, endDate);

        // 기간 전반부와 후반부 데이터 조회
        List<StoryCompletion> firstHalfCompletions = storyCompletionRepository
                .findByChildIdAndCompletedAtBetween(childId, startDate, midDate);

        List<StoryCompletion> secondHalfCompletions = storyCompletionRepository
                .findByChildIdAndCompletedAtBetween(childId, midDate, endDate);

        List<StoryCompletion> allCompletions = storyCompletionRepository
                .findByChildIdAndCompletedAtBetween(childId, startDate, endDate);

        log.info("조회된 동화: 전반부={}, 후반부={}, 전체={}", firstHalfCompletions.size(), secondHalfCompletions.size(), allCompletions.size());

        // Before/After 능력치 계산
        Map<String, Double> beforeAbilities = calculateAbilities(firstHalfCompletions);
        Map<String, Double> afterAbilities = calculateAbilities(secondHalfCompletions);

        // 강점 영역 (점수 높은 2개)
        List<Map<String, Object>> strengths = findTopAreas(afterAbilities, secondHalfCompletions, 2);

        // 성장 가능 영역 (점수 낮은 2개)
        List<Map<String, Object>> growthAreas = findBottomAreas(afterAbilities, secondHalfCompletions, 2);

        // 마일스톤 생성
        List<Map<String, Object>> milestones = generateMilestones(allCompletions, afterAbilities);

        // AI 종합 평가 생성
        String aiEvaluation = generateAIEvaluation(
                beforeAbilities, afterAbilities, strengths, growthAreas, allCompletions.size(), period
        );

        // AI 추천 활동 생성
        List<Map<String, Object>> recommendations = generateAIRecommendations(
                beforeAbilities, afterAbilities, strengths, growthAreas, allCompletions.size(), period
        );

        Map<String, Object> result = new HashMap<>();
        result.put("comparison", Map.of(
                "start", beforeAbilities,
                "end", afterAbilities
        ));
        result.put("aiEvaluation", aiEvaluation);
        result.put("strengths", strengths);
        result.put("growthAreas", growthAreas);
        result.put("milestones", milestones);
        result.put("recommendations", recommendations);

        log.info("성장 리포트 생성 완료");
        return result;

    }

    // AI 추천 활동 생성
    private List<Map<String, Object>> generateAIRecommendations(
            Map<String, Double> beforeAbilities,
            Map<String, Double> afterAbilities,
            List<Map<String, Object>> strengths,
            List<Map<String, Object>> growthAreas,
            int totalStories,
            String period) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("beforeAbilities", beforeAbilities);
            requestBody.put("afterAbilities", afterAbilities);
            requestBody.put("strengths", strengths);
            requestBody.put("growthAreas", growthAreas);
            requestBody.put("totalStories", totalStories);
            requestBody.put("period", period);

            Map<String, List<Map<String, Object>>> response = webClientBuilder.build()
                    .post()
                    .uri(aiServerUrl + "/ai/generate-growth-recommendations")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, List<Map<String, Object>>>>() {})
                    .block();

            List<Map<String, Object>> recommendations = response != null ? response.get("recommendations") : null;
            if (recommendations != null && !recommendations.isEmpty()) {
                log.info("AI 추천 활동 생성 성공: {}개", recommendations.size());
                return recommendations;
            }

            return getFallbackRecommendations(growthAreas);

        } catch (Exception e) {
            log.error("AI 추천 활동 생성 실패, 폴백 사용: {}", e.getMessage());
            return getFallbackRecommendations(growthAreas);
        }
    }


    // 풀백 추천 활동
    private List<Map<String, Object>> getFallbackRecommendations(List<Map<String, Object>> growthAreas) {
        List<Map<String, Object>> recommendations = new ArrayList<>();

        for (int i = 0; i < Math.min(3, growthAreas.size()); i++) {
            Map<String, Object> area = growthAreas.get(i);
            Map<String, Object> rec = new HashMap<>();
            rec.put("priority", i + 1);
            rec.put("activity", area.get("area") + " 향상 활동");
            rec.put("description", "아이와 함께 " + area.get("area") + " 능력을 키우는 활동을 해보세요.");
            rec.put("targetArea", area.get("area"));
            recommendations.add(rec);
        }

        return recommendations;
    }


    // AI 종합 평가 생성
    private String generateAIEvaluation(Map<String, Double> beforeAbilities,
                                        Map<String, Double> afterAbilities,
                                        List<Map<String, Object>> strengths,
                                        List<Map<String, Object>> growthAreas,
                                        int totalStories,
                                        String period) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("beforeAbilities", beforeAbilities);
            requestBody.put("afterAbilities", afterAbilities);
            requestBody.put("strengths", strengths);
            requestBody.put("growthAreas", growthAreas);
            requestBody.put("totalStories", totalStories);
            requestBody.put("period", period);

            Map<String, String> response = webClientBuilder.build()
                    .post()
                    .uri(aiServerUrl + "/ai/generate-growth-evaluation")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, String>>() {})
                    .block();

            String evaluation = response != null ? response.get("evaluation") : null;
            if (evaluation != null && !evaluation.isEmpty()) {
                log.info("AI 평가 생성 성공: {}자", evaluation.length());
                return evaluation;
            }

            return getFallbackEvaluation(strengths, totalStories);
        } catch (Exception e) {
            log.error("AI 평가 생성 실패, 풀백 사용: {}", e.getMessage());
            return getFallbackEvaluation(strengths, totalStories);
        }
    }

    // 풀백 평가
    private String getFallbackEvaluation(List<Map<String, Object>> strengths, int totalStories) {
        StringBuilder sb = new StringBuilder();
        sb.append("이번 기간 동안 아이는 ").append(totalStories).append("개의 동화를 완료하며 ");

        if (!strengths.isEmpty()) {
            String topStrength = (String) strengths.get(0).get("area");
            sb.append(topStrength).append(" 영역에서 ");
        }

        sb.append("긍정적인 성장을 보였습니다.");
        return sb.toString();
    }


    // 마일스톤 생성
    private List<Map<String, Object>> generateMilestones(List<StoryCompletion> allCompletions, Map<String, Double> afterAbilities) {
        // AI로 마일스톤 생성
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("beforeAbilities", new HashMap<>());
            requestBody.put("afterAbilities", afterAbilities);
            requestBody.put("strengths", new ArrayList<>());
            requestBody.put("growthAreas", new ArrayList<>());
            requestBody.put("totalStories", allCompletions.size());
            requestBody.put("period", "month");

            Map<String, List<Map<String, Object>>> response = webClientBuilder.build()
                    .post()
                    .uri(aiServerUrl + "/ai/generate-milestones")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, List<Map<String, Object>>>>() {})
                    .block();

            List<Map<String, Object>> aiMilestones = response != null ? response.get("milestones") : null;
            if (aiMilestones != null && !aiMilestones.isEmpty()) {
                // date 추가
                String today = LocalDateTime.now().toLocalDate().toString();
                aiMilestones.forEach(m -> m.put("date", today));
                log.info("AI 마일스톤 생성 성공: {}개", aiMilestones.size());
                return aiMilestones;
            }
        } catch (Exception e) {
            log.error("AI 마일스톤 생성 실패, 기본값 사용: {}", e.getMessage());
        }

        // 폴백: 기본 마일스톤
        List<Map<String, Object>> milestones = new ArrayList<>();
        int totalStories = allCompletions.size();
        if (totalStories >= 5) {
            milestones.add(Map.of(
               "achievement", totalStories + "개의 동화를 완료했습니다",
               "date", LocalDateTime.now().toLocalDate().toString()
            ));
        }

        afterAbilities.forEach((ability, score) -> {
            if (score >= 75) {
                milestones.add(Map.of(
                        "achievement", ability + " 능력 " + score.intValue() + "점 달성",
                        "date", LocalDateTime.now().toLocalDate().toString()
                ));
            }
        });

        return milestones;
    }

    private List<Map<String, Object>> findBottomAreas(Map<String, Double> afterAbilities, List<StoryCompletion> secondHalfCompletions, int limit) {
        List<Map<String, Object>> basicAreas = afterAbilities.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.comparingByValue())
                .limit(limit)
                .map(entry -> {
                    Map<String, Object> area = new HashMap<>();
                    area.put("area", entry.getKey());
                    area.put("score", entry.getValue().intValue());
                    return area;
                })
                .collect(Collectors.toList());

        // AI로 description과 recommendation 생성
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("growthAreas", basicAreas);
            requestBody.put("beforeAbilities", new HashMap<>());
            requestBody.put("afterAbilities", afterAbilities);
            requestBody.put("strengths", new ArrayList<>());
            requestBody.put("totalStories", 0);
            requestBody.put("period", "month");

            Map<String, List<Map<String, Object>>> response = webClientBuilder.build()
                    .post()
                    .uri(aiServerUrl + "/ai/generate-growth-area-descriptions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, List<Map<String, Object>>>>() {})
                    .block();

            List<Map<String, Object>> aiDescriptions = response != null ? response.get("descriptions") : null;
            if (aiDescriptions != null && !aiDescriptions.isEmpty()) {
                log.info("AI 성장 영역 설명 생성 성공: {}개", aiDescriptions.size());
                return aiDescriptions;
            }
        } catch (Exception e) {
            log.error("AI 성장 영역 설명 생성 실패, 기본값 사용: {}", e.getMessage());
        }

        // 폴백: 기본 description 추가
        basicAreas.forEach(area -> {
            area.put("description", area.get("area") + " 영역을 더 발전시킬 수 있습니다.");
            area.put("recommendation", area.get("area") + " 관련 동화를 함께 읽어보세요.");
        });

        return basicAreas;
    }

    // 강점 영역 찾기
    private List<Map<String, Object>> findTopAreas(Map<String, Double> afterAbilities, List<StoryCompletion> secondHalfCompletions, int limit) {
        List<Map<String, Object>> basicStrengths = afterAbilities.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> {
                    Map<String, Object> area = new HashMap<>();
                    area.put("area", entry.getKey());
                    area.put("score", entry.getValue().intValue());
                    area.put("example", findExample(secondHalfCompletions, entry.getKey()));
                    return area;
                })
                .collect(Collectors.toList());

        // AI로 description 생성
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("beforeAbilities", new HashMap<>());
            requestBody.put("afterAbilities", afterAbilities);
            requestBody.put("strengths", basicStrengths);
            requestBody.put("growthAreas", new ArrayList<>());
            requestBody.put("totalStories", 0);
            requestBody.put("period", "month");

            Map<String, List<Map<String, Object>>> response = webClientBuilder.build()
                    .post()
                    .uri(aiServerUrl + "/ai/generate-strength-descriptions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, List<Map<String, Object>>>>() {})
                    .block();

            List<Map<String, Object>> aiStrengths = response != null ? response.get("descriptions") : null;
            if (aiStrengths != null && !aiStrengths.isEmpty()) {
                log.info("AI 강점 설명 생성 성공: {}개", aiStrengths.size());
                return aiStrengths;
            }
        } catch (Exception e) {
            log.error("AI 강점 설명 생성 실패, 기본값 사용: {}", e.getMessage());
        }

        // 폴백: 기본 description 추가
        basicStrengths.forEach(area -> {
            area.put("description", area.get("area") + " 영역에서 뛰어난 능력을 보여줍니다.");
        });

        return basicStrengths;
    }

    // 능력치 계산
    private Map<String, Double> calculateAbilities(List<StoryCompletion> firstHalfCompletions) {
        Map<String, Integer> abilityPoints = new HashMap<>();
        Map<String, Integer> abilityCount = new HashMap<>();

        for (StoryCompletion completion : firstHalfCompletions) {
            List<StoryCompletion.ChoiceRecord> choices = completion.getChoicesJson();
            if (choices != null) {
                for (StoryCompletion.ChoiceRecord choice : choices) {
                    String abilityType = choice.getAbilityType();
                    Integer points = choice.getAbilityPoints();

                    if (abilityType != null && points != null) {
                        abilityPoints.putIfAbsent(abilityType, 0);
                        abilityCount.putIfAbsent(abilityType, 0);

                        abilityPoints.put(abilityType, abilityPoints.get(abilityType) + points);
                        abilityCount.put(abilityType, abilityCount.get(abilityType) + 1);
                    }
                }
            }
        }

        // 평균 점수 계산 (0-100 스케일)
        // DB 점수 범위: 10-15점 → 0-100으로 정규화
        Map<String, Double> result = new HashMap<>();
        for (Map.Entry<String, Integer> entry : abilityPoints.entrySet()) {
            String ability = entry.getKey();
            int count = abilityCount.get(ability);
            if (count > 0) {
                double avgPoints = (double) entry.getValue() / count;
                // 10점 = 0점, 15점 = 100점으로 정규화
                double normalized = ((avgPoints - 10.0) / 5.0) * 100.0;
                result.put(ability, Math.max(0.0, Math.min(normalized, 100.0)));
            }
        }

        return result;
    }

    // 중간 지점 계산
    private LocalDateTime calculateMidDate(LocalDateTime startDate, LocalDateTime endDate) {
        long daysTotal = java.time.Duration.between(startDate, endDate).toDays();
        return startDate.plusDays(daysTotal / 2);
    }

    // 기간 시작일 계산
    private LocalDateTime calculateStartDate(String period) {
        LocalDateTime now = LocalDateTime.now();
        return switch(period) {
            case "month" -> now.minusMonths(1);
            case "quarter" -> now.minusMonths(3);
            case "halfyear" -> now.minusMonths(6);
            default -> now.minusMonths(1);
        };
    }

    // 예시 찾기
    private String findExample(List<StoryCompletion> completions, String ability) {
        for (StoryCompletion completion : completions) {
            List<StoryCompletion.ChoiceRecord> choices = completion.getChoicesJson();
            if (choices != null) {
                for (StoryCompletion.ChoiceRecord choice : choices) {
                    if (ability.equals(choice.getAbilityType())) {
                        String storyTitle = completion.getStoryTitle();
                        String choiceText = choice.getChoiceText();

                        // AI로 자연스러운 예시 설명 생성
                        try {
                            Map<String, Object> requestBody = new HashMap<>();
                            requestBody.put("storyTitle", storyTitle);
                            requestBody.put("choiceText", choiceText);
                            requestBody.put("ability", ability);

                            Map<String, String> response = webClientBuilder.build()
                                    .post()
                                    .uri(aiServerUrl + "/ai/generate-example-description")
                                    .bodyValue(requestBody)
                                    .retrieve()
                                    .bodyToMono(new ParameterizedTypeReference<Map<String, String>>() {})
                                    .block();

                            String aiExample = response != null ? response.get("example") : null;
                            if (aiExample != null && !aiExample.isEmpty()) {
                                return aiExample;
                            }
                        } catch (Exception e) {
                            log.error("AI 예시 설명 생성 실패, 기본값 사용: {}", e.getMessage());
                        }

                        // 폴백: 기본 형식
                        return "'" + storyTitle + "'에서 '" + choiceText + "'를 선택했습니다.";
                    }
                }
            }
        }
        return ability + " 능력을 보여주는 선택을 했습니다.";
    }

}
