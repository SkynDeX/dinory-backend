package com.sstt.dinory.domain.chat.repository;

import com.sstt.dinory.domain.chat.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    List<ChatSession> findByChildIdOrderByStartedAtDesc(Long childId);

    // [2025-11-07 수정] 활성 세션 조회 (한 계정당 하나의 세션 유지)
    Optional<ChatSession> findTopByChildIdAndEndedAtIsNullOrderByStartedAtDesc(Long childId);

    List<ChatSession> findByChildIdAndEndedAtIsNull(Long childId);
}