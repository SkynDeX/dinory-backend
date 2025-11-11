package com.sstt.dinory.domain.child.controller;

import com.sstt.dinory.common.security.service.CustomUserDetails;
import com.sstt.dinory.domain.child.entity.ChildRewardEntity;
import com.sstt.dinory.domain.child.service.ChildRewardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/child/reward")
@RequiredArgsConstructor
public class ChildRewardController {

    private final ChildRewardService rewardService;

    // 현재 리워드 상태 조회 (childId로)
    @GetMapping("/{childId}")
    public Map<String, Integer> getReward(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long childId) {
        Long memberId = userDetails.getMember().getId();
        ChildRewardEntity reward = rewardService.getReward(memberId, childId);
        Map<String, Integer> response = new HashMap<>();
        response.put("stars", reward.getStars());
        response.put("eggs", reward.getEggs());
        return response;
    }

    // 별 추가 (childId로)
    @PostMapping("/{childId}/star")
    public Map<String, Integer> addStar(@PathVariable Long childId) {
        ChildRewardEntity updated = rewardService.addStar(childId);
        Map<String, Integer> response = new HashMap<>();
        response.put("stars", updated.getStars());
        response.put("eggs", updated.getEggs());
        return response;
    }
}
