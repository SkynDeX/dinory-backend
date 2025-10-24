package com.sstt.dinory.domain.child.repository;

import com.sstt.dinory.domain.child.entity.ChildDinoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChildDinoRepository extends JpaRepository<ChildDinoEntity, Long> {
    List<ChildDinoEntity> findByMemberId(Long memberId);
}

