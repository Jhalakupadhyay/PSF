package com.grobird.psf.organization.service;

import com.grobird.psf.config.security.UserPrincipal;
import com.grobird.psf.config.cache.CacheConfig;
import com.grobird.psf.organization.dto.*;
import com.grobird.psf.organization.entity.OrganizationEntity;
import com.grobird.psf.organization.repository.OrganizationRepository;
import com.grobird.psf.opportunity.repository.OpportunityRepository;
import com.grobird.psf.user.dto.UserDTO;
import com.grobird.psf.user.entity.UserEntity;
import com.grobird.psf.user.enums.Role;
import com.grobird.psf.user.repository.UserRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrganizationService {

    private static final ZoneId UTC = ZoneId.of("UTC");

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final OpportunityRepository opportunityRepository;

    public OrganizationService(OrganizationRepository organizationRepository,
                               UserRepository userRepository,
                               OpportunityRepository opportunityRepository) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.opportunityRepository = opportunityRepository;
    }

    private static void requireSuperAdmin(UserPrincipal principal) {
        if (principal == null || !Role.SUPER_ADMIN.name().equalsIgnoreCase(principal.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Super Admin can perform this action");
        }
    }

    /**
     * Create a new organization (tenant). Super Admin only.
     */
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.ORGANIZATIONS_SUMMARY, key = "'all'")
    public OrganizationResponse create(CreateOrganizationRequest request, UserPrincipal principal) {
        requireSuperAdmin(principal);
        OrganizationEntity entity = OrganizationEntity.builder()
                .companyName(request.getCompanyName().trim())
                .industry(request.getIndustry().trim())
                .createdAt(Instant.now())
                .build();
        entity = organizationRepository.save(entity);
        return toResponse(entity);
    }

    /**
     * List all organizations with total admins and total opportunities. Cached 5 minutes.
     */
    @Cacheable(cacheNames = CacheConfig.ORGANIZATIONS_SUMMARY, key = "'all'")
    public List<OrganizationSummaryResponse> listOrganizationsWithCounts(UserPrincipal principal) {
        requireSuperAdmin(principal);
        List<OrganizationEntity> orgs = organizationRepository.findAll();
        return orgs.stream()
                .map(org -> OrganizationSummaryResponse.builder()
                        .id(org.getId())
                        .companyName(org.getCompanyName())
                        .industry(org.getIndustry())
                        .createdAt(org.getCreatedAt())
                        .totalAdmins(userRepository.countByTenantIdAndRole(org.getId(), Role.ADMIN.name()))
                        .totalOpportunities(opportunityRepository.countByTenantId(org.getId()))
                        .build())
                .toList();
    }

    /**
     * List all admins for an organization. Super Admin only.
     */
    public List<UserDTO> listAdminsByOrganizationId(Long organizationId, UserPrincipal principal) {
        requireSuperAdmin(principal);
        organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
        List<UserEntity> admins = userRepository.findByTenantIdAndRole(organizationId, Role.ADMIN.name());
        return admins.stream().map(this::toUserDTO).toList();
    }

    /**
     * List all sales under a specific admin in an organization. Super Admin only.
     */
    public List<UserDTO> listSalesByAdminId(Long organizationId, Long adminId, UserPrincipal principal) {
        requireSuperAdmin(principal);
        organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
        UserEntity admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin not found"));
        if (!Role.ADMIN.name().equals(admin.getRole()) || !organizationId.equals(admin.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not an admin of this organization");
        }
        List<UserEntity> sales = userRepository.findByReportedToUserId(adminId);
        return sales.stream().map(this::toUserDTO).toList();
    }

    /**
     * Dashboard: total organizations only.
     */
    public SuperAdminDashboardResponse getDashboard(UserPrincipal principal) {
        requireSuperAdmin(principal);
        long total = organizationRepository.count();
        return SuperAdminDashboardResponse.builder()
                .totalOrganizations(total)
                .build();
    }

    /**
     * Organizations created stats by type: daily (last 30 days), weekly (last 12 weeks), monthly (last 12 months).
     * Returns date vs count and total created in that period.
     */
    public OrganizationCreatedStatsResponse getCreatedStats(String type, UserPrincipal principal) {
        requireSuperAdmin(principal);
        Instant now = Instant.now();
        Instant from;
        String normalizedType;
        switch (type != null ? type.trim().toLowerCase() : "") {
            case "daily" -> {
                from = now.minus(30, ChronoUnit.DAYS);
                normalizedType = "daily";
            }
            case "weekly" -> {
                from = now.minus(12 * 7L, ChronoUnit.DAYS);
                normalizedType = "weekly";
            }
            case "monthly" -> {
                from = now.atZone(UTC).minusMonths(12).toInstant();
                normalizedType = "monthly";
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid type. Use: daily, weekly, or monthly");
        }
        List<OrganizationEntity> orgs = organizationRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(from, now);
        List<OrganizationCreatedStatsResponse.DateCountEntry> dataPoints;
        if ("daily".equals(normalizedType)) {
            Map<LocalDate, Long> byDay = orgs.stream()
                    .collect(Collectors.groupingBy(
                            o -> o.getCreatedAt().atZone(UTC).toLocalDate(),
                            Collectors.counting()));
            LocalDate start = from.atZone(UTC).toLocalDate();
            LocalDate end = now.atZone(UTC).toLocalDate();
            dataPoints = start.datesUntil(end.plusDays(1))
                    .map(d -> OrganizationCreatedStatsResponse.DateCountEntry.builder()
                            .date(d.toString())
                            .count(byDay.getOrDefault(d, 0L))
                            .build())
                    .toList();
        } else if ("weekly".equals(normalizedType)) {
            Map<LocalDate, Long> byWeekStart = orgs.stream()
                    .collect(Collectors.groupingBy(
                            o -> o.getCreatedAt().atZone(UTC).toLocalDate().with(DayOfWeek.MONDAY),
                            Collectors.counting()));
            LocalDate start = from.atZone(UTC).toLocalDate().with(DayOfWeek.MONDAY);
            LocalDate end = now.atZone(UTC).toLocalDate().with(DayOfWeek.MONDAY);
            List<LocalDate> weeks = new ArrayList<>();
            for (LocalDate d = start; !d.isAfter(end); d = d.plusWeeks(1)) {
                weeks.add(d);
            }
            dataPoints = weeks.stream()
                    .map(d -> OrganizationCreatedStatsResponse.DateCountEntry.builder()
                            .date(d.toString() + " (week)")
                            .count(byWeekStart.getOrDefault(d, 0L))
                            .build())
                    .toList();
        } else {
            Map<YearMonth, Long> byMonth = orgs.stream()
                    .collect(Collectors.groupingBy(
                            o -> YearMonth.from(o.getCreatedAt().atZone(UTC)),
                            Collectors.counting()));
            YearMonth start = YearMonth.from(from.atZone(UTC));
            YearMonth end = YearMonth.from(now.atZone(UTC));
            List<YearMonth> months = new ArrayList<>();
            for (YearMonth m = start; !m.isAfter(end); m = m.plusMonths(1)) {
                months.add(m);
            }
            dataPoints = months.stream()
                    .map(m -> OrganizationCreatedStatsResponse.DateCountEntry.builder()
                            .date(m.toString())
                            .count(byMonth.getOrDefault(m, 0L))
                            .build())
                    .toList();
        }
        long totalCreated = orgs.size();
        return OrganizationCreatedStatsResponse.builder()
                .type(normalizedType)
                .dataPoints(dataPoints)
                .totalCreated(totalCreated)
                .build();
    }

    private OrganizationResponse toResponse(OrganizationEntity e) {
        return OrganizationResponse.builder()
                .id(e.getId())
                .companyName(e.getCompanyName())
                .industry(e.getIndustry())
                .createdAt(e.getCreatedAt())
                .build();
    }

    private UserDTO toUserDTO(UserEntity u) {
        return UserDTO.builder()
                .id(u.getId())
                .tenantId(u.getTenantId() != null ? u.getTenantId().toString() : null)
                .name(u.getUsername())
                .email(u.getEmail())
                .role(u.getRole())
                .reportedToUserId(u.getReportedToUserId())
                .contactNumber(u.getContactNumber())
                .department(u.getDepartment())
                .employeeId(u.getEmployeeId())
                .build();
    }
}
