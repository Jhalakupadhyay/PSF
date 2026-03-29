package com.grobird.psf.video.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grobird.psf.opportunity.entity.OpportunityEntity;
import com.grobird.psf.opportunity.repository.OpportunityRepository;
import com.grobird.psf.user.entity.UserEntity;
import com.grobird.psf.user.repository.UserRepository;
import com.grobird.psf.video.cache.AdminPitchUploadStatsCacheStore;
import com.grobird.psf.video.dto.AdminPitchUploadStatsResponse;
import com.grobird.psf.video.dto.AdminPitchUploadStatsResponse.DataPoint;
import com.grobird.psf.video.dto.AdminPitchUploadStatsResponse.TeamUploadStats;
import com.grobird.psf.video.dto.AdminPitchUploadStatsResponse.UserUploadStats;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AdminPitchUploadStatsService {

    private static final Logger log = LoggerFactory.getLogger(AdminPitchUploadStatsService.class);
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final int SCALE = 2;
    private static final List<SalesSubmissionStatus> COMPLETED_STATUS = List.of(SalesSubmissionStatus.completed);

    private final UserRepository userRepository;
    private final OpportunityRepository opportunityRepository;
    private final SalesSubmissionRepository submissionRepository;
    private final AdminPitchUploadStatsCacheStore cacheStore;
    private final ObjectMapper objectMapper;

    public AdminPitchUploadStatsService(UserRepository userRepository,
                                        OpportunityRepository opportunityRepository,
                                        SalesSubmissionRepository submissionRepository,
                                        AdminPitchUploadStatsCacheStore cacheStore,
                                        ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.opportunityRepository = opportunityRepository;
        this.submissionRepository = submissionRepository;
        this.cacheStore = cacheStore;
        this.objectMapper = objectMapper;
    }

    public AdminPitchUploadStatsResponse getStats(Long tenantId, Long adminUserId, Long salesUserId, String type) {
        String normalizedType = normalizeType(type);

        Optional<String> cached = cacheStore.get(tenantId, adminUserId, normalizedType, salesUserId);
        if (cached.isPresent()) {
            try {
                return objectMapper.readValue(cached.get(), AdminPitchUploadStatsResponse.class);
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse cached pitch upload stats for admin {}", adminUserId, e);
            }
        }

        AdminPitchUploadStatsResponse response = computeStats(adminUserId, salesUserId, normalizedType);

        try {
            cacheStore.put(tenantId, adminUserId, normalizedType, salesUserId, objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache pitch upload stats for admin {}", adminUserId, e);
        }

        return response;
    }

    public void invalidateCacheForAdmin(Long tenantId, Long adminUserId) {
        cacheStore.invalidateAllForAdmin(tenantId, adminUserId);
    }

    private AdminPitchUploadStatsResponse computeStats(Long adminUserId, Long salesUserId, String type) {
        TimeWindow window = computeTimeWindow(type);
        List<String> bucketLabels = buildBucketLabels(type, window);

        List<UserEntity> salesUsers = userRepository.findByReportedToUserId(adminUserId);
        if (salesUsers.isEmpty()) {
            return AdminPitchUploadStatsResponse.builder()
                    .type(type)
                    .selectedUser(null)
                    .teamStats(emptyTeamStats(bucketLabels))
                    .build();
        }

        if (salesUserId != null) {
            boolean validUser = salesUsers.stream().anyMatch(u -> u.getId().equals(salesUserId));
            if (!validUser) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Sales user " + salesUserId + " does not report to admin " + adminUserId);
            }
        }

        Map<Long, List<SalesSubmissionEntity>> submissionsByUser = new HashMap<>();
        Map<Long, String> usernameMap = new HashMap<>();

        for (UserEntity salesUser : salesUsers) {
            usernameMap.put(salesUser.getId(), salesUser.getUsername());
            List<Long> oppIds = opportunityRepository.findBySales_Id(salesUser.getId()).stream()
                    .map(OpportunityEntity::getId)
                    .toList();
            if (oppIds.isEmpty()) {
                submissionsByUser.put(salesUser.getId(), List.of());
            } else {
                List<SalesSubmissionEntity> subs = submissionRepository.findByOpportunityIdInAndStatusInAndCreatedAtBetween(
                        oppIds, COMPLETED_STATUS, window.from(), window.to());
                submissionsByUser.put(salesUser.getId(), subs);
            }
        }

        List<SalesSubmissionEntity> allSubs = submissionsByUser.values().stream()
                .flatMap(List::stream)
                .toList();

        List<DataPoint> teamDataPoints = bucketize(allSubs, type, bucketLabels);
        int totalCount = allSubs.size();
        int salesUserCount = salesUsers.size();
        Double averageCount = salesUserCount > 0
                ? BigDecimal.valueOf((double) totalCount / salesUserCount).setScale(SCALE, RoundingMode.HALF_UP).doubleValue()
                : null;

        TeamUploadStats teamStats = TeamUploadStats.builder()
                .averageUploadCount(averageCount)
                .totalUploadCount(totalCount)
                .salesUserCount(salesUserCount)
                .dataPoints(teamDataPoints)
                .build();

        UserUploadStats selectedUser = null;
        if (salesUserId != null) {
            List<SalesSubmissionEntity> userSubs = submissionsByUser.getOrDefault(salesUserId, List.of());
            List<DataPoint> userDataPoints = bucketize(userSubs, type, bucketLabels);
            selectedUser = UserUploadStats.builder()
                    .userId(salesUserId)
                    .username(usernameMap.get(salesUserId))
                    .uploadCount(userSubs.size())
                    .dataPoints(userDataPoints)
                    .build();
        }

        return AdminPitchUploadStatsResponse.builder()
                .type(type)
                .selectedUser(selectedUser)
                .teamStats(teamStats)
                .build();
    }

    private List<DataPoint> bucketize(List<SalesSubmissionEntity> submissions, String type, List<String> bucketLabels) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String label : bucketLabels) {
            counts.put(label, 0);
        }

        for (SalesSubmissionEntity s : submissions) {
            if (s.getCreatedAt() == null) continue;
            String key = toBucketKey(s.getCreatedAt(), type);
            counts.computeIfPresent(key, (k, v) -> v + 1);
        }

        return counts.entrySet().stream()
                .map(e -> DataPoint.builder().period(e.getKey()).count(e.getValue()).build())
                .toList();
    }

    private String toBucketKey(Instant instant, String type) {
        LocalDate date = instant.atZone(UTC).toLocalDate();
        return switch (type) {
            case "weekly" -> {
                LocalDate weekStart = date.with(java.time.DayOfWeek.MONDAY);
                yield weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE);
            }
            case "monthly" -> date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            case "yearly" -> String.valueOf(date.getYear());
            default -> date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        };
    }

    private List<String> buildBucketLabels(String type, TimeWindow window) {
        List<String> labels = new ArrayList<>();
        LocalDate start = window.from().atZone(UTC).toLocalDate();
        LocalDate end = window.to().atZone(UTC).toLocalDate();

        switch (type) {
            case "weekly" -> {
                LocalDate weekStart = start.with(java.time.DayOfWeek.MONDAY);
                while (!weekStart.isAfter(end)) {
                    labels.add(weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE));
                    weekStart = weekStart.plusWeeks(1);
                }
            }
            case "monthly" -> {
                LocalDate monthStart = start.withDayOfMonth(1);
                while (!monthStart.isAfter(end)) {
                    labels.add(monthStart.format(DateTimeFormatter.ofPattern("yyyy-MM")));
                    monthStart = monthStart.plusMonths(1);
                }
            }
            case "yearly" -> {
                int startYear = start.getYear();
                int endYear = end.getYear();
                for (int y = startYear; y <= endYear; y++) {
                    labels.add(String.valueOf(y));
                }
            }
        }
        return labels;
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

    private static TeamUploadStats emptyTeamStats(List<String> bucketLabels) {
        List<DataPoint> emptyPoints = bucketLabels.stream()
                .map(label -> DataPoint.builder().period(label).count(0).build())
                .toList();
        return TeamUploadStats.builder()
                .averageUploadCount(null)
                .totalUploadCount(0)
                .salesUserCount(0)
                .dataPoints(emptyPoints)
                .build();
    }

    private record TimeWindow(Instant from, Instant to) {}
}
