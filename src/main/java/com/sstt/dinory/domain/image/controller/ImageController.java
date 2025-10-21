package com.sstt.dinory.domain.image.controller;

import com.sstt.dinory.domain.image.dto.ImageGenerationRequest;
import com.sstt.dinory.domain.image.dto.ImageGenerationResponse;
import com.sstt.dinory.domain.image.service.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/image")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ImageController {

    private final ImageService imageService;

    @PostMapping("/generate")
    public ResponseEntity<ImageGenerationResponse> generateImage(
            @RequestBody ImageGenerationRequest request) {
        ImageGenerationResponse response = imageService.generateImage(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ImageGenerationResponse> getImageById(@PathVariable Long id) {
        ImageGenerationResponse response = imageService.getImageById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/scene/{sceneId}")
    public ResponseEntity<List<ImageGenerationResponse>> getImagesBySceneId(
            @PathVariable Long sceneId) {
        List<ImageGenerationResponse> responses = imageService.getImagesBySceneId(sceneId);
        return ResponseEntity.ok(responses);
    }
}