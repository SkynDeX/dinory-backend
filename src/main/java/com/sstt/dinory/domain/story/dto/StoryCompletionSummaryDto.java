package com.sstt.dinory.domain.story.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.sstt.dinory.domain.story.entity.StoryCompletion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryCompletionSummaryDto {

    private Long completionId;
    private Long childId;
    private String childName;
    private String storyId;
    private String storyTitle;
    private Integer totalTime;
    private LocalDateTime completedAt;
    private List<ChoiceRecordDto> choices;

    // 능력치 합계
    private Integer totalCourage;      // 용기
    private Integer totalEmpathy;      // 공감
    private Integer totalCreativity;   // 창의성
    private Integer totalResponsibility; // 책임감
    private Integer totalFriendship;   // 우정

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChoiceRecordDto {
        private Integer sceneNumber;
        private String choiceId;  // AI 서버에서 "c11", "c12" 등의 String 반환
        private String abilityType;
        private Integer abilityPoints;
    }

    public static StoryCompletionSummaryDto from(StoryCompletion completion) {
        // 능력치별 합계 계산
        int totalCourage = 0;
        int totalEmpathy = 0;
        int totalCreativity = 0;
        int totalResponsibility = 0;
        int totalFriendship = 0;

        List<ChoiceRecordDto> choiceDtos = completion.getChoicesJson().stream()
            .map(choice -> {
                return ChoiceRecordDto.builder()
                    .sceneNumber(choice.getSceneNumber())
                    .choiceId(choice.getChoiceId())
                    .abilityType(choice.getAbilityType())
                    .abilityPoints(choice.getAbilityPoints())
                    .build();
            })
            .toList();

        for (StoryCompletion.ChoiceRecord choice : completion.getChoicesJson()) {
            if (choice.getAbilityType() != null && choice.getAbilityPoints() != null) {
                switch (choice.getAbilityType()) {
                    case "용기", "courage" -> totalCourage += choice.getAbilityPoints();
                    case "공감", "empathy" -> totalEmpathy += choice.getAbilityPoints();
                    case "창의성", "creativity" -> totalCreativity += choice.getAbilityPoints();
                    case "책임감", "responsibility" -> totalResponsibility += choice.getAbilityPoints();
                    case "우정", "friendship" -> totalFriendship += choice.getAbilityPoints();
                }
            }
        }

        return StoryCompletionSummaryDto.builder()
            .completionId(completion.getId())
            .childId(completion.getChild().getId())
            .childName(completion.getChild().getName())
            .storyId(completion.getStory().getId())
            .storyTitle(completion.getStory().getTitle())
            .totalTime(completion.getTotalTime())
            .completedAt(completion.getCompletedAt())
            .choices(choiceDtos)
            .totalCourage(totalCourage)
            .totalEmpathy(totalEmpathy)
            .totalCreativity(totalCreativity)
            .totalResponsibility(totalResponsibility)
            .totalFriendship(totalFriendship)
            .build();
    }
}