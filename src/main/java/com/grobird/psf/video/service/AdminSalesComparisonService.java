package com.grobird.psf.video.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grobird.psf.opportunity.entity.OpportunityEntity;
import com.grobird.psf.opportunity.repository.OpportunityRepository;
import com.grobird.psf.user.entity.UserEntity;
import com.grobird.psf.user.repository.UserRepository;
import com.grobird.psf.video.cache.AdminSalesComparisonCacheStore;
import com.grobird.psf.video.dashboard.DashboardMetricsExtractor;
import com.grobird.psf.video.dashboard.DashboardMetricsSnapshot;
import com.grobird.psf.video.dto.AdminSalesComparisonResponse;
import com.grobird.psf.video.dto.AdminSalesComparisonResponse.SalesUserMetrics;
import com.grobird.psf.video.dto.AdminSalesComparisonResponse.TeamAverageMetrics;
import com.grobird.psf.video.entity.SalesSubmissionEntity;
import com.grobird.psf.video.entity.SalesSubmissionStatus;
import com.grobird.psf.video.repository.SalesSubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for admin sales comparison dashboard.
 * Computes time-filtered metrics comparing a selected sales user against team average.
 * Uses Redis caching with cache invalidation on submission completion.
 */
@Service
@Transactional(readOnly = true)
public class AdminSalesComparisonService {

    private static final Logger log = LoggerFactory.getLogger(AdminSalesComparisonService.class);
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final int SCALE = 2;
    private static final List<SalesSubmissionStatus> COMPLETED_STATUS = List.of(SalesSubmissionStatus.completed);

    private final UserRepository userRepository;
    private final OpportunityRepository opportunityRepository;
    private final SalesSubmissionRepository submissionRepository;
    private final AdminSalesComparisonCacheStore cacheStore;
    private final ObjectMapper objectMapper;

    public AdminSalesComparisonService(UserRepository userRepository,
                                       OpportunityRepository opportunityRepository,
                                       SalesSubmissionRepository submissionRepository,
                                       AdminSalesComparisonCacheStore cacheStore,
                                       ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.opportunityRepository = opportunityRepository;
        this.submissionRepository = submissionRepository;
        this.cacheStore = cacheStore;
        this.objectMapper = objectMapper;
    }

    /**
     * Get sales comparison data for an admin. Checks cache first, computes if miss.
     *
     * @param tenantId    the tenant
     * @param adminUserId the admin user id
     * @param salesUserId optional specific sales user to compare (null = team only)
     * @param type        weekly, monthly, or yearly
     */
    public AdminSalesComparisonResponse getComparison(Long tenantId, Long adminUserId, Long salesUserId, String type) {
        String normalizedType = normalizeType(type);

        Optional<String> cached = cacheStore.get(tenantId, adminUserId, normalizedType, salesUserId);
        if (cached.isPresent()) {
            try {
                return objectMapper.readValue(cached.get(), AdminSalesComparisonResponse.class);
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse cached comparison for admin {}", adminUserId, e);
            }
        }

        AdminSalesComparisonResponse response = computeComparison(tenantId, adminUserId, salesUserId, normalizedType);

        try {
            cacheStore.put(tenantId, adminUserId, normalizedType, salesUserId, objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache comparison for admin {}", adminUserId, e);
        }

        return response;
    }

    /**
     * Invalidate all cached comparison data for an admin.
     * Called when any sales user under this admin completes a submission.
     */
    public void invalidateCacheForAdmin(Long tenantId, Long adminUserId) {
        cacheStore.invalidateAllForAdmin(tenantId, adminUserId);
    }

    private AdminSalesComparisonResponse computeComparison(Long tenantId, Long adminUserId, Long salesUserId, String type) {
        TimeWindow window = computeTimeWindow(type);

        List<UserEntity> salesUsers = userRepository.findByReportedToUserId(adminUserId);
        if (salesUsers.isEmpty()) {
            return AdminSalesComparisonResponse.builder()
                    .type(type)
                    .selectedUser(null)
                    .teamAverage(emptyTeamMetrics())
                    .build();
        }

        if (salesUserId != null) {
            boolean validUser = salesUsers.stream().anyMatch(u -> u.getId().equals(salesUserId));
            if (!validUser) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Sales user " + salesUserId + " does not report to admin " + adminUserId);
            }
        }

        Map<Long, List<SalesSubmissionEntity>> submissionsBySalesUser = new HashMap<>();
        Map<Long, String> usernameMap = new HashMap<>();

        for (UserEntity salesUser : salesUsers) {
            usernameMap.put(salesUser.getId(), salesUser.getUsername());
            List<Long> oppIds = opportunityRepository.findBySales_Id(salesUser.getId()).stream()
                    .map(OpportunityEntity::getId)
                    .toList();
            if (oppIds.isEmpty()) {
                submissionsBySalesUser.put(salesUser.getId(), List.of());
            } else {
                List<SalesSubmissionEntity> subs = submissionRepository.findByOpportunityIdInAndStatusInAndCreatedAtBetween(
                        oppIds, COMPLETED_STATUS, window.from(), window.to());
                submissionsBySalesUser.put(salesUser.getId(), subs);
            }
        }

        TeamAverageMetrics teamAverage = computeTeamAverage(submissionsBySalesUser);

        SalesUserMetrics selectedUser = null;
        if (salesUserId != null) {
            List<SalesSubmissionEntity> userSubs = submissionsBySalesUser.getOrDefault(salesUserId, List.of());
            selectedUser = computeUserMetrics(salesUserId, usernameMap.get(salesUserId), userSubs);
        }

        return AdminSalesComparisonResponse.builder()
                .type(type)
                .selectedUser(selectedUser)
                .teamAverage(teamAverage)
                .build();
    }

    private TeamAverageMetrics computeTeamAverage(Map<Long, List<SalesSubmissionEntity>> submissionsBySalesUser) {
        List<Double> allVocal = new ArrayList<>();
        List<Double> allConfidence = new ArrayList<>();
        List<Double> allFacial = new ArrayList<>();
        List<Double> allContent = new ArrayList<>();
        List<Double> allSpeech = new ArrayList<>();
        List<Double> allAudience = new ArrayList<>();
        List<Double> allComparison = new ArrayList<>();
        int totalSubmissions = 0;

        for (List<SalesSubmissionEntity> subs : submissionsBySalesUser.values()) {
            for (SalesSubmissionEntity s : subs) {
                totalSubmissions++;
                if (s.getComparisonScore() != null) {
                    allComparison.add(s.getComparisonScore().doubleValue());
                }
                if (s.getFullResult() != null) {
                    DashboardMetricsSnapshot snap = DashboardMetricsExtractor.extract(s.getFullResult());
                    if (snap.vocalDelivery() != null) allVocal.add(snap.vocalDelivery());
                    if (snap.confidenceIndex() != null) allConfidence.add(snap.confidenceIndex());
                    if (snap.facialEngagement() != null) allFacial.add(snap.facialEngagement());
                    if (snap.contentQuality() != null) allContent.add(snap.contentQuality());
                    if (snap.speechFluency() != null) allSpeech.add(snap.speechFluency());
                    if (snap.audienceEngagement() != null) allAudience.add(snap.audienceEngagement());
                }
            }
        }

        return TeamAverageMetrics.builder()
                .vocalDelivery(avg(allVocal))
                .confidenceIndex(avg(allConfidence))
                .facialEngagement(avg(allFacial))
                .contentQuality(avg(allContent))
                .speechFluency(avg(allSpeech))
                .audienceEngagement(avg(allAudience))
                .averageScore(computeAverageScore(allComparison))
                .salesUserCount(submissionsBySalesUser.size())
                .totalSubmissions(totalSubmissions)
                .build();
    }

    private SalesUserMetrics computeUserMetrics(Long userId, String username, List<SalesSubmissionEntity> submissions) {
        List<Double> vocal = new ArrayList<>();
        List<Double> confidence = new ArrayList<>();
        List<Double> facial = new ArrayList<>();
        List<Double> content = new ArrayList<>();
        List<Double> speech = new ArrayList<>();
        List<Double> audience = new ArrayList<>();
        List<Double> comparison = new ArrayList<>();

        for (SalesSubmissionEntity s : submissions) {
            if (s.getComparisonScore() != null) {
                comparison.add(s.getComparisonScore().doubleValue());
            }
            if (s.getFullResult() != null) {
                DashboardMetricsSnapshot snap = DashboardMetricsExtractor.extract(s.getFullResult());
                if (snap.vocalDelivery() != null) vocal.add(snap.vocalDelivery());
                if (snap.confidenceIndex() != null) confidence.add(snap.confidenceIndex());
                if (snap.facialEngagement() != null) facial.add(snap.facialEngagement());
                if (snap.contentQuality() != null) content.add(snap.contentQuality());
                if (snap.speechFluency() != null) speech.add(snap.speechFluency());
                if (snap.audienceEngagement() != null) audience.add(snap.audienceEngagement());
            }
        }

        return SalesUserMetrics.builder()
                .userId(userId)
                .username(username)
                .vocalDelivery(avg(vocal))
                .confidenceIndex(avg(confidence))
                .facialEngagement(avg(facial))
                .contentQuality(avg(content))
                .speechFluency(avg(speech))
                .audienceEngagement(avg(audience))
                .averageScore(computeAverageScore(comparison))
                .submissionCount(submissions.size())
                .build();
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return "weekly";
        }
        String t = type.trim().toLowerCase();
        return switch (t) {
            case "weekly", "monthly", "yearly" -> t;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid type. Use: weekly, monthly, or yearly");
        };
    }

    private TimeWindow computeTimeWindow(String type) {
        Instant now = Instant.now();
        Instant from = switch (type) {
            case "weekly" -> now.minus(12 * 7L, ChronoUnit.DAYS);
            case "monthly" -> now.atZone(UTC).minusMonths(12).toInstant();
            case "yearly" -> now.atZone(UTC).minusYears(5).toInstant();
            default -> now.minus(12 * 7L, ChronoUnit.DAYS);
        };
        return new TimeWindow(from, now);
    }

    private static Double avg(List<Double> values) {
        if (values == null || values.isEmpty()) return null;
        double sum = 0;
        for (Double v : values) {
            sum += v;
        }
        return BigDecimal.valueOf(sum / values.size()).setScale(SCALE, RoundingMode.HALF_UP).doubleValue();
    }

    private static Double computeAverageScore(List<Double> comparisonScores) {
        if (comparisonScores == null || comparisonScores.isEmpty()) return null;
        double sum = 0;
        for (Double v : comparisonScores) {
            sum += v;
        }
        double avgComparison = sum / comparisonScores.size();
        double scoreOutOf5 = (avgComparison / 100.0) * 5.0;
        return BigDecimal.valueOf(scoreOutOf5).setScale(SCALE, RoundingMode.HALF_UP).doubleValue();
    }

    private static TeamAverageMetrics emptyTeamMetrics() {
        return TeamAverageMetrics.builder()
                .vocalDelivery(null)
                .confidenceIndex(null)
                .facialEngagement(null)
                .contentQuality(null)
                .speechFluency(null)
                .audienceEngagement(null)
                .averageScore(null)
                .salesUserCount(0)
                .totalSubmissions(0)
                .build();
    }

    private record TimeWindow(Instant from, Instant to) {}
}
