package com.sstt.dinory.domain.child.controller;

import com.sstt.dinory.common.security.service.CustomUserDetails;
import com.sstt.dinory.domain.child.entity.ChildDinoEntity;
import com.sstt.dinory.domain.child.service.ChildDinoService;
import com.sstt.dinory.domain.child.service.ChildRewardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/dino/child")
@RequiredArgsConstructor
public class ChildDinoController {

    private final ChildDinoService dinoService;
    private final ChildRewardService rewardService;

    // 내 공룡 목록 보기
    @GetMapping("/{childId}")
    public List<ChildDinoEntity> getMyDinos(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long childId) {
        Long memberId = userDetails.getMember().getId();
        return dinoService.getMyDinos(memberId, childId);
    }

    // 알 부화 요청 랜덤으로 공룡을 저장하기
    @PostMapping("/{childId}/hatch")
    public ChildDinoEntity hatchDino(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long childId,
            @RequestParam String name,
            @RequestParam String colorType
    ) {
        Long memberId = userDetails.getMember().getId();
        // 알 사용 갯수 차감
        rewardService.useEgg(memberId, childId);

        // 공룡 부화
        return dinoService.hatchDino(memberId, childId, name, colorType);
    }
}
