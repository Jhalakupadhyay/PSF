package com.grobird.psf.video.controller;

import com.grobird.psf.config.pservice.PServiceProperties;
import com.grobird.psf.video.dto.PitchAnalysisWebhookPayload;
import com.grobird.psf.video.service.SalesAnalysisService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Receives callbacks from pitch-analyzer (Python) when analysis completes or fails.
 * Optional: require X-P-Service-Secret header if p-service.webhook-secret is set.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
public class PitchAnalysisWebhookController {

    private final SalesAnalysisService salesAnalysisService;
    private final PServiceProperties pServiceProperties;

    public PitchAnalysisWebhookController(SalesAnalysisService salesAnalysisService,
                                          PServiceProperties pServiceProperties) {
        this.salesAnalysisService = salesAnalysisService;
        this.pServiceProperties = pServiceProperties;
    }

    @PostMapping("/pitch-analysis")
    public ResponseEntity<Void> pitchAnalysis(HttpServletRequest request,
                                              @RequestBody PitchAnalysisWebhookPayload payload) {
        String secret = pServiceProperties.getWebhookSecret();
        if (secret != null && !secret.isBlank()) {
            String provided = request.getHeader("X-P-Service-Secret");
            if (!secret.equals(provided)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        }
        if (payload == null || payload.getAnalysisId() == null) {
            return ResponseEntity.badRequest().build();
        }
        salesAnalysisService.handleWebhookCallback(
                payload.getAnalysisId(),
                payload.getStatus(),
                payload.getOverall_score(),
                payload.getComparison_score(),
                payload.getError_message()
        );
        return ResponseEntity.ok().build();
    }
}
