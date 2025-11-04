package com.sstt.dinory.domain.chat.repository;

import com.sstt.dinory.domain.chat.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}