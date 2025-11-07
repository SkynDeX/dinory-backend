package com.sstt.dinory.domain.child.repository;

import com.sstt.dinory.domain.child.entity.ChildRewardEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChildRewardRepository extends JpaRepository<ChildRewardEntity, Long> {
    Optional<ChildRewardEntity> findByChildId(Long childId);
}