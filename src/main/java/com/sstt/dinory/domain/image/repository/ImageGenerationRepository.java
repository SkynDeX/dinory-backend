package com.sstt.dinory.domain.image.repository;

import com.sstt.dinory.domain.image.entity.ImageGeneration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ImageGenerationRepository extends JpaRepository<ImageGeneration, Long> {

    List<ImageGeneration> findBySceneId(Long sceneId);

    List<ImageGeneration> findByStatus(String status);

    Optional<ImageGeneration> findTopBySceneIdOrderByRequestedAtDesc(Long sceneId);

    // [2025-11-03 김광현] 동일한 프롬프트로 생성된 이미지 찾기 (캐싱용)
    Optional<ImageGeneration> findFirstByPromptAndStatusOrderByCompletedAtDesc(String prompt, String status);
}