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
@Table(name = "scene")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Scene {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "story_id", nullable = false)
    private Story story;

    @Column(name = "scene_number", nullable = false)
    private Integer sceneNumber;  // 1~8

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;  // 씬 내용 텍스트

    @Column(name = "image_url", length = 500)
    private String imageUrl;  // 생성된 이미지 URL

    @Column(name = "image_prompt", columnDefinition = "TEXT")
    private String imagePrompt;  // DALL-E 이미지 생성용 프롬프트

}
