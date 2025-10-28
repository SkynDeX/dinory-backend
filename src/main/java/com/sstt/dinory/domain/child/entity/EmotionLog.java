package com.sstt.dinory.domain.child.entity;

import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
@Table(name = "emotion_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmotionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_id", nullable = false)
    private Child child;
    
    @Column(nullable = false, length = 20)
    private String emotion;  
    
    @Column(length = 20)
    private String sentiment;  
    
    @Column(length = 20)
    private String source; 
    
    @Column(columnDefinition = "TEXT")
    private String context;  // 상황 설명

    // 선택한 관심사 (JSON 배열로 저장)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "interests", columnDefinition = "json")
    private List<String> interests;
    
    @Column(name = "recorded_at")
    private LocalDateTime recordedAt;
    
    @PrePersist
    protected void onCreate() {
        if (recordedAt == null) {
            recordedAt = LocalDateTime.now();
        }
    }
}
