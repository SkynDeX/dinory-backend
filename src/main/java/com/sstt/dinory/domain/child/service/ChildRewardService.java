package com.sstt.dinory.domain.child.service;

import com.sstt.dinory.domain.child.entity.ChildRewardEntity;
import com.sstt.dinory.domain.child.repository.ChildRewardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChildRewardService {

    private final ChildRewardRepository rewardRepository;

    // 로그인 시 리워드 데이터 없으면 생성
    public ChildRewardEntity getOrCreateReward(Long childId) {
        return rewardRepository.findByChildId(childId)
                .orElseGet(() -> rewardRepository.save(
                        ChildRewardEntity.builder()
                                .childId(childId)
                                .stars(0)
                                .eggs(0)
                                .build()
                ));
    }

    // 별 추가
    public ChildRewardEntity addStar(Long childId) {
        ChildRewardEntity reward = getOrCreateReward(childId);

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

    // 알 사용 (부화 시)
    public ChildRewardEntity useEgg(Long childId) {
        ChildRewardEntity reward = getOrCreateReward(childId);

        if (reward.getEggs() <= 0) {
            throw new RuntimeException("보유한 알이 없습니다.");
        }

        reward.useEgg();
        return rewardRepository.save(reward);
    }

    // 리워드 상태 조회하기
    public ChildRewardEntity getReward(Long childId) {
        return getOrCreateReward(childId);
    }

}
