package com.sstt.dinory.domain.story.entity;

import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "choices_json", columnDefinition = "json")
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
        private Long choiceId;
        private String abilityType;
        private Integer abilityPoints;
    }
}
