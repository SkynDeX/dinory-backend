package com.sstt.dinory.domain.child.entity;


import jakarta.persistence.*;
import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table
public class MemberRewardEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long memberId;  // 유저

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
}
