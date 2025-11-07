package com.sstt.dinory.domain.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chat_session")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "child_id")
    private Long childId;

    @Column(name = "story_completion_id")
    private Long storyCompletionId;  // 동화 완료 후 챗봇인 경우 연결

    @CreationTimestamp
    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    /**
     * [2025-11-07 추가] 대화 종료 버튼 클릭 시각 기록 (세션은 활성 유지)
     * - 사용자가 "대화 종료" 버튼을 눌렀을 때 기록
     * - ended_at과 달리 세션은 계속 활성 상태로 유지됨
     * - 다음에 디노 클릭 시 이전 대화 이어서 가능
     */
    @Column(name = "last_closed_at")
    private LocalDateTime lastClosedAt;

    @OneToMany(mappedBy = "chatSession", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ChatMessage> messages = new ArrayList<>();
}