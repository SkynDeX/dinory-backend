package com.sstt.dinory.domain.story.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sstt.dinory.domain.story.entity.Story;

@Repository
public interface StoryRepository extends JpaRepository<Story, String>{
    
    // 카테고리로 동화 찾기
    List<Story> findByCategory(String category);

    // 테마로 동화 찾기
    List<Story> findByTheme(String theme);

    // 연령대에 맞는 동화 찾기
    List<Story> findByAgeMinLessThanEqualAndAgeMaxGreaterThanEqual(Integer age, Integer sameAge);

}
