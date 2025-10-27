package com.sstt.dinory.domain.story.entity;

import java.time.LocalDateTime;
import java.util.List;

import com.sstt.dinory.domain.child.entity.Child;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Convert;
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
    private Integer totalTime;  // 총 소요 시간 (초)
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    // 선택한 경로를 JSON으로 저장
    @Column(name = "choices_json", columnDefinition = "TEXT")
    @Convert(converter = ChoiceRecordListConverter.class)
    private List<ChoiceRecord> choicesJson;
    
    @PrePersist
    protected void onCreate() {
        if (completedAt == null) {
            completedAt = LocalDateTime.now();
        }
    }
    
    // 내부 클래스: 선택 기록용
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChoiceRecord {
        private Integer sceneNumber;
        private String choiceId;  // AI 서버에서 "c11", "c12" 등의 String 반환
        private String abilityType;
        private Integer abilityPoints;
    }

    // JSON Converter
    @jakarta.persistence.Converter
    public static class ChoiceRecordListConverter implements jakarta.persistence.AttributeConverter<List<ChoiceRecord>, String> {
        private static final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

        @Override
        public String convertToDatabaseColumn(List<ChoiceRecord> attribute) {
            if (attribute == null || attribute.isEmpty()) {
                return "[]";
            }
            try {
                return objectMapper.writeValueAsString(attribute);
            } catch (Exception e) {
                throw new IllegalArgumentException("Error converting list to JSON", e);
            }
        }

        @Override
        public List<ChoiceRecord> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isEmpty()) {
                return new java.util.ArrayList<>();
            }
            try {
                return objectMapper.readValue(dbData, new com.fasterxml.jackson.core.type.TypeReference<List<ChoiceRecord>>() {});
            } catch (Exception e) {
                throw new IllegalArgumentException("Error converting JSON to list", e);
            }
        }
    }
}
