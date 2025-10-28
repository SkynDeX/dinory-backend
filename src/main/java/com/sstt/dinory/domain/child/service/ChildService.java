package com.sstt.dinory.domain.child.service;

import com.sstt.dinory.domain.auth.entity.Member;
import com.sstt.dinory.domain.auth.repository.MemberRepository;
import com.sstt.dinory.domain.child.dto.ChildRequestDto;
import com.sstt.dinory.domain.child.dto.ChildResponseDto;
import com.sstt.dinory.domain.child.entity.Child;
import com.sstt.dinory.domain.child.repository.ChildRepository;
import com.sstt.dinory.domain.story.entity.StoryCompletion;
import com.sstt.dinory.domain.story.repository.StoryCompletionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChildService {

    private final ChildRepository childRepository;
    private final MemberRepository memberRepository;
    private final StoryCompletionRepository storyCompletionRepository;

    // 자녀 목록 조회
    public List<ChildResponseDto> getChildrenByMemberId(Long memberId) {
        List<Child> children = childRepository.findByMemberId(memberId);

        return children.stream()
                .map(child -> {
                    // 실제 동화 개수 계산
                    Integer totalStories = getTotalStories(child.getId());

                    // 실제 마지막 활동 계산
                    String lastActivity = getLastActivity(child.getId());

                    return ChildResponseDto.from(child, totalStories, lastActivity);
                })
                .collect(Collectors.toList());
    }

    // 자녀 상세 조회
    public ChildResponseDto getChild(Long memberId, Long childId) {
        Child child = childRepository.findById(childId)
                .orElseThrow(() -> new RuntimeException("자녀를 찾을 수 없습니다."));

        // 권한 확인: 해당 자녀가 로그인한 부모의 자녀인지 확인
        if(!child.getMember().getId().equals(memberId)) {
            throw new RuntimeException("접근 권한이 없습니다.");
        }

        // 실제 동화 개수 계산
        Integer totalStories = getTotalStories(childId);

        // 실제 마지막 활동 계산
        String lastActivity = getLastActivity(childId);

        return ChildResponseDto.from(child, totalStories, lastActivity);
    }
    
    
    // 자녀 등록
    @Transactional
    public ChildResponseDto createChild(Long memberId, ChildRequestDto requestDto) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(()-> new RuntimeException("회원을 찾을 수 없습니다."));

        Child child = Child.builder()
                .member(member)
                .name(requestDto.getName())
                .birthDate(requestDto.getBirthDate())
                .gender(requestDto.getGender())
                .concerns(requestDto.getConcerns())
//                .interests(requestDto.getInterests())
                .build();

        Child savedChild = childRepository.save(child);

        return ChildResponseDto.from(savedChild, 0, "활동 없음");
    }

    
    // 자녀 정보 수정
    @Transactional
    public ChildResponseDto updateChild(Long memberId, Long childId, ChildRequestDto requestDto) {
        Child child = childRepository.findById(childId)
                .orElseThrow(()-> new RuntimeException("자녀를 찾을 수 없습니다."));

        // 권한 확인
        if (!child.getMember().getId().equals(memberId)) {
            throw new RuntimeException("접근 권한이 없습니다.");
        }

        // 정보 수정
        child.setName(requestDto.getName());
        child.setBirthDate(requestDto.getBirthDate());
        child.setGender(requestDto.getGender());
        child.setConcerns(requestDto.getConcerns());
//        child.setInterests(requestDto.getInterests());

        Child updatedChild = childRepository.save(child);

        // 실제 동화 개수 계산
        Integer totalStories = getTotalStories(childId);
        String lastActivity = getLastActivity(childId);

        return ChildResponseDto.from(updatedChild, totalStories, lastActivity);
    }

    
    // 자녀 정보 삭제
    @Transactional
    public void deleteChildId(Long memberId, Long childId) {
        Child child = childRepository.findById(childId)
                .orElseThrow(() -> new RuntimeException("자녀를 찾을 수 없습니다."));

        // 권한 확인
        if (!child.getMember().getId().equals(memberId)) {
            throw new RuntimeException("접근 권한이 없습니다.");
        }

        // 관련 데이터 삭제 처리
        childRepository.delete(child);
    }


    // 실제 완료한 동화 개수 계산
    private Integer getTotalStories(Long childId) {
        List<StoryCompletion> completions = storyCompletionRepository.findByChildId(childId);
        return completions.size();
    }

    // 실제 마지막 활동 시간 계산
    private String getLastActivity(Long childId) {
        List<StoryCompletion> completions = storyCompletionRepository.findByChildId(childId);

        if (completions.isEmpty()) {
            return "활동 없음";
        }

        // 가장 최근 완료된 동화의 시간 가져오기
        LocalDateTime lastCompletedAt = completions.stream()
                .map(StoryCompletion::getCompletedAt)
                .filter(date -> date != null)  // [2025-10-28] 광현 추가 null 필터링 추가 
                .max(LocalDateTime::compareTo)
                .orElse(null);

        if (lastCompletedAt == null) {
            return "활동 없음";
        }

        return calculateTimeAgo(lastCompletedAt);
    }

    // 시간 경과 계산
    private String calculateTimeAgo(LocalDateTime dateTime) {
        LocalDateTime now = LocalDateTime.now();

        long minutes = ChronoUnit.MINUTES.between(dateTime, now);
        if (minutes < 1) {
            return "방금";
        }

        long hours = ChronoUnit.HOURS.between(dateTime, now);
        if (hours < 1) {
            return minutes + "분 전";
        }

        if (hours < 24) {
            return hours + "시간 전";
        }

        long days = ChronoUnit.DAYS.between(dateTime, now);
        if (days < 7) {
            return days + "일 전";
        }

        long weeks = days / 7;
        if (weeks < 4) {
            return weeks + "주 전";
        }

        long months = days / 30;
        if (months < 12) {
            return months + "개월 전";
        }

        long years = days / 365;
        return years + "년 전";
    }
}
