package com.sstt.dinory.domain.story.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.sstt.dinory.domain.story.dto.RecommendedStoryDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoryRecommendationService {
    
    private final WebClient.Builder webClientBuilder;

    @Value("${ai.server.url:http://localhost:8000}")
    private String aiServerUrl;

    public List<RecommendedStoryDto> getRecommendations(
        String emotion,
        List<String> interests,
        Long childId,
        Integer limit) {

            log.info("AI 서버에서 동화 추천 요청 -emotion: {}, interests:{}", emotion, interests);

            try {
                // AI 서버로 보낼 요청
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("emotion", emotion);
                requestBody.put("interests", interests);
                requestBody.put("childId", childId);
                requestBody.put("limit", limit != null ? limit : 5);

                // AI 서버 응답 받기
                List<Map<String, Object>> aiResponse = webClientBuilder.build()
                    .post()
                    .uri(aiServerUrl + "/ai/recommend-stories")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .block();

                // DTO 변환
                List<RecommendedStoryDto> recommendations = new ArrayList<>();
                for (Map<String, Object> item : aiResponse) {
                    Map<String, Object> metadata = (Map<String, Object>) item.get("metadata");

                    log.info("metadata 내용: {}", metadata);
                    
                    RecommendedStoryDto dto = RecommendedStoryDto.builder()
                            .storyId((String) item.get("storyId"))
                            .title((String) item.get("title"))
                            .matchingScore((Integer) item.get("matchingScore"))
                            .coverImageUrl(metadata != null ? (String) metadata.get("coverImageUrl") : null)
                            .themes(metadata != null ? parseThemes(metadata) : List.of())
                            .estimatedTime(metadata != null ? parseEstimatedTime(metadata) : 10)
                            .description(metadata != null ? (String) metadata.get("plotSummaryText") : "")
                            .build();
                    
                    recommendations.add(dto);
                }

                log.info("AI 서버에서 {}개의 추천 동화를 받았습니다.", recommendations != null ? recommendations.size() : 0);
                return recommendations;

            } catch(Exception e) {
                log.error("AI 서버 호출 실패: {}", e.getMessage(), e);
                throw new RuntimeException("동화 추천 중 오류가 발생했습니다: " + e.getMessage());
            }
    }
    // 헬퍼 메서드: metadata에서 themes 추출
    private List<String> parseThemes(Map<String, Object> metadata) {
        String classification = (String) metadata.get("classification");
        return classification != null ? List.of(classification) : List.of();
    }

    // 헬퍼 메서드: 예상 시간 계산 (기본 10분)
    private Integer parseEstimatedTime(Map<String, Object> metadata) {
        return 10; // 기본값
    }
}

