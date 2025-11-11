package com.sstt.dinory.domain.child.service;

import com.sstt.dinory.domain.child.entity.Child;
import com.sstt.dinory.domain.child.entity.ChildRewardEntity;
import com.sstt.dinory.domain.child.repository.ChildRepository;
import com.sstt.dinory.domain.child.repository.ChildRewardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChildRewardService {

    private final ChildRewardRepository rewardRepository;
    private final ChildRepository childRepository;

    // [2025-11-11 김광현] 권한 검증 메서드 추가
    private void validateChildOwnership(Long memberId, Long childId) {
        Child child = childRepository.findById(childId)
                .orElseThrow(() -> new RuntimeException("자ㄹ녀를 찾을 수 없습니다."));

        if(!child.getMember().getId().equals(memberId)) {
            throw new RuntimeException("접근 권한이 없습니다.");
        }
    }

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
    public ChildRewardEntity useEgg(Long memberId,Long childId) {
        validateChildOwnership(memberId, childId);

        ChildRewardEntity reward = getOrCreateReward(childId);

        if (reward.getEggs() <= 0) {
            throw new RuntimeException("보유한 알이 없습니다.");
        }

        reward.useEgg();
        return rewardRepository.save(reward);
    }

    // 리워드 상태 조회하기
    public ChildRewardEntity getReward(Long memberId, Long childId) {
        validateChildOwnership(memberId, childId);
        return getOrCreateReward(childId);
    }

}
