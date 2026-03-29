package com.grobird.psf.user.controller;

import com.grobird.psf.config.security.UserPrincipal;
import com.grobird.psf.user.dto.AddSalesRequest;
import com.grobird.psf.user.dto.SalesUserDetailResponse;
import com.grobird.psf.user.dto.UserDTO;
import com.grobird.psf.user.service.UserService;
import com.grobird.psf.video.dto.AdminDashboardMetricsResponse;
import com.grobird.psf.video.dto.AdminPitchUploadStatsResponse;
import com.grobird.psf.video.dto.AdminSalesComparisonResponse;
import com.grobird.psf.video.dto.DashboardMetricsResponse;
import com.grobird.psf.video.dto.SalesLeaderboardResponse;
import com.grobird.psf.video.dto.SalesSubmissionStatsResponse;
import com.grobird.psf.video.service.AdminDashboardMetricsService;
import com.grobird.psf.video.service.AdminLeaderboardService;
import com.grobird.psf.video.service.AdminPitchUploadStatsService;
import com.grobird.psf.video.service.AdminSalesComparisonService;
import com.grobird.psf.video.service.SalesDashboardMetricsService;
import com.grobird.psf.video.service.SalesSubmissionStatsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final SalesDashboardMetricsService salesDashboardMetricsService;
    private final SalesSubmissionStatsService salesSubmissionStatsService;
    private final AdminDashboardMetricsService adminDashboardMetricsService;
    private final AdminSalesComparisonService adminSalesComparisonService;
    private final AdminPitchUploadStatsService adminPitchUploadStatsService;
    private final AdminLeaderboardService adminLeaderboardService;

    public UserController(UserService userService, SalesDashboardMetricsService salesDashboardMetricsService,
                          SalesSubmissionStatsService salesSubmissionStatsService,
                          AdminDashboardMetricsService adminDashboardMetricsService,
                          AdminSalesComparisonService adminSalesComparisonService,
                          AdminPitchUploadStatsService adminPitchUploadStatsService,
                          AdminLeaderboardService adminLeaderboardService) {
        this.userService = userService;
        this.salesDashboardMetricsService = salesDashboardMetricsService;
        this.salesSubmissionStatsService = salesSubmissionStatsService;
        this.adminDashboardMetricsService = adminDashboardMetricsService;
        this.adminSalesComparisonService = adminSalesComparisonService;
        this.adminPitchUploadStatsService = adminPitchUploadStatsService;
        this.adminLeaderboardService = adminLeaderboardService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserDTO> register(
            @RequestBody UserDTO userDTO,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UserDTO created = userService.createUser(userDTO, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/me/sales")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDTO> addSales(
            @Valid @RequestBody AddSalesRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UserDTO created = userService.addSales(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/by-username/{username}")
    public ResponseEntity<UserDTO> findByUsername(@PathVariable String username) {
        UserDTO user = userService.findByUsername(username);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/me/sales")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserDTO>> listMySales(@AuthenticationPrincipal UserPrincipal principal) {
        List<UserDTO> sales = userService.listMySales(principal);
        return ResponseEntity.ok(sales);
    }

    /**
     * Lists detailed information for all sales users under the admin.
     * Includes: name, department, email, phoneNumber, createdAt, invitationStatus, averageScore, latestOpportunityDate.
     */
    @GetMapping("/me/sales-details")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SalesUserDetailResponse>> listMySalesDetails(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<SalesUserDetailResponse> details = userService.listMySalesDetails(principal);
        return ResponseEntity.ok(details);
    }

    @GetMapping("/me/dashboard-metrics")
    @PreAuthorize("hasRole('SALES')")
    public ResponseEntity<DashboardMetricsResponse> getMyDashboardMetrics(@AuthenticationPrincipal UserPrincipal principal) {
        Long tenantId = principal.getTenantId();
        Long userId = principal.getUserId();
        if (tenantId == null || userId == null) {
            return ResponseEntity.badRequest().build();
        }
        DashboardMetricsResponse metrics = salesDashboardMetricsService.getOrCompute(tenantId, userId);
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/me/submissions-stats")
    @PreAuthorize("hasRole('SALES')")
    public ResponseEntity<SalesSubmissionStatsResponse> getMySubmissionsStats(
            @RequestParam(required = false, defaultValue = "weekly") String type,
            @AuthenticationPrincipal UserPrincipal principal) {
        SalesSubmissionStatsResponse stats = salesSubmissionStatsService.getStatsForCurrentSales(principal, type);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<UserDTO>> listAllUsers(@AuthenticationPrincipal UserPrincipal principal) {
        List<UserDTO> users = userService.listAllUsers(principal);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/me/team-dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminDashboardMetricsResponse> getTeamDashboard(@AuthenticationPrincipal UserPrincipal principal) {
        Long tenantId = principal.getTenantId();
        Long adminUserId = principal.getUserId();
        if (tenantId == null || adminUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        AdminDashboardMetricsResponse metrics = adminDashboardMetricsService.getOrCompute(tenantId, adminUserId);
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/me/sales-comparison")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminSalesComparisonResponse> getSalesComparison(
            @RequestParam(required = false) Long salesUserId,
            @RequestParam(required = false, defaultValue = "weekly") String type,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long tenantId = principal.getTenantId();
        Long adminUserId = principal.getUserId();
        if (tenantId == null || adminUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        AdminSalesComparisonResponse comparison = adminSalesComparisonService.getComparison(
                tenantId, adminUserId, salesUserId, type);
        return ResponseEntity.ok(comparison);
    }

    @GetMapping("/me/pitch-upload-stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminPitchUploadStatsResponse> getPitchUploadStats(
            @RequestParam(required = false) Long salesUserId,
            @RequestParam(required = false, defaultValue = "weekly") String type,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long tenantId = principal.getTenantId();
        Long adminUserId = principal.getUserId();
        if (tenantId == null || adminUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        AdminPitchUploadStatsResponse stats = adminPitchUploadStatsService.getStats(
                tenantId, adminUserId, salesUserId, type);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/me/sales-leaderboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SalesLeaderboardResponse> getSalesLeaderboard(
            @AuthenticationPrincipal UserPrincipal principal) {
        Long tenantId = principal.getTenantId();
        Long adminUserId = principal.getUserId();
        if (tenantId == null || adminUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        SalesLeaderboardResponse leaderboard = adminLeaderboardService.getLeaderboard(tenantId, adminUserId);
        return ResponseEntity.ok(leaderboard);
    }

    /**
     * Admin resets password for a sales user under them.
     * Generates new random password and sends it via email.
     * Resets the sales user's invitation status to PENDING.
     */
    @PostMapping("/{userId}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> adminResetSalesPassword(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserPrincipal principal) {
        userService.adminResetSalesPassword(userId, principal);
        return ResponseEntity.ok(Map.of("message", "Password reset successfully. New credentials sent to user's email."));
    }
}
