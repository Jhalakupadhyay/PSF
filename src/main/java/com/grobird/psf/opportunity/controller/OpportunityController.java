package com.grobird.psf.opportunity.controller;

import com.grobird.psf.config.security.UserPrincipal;
import com.grobird.psf.opportunity.dto.CompanySuggestionResponse;
import com.grobird.psf.opportunity.dto.OpportunityProgressResponse;
import com.grobird.psf.opportunity.dto.OpportunityRequest;
import com.grobird.psf.opportunity.dto.OpportunityResponse;
import com.grobird.psf.opportunity.service.OpportunityService;
import com.grobird.psf.opportunity.service.OpportunityProgressService;
import com.grobird.psf.qna.client.QnaInfoClient;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/opportunities")
public class OpportunityController {

    private final OpportunityService opportunityService;
    private final OpportunityProgressService opportunityProgressService;
    private final QnaInfoClient qnaInfoClient;

    public OpportunityController(OpportunityService opportunityService,
                                 OpportunityProgressService opportunityProgressService,
                                 QnaInfoClient qnaInfoClient) {
        this.opportunityService = opportunityService;
        this.opportunityProgressService = opportunityProgressService;
        this.qnaInfoClient = qnaInfoClient;
    }

    @PostMapping
    @PreAuthorize("hasRole('SALES')")
    public ResponseEntity<OpportunityResponse> create(
            @Valid @RequestBody OpportunityRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        OpportunityResponse created = opportunityService.create(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        opportunityService.delete(id, principal);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/progress")
    @PreAuthorize("hasAnyRole('SALES', 'ADMIN')")
    public ResponseEntity<List<OpportunityProgressResponse>> getProgress(@AuthenticationPrincipal UserPrincipal principal) {
        List<OpportunityProgressResponse> list = opportunityProgressService.getProgress(principal);
        return ResponseEntity.ok(list);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SALES', 'ADMIN')")
    public ResponseEntity<List<OpportunityResponse>> list(@AuthenticationPrincipal UserPrincipal principal) {
        List<OpportunityResponse> list = opportunityService.list(principal);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/company/suggest")
    @PreAuthorize("hasAnyRole('SALES', 'ADMIN')")
    public ResponseEntity<List<CompanySuggestionResponse>> suggestCompanies(@RequestParam("q") String query) {
        if (query == null || query.isBlank()) {
            return ResponseEntity.ok(List.of());
        }
        List<Map<String, String>> raw = qnaInfoClient.suggestCompanies(query.trim());
        List<CompanySuggestionResponse> suggestions = raw.stream()
                .map(m -> CompanySuggestionResponse.builder()
                        .name(m.get("name"))
                        .domain(m.get("domain"))
                        .logo(m.get("logo"))
                        .build())
                .toList();
        return ResponseEntity.ok(suggestions);
    }
}
