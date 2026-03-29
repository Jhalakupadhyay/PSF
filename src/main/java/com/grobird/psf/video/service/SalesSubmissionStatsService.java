package com.grobird.psf.video.service;

import com.grobird.psf.config.security.UserPrincipal;
import com.grobird.psf.opportunity.repository.OpportunityRepository;
import com.grobird.psf.user.enums.Role;
import com.grobird.psf.video.dto.SalesSubmissionStatsResponse;
import com.grobird.psf.video.entity.SalesSubmissionEntity;
import com.grobird.psf.video.entity.SalesSubmissionStatus;
import com.grobird.psf.video.repository.SalesSubmissionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Stats for pitch submissions uploaded by the current sales user, grouped by weekly/monthly/yearly for dashboard graphs.
 */
@Service
@Transactional(readOnly = true)
public class SalesSubmissionStatsService {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final List<SalesSubmissionStatus> NON_FAILED_STATUSES = Arrays.asList(
            SalesSubmissionStatus.pending,
            SalesSubmissionStatus.processing,
            SalesSubmissionStatus.completed
    );

    private final OpportunityRepository opportunityRepository;
    private final SalesSubmissionRepository submissionRepository;

    public SalesSubmissionStatsService(OpportunityRepository opportunityRepository,
                                      SalesSubmissionRepository submissionRepository) {
        this.opportunityRepository = opportunityRepository;
        this.submissionRepository = submissionRepository;
    }

    /**
     * Returns submission counts for the current sales user over a recent window, grouped by the given type.
     */
    public SalesSubmissionStatsResponse getStatsForCurrentSales(UserPrincipal principal, String type) {
        if (principal == null || !Role.SALES.name().equalsIgnoreCase(principal.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only SALES users can access submission stats");
        }
        Instant now = Instant.now();
        Instant from;
        String normalizedType;
        switch (type != null ? type.trim().toLowerCase() : "weekly") {
            case "weekly" -> {
                from = now.minus(12 * 7L, ChronoUnit.DAYS);
                normalizedType = "weekly";
            }
            case "monthly" -> {
                from = now.atZone(UTC).minusMonths(12).toInstant();
                normalizedType = "monthly";
            }
            case "yearly" -> {
                from = now.atZone(UTC).minusYears(5).toInstant();
                normalizedType = "yearly";
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid type. Use: weekly, monthly, or yearly");
        }

        List<Long> opportunityIds = opportunityRepository.findBySales_Id(principal.getUserId()).stream()
                .map(o -> o.getId())
                .toList();

        List<SalesSubmissionEntity> submissions;
        if (opportunityIds.isEmpty()) {
            submissions = List.of();
        } else {
            submissions = submissionRepository.findByOpportunityIdInAndStatusInAndCreatedAtBetween(
                    opportunityIds, NON_FAILED_STATUSES, from, now);
        }

        List<SalesSubmissionStatsResponse.DateCountEntry> dataPoints = buildDataPoints(normalizedType, from, now, submissions);
        return SalesSubmissionStatsResponse.builder()
                .type(normalizedType)
                .dataPoints(dataPoints)
                .totalSubmissions(submissions.size())
                .build();
    }

    private List<SalesSubmissionStatsResponse.DateCountEntry> buildDataPoints(
            String normalizedType, Instant from, Instant to, List<SalesSubmissionEntity> submissions) {
        if ("weekly".equals(normalizedType)) {
            Map<LocalDate, Long> byWeekStart = submissions.stream()
                    .filter(s -> s.getCreatedAt() != null)
                    .collect(Collectors.groupingBy(
                            s -> s.getCreatedAt().atZone(UTC).toLocalDate().with(DayOfWeek.MONDAY),
                            Collectors.counting()));
            LocalDate start = from.atZone(UTC).toLocalDate().with(DayOfWeek.MONDAY);
            LocalDate end = to.atZone(UTC).toLocalDate().with(DayOfWeek.MONDAY);
            List<LocalDate> weeks = new ArrayList<>();
            for (LocalDate d = start; !d.isAfter(end); d = d.plusWeeks(1)) {
                weeks.add(d);
            }
            return weeks.stream()
                    .map(d -> SalesSubmissionStatsResponse.DateCountEntry.builder()
                            .date(d.toString() + " (week)")
                            .count(byWeekStart.getOrDefault(d, 0L))
                            .build())
                    .toList();
        }
        if ("monthly".equals(normalizedType)) {
            Map<YearMonth, Long> byMonth = submissions.stream()
                    .filter(s -> s.getCreatedAt() != null)
                    .collect(Collectors.groupingBy(
                            s -> YearMonth.from(s.getCreatedAt().atZone(UTC)),
                            Collectors.counting()));
            YearMonth start = YearMonth.from(from.atZone(UTC));
            YearMonth end = YearMonth.from(to.atZone(UTC));
            List<YearMonth> months = new ArrayList<>();
            for (YearMonth m = start; !m.isAfter(end); m = m.plusMonths(1)) {
                months.add(m);
            }
            return months.stream()
                    .map(m -> SalesSubmissionStatsResponse.DateCountEntry.builder()
                            .date(m.toString())
                            .count(byMonth.getOrDefault(m, 0L))
                            .build())
                    .toList();
        }
        // yearly
        Map<Year, Long> byYear = submissions.stream()
                .filter(s -> s.getCreatedAt() != null)
                .collect(Collectors.groupingBy(
                        s -> Year.from(s.getCreatedAt().atZone(UTC)),
                        Collectors.counting()));
        Year startYear = Year.from(from.atZone(UTC));
        Year endYear = Year.from(to.atZone(UTC));
        List<Year> years = new ArrayList<>();
        for (Year y = startYear; !y.isAfter(endYear); y = y.plusYears(1)) {
            years.add(y);
        }
        return years.stream()
                .map(y -> SalesSubmissionStatsResponse.DateCountEntry.builder()
                        .date(y.toString())
                        .count(byYear.getOrDefault(y, 0L))
                        .build())
                .toList();
    }
}
