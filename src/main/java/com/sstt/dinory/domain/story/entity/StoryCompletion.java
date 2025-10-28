package com.sstt.dinory.domain.story.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.sstt.dinory.domain.child.entity.Child;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "story_completion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_id", nullable = false)
    private Child child;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "story_id", nullable = false)
    private Story story;

    @Column(name = "total_time")
    private Integer totalTime;  // 초

    @Column(name = "completed_at")
    private LocalDateTime completedAt; // 완료 시점에만 세팅

    // [2025-10-28 김민중 추가] 동화 시작 시 아이의 감정 상태
    @Column(length = 50)
    private String emotion;  // "기뻐요", "슬퍼요", "화나요", "무서워요", 등

    // 선택 경로 JSON
    @Column(name = "choices_json", columnDefinition = "TEXT")
    @Convert(converter = ChoiceRecordListConverter.class)
    @Builder.Default
    private List<ChoiceRecord> choicesJson = new ArrayList<>();

    // 선택 기록
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChoiceRecord {
        private Integer sceneNumber;
        private String  choiceId;       // "c11" 등
        private String  choiceText;     // 실제 표시 텍스트
        private String  abilityType;
        private Integer abilityPoints;
    }

    // JSON Converter
    @jakarta.persistence.Converter
    public static class ChoiceRecordListConverter implements jakarta.persistence.AttributeConverter<List<ChoiceRecord>, String> {
        private static final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
                new com.fasterxml.jackson.databind.ObjectMapper();

        @Override
        public String convertToDatabaseColumn(List<ChoiceRecord> attribute) {
            try {
                return (attribute == null || attribute.isEmpty())
                        ? "[]"
                        : objectMapper.writeValueAsString(attribute);
            } catch (Exception e) {
                throw new IllegalArgumentException("Error converting list to JSON", e);
            }
        }

        @Override
        public List<ChoiceRecord> convertToEntityAttribute(String dbData) {
            try {
                if (dbData == null || dbData.isEmpty()) return new ArrayList<>();
                return objectMapper.readValue(dbData,
                        new com.fasterxml.jackson.core.type.TypeReference<List<ChoiceRecord>>() {});
            } catch (Exception e) {
                throw new IllegalArgumentException("Error converting JSON to list", e);
            }
        }
    }
}
