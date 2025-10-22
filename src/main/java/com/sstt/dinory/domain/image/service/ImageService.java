package com.sstt.dinory.domain.image.service;

import com.sstt.dinory.domain.image.dto.ImageGenerationRequest;
import com.sstt.dinory.domain.image.dto.ImageGenerationResponse;
import com.sstt.dinory.domain.image.entity.ImageGeneration;
import com.sstt.dinory.domain.image.repository.ImageGenerationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageService {

    private final ImageGenerationRepository imageGenerationRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${stability.api.key:}")
    private String stabilityApiKey;

    @Value("${stability.api.url:https://api.stability.ai/v2beta/stable-image/generate/ultra}")
    private String stabilityApiUrl;

    @Transactional
    public ImageGenerationResponse generateImage(ImageGenerationRequest request) {
        // 1. DB에 pending 상태로 저장
        ImageGeneration imageGeneration = ImageGeneration.builder()
                .sceneId(request.getSceneId())
                .prompt(request.getPrompt())
                .style(request.getStyle() != null ? request.getStyle() : "default")
                .status("pending")
                .build();

        imageGeneration = imageGenerationRepository.save(imageGeneration);

        try {
            // 2. Stability AI API 호출 시도
            String imageUrl = callStabilityAI(request.getPrompt(), request.getStyle());

            // 3. 성공 시 completed 상태로 업데이트
            imageGeneration.setStatus("completed");
            imageGeneration.setImageUrl(imageUrl);
            imageGeneration.setCompletedAt(LocalDateTime.now());

        } catch (Exception e) {
            // 402 에러는 조용히 처리
            if (e.getMessage() != null && e.getMessage().contains("402")) {
                log.info("Stability AI 크레딧 부족, Pollinations AI로 전환합니다.");
            } else {
                log.warn("Stability AI 실패, Pollinations AI로 전환합니다: {}", e.getMessage());
            }

            try {
                // 4. Pollinations AI 폴백 시도 (스타일 포함)
                String imageUrl = callPollinationsAI(request.getPrompt(), request.getStyle());

                imageGeneration.setStatus("completed");
                imageGeneration.setImageUrl(imageUrl);
                imageGeneration.setCompletedAt(LocalDateTime.now());

            } catch (Exception pollinationsError) {
                // 5. 모두 실패 시 failed 상태로 업데이트
                log.error("Both Stability AI and Pollinations AI failed", pollinationsError);
                imageGeneration.setStatus("failed");
                imageGeneration.setErrorMessage("Stability AI: " + e.getMessage() +
                    " | Pollinations AI: " + pollinationsError.getMessage());
                imageGeneration.setCompletedAt(LocalDateTime.now());
            }
        }

        imageGeneration = imageGenerationRepository.save(imageGeneration);

        // 5. Response 반환
        return convertToResponse(imageGeneration);
    }

    private String callPollinationsAI(String prompt, String style) {
        try {
            // Pollinations AI는 URL 기반으로 이미지 생성
            // 스타일을 프롬프트에 포함시킴
            String fullPrompt = prompt;

            if (style != null && !style.isEmpty() && !style.equals("default")) {
                // 스타일을 자연어로 변환
                String styleDescription = convertStyleToDescription(style);
                fullPrompt = prompt + ", " + styleDescription;
            }

            String encodedPrompt = java.net.URLEncoder.encode(fullPrompt, "UTF-8");
            String imageUrl = "https://image.pollinations.ai/prompt/" + encodedPrompt +
                "?width=1024&height=1024&nologo=true&enhance=true";

            log.info("Pollinations AI 프롬프트: {}", fullPrompt);
            return imageUrl;

        } catch (Exception e) {
            log.error("Pollinations AI URL generation failed", e);
            throw new RuntimeException("Failed to generate Pollinations AI URL: " + e.getMessage(), e);
        }
    }

    private String convertStyleToDescription(String style) {
        return switch (style.toLowerCase()) {
            case "anime" -> "anime style, manga art";
            case "photographic" -> "photorealistic, highly detailed photograph";
            case "digital-art" -> "digital art, concept art";
            case "cinematic" -> "cinematic lighting, movie scene";
            case "fantasy-art" -> "fantasy art, magical atmosphere";
            default -> "";
        };
    }

    private String callStabilityAI(String prompt, String style) {
        try {
            // HTTP 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("Authorization", "Bearer " + stabilityApiKey);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            // MultiValueMap으로 form-data 구성
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("prompt", prompt);
            body.add("output_format", "png");

            if (style != null && !style.isEmpty() && !style.equals("default")) {
                body.add("style_preset", style);
            }

            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

            // API 호출
            ResponseEntity<Map> response = restTemplate.exchange(
                    stabilityApiUrl,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();

                // Stability AI 응답에서 이미지 URL 또는 base64 데이터 추출
                if (responseBody.containsKey("image")) {
                    return (String) responseBody.get("image");
                } else if (responseBody.containsKey("artifacts")) {
                    List<Map<String, Object>> artifacts = (List<Map<String, Object>>) responseBody.get("artifacts");
                    if (!artifacts.isEmpty()) {
                        return "data:image/png;base64," + artifacts.get(0).get("base64");
                    }
                }
            }

            throw new RuntimeException("Failed to generate image: Invalid response from Stability AI");

        } catch (Exception e) {
            // 402 Payment Required는 예상된 에러이므로 warn 레벨로 처리
            if (e.getMessage() != null && e.getMessage().contains("402")) {
                log.warn("Stability AI API: Insufficient credits (expected, will use fallback)");
            } else {
                log.error("Stability AI API call failed", e);
            }
            throw new RuntimeException("Failed to call Stability AI API: " + e.getMessage(), e);
        }
    }

    public ImageGenerationResponse getImageById(Long id) {
        ImageGeneration imageGeneration = imageGenerationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Image generation not found with id: " + id));
        return convertToResponse(imageGeneration);
    }

    public List<ImageGenerationResponse> getImagesBySceneId(Long sceneId) {
        return imageGenerationRepository.findBySceneId(sceneId).stream()
                .map(this::convertToResponse)
                .toList();
    }

    private ImageGenerationResponse convertToResponse(ImageGeneration imageGeneration) {
        return ImageGenerationResponse.builder()
                .id(imageGeneration.getId())
                .sceneId(imageGeneration.getSceneId())
                .prompt(imageGeneration.getPrompt())
                .style(imageGeneration.getStyle())
                .status(imageGeneration.getStatus())
                .imageUrl(imageGeneration.getImageUrl())
                .errorMessage(imageGeneration.getErrorMessage())
                .requestedAt(imageGeneration.getRequestedAt())
                .completedAt(imageGeneration.getCompletedAt())
                .build();
    }
}