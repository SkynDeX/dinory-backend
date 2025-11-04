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
            @RequestParam(defaultValue = "day") String period) {

        Map<String, Object> data = overviewService.getOverview(childId, period);

        OverviewResponseDto overviewResponseDto = OverviewResponseDto.builder()
                .abilities((Map<String, Double>) data.get("abilities"))
                .totalStories((Integer) data.get("totalStories"))
                .totalTime((Integer) data.get("totalTime"))
                .emotions((List<Map<String, Object>>) data.get("emotions"))
                .choices((List<Map<String, Object>>) data.get("choices"))
                .topics((List<Map<String, Object>>) data.get("topics"))
                .recentStories((List<Map<String, Object>>) data.get("recentStories"))
                .build();

        return ResponseEntity.ok(overviewResponseDto);
    };

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
            @RequestParam(defaultValue = "month") String period
    ) {
        log.info("성장 리포트 조회: childId={}, period={}", childId, period);

        try {
            Map<String, Object> report = growthReportService.getGrowthReport(childId, period);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            log.error("성장 리포트 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "리포트 생성 실패: " + e.getMessage()));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "Parent Dashboard Controller is working!"));
    }
}
