package com.sstt.dinory.domain.parent.service;


import com.sstt.dinory.domain.child.entity.Child;
import com.sstt.dinory.domain.child.repository.ChildRepository;
import com.sstt.dinory.domain.parent.dto.history.StoryCompletionDto;
import com.sstt.dinory.domain.parent.dto.history.StoryHistoryResponseDto;
import com.sstt.dinory.domain.story.entity.StoryCompletion;
import com.sstt.dinory.domain.story.repository.StoryCompletionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class StoryHistoryService {

    private final StoryCompletionRepository completionRepository;
    private final ChildRepository childRepository;

    public StoryHistoryResponseDto getStoryHistory(Long childId, LocalDate startDate, LocalDate endDate, Pageable pageable) {

        // 자녀 존재 확인
        Child child = childRepository.findById(childId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 자녀입니다."));

        // 날짜 범위 설정 (기본값: 전체기간)
        LocalDateTime startDateTime = (startDate != null) ? startDate.atStartOfDay() : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime endDateTime = (endDate != null) ? endDate.atTime(23, 59, 59) : LocalDateTime.now();

        // 완료된 동화 조회 (페이지네이션)
        Page<StoryCompletion> completionsPage = completionRepository.findByChildIdAndCompletedAtBetweenOrderByCompletedAtDesc(
                childId, startDateTime, endDateTime, pageable
        );

        // DTO 변환
        List<StoryCompletionDto> completionDtos = completionsPage.getContent().stream()
                .map(completion -> convertToDto(completion))
                .collect(Collectors.toList());

        // 통계 계산 (전체 데이터 기준)
        List<StoryCompletion> allCompletions = completionRepository.findByChildIdAndCompletedAtBetween(
                childId, startDateTime, endDateTime
        );

        Map<String, Object> statistics = calculateStatistics(allCompletions);

        // 페이지네이션 정보
        Map<String, Object> pagination = new HashMap<>();
        pagination.put("currentPage", completionsPage.getNumber());
        pagination.put("totalPages", completionsPage.getTotalPages());
        pagination.put("totalElements", completionsPage.getTotalElements());

        return StoryHistoryResponseDto.builder()
                .completions(completionDtos)
                .statistics(statistics)
                .pagination(pagination)
                .build();
    }

    private StoryCompletionDto convertToDto(StoryCompletion completion) {
        // 선택 요약 계산
        Map<String, Integer> choicesSummary = new HashMap<>();
        List<StoryCompletion.ChoiceRecord> choices = completion.getChoicesJson();

        if (choices != null) {
            for (StoryCompletion.ChoiceRecord choice : choices) {
                String abilityType = choice.getAbilityType();
                if (abilityType != null) {
                    choicesSummary.put(abilityType, choicesSummary.getOrDefault(abilityType, 0) + 1);
                }
            }
        }

        // interests는 storyCompletion에서 직접 가져오기
        List<String> interests = completion.getInterests();
        if (interests == null || interests.isEmpty()) {
            // fallback: child의 interests 사용
            interests = completion.getChild().getInterests();
        }

        return StoryCompletionDto.builder()
                .completionId(completion.getId())
                .storyId(completion.getStory().getId())
                .storyTitle(completion.getStory().getTitle())
                .storyTheme(completion.getStory().getTheme())
                .completedAt(completion.getCompletedAt())
                .duration(completion.getTotalTime())
                .emotion(completion.getEmotion())
                .interests(interests)
                .choicesSummary(choicesSummary)
                .build();
    }

    private Map<String, Object> calculateStatistics(List<StoryCompletion> completions) {
        Map<String, Object> stats = new HashMap<>();

        int totalStories = completions.size();
        int totalReadTime = 0;

        for (StoryCompletion completion : completions) {
            if (completion.getTotalTime() != null) {
                totalReadTime += completion.getTotalTime();
            }
        }

        double averageDuration = totalStories > 0 ? (double) totalReadTime / totalStories : 0;
        
        // 연속 학습 일수 계산
        int consecutiveDays = calculateConsecutiveDays(completions);

        stats.put("totalStories", totalStories);
        stats.put("totalReadTime", totalReadTime);
        stats.put("averageDuration", Math.round(averageDuration));
        stats.put("consecutiveDays", consecutiveDays);

        return stats;
    }

    private int calculateConsecutiveDays(List<StoryCompletion> completions) {
        if (completions.isEmpty()) return 0;

        // 날짜별로 그룹핑 (최신순 정렬되어 있음)
        Set<LocalDate> uniqueDates = new HashSet<>();
        for (StoryCompletion completion : completions) {
            if (completion.getCompletedAt() != null) {
                uniqueDates.add(completion.getCompletedAt().toLocalDate());
            }
        }

        // 날짜 정렬 (최신순)
        List<LocalDate> sortedDates = new ArrayList<>(uniqueDates);
        sortedDates.sort(Comparator.reverseOrder());

        if (sortedDates.isEmpty()) return 0;

        // 오늘 또는 어제부터 시작하는지 확인
        LocalDate today = LocalDate.now();
        LocalDate latestDate = sortedDates.get(0);

        // 최근 동화가 오늘이거나 어제가 아니면 연속 끊김
        if (latestDate.isBefore(today.minusDays(1))) {
            return 0;
        }

        // 연속 일수 계산
        int consecutive = 1;
        for (int i = 1; i < sortedDates.size(); i++) {
            LocalDate current = sortedDates.get(i);
            LocalDate previous = sortedDates.get(i - 1);

            // 하루 차이인지 확인
            if (previous.minusDays(1).equals(current)) {
                consecutive++;
            } else {
                break; // 연속 끊김
            }
        }

        return consecutive;
    }

}
