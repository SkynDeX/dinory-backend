package com.sstt.dinory.domain.child.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "child_reward")
public class ChildRewardEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long childId;

    private int stars;  // 현재 별 개수
    private int eggs;   // 현재 알 개수

    // 별 1개 추가
    public void addStar() {
        this.stars += 1;
        if (this.stars >= 5) {
            this.eggs += 1;
            this.stars = 0; // 별 초기화
        }
    }

    // 알 1개 추가
    public void addEgg() {
        this.eggs += 1;
    }

    // 알 1개 사용
    public void useEgg() {
        if (this.eggs > 0) {
            this.eggs -= 1;
        }
    }
}
