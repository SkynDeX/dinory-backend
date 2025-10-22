package com.sstt.dinory.domain.child.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sstt.dinory.domain.child.entity.Child;

@Repository
public interface ChildRepository extends JpaRepository<Child, Long>{
    
    List<Child> findByMemberId(Long memberId);

}
