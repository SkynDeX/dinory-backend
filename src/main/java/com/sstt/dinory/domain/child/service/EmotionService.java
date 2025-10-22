package com.sstt.dinory.domain.child.service;

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
        if(request.getInterests() != null && !request.getInterests().isEmpty()) {
            child.setInterests(request.getInterests());
            childRepository.save(child);
        }

        // Sentiment 자동 분류
        String sentiment = classifySentiment(request.getEmotion());

        // EmotionLog 저장
        EmotionLog emotionLog = EmotionLog.builder()
            .child(child)
            .emotion(request.getEmotion())
            .sentiment(sentiment)
            .source(request.getSource() != null ? request.getSource() : "check_in")
            .context(request.getContext())
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
    
}
