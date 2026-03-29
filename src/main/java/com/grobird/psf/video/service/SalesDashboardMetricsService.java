package com.grobird.psf.video.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grobird.psf.notification.service.NotificationService;
import com.grobird.psf.opportunity.repository.OpportunityRepository;
import com.grobird.psf.user.entity.UserEntity;
import com.grobird.psf.user.repository.UserRepository;
import com.grobird.psf.video.cache.DashboardMetricsCacheStore;
import com.grobird.psf.video.dashboard.DashboardMetricsExtractor;
import com.grobird.psf.video.dashboard.DashboardMetricsSnapshot;
import com.grobird.psf.video.dto.DashboardMetricsResponse;
import com.grobird.psf.video.entity.SalesDashboardMetricsEntity;
import com.grobird.psf.video.entity.SalesSubmissionEntity;
import com.grobird.psf.video.entity.SalesSubmissionStatus;
import com.grobird.psf.video.repository.SalesDashboardMetricsRepository;
import com.grobird.psf.video.repository.SalesSubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Aggregates dashboard metrics from completed sales submissions per sales user.
 * Persists to sales_dashboard_metrics and caches in Redis; invalidates cache on new completion.
 */
@Service
@Transactional
public class SalesDashboardMetricsService {

    private static final Logger log = LoggerFactory.getLogger(SalesDashboardMetricsService.class);
    private static final int SCALE = 2;

    private final OpportunityRepository opportunityRepository;
    private final SalesSubmissionRepository submissionRepository;
    private final SalesDashboardMetricsRepository dashboardRepository;
    private final DashboardMetricsCacheStore dashboardCache;
    private final ObjectMapper objectMapper;
    private final AdminDashboardMetricsService adminDashboardMetricsService;
    private final AdminSalesComparisonService adminSalesComparisonService;
    private final AdminPitchUploadStatsService adminPitchUploadStatsService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public SalesDashboardMetricsService(OpportunityRepository opportunityRepository,
                                        SalesSubmissionRepository submissionRepository,
                                        SalesDashboardMetricsRepository dashboardRepository,
                                        DashboardMetricsCacheStore dashboardCache,
                                        ObjectMapper objectMapper,
                                        @Lazy AdminDashboardMetricsService adminDashboardMetricsService,
                                        @Lazy AdminSalesComparisonService adminSalesComparisonService,
                                        @Lazy AdminPitchUploadStatsService adminPitchUploadStatsService,
                                        UserRepository userRepository,
                                        NotificationService notificationService) {
        this.opportunityRepository = opportunityRepository;
        this.submissionRepository = submissionRepository;
        this.dashboardRepository = dashboardRepository;
        this.dashboardCache = dashboardCache;
        this.objectMapper = objectMapper;
        this.adminDashboardMetricsService = adminDashboardMetricsService;
        this.adminSalesComparisonService = adminSalesComparisonService;
        this.adminPitchUploadStatsService = adminPitchUploadStatsService;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    /**
     * Get dashboard metrics for the sales user: cache first, then DB, then recompute if missing.
     */
    public DashboardMetricsResponse getOrCompute(Long tenantId, Long userId) {
        Optional<String> cached = dashboardCache.get(tenantId, userId);
        if (cached.isPresent()) {
            try {
                return objectMapper.readValue(cached.get(), DashboardMetricsResponse.class);
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse cached dashboard metrics for user {}", userId, e);
            }
        }

        Optional<SalesDashboardMetricsEntity> existing = dashboardRepository.findByTenantIdAndUserId(tenantId, userId);
        if (existing.isEmpty()) {
            recomputeAndSave(tenantId, userId);
            existing = dashboardRepository.findByTenantIdAndUserId(tenantId, userId);
        }

        DashboardMetricsResponse response = existing
                .map(this::entityToResponse)
                .orElse(emptyResponse());
        try {
            dashboardCache.put(tenantId, userId, objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache dashboard metrics for user {}", userId, e);
        }
        return response;
    }

    /**
     * Called when a submission completes: recompute aggregates for that submission's sales user and invalidate cache.
     */
    public void onSubmissionCompleted(SalesSubmissionEntity submission) {
        Long tenantId = submission.getTenantId();
        Long opportunityId = submission.getOpportunityId();
        if (tenantId == null || opportunityId == null) {
            return;
        }
        Long salesUserId = opportunityRepository.findById(opportunityId)
                .map(o -> o.getSales() != null ? o.getSales().getId() : null)
                .orElse(null);
        if (salesUserId == null) {
            log.warn("No sales user for opportunity {} (submission {})", opportunityId, submission.getId());
            return;
        }
        recomputeAndSave(tenantId, salesUserId);
        dashboardCache.invalidate(tenantId, salesUserId);
        notificationService.createForCompletedSubmission(submission, salesUserId);
    }

    /**
     * Recompute aggregates and invalidate all related caches (sales + admin).
     * Use after data changes like opportunity deletion.
     */
    public void recomputeAndInvalidateCache(Long tenantId, Long salesUserId) {
        recomputeAndSave(tenantId, salesUserId);
        dashboardCache.invalidate(tenantId, salesUserId);
    }

    /**
     * Recompute aggregates from all completed submissions for the sales user and persist.
     */
    public void recomputeAndSave(Long tenantId, Long salesUserId) {
        List<Long> opportunityIds = opportunityRepository.findBySales_Id(salesUserId).stream()
                .map(o -> o.getId())
                .collect(Collectors.toList());
        if (opportunityIds.isEmpty()) {
            saveEmptyRow(tenantId, salesUserId);
            return;
        }

        List<SalesSubmissionEntity> submissions = submissionRepository.findByOpportunityIdInAndStatus(
                opportunityIds, SalesSubmissionStatus.completed);

        List<Double> vocal = new ArrayList<>();
        List<Double> confidence = new ArrayList<>();
        List<Double> facial = new ArrayList<>();
        List<Double> content = new ArrayList<>();
        List<Double> speech = new ArrayList<>();
        List<Double> audience = new ArrayList<>();
        List<Double> comparisonScores = new ArrayList<>();

        for (SalesSubmissionEntity s : submissions) {
            if (s.getComparisonScore() != null) {
                comparisonScores.add(s.getComparisonScore().doubleValue());
            }
            if (s.getFullResult() == null) continue;
            DashboardMetricsSnapshot snap = DashboardMetricsExtractor.extract(s.getFullResult());
            if (snap.vocalDelivery() != null) vocal.add(snap.vocalDelivery());
            if (snap.confidenceIndex() != null) confidence.add(snap.confidenceIndex());
            if (snap.facialEngagement() != null) facial.add(snap.facialEngagement());
            if (snap.contentQuality() != null) content.add(snap.contentQuality());
            if (snap.speechFluency() != null) speech.add(snap.speechFluency());
            if (snap.audienceEngagement() != null) audience.add(snap.audienceEngagement());
        }

        SalesDashboardMetricsEntity entity = dashboardRepository.findByTenantIdAndUserId(tenantId, salesUserId)
                .orElseGet(() -> {
                    SalesDashboardMetricsEntity e = new SalesDashboardMetricsEntity();
                    e.setTenantId(tenantId);
                    e.setUserId(salesUserId);
                    return e;
                });

        entity.setVocalDeliveryAvg(avg(vocal));
        entity.setConfidenceIndexAvg(avg(confidence));
        entity.setFacialEngagementAvg(avg(facial));
        entity.setContentQualityAvg(avg(content));
        entity.setSpeechFluencyAvg(avg(speech));
        entity.setAudienceEngagementAvg(avg(audience));
        entity.setSubmissionCount(submissions.size());
        entity.setAverageScore(computeAverageScore(comparisonScores));
        entity.setUpdatedAt(Instant.now());
        dashboardRepository.save(entity);

        adminDashboardMetricsService.onSalesUserMetricsUpdated(tenantId, salesUserId);

        UserEntity salesUser = userRepository.findById(salesUserId).orElse(null);
        if (salesUser != null && salesUser.getReportedToUserId() != null) {
            adminSalesComparisonService.invalidateCacheForAdmin(tenantId, salesUser.getReportedToUserId());
            adminPitchUploadStatsService.invalidateCacheForAdmin(tenantId, salesUser.getReportedToUserId());
        }
    }

    private void saveEmptyRow(Long tenantId, Long salesUserId) {
        SalesDashboardMetricsEntity entity = dashboardRepository.findByTenantIdAndUserId(tenantId, salesUserId)
                .orElseGet(() -> {
                    SalesDashboardMetricsEntity e = new SalesDashboardMetricsEntity();
                    e.setTenantId(tenantId);
                    e.setUserId(salesUserId);
                    return e;
                });
        entity.setVocalDeliveryAvg(null);
        entity.setConfidenceIndexAvg(null);
        entity.setFacialEngagementAvg(null);
        entity.setContentQualityAvg(null);
        entity.setSpeechFluencyAvg(null);
        entity.setAudienceEngagementAvg(null);
        entity.setSubmissionCount(0);
        entity.setAverageScore(null);
        entity.setUpdatedAt(Instant.now());
        dashboardRepository.save(entity);
    }

    private static BigDecimal computeAverageScore(List<Double> comparisonScores) {
        if (comparisonScores == null || comparisonScores.isEmpty()) return null;
        double sum = 0;
        for (Double v : comparisonScores) {
            sum += v;
        }
        double avgComparison = sum / comparisonScores.size();
        double scoreOutOf5 = (avgComparison / 100.0) * 5.0;
        return BigDecimal.valueOf(scoreOutOf5).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal avg(List<Double> values) {
        if (values == null || values.isEmpty()) return null;
        double sum = 0;
        for (Double v : values) {
            sum += v;
        }
        return BigDecimal.valueOf(sum / values.size()).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private DashboardMetricsResponse entityToResponse(SalesDashboardMetricsEntity e) {
        return DashboardMetricsResponse.builder()
                .vocalDelivery(toDouble(e.getVocalDeliveryAvg()))
                .confidenceIndex(toDouble(e.getConfidenceIndexAvg()))
                .facialEngagement(toDouble(e.getFacialEngagementAvg()))
                .contentQuality(toDouble(e.getContentQualityAvg()))
                .speechFluency(toDouble(e.getSpeechFluencyAvg()))
                .audienceEngagement(toDouble(e.getAudienceEngagementAvg()))
                .submissionCount(e.getSubmissionCount())
                .averageScore(toDouble(e.getAverageScore()))
                .build();
    }

    private static Double toDouble(BigDecimal bd) {
        return bd == null ? null : bd.doubleValue();
    }

    private static DashboardMetricsResponse emptyResponse() {
        return DashboardMetricsResponse.builder()
                .vocalDelivery(null)
                .confidenceIndex(null)
                .facialEngagement(null)
                .contentQuality(null)
                .speechFluency(null)
                .audienceEngagement(null)
                .submissionCount(0)
                .averageScore(null)
                .build();
    }
}
