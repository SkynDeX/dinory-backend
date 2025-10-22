package com.sstt.dinory.domain.child.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sstt.dinory.domain.child.entity.EmotionLog;

@Repository
public interface EmotionLogRepository extends JpaRepository<EmotionLog, Long>{

    List<EmotionLog> findByChildIdOrderByRecordedAtDesc(Long childId);

} 
