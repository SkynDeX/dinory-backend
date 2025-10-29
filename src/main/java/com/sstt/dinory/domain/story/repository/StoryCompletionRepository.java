package com.sstt.dinory.domain.story.repository;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sstt.dinory.domain.story.entity.StoryCompletion;

@Repository
public interface StoryCompletionRepository extends JpaRepository<StoryCompletion, Long>{
    
     // 특정 아이의 모든 동화 완료 기록
    List<StoryCompletion> findByChildId(Long childId);
    
    // 특정 아이의 특정 동화 완료 기록
    Optional<StoryCompletion> findByChildIdAndStoryId(Long childId, Long storyId);
    
    // 특정 동화의 모든 완료 기록 (통계용)
    List<StoryCompletion> findByStoryId(Long storyId);

    Page<StoryCompletion> findByChildIdAndCompletedAtBetweenOrderByCompletedAtDesc(
            Long childId,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            Pageable pageable
    );

    List<StoryCompletion> findByChildIdAndCompletedAtBetween(
            Long childId,
            LocalDateTime startDate,
            LocalDateTime endDate
    );
}
