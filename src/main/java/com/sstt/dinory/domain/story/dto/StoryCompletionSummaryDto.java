package com.sstt.dinory.domain.story.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.sstt.dinory.domain.story.entity.Scene;
import com.sstt.dinory.domain.story.entity.StoryCompletion;
import com.sstt.dinory.domain.story.repository.SceneRepository;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class StoryCompletionSummaryDto {

    private Long completionId;
    private Long childId;
    private String childName;
    private Long  storyId;
    private String pineconeId; // Pinecone UUID
    private String storyTitle;
    private Integer totalTime;
    private LocalDateTime completedAt;
    private List<ChoiceRecordDto> choices;
    private List<SceneDto> scenes;  // [2025-11-04 김민중 추가] Scene 정보

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
        private String choiceText;  // [2025-11-05 추가] 선택지 텍스트
        private String abilityType;
        private Integer abilityPoints;
    }

    // [2025-11-04 김민중 추가] Scene 정보 DTO
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SceneDto {
        private Integer sceneNumber;
        private String content;  // 씬 내용
        private String imageUrl;
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
                    .choiceText(choice.getChoiceText())  // [2025-11-05 추가]
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
            .pineconeId(completion.getStory().getPineconeId())
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

    /**
     * [2025-11-05 추가] Scene 정보를 포함한 StoryCompletionSummaryDto 생성
     */
    public static StoryCompletionSummaryDto fromEntity(StoryCompletion completion, SceneRepository sceneRepository) {
        // 기본 정보는 from() 메서드로 생성
        StoryCompletionSummaryDto dto = from(completion);

        // Scene 정보 추가
        List<Scene> scenes = sceneRepository.findByStoryIdOrderBySceneNumberAsc(completion.getStory().getId());
        List<SceneDto> sceneDtos = scenes.stream()
            .map(scene -> SceneDto.builder()
                .sceneNumber(scene.getSceneNumber())
                .content(scene.getContent())
                .imageUrl(scene.getImageUrl())
                .build())
            .collect(Collectors.toList());

        // toBuilder()를 사용해서 scenes 필드 추가
        return dto.toBuilder()
            .scenes(sceneDtos)
            .build();
    }
}