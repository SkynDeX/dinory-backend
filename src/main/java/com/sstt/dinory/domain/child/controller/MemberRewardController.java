package com.sstt.dinory.domain.child.controller;

import com.sstt.dinory.domain.child.entity.MemberRewardEntity;
import com.sstt.dinory.domain.child.service.MemberRewardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/reward")
@RequiredArgsConstructor
public class MemberRewardController {

    private final MemberRewardService rewardService;

    // 현재 리워드 상태 조회
    @GetMapping("/my")
    public Map<String, Integer> getMyReward(
            @AuthenticationPrincipal(expression = "member.id") Long memberId) {
        MemberRewardEntity reward = rewardService.getReward(memberId);
        Map<String, Integer> response = new HashMap<>();
        response.put("stars", reward.getStars());
        response.put("eggs", reward.getEggs());
        return response;
    }

    // 별 추가
    @PostMapping("/star")
    public Map<String, Integer> addStar(
            @AuthenticationPrincipal(expression = "member.id") Long memberId) {
        MemberRewardEntity updated = rewardService.addStar(memberId);
        Map<String, Integer> response = new HashMap<>();
        response.put("stars", updated.getStars());
        response.put("eggs", updated.getEggs());
        return response;
    }

    // 알 추가
    @PostMapping("/egg")
    public Map<String, Integer> addEgg(
            @AuthenticationPrincipal(expression = "member.id") Long memberId) {
        MemberRewardEntity updated = rewardService.addEgg(memberId);
        Map<String, Integer> response = new HashMap<>();
        response.put("stars", updated.getStars());
        response.put("eggs", updated.getEggs());
        return response;
    }
}
