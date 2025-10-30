package com.sstt.dinory.domain.story.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sstt.dinory.domain.story.entity.Story;

import jakarta.persistence.LockModeType;

@Repository
public interface StoryRepository extends JpaRepository<Story, Long>{

    // [2025-10-29 김광현] 추가
    Optional<Story> findByPineconeId(String pineconeId);

    // [2025-10-30] 락을 사용한 조회 메서드 추가
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Story s WHERE s.pineconeId = :pineconeId")
    Optional<Story> findByPineconeIdWithLock(@Param("pineconeId") String pineconeId);
    
    // 카테고리로 동화 찾기
    List<Story> findByCategory(String category);

    // 테마로 동화 찾기
    List<Story> findByTheme(String theme);

    // 연령대에 맞는 동화 찾기
    List<Story> findByAgeMinLessThanEqualAndAgeMaxGreaterThanEqual(Integer age, Integer sameAge);

}
