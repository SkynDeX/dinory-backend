package com.sstt.dinory.domain.child.repository;

import com.sstt.dinory.domain.child.entity.ChildDinoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChildDinoRepository extends JpaRepository<ChildDinoEntity, Long> {
    List<ChildDinoEntity> findByChildId(Long childId);    // [2025-11-07 김광현] memberId-> childId 로 변경

    // [2025-11-11 김광현] 공룡 중복체크
    boolean existsByChildIdAndColorType(Long childId, String colorType);

    // [2025-11-12 김광현] 공룡 개수 세기 추가
    long countByChildId(Long childId);

}

