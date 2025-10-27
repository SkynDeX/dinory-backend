package com.sstt.dinory.domain.child.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

// 자녀 등록/수정 요청 dto
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChildRequestDto {

    private String name;
    private LocalDate birthDate;
    private String gender;
    private List<String> concerns;
//    private List<String> interests;
}
