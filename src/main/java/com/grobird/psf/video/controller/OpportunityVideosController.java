package com.grobird.psf.video.controller;

import com.grobird.psf.config.security.UserPrincipal;
import com.grobird.psf.config.tenant.TenantContext;
import com.grobird.psf.video.dto.SalesSubmissionStatusResponse;
import com.grobird.psf.video.dto.SaveVideoRequest;
import com.grobird.psf.video.dto.UploadUrlResponse;
import com.grobird.psf.video.dto.ReferenceVideoSummaryResponse;
import com.grobird.psf.video.entity.ReferenceVideoEntity;
import com.grobird.psf.video.entity.SalesSubmissionEntity;
import com.grobird.psf.video.service.S3PresignedService;
import com.grobird.psf.video.service.SalesSubmissionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Sales P section: upload golden pitch or skillset video per opportunity, poll status/result.
 */
@RestController
@RequestMapping("/api/v1/opportunities/{opportunityId}/videos")
@PreAuthorize("hasAnyRole('SALES', 'ADMIN')")
public class OpportunityVideosController {

    private final SalesSubmissionService salesSubmissionService;
    private final S3PresignedService s3PresignedService;

    public OpportunityVideosController(SalesSubmissionService salesSubmissionService,
                                        S3PresignedService s3PresignedService) {
        this.salesSubmissionService = salesSubmissionService;
        this.s3PresignedService = s3PresignedService;
    }

    // ─── Golden pitch deck (sales) ───────────────────────────────────────────

    @GetMapping("/golden-pitch-deck/upload-url")
    public ResponseEntity<UploadUrlResponse> getGoldenPitchUploadUrl(
            @PathVariable Long opportunityId,
            @AuthenticationPrincipal UserPrincipal principal) {
        salesSubmissionService.requireOpportunityAccessForController(opportunityId, principal);
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        S3PresignedService.UploadResult result = s3PresignedService.generateUploadUrlSalesGoldenPitch(tenantId, opportunityId);
        return ResponseEntity.ok(UploadUrlResponse.builder()
                .uploadUrl(result.uploadUrl())
                .key(result.key())
                .build());
    }

    @PutMapping("/golden-pitch-deck")
    public ResponseEntity<SalesSubmissionStatusResponse> saveGoldenPitchVideo(
            @PathVariable Long opportunityId,
            @Valid @RequestBody SaveVideoRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        SalesSubmissionEntity submission = salesSubmissionService.createSubmissionForGoldenPitch(
                opportunityId, request.getKey(), principal);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(toStatusResponse(submission, null));
    }

    @GetMapping("/golden-pitch-deck")
    public ResponseEntity<SalesSubmissionStatusResponse> getGoldenPitch(
            @PathVariable Long opportunityId,
            @AuthenticationPrincipal UserPrincipal principal) {
        Optional<SalesSubmissionEntity> latest = salesSubmissionService.findLatestGoldenPitchSubmission(opportunityId, principal);
        if (latest.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String playbackUrl = latest.get().getVideoS3Key() != null
                ? s3PresignedService.generatePlaybackUrl(latest.get().getVideoS3Key())
                : null;
        return ResponseEntity.ok(toStatusResponse(latest.get(), playbackUrl));
    }

    @GetMapping("/golden-pitch-deck/status")
    public ResponseEntity<SalesSubmissionStatusResponse> getGoldenPitchStatus(
            @PathVariable Long opportunityId,
            @AuthenticationPrincipal UserPrincipal principal) {
        Optional<SalesSubmissionEntity> latest = salesSubmissionService.findLatestGoldenPitchSubmission(opportunityId, principal);
        if (latest.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String playbackUrl = s3PresignedService.generatePlaybackUrl(latest.get().getVideoS3Key());
        return ResponseEntity.ok(toStatusResponse(latest.get(), playbackUrl));
    }

    // ─── Skillsets (sales) ───────────────────────────────────────────────────

    @GetMapping("/skillsets/{referenceVideoId}/upload-url")
    public ResponseEntity<UploadUrlResponse> getSkillsetUploadUrl(
            @PathVariable Long opportunityId,
            @PathVariable Long referenceVideoId,
            @AuthenticationPrincipal UserPrincipal principal) {
        salesSubmissionService.requireOpportunityAccessForController(opportunityId, principal);
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        S3PresignedService.UploadResult result = s3PresignedService.generateUploadUrlSalesSkillset(tenantId, opportunityId, referenceVideoId);
        return ResponseEntity.ok(UploadUrlResponse.builder()
                .uploadUrl(result.uploadUrl())
                .key(result.key())
                .build());
    }

    @PutMapping("/skillsets/{referenceVideoId}/video")
    public ResponseEntity<SalesSubmissionStatusResponse> saveSkillsetVideo(
            @PathVariable Long opportunityId,
            @PathVariable Long referenceVideoId,
            @Valid @RequestBody SaveVideoRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        SalesSubmissionEntity submission = salesSubmissionService.createSubmissionForSkillset(
                opportunityId, referenceVideoId, request.getKey(), principal);
        String playbackUrl = s3PresignedService.generatePlaybackUrl(submission.getVideoS3Key());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(toStatusResponse(submission, playbackUrl));
    }

    @GetMapping("/skillsets")
    public ResponseEntity<List<ReferenceVideoSummaryResponse>> listSkillsetReferences(
            @PathVariable Long opportunityId,
            @AuthenticationPrincipal UserPrincipal principal) {
        salesSubmissionService.requireOpportunityAccessForController(opportunityId, principal);
        List<ReferenceVideoEntity> refs = salesSubmissionService.listSkillsetReferences(principal);
        List<ReferenceVideoSummaryResponse> responses = refs.stream()
                .map(r -> ReferenceVideoSummaryResponse.builder()
                        .id(r.getId())
                        .name(r.getName())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/skillsets/{referenceVideoId}/status")
    public ResponseEntity<SalesSubmissionStatusResponse> getSkillsetSubmissionStatus(
            @PathVariable Long opportunityId,
            @PathVariable Long referenceVideoId,
            @AuthenticationPrincipal UserPrincipal principal) {
        Optional<SalesSubmissionEntity> submission = salesSubmissionService.findLatestSubmissionForSkillset(
                opportunityId, referenceVideoId, principal);
        if (submission.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String playbackUrl = s3PresignedService.generatePlaybackUrl(submission.get().getVideoS3Key());
        return ResponseEntity.ok(toStatusResponse(submission.get(), playbackUrl));
    }

    private SalesSubmissionStatusResponse toStatusResponse(SalesSubmissionEntity s, String playbackUrl) {
        return SalesSubmissionStatusResponse.builder()
                .id(s.getId())
                .status(s.getStatus() != null ? s.getStatus().name() : null)
                .overallScore(s.getOverallScore())
                .comparisonScore(s.getComparisonScore())
                .fullResult(s.getFullResult())
                .errorMessage(s.getErrorMessage())
                .videoPlaybackUrl(playbackUrl)
                .build();
    }
}
