package com.sstt.dinory.domain.child.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sstt.dinory.domain.child.dto.EmotionInterestRequest;
import com.sstt.dinory.domain.child.dto.EmotionLogDto;
import com.sstt.dinory.domain.child.entity.Child;
import com.sstt.dinory.domain.child.entity.EmotionLog;
import com.sstt.dinory.domain.child.repository.ChildRepository;
import com.sstt.dinory.domain.child.repository.EmotionLogRepository;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmotionService {
    
    private final ChildRepository childRepository;
    private final EmotionLogRepository emotionLogRepository;

    @Transactional
    public EmotionLogDto saveEmotion(EmotionInterestRequest request) {
        // child 조회
        Child child = childRepository.findById(request.getChildId())
            .orElseThrow(() -> 
                new RuntimeException("child 찾지 못함: " + request.getChildId()));
        
        // 관심사 업데이트
//        if(request.getInterests() != null && !request.getInterests().isEmpty()) {
//            child.setInterests(request.getInterests());
//            childRepository.save(child);
//        }

        // Sentiment 자동 분류
        String sentiment = classifySentiment(request.getEmotion());

        // EmotionLog 저장
        EmotionLog emotionLog = EmotionLog.builder()
            .child(child)
            .emotion(request.getEmotion())
            .sentiment(sentiment)
            .source(request.getSource() != null ? request.getSource() : "check_in")
            .context(request.getContext())
            .interests(request.getInterests())  // 추가
            .build();
        
        EmotionLog saved = emotionLogRepository.save(emotionLog);

        log.info("Emotion saved - childId: {}, emotion: {}, sentiment: {}", 
            request.getChildId(), request.getEmotion(), sentiment);


        // DTO 변환
        return EmotionLogDto.builder()
            .id(saved.getId())
            .childId(saved.getChild().getId())
            .emotion(saved.getEmotion())
            .sentiment(saved.getSentiment())
            .source(saved.getSource())
            .context(saved.getContext())
            .interests(saved.getInterests())    // 추가
            .recordedAt(saved.getRecordedAt())
            .build();
    }

        private String classifySentiment(String emotion) {
            return switch (emotion.toLowerCase()) {
                case "happy", "excited" -> "positive";
                case "sad", "angry", "scared", "tired" -> "negative";
                default -> "neutral";
            };
        }
    
    // 관심사 업데이트
    // @Transactional
    // public EmotionLogDto updateInterests(Long emotionLogId, List<String> interests) {
    //     EmotionLog emotionLog = emotionLogRepository.findById(emotionLogId)
    //         .orElseThrow(() -> new RuntimeException("EmotionLog 찾지 못함: " + emotionLogId));
        
    //     emotionLog.setInterests(interests);
    //     EmotionLog updated = emotionLogRepository.save(emotionLog);

    //     log.info("Interests 업데이트 - emotionLogId: {}, interests: {}", emotionLogId, interests);

    //     return EmotionLogDto.builder()
    //     .id(updated.getId())
    //     .childId(updated.getChild().getId())
    //     .emotion(updated.getEmotion())
    //     .sentiment(updated.getSentiment())
    //     .source(updated.getSource())
    //     .context(updated.getContext())
    //     .interests(updated.getInterests())
    //     .recordedAt(updated.getRecordedAt())
    //     .build();
    // }

    // // 최근 EmotionLog 조회
    // @Transactional(readOnly = true)
    // public EmotionLogDto getLatestEmotion(Long childId) {
    //     List<EmotionLog> logs = emotionLogRepository.findByChildIdOrderByRecordedAtDesc(childId);
        
    //     if (logs.isEmpty()) {
    //         throw new RuntimeException("EmotionLog가 없습니다. childId: " + childId);
    //     }
        
    //     EmotionLog latest = logs.get(0);
        
    //     return EmotionLogDto.builder()
    //         .id(latest.getId())
    //         .childId(latest.getChild().getId())
    //         .emotion(latest.getEmotion())
    //         .sentiment(latest.getSentiment())
    //         .source(latest.getSource())
    //         .context(latest.getContext())
    //         .interests(latest.getInterests())
    //         .recordedAt(latest.getRecordedAt())
    //         .build();
    // }
}
