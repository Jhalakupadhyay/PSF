package com.grobird.psf.video.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grobird.psf.user.entity.UserEntity;
import com.grobird.psf.user.repository.UserRepository;
import com.grobird.psf.video.cache.AdminDashboardMetricsCacheStore;
import com.grobird.psf.video.dto.AdminDashboardMetricsResponse;
import com.grobird.psf.video.entity.AdminDashboardMetricsEntity;
import com.grobird.psf.video.entity.SalesDashboardMetricsEntity;
import com.grobird.psf.video.repository.AdminDashboardMetricsRepository;
import com.grobird.psf.video.repository.SalesDashboardMetricsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Aggregates team dashboard metrics for admin users.
 * Computes teamMatchScore as average of all sales users' averageScore.
 * Persists to admin_dashboard_metrics and caches in Redis; invalidates cache when any sales user's metrics change.
 */
@Service
@Transactional
public class AdminDashboardMetricsService {

    private static final Logger log = LoggerFactory.getLogger(AdminDashboardMetricsService.class);
    private static final int SCALE = 2;

    private final UserRepository userRepository;
    private final SalesDashboardMetricsRepository salesDashboardRepository;
    private final AdminDashboardMetricsRepository adminDashboardRepository;
    private final AdminDashboardMetricsCacheStore adminCache;
    private final ObjectMapper objectMapper;

    public AdminDashboardMetricsService(UserRepository userRepository,
                                        SalesDashboardMetricsRepository salesDashboardRepository,
                                        AdminDashboardMetricsRepository adminDashboardRepository,
                                        AdminDashboardMetricsCacheStore adminCache,
                                        ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.salesDashboardRepository = salesDashboardRepository;
        this.adminDashboardRepository = adminDashboardRepository;
        this.adminCache = adminCache;
        this.objectMapper = objectMapper;
    }

    /**
     * Get team dashboard metrics for the admin: cache first, then DB, then recompute if missing.
     */
    public AdminDashboardMetricsResponse getOrCompute(Long tenantId, Long adminUserId) {
        Optional<String> cached = adminCache.get(tenantId, adminUserId);
        if (cached.isPresent()) {
            try {
                return objectMapper.readValue(cached.get(), AdminDashboardMetricsResponse.class);
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse cached admin dashboard metrics for admin {}", adminUserId, e);
            }
        }

        Optional<AdminDashboardMetricsEntity> existing = adminDashboardRepository.findByTenantIdAndAdminUserId(tenantId, adminUserId);
        if (existing.isEmpty()) {
            recomputeAndSave(tenantId, adminUserId);
            existing = adminDashboardRepository.findByTenantIdAndAdminUserId(tenantId, adminUserId);
        }

        AdminDashboardMetricsResponse response = existing
                .map(this::entityToResponse)
                .orElse(emptyResponse());
        try {
            adminCache.put(tenantId, adminUserId, objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache admin dashboard metrics for admin {}", adminUserId, e);
        }
        return response;
    }

    /**
     * Called when a sales user's metrics are updated: find their admin and recompute.
     */
    public void onSalesUserMetricsUpdated(Long tenantId, Long salesUserId) {
        if (salesUserId == null) {
            return;
        }
        Optional<UserEntity> salesUserOpt = userRepository.findById(salesUserId);
        if (salesUserOpt.isEmpty()) {
            log.warn("Sales user {} not found when updating admin metrics", salesUserId);
            return;
        }
        Long adminUserId = salesUserOpt.get().getReportedToUserId();
        if (adminUserId == null) {
            log.debug("Sales user {} has no reportedToUserId, skipping admin metrics update", salesUserId);
            return;
        }
        Optional<UserEntity> adminOpt = userRepository.findById(adminUserId);
        Long adminTenantId = adminOpt.map(UserEntity::getTenantId).orElse(tenantId);
        recomputeAndSave(adminTenantId, adminUserId);
        adminCache.invalidate(adminTenantId, adminUserId);
    }

    /**
     * Recompute team metrics from all sales users' averageScore who report to this admin.
     */
    public void recomputeAndSave(Long tenantId, Long adminUserId) {
        List<UserEntity> salesUsers = userRepository.findByReportedToUserId(adminUserId);
        if (salesUsers.isEmpty()) {
            saveEmptyRow(tenantId, adminUserId, 0);
            return;
        }

        List<Double> averageScores = new ArrayList<>();
        for (UserEntity salesUser : salesUsers) {
            Long salesTenantId = salesUser.getTenantId();
            Optional<SalesDashboardMetricsEntity> metricsOpt = salesDashboardRepository.findByTenantIdAndUserId(salesTenantId, salesUser.getId());
            if (metricsOpt.isPresent() && metricsOpt.get().getAverageScore() != null) {
                averageScores.add(metricsOpt.get().getAverageScore().doubleValue());
            }
        }

        AdminDashboardMetricsEntity entity = adminDashboardRepository.findByTenantIdAndAdminUserId(tenantId, adminUserId)
                .orElseGet(() -> {
                    AdminDashboardMetricsEntity e = new AdminDashboardMetricsEntity();
                    e.setTenantId(tenantId);
                    e.setAdminUserId(adminUserId);
                    return e;
                });

        entity.setTeamMatchScore(computeTeamMatchScore(averageScores));
        entity.setSalesUserCount(salesUsers.size());
        entity.setUpdatedAt(Instant.now());
        adminDashboardRepository.save(entity);
    }

    private void saveEmptyRow(Long tenantId, Long adminUserId, int salesUserCount) {
        AdminDashboardMetricsEntity entity = adminDashboardRepository.findByTenantIdAndAdminUserId(tenantId, adminUserId)
                .orElseGet(() -> {
                    AdminDashboardMetricsEntity e = new AdminDashboardMetricsEntity();
                    e.setTenantId(tenantId);
                    e.setAdminUserId(adminUserId);
                    return e;
                });
        entity.setTeamMatchScore(null);
        entity.setSalesUserCount(salesUserCount);
        entity.setUpdatedAt(Instant.now());
        adminDashboardRepository.save(entity);
    }

    private static BigDecimal computeTeamMatchScore(List<Double> averageScores) {
        if (averageScores == null || averageScores.isEmpty()) return null;
        double sum = 0;
        for (Double v : averageScores) {
            sum += v;
        }
        return BigDecimal.valueOf(sum / averageScores.size()).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private AdminDashboardMetricsResponse entityToResponse(AdminDashboardMetricsEntity e) {
        return AdminDashboardMetricsResponse.builder()
                .teamMatchScore(toDouble(e.getTeamMatchScore()))
                .salesUserCount(e.getSalesUserCount())
                .build();
    }

    private static Double toDouble(BigDecimal bd) {
        return bd == null ? null : bd.doubleValue();
    }

    private static AdminDashboardMetricsResponse emptyResponse() {
        return AdminDashboardMetricsResponse.builder()
                .teamMatchScore(null)
                .salesUserCount(0)
                .build();
    }
}
