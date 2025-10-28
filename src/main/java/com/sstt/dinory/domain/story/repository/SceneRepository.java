package com.sstt.dinory.domain.story.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sstt.dinory.domain.story.entity.Scene;
import com.sstt.dinory.domain.story.entity.Story;

/**
 * [2025-10-28 김민중 추가] Scene 엔티티 Repository
 *
 * 추가 이유: 분기형 스토리 시스템에서 생성된 씬(장면)을 DB에 저장하기 위해 필요
 * - AI가 생성한 각 씬의 content를 scene 테이블에 저장
 * - story_id와 scene_number로 씬 조회 가능
 */
@Repository
public interface SceneRepository extends JpaRepository<Scene, Long> {

    /**
     * 특정 스토리의 특정 씬 번호에 해당하는 Scene 조회
     * @param story Story 엔티티
     * @param sceneNumber 씬 번호 (1~8)
     * @return Scene 엔티티
     */
    Optional<Scene> findByStoryAndSceneNumber(Story story, Integer sceneNumber);

    /**
     * 특정 스토리의 모든 씬 조회
     * @param story Story 엔티티
     * @return Scene 리스트
     */
    List<Scene> findByStoryOrderBySceneNumber(Story story);
}