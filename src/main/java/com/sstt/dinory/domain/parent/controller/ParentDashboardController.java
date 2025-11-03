package com.sstt.dinory.domain.parent.controller;

import com.sstt.dinory.domain.parent.dto.history.StoryHistoryResponseDto;
import com.sstt.dinory.domain.parent.dto.overview.OverviewResponseDto;
import com.sstt.dinory.domain.parent.service.OverviewService;
import com.sstt.dinory.domain.parent.service.StoryHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.Response;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/parent/dashboard")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
@Slf4j
public class ParentDashboardController {

    private final StoryHistoryService storyHistoryService;
    private final OverviewService overviewService;

    @GetMapping("/overview")
    public ResponseEntity<OverviewResponseDto> getOverview(
            @RequestParam Long childId,
            @RequestParam(defaultValue = "day") String period) {

        Map<String, Object> data = overviewService.getOverview(childId, period);

        OverviewResponseDto overviewResponseDto = OverviewResponseDto.builder()
                .abilities((Map<String, Double>) data.get("abilities"))
                .totalStories((Integer) data.get("totalStories"))
                .totalTime((Integer) data.get("totalTime"))
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

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "Parent Dashboard Controller is working!"));
    }
}
