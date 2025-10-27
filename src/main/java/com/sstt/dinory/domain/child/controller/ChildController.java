package com.sstt.dinory.domain.child.controller;

import com.sstt.dinory.common.security.service.CustomUserDetails;
import com.sstt.dinory.domain.child.dto.ChildRequestDto;
import com.sstt.dinory.domain.child.dto.ChildResponseDto;
import com.sstt.dinory.domain.child.service.ChildService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/children")
@RequiredArgsConstructor
public class ChildController {

    private final ChildService childService;

    // 자녀 목록 조회
    @GetMapping
    public ResponseEntity<List<ChildResponseDto>> getChildren(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long memberId = userDetails.getMember().getId();
        List<ChildResponseDto> children = childService.getChildrenByMemberId(memberId);
        return ResponseEntity.ok(children);
    }

    // 자녀 상세 조회
    @GetMapping("/{childId}")
    public ResponseEntity<ChildResponseDto> getChild(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long childId) {
        Long memberId = userDetails.getMember().getId();
        ChildResponseDto child = childService.getChild(memberId, childId);
        return ResponseEntity.ok(child);
    }

    // 자녀 등록
    @PostMapping
    public ResponseEntity<ChildResponseDto> createChild(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody ChildRequestDto requestDto) {
        Long memberId = userDetails.getMember().getId();
        ChildResponseDto child = childService.createChild(memberId, requestDto);
        return ResponseEntity.ok(child);
    }

    // 자녀 정보 수정
    @PutMapping("/{childId}")
    public ResponseEntity<ChildResponseDto> updateChild(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long childId,
            @RequestBody ChildRequestDto requestDto) {
        Long memberId = userDetails.getMember().getId();
        ChildResponseDto child = childService.updateChild(memberId, childId, requestDto);
        return ResponseEntity.ok(child);
    }

    // 자녀 삭제
    @DeleteMapping("/{childId}")
    public ResponseEntity<Void> deleteChild(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long childId) {
        Long memberId = userDetails.getMember().getId();
        childService.deleteChildId(memberId, childId);
        return ResponseEntity.noContent().build();
    }

}
