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

    /**
     * 특정 씬의 모든 선택지 조회
     * @param scene Scene 엔티티
     * @return Choice 리스트
     */
    List<Choice> findByScene(Scene scene);

    /**
     * [2025-10-28 김민중 추가] 중복 선택지 체크
     * 동일한 씬에 동일한 choiceText가 저장되어 있는지 확인
     * @param scene Scene 엔티티
     * @param choiceText 선택지 텍스트
     * @return Choice 엔티티 (있으면 반환, 없으면 empty)
     */
    Optional<Choice> findBySceneAndChoiceText(Scene scene, String choiceText);
}
