package com.sstt.dinory.domain.child.service;

import com.sstt.dinory.domain.child.entity.MemberRewardEntity;
import com.sstt.dinory.domain.child.repository.MemberRewardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberRewardService {

    private final MemberRewardRepository rewardRepository;

    // 로그인 시 리워드 데이터 없으면 생성
    public MemberRewardEntity getOrCreateReward(Long memberId) {
        return rewardRepository.findByMemberId(memberId)
                .orElseGet(() -> rewardRepository.save(
                        MemberRewardEntity.builder()
                                .memberId(memberId)
                                .stars(0)
                                .eggs(0)
                                .build()
                ));
    }

    // 별 추가
    public MemberRewardEntity addStar(Long memberId) {
        MemberRewardEntity reward = getOrCreateReward(memberId);

        int stars = reward.getStars() + 1;
        int eggs = reward.getEggs();

        if (stars >= 5) {
            stars = 0;
            eggs += 1; // 자동 알 추가
        }

        reward.setStars(stars);
        reward.setEggs(eggs);
        return rewardRepository.save(reward);
    }


    // 알 추가
    public MemberRewardEntity addEgg(Long memberId) {
        MemberRewardEntity reward = getOrCreateReward(memberId);
        reward.setEggs(reward.getEggs() + 1);
        return rewardRepository.save(reward);
    }

    // 리워드 상태 조회하기
    public MemberRewardEntity getReward(Long memberId) {
        return getOrCreateReward(memberId);
    }
}
