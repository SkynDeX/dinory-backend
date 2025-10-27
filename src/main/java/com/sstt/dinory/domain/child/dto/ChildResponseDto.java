package com.sstt.dinory.domain.child.dto;

import com.sstt.dinory.domain.child.entity.Child;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// 자녀 조회 응답 DTO
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChildResponseDto {

    private Long id;
    private String name;
    private LocalDate birthDate;
    private String gender;
    private List<String> concerns;
//    private List<String> interests;
    private String avatar;
    private Integer totalStories;
    private String lastActivity;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Entity => Dto 변환
    public static ChildResponseDto from(Child child, Integer totalStories, String lastActivity) {
        return ChildResponseDto.builder()
                .id(child.getId())
                .name(child.getName())
                .birthDate(child.getBirthDate())
                .gender(child.getGender())
                .concerns(child.getConcerns())
//                .interests(child.getInterests())
                .avatar(child.getGender().equals("male") ? "\uD83D\uDC66" : "\uD83D\uDC67")
                .totalStories(totalStories)
                .lastActivity(lastActivity)
                .createdAt(child.getCreatedAt())
                .updatedAt(child.getUpdatedAt())
                .build();
    }
}
