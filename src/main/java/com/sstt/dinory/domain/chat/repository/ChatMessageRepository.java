package com.sstt.dinory.domain.chat.repository;

import com.sstt.dinory.domain.chat.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByChatSessionIdOrderByCreatedAtAsc(Long sessionId);

    List<ChatMessage> findByChatSessionIdAndSenderOrderByCreatedAtAsc(Long sessionId, String sender);
}