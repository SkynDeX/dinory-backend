package com.sstt.dinory.domain.child.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sstt.dinory.domain.child.entity.AbilityScore;

public interface AbilityScoreRepository extends JpaRepository<AbilityScore, Long>{
    List<AbilityScore> findByChildId(Long childId);
    Optional<AbilityScore> findByChildIdAndAbilityType(Long childId, String abilityType);
}
