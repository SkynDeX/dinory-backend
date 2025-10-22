// src/main/java/com/sstt/dinory/domain/tts/controller/TtsController.java
package com.sstt.dinory.domain.tts.controller;

import com.sstt.dinory.domain.tts.dto.TtsRequest;
import com.sstt.dinory.domain.tts.service.TtsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/tts")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class TtsController {

    private final TtsService ttsService;

    /** Google Cloud TTS - MP3 반환 */
    @PostMapping("/googlecloud")
    public ResponseEntity<byte[]> googleCloudTts(@RequestBody TtsRequest request) {
        try {
            String text = request.getText();
            if (text == null || text.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            byte[] audioData = ttsService.generateGoogleCloudTts(
                    text,
                    request.getVoiceName(),
                    request.getSpeakingRate() != null ? request.getSpeakingRate() : 1.0,
                    request.getPitch() != null ? request.getPitch() : 0.0
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("audio/mpeg"));
            headers.setCacheControl("no-store");

            return ResponseEntity.ok().headers(headers).body(audioData);

        } catch (IOException e) {
            log.error("Google Cloud TTS error", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /** Gemini TTS - WAV 반환 */
    @PostMapping("/gemini")
    public ResponseEntity<byte[]> geminiTts(@RequestBody TtsRequest request) {
        log.info("==================== TTS 컨트롤러 진입 ====================");
        try {
            String text = request.getText();
            if (text == null || text.trim().isEmpty()) {
                log.warn("Gemini TTS: Empty text received");
                return ResponseEntity.badRequest().build();
            }

            byte[] audioData = ttsService.generateGeminiTts(text, request.getVoiceName());
            if (audioData == null || audioData.length == 0) {
                log.error("Gemini TTS returned empty audio data");
                return ResponseEntity.internalServerError().build();
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("audio/wav"));
            headers.setCacheControl("no-store");
            headers.setContentLength(audioData.length);

            log.info("==================== TTS 성공 ====================");
            return ResponseEntity.ok().headers(headers).body(audioData);

        } catch (IllegalStateException e) {
            log.error("==================== TTS 설정 오류 ====================", e);
            return ResponseEntity.status(503).build();
        } catch (IOException e) {
            log.error("==================== TTS IO 오류 ====================", e);
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            log.error("==================== TTS 예상치 못한 오류 ====================", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
