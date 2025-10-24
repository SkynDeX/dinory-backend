package com.sstt.dinory.domain.child.repository;

import com.sstt.dinory.domain.child.entity.MemberRewardEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRewardRepository extends JpaRepository<MemberRewardEntity, Long> {
    Optional<MemberRewardEntity> findByMemberId(Long memberId);
}
