package com.sstt.dinory.domain.story.entity;

import jakarta.persistence.Column;
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
@Table(name = "choice")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Choice {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scene_id", nullable = false)
    private Scene scene;
    
    @Column(name = "choice_text", columnDefinition = "TEXT", nullable = false)
    private String choiceText;  // 선택지 텍스트
    
    @Column(name = "ability_type", length = 50, nullable = false)
    private String abilityType;  // "친절", "용기", "공감", "우정", "자존감"
    
    @Column(name = "ability_points", nullable = false)
    private Integer abilityPoints;  // 획득 점수
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "next_scene_id")
    private Scene nextScene;  // 다음 씬 (분기용, nullable)
    
}