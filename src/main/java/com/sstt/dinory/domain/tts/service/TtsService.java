// src/main/java/com/sstt/dinory/domain/tts/service/TtsService.java
package com.sstt.dinory.domain.tts.service;

import com.google.cloud.texttospeech.v1.*;
import com.google.protobuf.ByteString;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.api.gax.core.FixedCredentialsProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

@Service
@Slf4j
public class TtsService {

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${gcp.tts.key-path:}")
    private String gcpTtsKeyPath;

    private final WebClient webClient;

    // ▲ WebClient 메모리 한도 상향(기본 256KB → 16MB)
    public TtsService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                        .build())
                .build();
    }

    /** Google Cloud TTS - MP3 생성 */
    public byte[] generateGoogleCloudTts(String text, String voiceName, Double speakingRate, Double pitch) throws IOException {
        // GCP 인증 키 파일 로드
        GoogleCredentials credentials;
        if (gcpTtsKeyPath != null && !gcpTtsKeyPath.isEmpty()) {
            try (FileInputStream keyFileStream = new FileInputStream(gcpTtsKeyPath)) {
                credentials = GoogleCredentials.fromStream(keyFileStream);
            }
        } else {
            // 키 경로가 없으면 기본 credentials 사용 (GOOGLE_APPLICATION_CREDENTIALS 환경변수)
            credentials = GoogleCredentials.getApplicationDefault();
        }

        // TextToSpeechClient 생성 시 credentials 명시
        TextToSpeechSettings settings = TextToSpeechSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                .build();

        try (TextToSpeechClient textToSpeechClient = TextToSpeechClient.create(settings)) {
            SynthesisInput input = SynthesisInput.newBuilder().setText(text).build();

            VoiceSelectionParams voice = VoiceSelectionParams.newBuilder()
                    .setLanguageCode("ko-KR")
                    .setName(voiceName != null ? voiceName : "ko-KR-Neural2-B")
                    .build();

            AudioConfig.Builder audioConfigBuilder = AudioConfig.newBuilder().setAudioEncoding(AudioEncoding.MP3);
            if (speakingRate != null) audioConfigBuilder.setSpeakingRate(speakingRate);
            if (pitch != null) audioConfigBuilder.setPitch(pitch);

            SynthesizeSpeechResponse response = textToSpeechClient.synthesizeSpeech(input, voice, audioConfigBuilder.build());
            ByteString audioContents = response.getAudioContent();
            return audioContents.toByteArray();
        }
    }

    /** Gemini TTS - WAV 생성 */
    public byte[] generateGeminiTts(String text, String voiceName) throws IOException {
        log.info("=== Gemini TTS 시작 ===");

        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY is not configured");
        }

        final String model = "gemini-2.5-flash-preview-tts"; // TTS 지원 모델
        final String urlPath = "/v1beta/models/" + model + ":generateContent";

        // 요청 바디
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("contents", List.of(Map.of(
                "parts", List.of(Map.of("text", text))
        )));
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("responseModalities", List.of("AUDIO"));

        Map<String, Object> voiceConfig = Map.of(
                "prebuiltVoiceConfig", Map.of(
                        "voiceName", (voiceName != null && !voiceName.isBlank()) ? voiceName : "Kore"
                )
        );
        Map<String, Object> speechConfig = Map.of("voiceConfig", voiceConfig);
        generationConfig.put("speechConfig", speechConfig);
        body.put("generationConfig", generationConfig);

        String responseJson;
        try {
            responseJson = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("generativelanguage.googleapis.com")
                            .path(urlPath)
                            .queryParam("key", geminiApiKey)
                            .build())
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception e) {
            throw new IOException("Failed to call Gemini API: " + e.getMessage(), e);
        }

        String base64 = extractInlinePcm(responseJson);
        if (base64 == null) {
            log.error("오디오 데이터 추출 실패: {}", responseJson);
            throw new IOException("No audio data in Gemini response");
        }

        byte[] pcm = Base64.getDecoder().decode(base64);

        // Gemini TTS: s16le, 24kHz, mono → WAV 래핑
        byte[] wav = convertPcmToWav(pcm, 24000, 1, 16);
        log.info("=== Gemini TTS 성공. wav={} bytes ===", wav.length);
        return wav;
    }

    /** candidates[0].content.parts[*].inlineData.data 에서 base64 PCM 추출 */
    private String extractInlinePcm(String json) {
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(json, JsonObject.class);
            JsonArray candidates = root.getAsJsonArray("candidates");
            if (candidates == null || candidates.size() == 0) return null;

            JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
            if (content == null) return null;

            JsonArray parts = content.getAsJsonArray("parts");
            if (parts == null) return null;

            for (int i = 0; i < parts.size(); i++) {
                JsonObject p = parts.get(i).getAsJsonObject();
                if (p.has("inlineData")) {
                    JsonObject inline = p.getAsJsonObject("inlineData");
                    if (inline.has("data")) return inline.get("data").getAsString();
                }
            }
            return null;
        } catch (Exception e) {
            log.error("inlineData.data 파싱 실패", e);
            return null;
        }
    }

    /** PCM → WAV */
    private byte[] convertPcmToWav(byte[] pcmData, int sampleRate, int channels, int bitsPerSample) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int dataSize = pcmData.length;
        int chunkSize = 36 + dataSize;

        out.write("RIFF".getBytes());
        out.write(intLE(chunkSize));
        out.write("WAVE".getBytes());

        out.write("fmt ".getBytes());
        out.write(intLE(16));
        out.write(shortLE((short) 1)); // PCM
        out.write(shortLE((short) channels));
        out.write(intLE(sampleRate));
        out.write(intLE(byteRate));
        out.write(shortLE((short) (channels * bitsPerSample / 8)));
        out.write(shortLE((short) bitsPerSample));

        out.write("data".getBytes());
        out.write(intLE(dataSize));
        out.write(pcmData);
        return out.toByteArray();
    }

    private byte[] intLE(int v) { return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array(); }
    private byte[] shortLE(short v) { return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v).array(); }
}
