package com.sstt.dinory.domain.child.entity;


import jakarta.persistence.*;
import lombok.*;

@Setter
@Getter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "child_dino")
public class ChildDinoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private  Long id;

    private Long memberId;       // 로그인된 사용자 
    private String dinoName;     // 공룡 이름
    private String colorType;    // 공룡 색상
    private boolean hatched;     // 부화 여부 확인
    private String hatchDate;    // 부화 날짜

}
