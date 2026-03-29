package com.grobird.psf.organization.controller;

import com.grobird.psf.config.security.UserPrincipal;
import com.grobird.psf.organization.dto.*;
import com.grobird.psf.organization.service.OrganizationService;
import com.grobird.psf.user.dto.UserDTO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Super Admin-only APIs: organizations (tenants), admins per org, sales per admin, dashboard.
 */
@RestController
@RequestMapping("/api/v1/super-admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminController {

    private final OrganizationService organizationService;

    public SuperAdminController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    /**
     * Create a new organization (tenant). Body: companyName, industry.
     */
    @PostMapping("/organizations")
    public ResponseEntity<OrganizationResponse> createOrganization(
            @Valid @RequestBody CreateOrganizationRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        OrganizationResponse created = organizationService.create(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * List all organizations with total admins and total opportunities. Cached 5 minutes.
     */
    @GetMapping("/organizations")
    public ResponseEntity<List<OrganizationSummaryResponse>> listOrganizations(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<OrganizationSummaryResponse> list = organizationService.listOrganizationsWithCounts(principal);
        return ResponseEntity.ok(list);
    }

    /**
     * List all admins for an organization.
     */
    @GetMapping("/organizations/{organizationId}/admins")
    public ResponseEntity<List<UserDTO>> listAdmins(
            @PathVariable Long organizationId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<UserDTO> admins = organizationService.listAdminsByOrganizationId(organizationId, principal);
        return ResponseEntity.ok(admins);
    }

    /**
     * List all sales under a specific admin in an organization.
     */
    @GetMapping("/organizations/{organizationId}/admins/{adminId}/sales")
    public ResponseEntity<List<UserDTO>> listSalesByAdmin(
            @PathVariable Long organizationId,
            @PathVariable Long adminId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<UserDTO> sales = organizationService.listSalesByAdminId(organizationId, adminId, principal);
        return ResponseEntity.ok(sales);
    }

    /**
     * Dashboard: total organizations only.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<SuperAdminDashboardResponse> getDashboard(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        SuperAdminDashboardResponse dashboard = organizationService.getDashboard(principal);
        return ResponseEntity.ok(dashboard);
    }

    /**
     * Organizations created stats: date vs count and total created.
     * Query param type: daily (last 30 days), weekly (last 12 weeks), monthly (last 12 months).
     */
    @GetMapping("/organizations/created-stats")
    public ResponseEntity<OrganizationCreatedStatsResponse> getOrganizationsCreatedStats(
            @RequestParam(defaultValue = "daily") String type,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        OrganizationCreatedStatsResponse stats = organizationService.getCreatedStats(type, principal);
        return ResponseEntity.ok(stats);
    }
}
