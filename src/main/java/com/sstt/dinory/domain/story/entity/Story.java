package com.sstt.dinory.domain.story.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "story")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Story {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;    // [2025-10-29 김광현] id 타입변경

    @Column(name = "pinecone_id", unique = true, length = 50)
    private String pineconeId;  // [2025-10-29 김광현] 파이콘 아이디로 따로 관리

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 50)
    private String category;

    @Column(length = 50)
    private String theme;  // "모험", "가족", "친구" 등
    
    @Column(name = "age_min")
    private Integer ageMin;
    
    @Column(name = "age_max")
    private Integer ageMax; 
    
    @Column(name = "estimated_time")
    private Integer estimatedTime;  // 예상 소요 시간 (분)
    
    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {

        createdAt = LocalDateTime.now();
    }   

}