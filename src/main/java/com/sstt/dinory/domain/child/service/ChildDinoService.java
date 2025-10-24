package com.sstt.dinory.domain.child.service;

import com.sstt.dinory.domain.child.entity.ChildDinoEntity;
import com.sstt.dinory.domain.child.repository.ChildDinoRepository;
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

    // 내 공룡 목록 불러오기
    public List<ChildDinoEntity> getMyDinos(Long memberId) {
        return dinoRepository.findByMemberId(memberId);
    }

    // 부화 저장
    public ChildDinoEntity hatchDino(Long memberId, String name, String colorType) {
        ChildDinoEntity dino = ChildDinoEntity.builder()
                .memberId(memberId)                        // 로그인된 사용자 ID
                .dinoName(name)                            // 프론트에서 전달한 이름
                .colorType(colorType)                      // 프론트에서 전달한 색상 타입
                .hatched(true)                             // 부화 여부 true
                .hatchDate(LocalDateTime.now().toString())  // 현재 시각을 부화일로 저장
                .build();

        return dinoRepository.save(dino);
    }
}
