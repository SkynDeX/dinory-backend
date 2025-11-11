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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    public Map<String, Object> getGrowthReport(Long childId, String period, LocalDate customStartDate, LocalDate customEndDate) {
        log.info("성장 리포트 생성 시작 (최적화 버전): childId={}, period={}", childId, period);

        LocalDateTime startDate;
        LocalDateTime endDate;

        if (customStartDate != null && customEndDate != null) {
            startDate = customStartDate.atStartOfDay();
            endDate = customEndDate.atTime(23, 59, 59);
        } else {
            startDate = calculateStartDate(period);
            endDate = LocalDateTime.now();
        }

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

        // 강점/성장가능 영역 기본 데이터 (점수, 예시만)
        List<Map<String, Object>> basicStrengths = findTopAreasBasic(afterAbilities, secondHalfCompletions, 2);
        List<Map<String, Object>> basicGrowthAreas = findBottomAreasBasic(afterAbilities, secondHalfCompletions,2);

        // AI 분석은 별도 엔드포인트로 분리 (성능 최적화)
        // 기본 템플릿 설명 추가
        basicStrengths.forEach(s -> s.put("description", ""));
        basicGrowthAreas.forEach(g -> {
            g.put("description", "");
            g.put("recommendation", "");
        });

        Map<String, Object> result = new HashMap<>();
        result.put("comparison", Map.of(
                "start", beforeAbilities,
                "end", afterAbilities
        ));
        result.put("aiEvaluation", "");  // 비동기 로딩
        result.put("strengths", basicStrengths);
        result.put("growthAreas", basicGrowthAreas);
        result.put("milestones", new ArrayList<>());  // 비동기 로딩
        result.put("recommendations", new ArrayList<>());  // 비동기 로딩

        log.info("성장 리포트 생성 완료 (최적화)");
        return result;
    }

    // AI 분석만 별도 조회 (비동기 로딩용)
    public Map<String, Object> getGrowthReportAIAnalysis(Long childId, String period, LocalDate customStartDate, LocalDate customEndDate) {
        log.info("성장 리포트 AI 분석 시작: childId={}, period={}", childId, period);

        LocalDateTime startDate;
        LocalDateTime endDate;

        if (customStartDate != null && customEndDate != null) {
            startDate = customStartDate.atStartOfDay();
            endDate = customEndDate.atTime(23, 59, 59);
        } else {
            startDate = calculateStartDate(period);
            endDate = LocalDateTime.now();
        }

        LocalDateTime midDate = calculateMidDate(startDate, endDate);

        // 기간 전반부와 후반부 데이터 조회
        List<StoryCompletion> firstHalfCompletions = storyCompletionRepository
                .findByChildIdAndCompletedAtBetween(childId, startDate, midDate);

        List<StoryCompletion> secondHalfCompletions = storyCompletionRepository
                .findByChildIdAndCompletedAtBetween(childId, midDate, endDate);

        List<StoryCompletion> allCompletions = storyCompletionRepository
                .findByChildIdAndCompletedAtBetween(childId, startDate, endDate);

        // Before/After 능력치 계산
        Map<String, Double> beforeAbilities = calculateAbilities(firstHalfCompletions);
        Map<String, Double> afterAbilities = calculateAbilities(secondHalfCompletions);

        // 강점/성장가능 영역 기본 데이터
        List<Map<String, Object>> basicStrengths = findTopAreasBasic(afterAbilities, secondHalfCompletions, 2);
        List<Map<String, Object>> basicGrowthAreas = findBottomAreasBasic(afterAbilities, secondHalfCompletions,2);

        // 🚀 통합 AI 호출
        Map<String, Object> aiContent = generateAllAIContent(
                beforeAbilities, afterAbilities, basicStrengths, basicGrowthAreas, allCompletions.size(), period
        );

        // AI 응답에서 데이터 추출
        String aiEvaluation = (String) aiContent.getOrDefault("evaluation", "");
        List<Map<String, Object>> recommendations = (List<Map<String, Object>>) aiContent.getOrDefault("recommendations", new ArrayList<>());
        List<Map<String, Object>> aiMilestones = (List<Map<String, Object>>) aiContent.getOrDefault("milestones", new ArrayList<>());
        List<Map<String, Object>> strengthDescriptions = (List<Map<String, Object>>) aiContent.getOrDefault("strengthDescriptions", new ArrayList<>());
        List<Map<String, Object>> growthDescriptions = (List<Map<String, Object>>) aiContent.getOrDefault("growthAreaDescriptions", new ArrayList<>());

        // 마일스톤에 날짜 추가
        String today = LocalDateTime.now().toLocalDate().toString();
        aiMilestones.forEach(m -> m.put("date", today));

        // 폴백: AI 실패시 기본값 사용
        if (aiEvaluation.isEmpty()) {
            aiEvaluation = getFallbackEvaluation(basicStrengths, allCompletions.size());
        }
        if (recommendations.isEmpty()) {
            recommendations = getFallbackRecommendations(basicGrowthAreas);
        }
        if (aiMilestones.isEmpty()) {
            aiMilestones = getFallbackMilestones(allCompletions.size(), afterAbilities);
        }
        if (strengthDescriptions.isEmpty()) {
            strengthDescriptions = basicStrengths;
            strengthDescriptions.forEach(s -> s.put("description", s.get("area") + " 영역에서 뛰어난 능력을 보여줍니다."));
        }
        if (growthDescriptions.isEmpty()) {
            growthDescriptions = basicGrowthAreas;
            growthDescriptions.forEach(g -> {
                g.put("description", g.get("area") + " 영역을 더 발전시킬 수 있습니다.");
                g.put("recommendation", g.get("area") + " 관련 동화를 함께 읽어보세요.");
            });
        }

        Map<String, Object> result = new HashMap<>();
        result.put("aiEvaluation", aiEvaluation);
        result.put("strengthDescriptions", strengthDescriptions);
        result.put("growthAreaDescriptions", growthDescriptions);
        result.put("milestones", aiMilestones);
        result.put("recommendations", recommendations);

        log.info("성장 리포트 AI 분석 완료");
        return result;
    }

    // 🚀 통합 AI 콘텐츠 생성 (한 번의 API 호출)
    private Map<String, Object> generateAllAIContent(
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

            log.info("통합 AI 콘텐츠 요청 시작");
            Map<String, Object> response = webClientBuilder.build()
                    .post()
                    .uri(aiServerUrl + "/ai/generate-all-growth-content")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response != null) {
                log.info("통합 AI 콘텐츠 생성 성공");
                return response;
            }
        } catch (Exception e) {
            log.error("통합 AI 콘텐츠 생성 실패: {}", e.getMessage());
        }

        return new HashMap<>();
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

    // 기본 강점 영역 (AI 설명 제외)
    private List<Map<String, Object>> findTopAreasBasic(Map<String, Double> afterAbilities, List<StoryCompletion> completions, int limit) {
        return afterAbilities.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> {
                    Map<String, Object> area = new HashMap<>();
                    area.put("area", entry.getKey());
                    area.put("score", entry.getValue().intValue());
                    area.put("examples", findExamples(completions, entry.getKey()));  // 복수형으로 변경
                    return area;
                })
                .collect(Collectors.toList());
    }

    // 기본 성장가능 영역 (AI 설명 제외)
    private List<Map<String, Object>> findBottomAreasBasic(Map<String, Double> afterAbilities, List<StoryCompletion> completions, int limit) {
        return afterAbilities.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.comparingByValue())
                .limit(limit)
                .map(entry -> {
                    Map<String, Object> area = new HashMap<>();
                    area.put("area", entry.getKey());
                    area.put("score", entry.getValue().intValue());
                    area.put("examples", findExamples(completions, entry.getKey()));
                    return area;
                })
                .collect(Collectors.toList());
    }

    // 기본 예시 찾기 (AI 생성 제외)
    private String findExampleBasic(List<StoryCompletion> completions, String ability) {
        for (StoryCompletion completion : completions) {
            List<StoryCompletion.ChoiceRecord> choices = completion.getChoicesJson();
            if (choices != null) {
                for (StoryCompletion.ChoiceRecord choice : choices) {
                    if (ability.equals(choice.getAbilityType())) {
                        return "'" + completion.getStoryTitle() + "'에서 '" + choice.getChoiceText() + "'를 선택했습니다.";
                    }
                }
            }
        }
        return ability + " 능력을 보여주는 선택을 했습니다.";
    }

    // 폴백 마일스톤
    private List<Map<String, Object>> getFallbackMilestones(int totalStories, Map<String, Double> afterAbilities) {
        List<Map<String, Object>> milestones = new ArrayList<>();
        String today = LocalDateTime.now().toLocalDate().toString();

        if (totalStories >= 5) {
            milestones.add(Map.of(
                    "achievement", totalStories + "개의 동화를 완료했습니다",
                    "date", today
            ));
        }

        afterAbilities.forEach((ability, score) -> {
            if (score >= 75) {
                milestones.add(Map.of(
                        "achievement", ability + " 능력 " + score.intValue() + "점 달성",
                        "date", today
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
                    area.put("examples", findExamples(secondHalfCompletions, entry.getKey()));  // 복수형으로 변경
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

        int minPoints = Integer.MAX_VALUE;
        int maxPoints = Integer.MIN_VALUE;
        int totalCount = 0;

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

                        // 디버그: 점수 범위 확인
                        minPoints = Math.min(minPoints, points);
                        maxPoints = Math.max(maxPoints, points);
                        totalCount++;
                    }
                }
            }
        }

        log.info("=== [GrowthReport] DB 점수 범위 확인 === min: {}, max: {}, totalCount: {}",
                 minPoints, maxPoints, totalCount);

        // 평균 점수 계산 (0-100 스케일)
        // DB 점수 범위: 10-17점 → 0-100으로 정규화
        // 일반 선택지: 10-15점, 커스텀 선택지: 12-17점 (보너스 +2점)
        Map<String, Double> result = new HashMap<>();
        for (Map.Entry<String, Integer> entry : abilityPoints.entrySet()) {
            String ability = entry.getKey();
            int count = abilityCount.get(ability);
            if (count > 0) {
                double avgPoints = (double) entry.getValue() / count;
                log.info("능력치 평균 - {}: {} (총 {}점 / {}회)", ability, avgPoints, entry.getValue(), count);
                // 10점 = 0점, 15점 = 100점으로 정규화 (커스텀은 최대 17점이지만 15점 기준으로 정규화)
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

    // 부정적 키워드 검사
    private boolean containsNegativeKeywords(String text) {
        if (text == null) {
            return false;
        }

        String lowerText = text.toLowerCase();

        // 책임 회피 관련 키워드
        String[] negativeKeywords = {
                "떠넘", "대신 해", "너가 해", "너 혼자", "네가 해",
                "알아서 해", "니가 해", "혼자 해줘", "혼자 해주라",
                "때리", "패", "죽", "욕", "비웃", "놀리", "따돌",
                "미워", "싫어", "바보", "멍청"
        };

        for (String keyword : negativeKeywords) {
            if (lowerText.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    // 예시 찾기 - 여러 동화의 예시를 리스트로 반환
    private List<String> findExamples(List<StoryCompletion> completions, String ability) {
        log.info("=== findExamples 시작: ability={}, completions={} ===", ability, completions.size());

        List<String> examples = new ArrayList<>();
        Set<String> usedStories = new HashSet<>();  // 동화 제목 중복 방지

        for (StoryCompletion completion : completions) {
            // 이미 3개 예시를 찾았으면 중단
            if (examples.size() >= 3) {
                break;
            }

            // 같은 동화는 한 번만 포함
            if (usedStories.contains(completion.getStoryTitle())) {
                continue;
            }

            List<StoryCompletion.ChoiceRecord> choices = completion.getChoicesJson();
            log.info("동화 '{}' 선택지 개수: {}", completion.getStoryTitle(), choices != null ? choices.size() : 0);

            if (choices != null) {
                for (StoryCompletion.ChoiceRecord choice : choices) {
                    log.info("  선택지: abilityType={}, text={}", choice.getAbilityType(), choice.getAbilityPoints(), choice.getChoiceText());

                    // 해당 능력이고, 점수가 12점 이상인 긍적적인 선택만 예시로 적용
                    if (ability.equals(choice.getAbilityType()) &&
                        choice.getAbilityType() != null &&
                        choice.getAbilityPoints() >= 12) {

                        String choiceText = choice.getChoiceText();

                        // 부정적 키워드가 포함된 선택지는 제외
                        if (containsNegativeKeywords(choiceText)) {
                            log.info("부정적 키워드 포함으로 제외: {}", choiceText);
                            continue;
                        }

                        String storyTitle = completion.getStoryTitle();

                        // 예시 추가
                        String example = "'" + storyTitle + "'에서 '" + choiceText + "'를 선택했습니다.";
                        examples.add(example);
                        usedStories.add(storyTitle);
                        log.info("✓ 예시 추가 ({}점): {}", choice.getAbilityPoints(), example);
                        break;  // 이 동화에서는 첫 번째 매칭만 사용
                    }
                }
            }
        }

        // 예시가 없으면 기본 메시지
        if (examples.isEmpty()) {
            log.warn("예시를 찾지 못함. 기본 메시지 사용");
            examples.add(ability + " 능력을 보여주는 선택을 했습니다.");
        }

        log.info("=== findExamples 완료: {}개 예시 반환 ===", examples.size());
        return examples;
    }

}
