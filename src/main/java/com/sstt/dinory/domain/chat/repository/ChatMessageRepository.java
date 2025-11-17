package com.sstt.dinory.domain.chat.repository;

import com.sstt.dinory.domain.chat.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByChatSessionIdOrderByCreatedAtAsc(Long sessionId);

    List<ChatMessage> findByChatSessionIdAndSenderOrderByCreatedAtAsc(Long sessionId, String sender);

    // RAG: 특정 아이의 최근 대화 메시지 조회
    @Query("SELECT cm FROM ChatMessage cm " +
           "JOIN cm.chatSession cs " +
           "WHERE cs.childId = :childId " +
           "ORDER BY cm.createdAt DESC")
    List<ChatMessage> findRecentMessagesByChildId(@Param("childId") Long childId, Pageable pageable);

    // 대시보드: 특정 아이의 기간별 대화 메시지 조회
    @Query("SELECT cm FROM ChatMessage cm " +
           "JOIN cm.chatSession cs " +
           "WHERE cs.childId = :childId " +
           "AND cm.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY cm.createdAt ASC")
    List<ChatMessage> findByChildIdAndCreatedAtBetween(
        @Param("childId") Long childId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    // 특정 패턴을 포함하는 메시지 삭제 (과거 잘못된 응답 정리용)
    @Query("SELECT cm FROM ChatMessage cm " +
           "WHERE cm.chatSession.id = :sessionId " +
           "AND cm.sender = 'AI' " +
           "AND cm.message LIKE %:pattern%")
    List<ChatMessage> findBySessionIdAndMessageContaining(
        @Param("sessionId") Long sessionId,
        @Param("pattern") String pattern
    );

    // 세션의 모든 메시지 삭제
    void deleteByChatSessionId(Long sessionId);
}