package com.grobird.psf.opportunity.controller;

import com.grobird.psf.config.security.UserPrincipal;
import com.grobird.psf.opportunity.dto.AdminOpportunitiesWithMetricsResponse;
import com.grobird.psf.opportunity.service.AdminOpportunityService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/opportunities")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOpportunityController {

    private final AdminOpportunityService adminOpportunityService;

    public AdminOpportunityController(AdminOpportunityService adminOpportunityService) {
        this.adminOpportunityService = adminOpportunityService;
    }

    @GetMapping
    public ResponseEntity<AdminOpportunitiesWithMetricsResponse> list(
            @RequestParam(required = false) Long salesUserId,
            @AuthenticationPrincipal UserPrincipal principal) {
        AdminOpportunitiesWithMetricsResponse response = adminOpportunityService.listForAdmin(principal, salesUserId);
        return ResponseEntity.ok(response);
    }
}
