package com.sstt.dinory.domain.chat.repository;

import com.sstt.dinory.domain.chat.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    List<ChatSession> findByChildIdOrderByStartedAtDesc(Long childId);

    Optional<ChatSession> findTopByChildIdAndEndedAtIsNullOrderByStartedAtDesc(Long childId);

    List<ChatSession> findByChildIdAndEndedAtIsNull(Long childId);

    // [2025-11-07 추가] DinoCharacter용 활성 세션 조회 (동화 완료 세션 제외)
    Optional<ChatSession> findTopByChildIdAndEndedAtIsNullAndStoryCompletionIdIsNullOrderByStartedAtDesc(Long childId);
}