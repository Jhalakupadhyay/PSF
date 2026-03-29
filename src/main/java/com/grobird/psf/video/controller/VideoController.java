package com.grobird.psf.video.controller;

import com.grobird.psf.config.security.UserPrincipal;
import com.grobird.psf.video.dto.CreateSkillsetRequest;
import com.grobird.psf.video.dto.GoldenPitchDeckResponse;
import com.grobird.psf.video.dto.SaveVideoRequest;
import com.grobird.psf.video.dto.SkillsetResponse;
import com.grobird.psf.video.dto.UploadUrlResponse;
import com.grobird.psf.video.service.GoldenPitchDeckService;
import com.grobird.psf.video.service.SkillsetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/videos")
@PreAuthorize("hasRole('ADMIN')")
public class VideoController {

    private final GoldenPitchDeckService goldenPitchDeckService;
    private final SkillsetService skillsetService;

    public VideoController(GoldenPitchDeckService goldenPitchDeckService, SkillsetService skillsetService) {
        this.goldenPitchDeckService = goldenPitchDeckService;
        this.skillsetService = skillsetService;
    }

    @PostMapping("/golden-pitch-deck/upload-url")
    public ResponseEntity<UploadUrlResponse> getGoldenPitchDeckUploadUrl(@AuthenticationPrincipal UserPrincipal principal) {
        UploadUrlResponse response = goldenPitchDeckService.getUploadUrl(principal);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/golden-pitch-deck")
    public ResponseEntity<Void> saveGoldenPitchDeckVideo(
            @Valid @RequestBody SaveVideoRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        goldenPitchDeckService.saveVideo(request.getKey(), principal);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/golden-pitch-deck")
    public ResponseEntity<GoldenPitchDeckResponse> getGoldenPitchDeck(@AuthenticationPrincipal UserPrincipal principal) {
        GoldenPitchDeckResponse response = goldenPitchDeckService.get(principal);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/golden-pitch-deck")
    public ResponseEntity<Void> deleteGoldenPitchDeck(@AuthenticationPrincipal UserPrincipal principal) {
        goldenPitchDeckService.delete(principal);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/skillsets")
    public ResponseEntity<SkillsetResponse> createSkillset(
            @Valid @RequestBody CreateSkillsetRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        SkillsetResponse created = skillsetService.createSkillset(request.getName(), principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/skillsets/{id}/video")
    public ResponseEntity<Void> saveSkillsetVideo(
            @PathVariable Long id,
            @Valid @RequestBody SaveVideoRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        skillsetService.saveVideo(id, request.getKey(), principal);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/skillsets")
    public ResponseEntity<List<SkillsetResponse>> listSkillsets(@AuthenticationPrincipal UserPrincipal principal) {
        List<SkillsetResponse> list = skillsetService.listSkillsets(principal);
        return ResponseEntity.ok(list);
    }

    @DeleteMapping("/skillsets/{id}")
    public ResponseEntity<Void> deleteSkillset(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        skillsetService.delete(id, principal);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/skillsets/{id}/upload-url")
    public ResponseEntity<UploadUrlResponse> getSkillsetUploadUrl(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UploadUrlResponse response = skillsetService.getUploadUrl(id, principal);
        return ResponseEntity.ok(response);
    }
}
