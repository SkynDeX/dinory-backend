package com.sstt.dinory.domain.child.controller;

import com.sstt.dinory.domain.child.entity.ChildDinoEntity;
import com.sstt.dinory.domain.child.service.ChildDinoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/dino")
@RequiredArgsConstructor
public class ChildDinoController {

    private final ChildDinoService dinoService;

    // 내 공룡 목록 보기
    @GetMapping("/my")
    public List<ChildDinoEntity> getMyDinos(
            @AuthenticationPrincipal(expression = "member.id") Long memberId) {
        return dinoService.getMyDinos(memberId);
    }

    // 알 부화 요청 랜덤으로 공룡을 저장하기
    @PostMapping("/hatch")
    public ChildDinoEntity hatchDino(
            @AuthenticationPrincipal(expression = "member.id") Long memberId,
            @RequestParam String name,
            @RequestParam String colorType
    ) {
        // 🔹 부화 시점 기록 및 필드 매핑
        ChildDinoEntity newDino = ChildDinoEntity.builder()
                .memberId(memberId)
                .dinoName(name)                        // 프론트의 name → dinoName 필드로 매핑
                .colorType(colorType)
                .hatched(true)                         // 부화 상태 true로 설정
                .hatchDate(LocalDateTime.now().toString()) // 부화 날짜 자동 저장
                .build();

        return dinoService.hatchDino(memberId, name, colorType);
    }
}
