package com.grobird.psf.qna.controller;

import com.grobird.psf.config.security.UserPrincipal;
import com.grobird.psf.qna.client.QnaInfoClientException;
import com.grobird.psf.qna.dto.QnaTargetRequest;
import com.grobird.psf.qna.service.SalesQnaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Mediator API for qna_info: questions and company info mapped to an opportunity.
 * 3-layer lookup: Redis (48h) -> DB -> qna_info API.
 */
@RestController
@RequestMapping("/api/v1/sales/qna")
@PreAuthorize("hasAnyRole('SALES', 'ADMIN')")
public class SalesQnaController {

    private final SalesQnaService salesQnaService;

    public SalesQnaController(SalesQnaService salesQnaService) {
        this.salesQnaService = salesQnaService;
    }

    /**
     * Get company info for an opportunity. Returns cached/saved data if available.
     * Body: { "opportunityId": 1 } or { "opportunityId": 1, "targetCompany": "Microsoft" }
     */
    @PostMapping("/info")
    public ResponseEntity<Map<String, Object>> getCompanyInfo(
            @Valid @RequestBody QnaTargetRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Map<String, Object> body = salesQnaService.getCompanyInfo(
                request.getOpportunityId(), request.getTargetCompany(), principal);
        return ResponseEntity.ok(body);
    }

    /**
     * Get questions for an opportunity (paginated, 10 per page).
     * Body: { "opportunityId": 1, "offset": 0 }
     * Returns: questions[], offset, pageSize, totalQuestions, hasMore
     */
    @PostMapping("/questions")
    public ResponseEntity<Map<String, Object>> getQuestions(
            @Valid @RequestBody QnaTargetRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        int offset = request.getOffset() != null ? request.getOffset() : 0;
        Map<String, Object> body = salesQnaService.getQuestions(
                request.getOpportunityId(), request.getTargetCompany(), offset, principal);
        return ResponseEntity.ok(body);
    }

    /**
     * Generate more questions (paginated). Same as GET — returns next page;
     * if offset exceeds stored questions, calls qna_info API for a fresh batch.
     * Body: { "opportunityId": 1, "offset": 10 }
     */
    @PostMapping("/questions/generate")
    public ResponseEntity<Map<String, Object>> generateQuestions(
            @Valid @RequestBody QnaTargetRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        int offset = request.getOffset() != null ? request.getOffset() : 0;
        Map<String, Object> body = salesQnaService.generateQuestions(
                request.getOpportunityId(), request.getTargetCompany(), offset, principal);
        return ResponseEntity.ok(body);
    }

    @ExceptionHandler(QnaInfoClientException.class)
    public ResponseEntity<Map<String, String>> handleQnaInfoError(QnaInfoClientException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("message", "Q&A info service is unavailable. Make sure the qna_info backend is running."));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusError(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("message", ex.getReason() != null ? ex.getReason() : ex.getMessage()));
    }
}
