package com.sstt.dinory.domain.story.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sstt.dinory.domain.story.entity.Choice;
import com.sstt.dinory.domain.story.entity.Scene;

/**
 * [2025-10-28 김민중 추가] Choice 엔티티 Repository
 *
 * 추가 이유: 분기형 스토리 시스템에서 생성된 선택지를 DB에 저장하기 위해 필요
 * - AI가 생성한 각 씬의 선택지들을 choice 테이블에 저장
 * - scene_id로 특정 씬의 선택지 조회 가능
 */
@Repository
public interface ChoiceRepository extends JpaRepository<Choice, Long> {

    List<Choice> findByScene(Scene scene);

    // ✅ 중복 체크용 (StoryService.saveChoice 에서 호출)
    Optional<Choice> findBySceneAndChoiceText(Scene scene, String choiceText);

    // (선택) 존재 여부만 필요하면 이것도 유용
    // boolean existsBySceneAndChoiceText(Scene scene, String choiceText);
}
