package com.sstt.dinory.domain.image.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "image_generation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageGeneration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scene_id")
    private Long sceneId;

    @Column(columnDefinition = "TEXT")
    private String prompt;

    @Column(length = 255)
    private String style;

    @Column(length = 50)
    private String status; // pending, completed, failed

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}