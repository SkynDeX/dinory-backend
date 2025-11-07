package com.sstt.dinory.domain.parent.controller;

import com.sstt.dinory.domain.parent.dto.history.StoryHistoryResponseDto;
import com.sstt.dinory.domain.parent.dto.overview.OverviewResponseDto;
import com.sstt.dinory.domain.parent.service.GrowthReportService;
import com.sstt.dinory.domain.parent.service.OverviewService;
import com.sstt.dinory.domain.parent.service.StoryHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/parent/dashboard")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
@Slf4j
public class ParentDashboardController {

    private final StoryHistoryService storyHistoryService;
    private final OverviewService overviewService;
    private final GrowthReportService growthReportService;

    @GetMapping("/overview")
    public ResponseEntity<OverviewResponseDto> getOverview(
            @RequestParam Long childId,
            @RequestParam(defaultValue = "day") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        Map<String, Object> data = overviewService.getOverview(childId, period, startDate, endDate);

        OverviewResponseDto overviewResponseDto = OverviewResponseDto.builder()
                .abilities((Map<String, Double>) data.get("abilities"))
                .abilityDetails((Map<String, Object>) data.get("abilityDetails"))
                .relatedStories((Map<String, List<Map<String, String>>>) data.get("relatedStories"))
                .totalStories((Integer) data.get("totalStories"))
                .totalTime((Integer) data.get("totalTime"))
                .emotions((List<Map<String, Object>>) data.get("emotions"))
                .choices((List<Map<String, Object>>) data.get("choices"))
                .topics((List<Map<String, Object>>) data.get("topics"))
                .recentStories((List<Map<String, Object>>) data.get("recentStories"))
                .build();

        return ResponseEntity.ok(overviewResponseDto);
    }

    // Topics 별도 조회 (비동기 로딩용)
    @GetMapping("/overview/topics")
    public ResponseEntity<List<Map<String, Object>>> getTopics(
            @RequestParam Long childId,
            @RequestParam(defaultValue = "day") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("Topics 조회 요청: childId={}, period={}", childId, period);

        try {
            List<Map<String, Object>> topics = overviewService.getTopics(childId, period, startDate, endDate);
            return ResponseEntity.ok(topics);
        } catch (Exception e) {
            log.error("Topics 조회 실패", e);
            // 실패 시 빈 리스트 반환
            return ResponseEntity.ok(new ArrayList<>());
        }
    }

    // AI 인사이트 별도 조회 (비동기 로딩용)
    @GetMapping("/overview/insights")
    public ResponseEntity<Map<String, Object>> getAIInsights(
            @RequestParam Long childId,
            @RequestParam(defaultValue = "day") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("AI 인사이트 조회 요청: childId={}, period={}", childId, period);

        try {
            Map<String, Object> insights = overviewService.getAIInsights(childId, period, startDate, endDate);
            return ResponseEntity.ok(insights);
        } catch (Exception e) {
            log.error("AI 인사이트 조회 실패", e);
            // 실패 시 기본값 반환
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("quickInsight", "아이와 함께 동화를 읽으며 성장해보세요!");
            fallback.put("recommendation", Map.of(
                "ability", "용기",
                "message", "용기 관련 동화를 함께 읽어보세요."
            ));
            return ResponseEntity.ok(fallback);
        }
    }

    @GetMapping("/story-history")
    public ResponseEntity<StoryHistoryResponseDto> getStoryHistory(
            @RequestParam Long childId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        log.info("=== 동화 히스토리 조회 요청 ===");
        log.info("childId: {}", childId);
        log.info("startDate: {}", startDate);
        log.info("endDate: {}", endDate);
        log.info("page: {}, size: {}", page, size);

        Pageable pageable = PageRequest.of(page, size);
        StoryHistoryResponseDto response = storyHistoryService.getStoryHistory(childId, startDate, endDate, pageable);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/growth-report")
    public ResponseEntity<Map<String, Object>> getGrowthReport(
            @RequestParam Long childId,
            @RequestParam(defaultValue = "month") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        log.info("성장 리포트 조회: childId={}, period={}", childId, period);

        try {
            Map<String, Object> report = growthReportService.getGrowthReport(childId, period, startDate, endDate);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            log.error("성장 리포트 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "리포트 생성 실패: " + e.getMessage()));
        }
    }

    // 성장 리포트 AI 분석 별도 조회 (비동기 로딩용)
    @GetMapping("/growth-report/ai-analysis")
    public ResponseEntity<Map<String, Object>> getGrowthReportAIAnalysis(
            @RequestParam Long childId,
            @RequestParam(defaultValue = "month") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        log.info("성장 리포트 AI 분석 조회: childId={}, period={}", childId, period);

        try {
            Map<String, Object> analysis = growthReportService.getGrowthReportAIAnalysis(childId, period, startDate, endDate);
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            log.error("성장 리포트 AI 분석 실패", e);
            // 실패 시 빈 값 반환
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("aiEvaluation", "AI 분석을 불러오는데 실패했습니다.");
            fallback.put("strengthDescriptions", new ArrayList<>());
            fallback.put("growthAreaDescriptions", new ArrayList<>());
            fallback.put("milestones", new ArrayList<>());
            fallback.put("recommendations", new ArrayList<>());
            return ResponseEntity.ok(fallback);
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "Parent Dashboard Controller is working!"));
    }
}
