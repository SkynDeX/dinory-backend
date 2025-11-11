package com.sstt.dinory.domain.child.service;

import com.sstt.dinory.domain.child.entity.Child;
import com.sstt.dinory.domain.child.entity.ChildDinoEntity;
import com.sstt.dinory.domain.child.repository.ChildDinoRepository;
import com.sstt.dinory.domain.child.repository.ChildRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ChildDinoService {

    private final ChildDinoRepository dinoRepository;
    private final ChildRepository childRepository;

    private void validateChildOwnership(Long memberId, Long childId) {
        Child child = childRepository.findById(childId)
                .orElseThrow(() -> new RuntimeException("자녀를 찾을 수 없습니다."));

        if (!child.getMember().getId().equals(memberId)) {
            throw new RuntimeException("접근 권한이 없습니다.");
        }
    }

    // 내 공룡 목록 불러오기
    public List<ChildDinoEntity> getMyDinos(Long memberId, Long childId) {
        validateChildOwnership(memberId, childId);
        return dinoRepository.findByChildId(childId);
    }

    // 부화 저장
    public ChildDinoEntity hatchDino(Long memberId, Long childId, String name, String colorType) {
        validateChildOwnership(memberId, childId);
        ChildDinoEntity dino = ChildDinoEntity.builder()
                .childId(childId)                        // memberId → childId
                .dinoName(name)                            // 프론트에서 전달한 이름
                .colorType(colorType)                      // 프론트에서 전달한 색상 타입
                .hatched(true)                             // 부화 여부 true
                .hatchDate(LocalDateTime.now().toString())  // 현재 시각을 부화일로 저장
                .build();

        return dinoRepository.save(dino);
    }
}
