package com.grobird.psf.opportunity.service;

import com.grobird.psf.config.security.UserPrincipal;
import com.grobird.psf.opportunity.dto.AdminOpportunityResponse;
import com.grobird.psf.opportunity.dto.AdminOpportunitiesWithMetricsResponse;
import com.grobird.psf.opportunity.dto.SalesUserMetricsResponse;
import com.grobird.psf.opportunity.entity.OpportunityEntity;
import com.grobird.psf.opportunity.repository.OpportunityRepository;
import com.grobird.psf.qna.repository.QnaQuestionsRepository;
import com.grobird.psf.user.entity.UserEntity;
import com.grobird.psf.user.enums.Role;
import com.grobird.psf.user.repository.UserRepository;
import com.grobird.psf.video.entity.ReferenceVideoType;
import com.grobird.psf.video.entity.SalesDashboardMetricsEntity;
import com.grobird.psf.video.entity.SalesSubmissionEntity;
import com.grobird.psf.video.repository.ReferenceVideoRepository;
import com.grobird.psf.video.repository.SalesDashboardMetricsRepository;
import com.grobird.psf.video.repository.SalesSubmissionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminOpportunityService {

    private final OpportunityRepository opportunityRepository;
    private final QnaQuestionsRepository qnaQuestionsRepository;
    private final SalesSubmissionRepository salesSubmissionRepository;
    private final ReferenceVideoRepository referenceVideoRepository;
    private final SalesDashboardMetricsRepository salesDashboardMetricsRepository;
    private final UserRepository userRepository;

    public AdminOpportunityService(OpportunityRepository opportunityRepository,
                                   QnaQuestionsRepository qnaQuestionsRepository,
                                   SalesSubmissionRepository salesSubmissionRepository,
                                   ReferenceVideoRepository referenceVideoRepository,
                                   SalesDashboardMetricsRepository salesDashboardMetricsRepository,
                                   UserRepository userRepository) {
        this.opportunityRepository = opportunityRepository;
        this.qnaQuestionsRepository = qnaQuestionsRepository;
        this.salesSubmissionRepository = salesSubmissionRepository;
        this.referenceVideoRepository = referenceVideoRepository;
        this.salesDashboardMetricsRepository = salesDashboardMetricsRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public AdminOpportunitiesWithMetricsResponse listForAdmin(UserPrincipal principal, Long salesUserId) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (!Role.ADMIN.name().equalsIgnoreCase(principal.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admin can access this endpoint");
        }

        Long adminUserId = principal.getUserId();
        List<OpportunityEntity> entities;
        UserEntity salesUserEntity = null;

        if (salesUserId != null) {
            Optional<UserEntity> salesUserOpt = userRepository.findById(salesUserId);
            if (salesUserOpt.isEmpty() || !adminUserId.equals(salesUserOpt.get().getReportedToUserId())) {
                return AdminOpportunitiesWithMetricsResponse.builder()
                        .salesUserMetrics(null)
                        .opportunities(List.of())
                        .build();
            }
            salesUserEntity = salesUserOpt.get();
            entities = opportunityRepository.findBySales_IdAndSales_ReportedToUserId(salesUserId, adminUserId);
        } else {
            entities = opportunityRepository.findBySales_ReportedToUserId(adminUserId);
        }

        if (entities.isEmpty()) {
            SalesUserMetricsResponse metrics = buildSalesUserMetrics(salesUserEntity);
            return AdminOpportunitiesWithMetricsResponse.builder()
                    .salesUserMetrics(metrics)
                    .opportunities(List.of())
                    .build();
        }

        List<Long> opportunityIds = entities.stream().map(OpportunityEntity::getId).toList();

        Set<Long> opportunityIdsWithQ = new HashSet<>(
                qnaQuestionsRepository.findDistinctOpportunityIdsByOpportunityIdIn(opportunityIds));

        List<SalesSubmissionEntity> goldenPitchSubmissions = salesSubmissionRepository
                .findByOpportunityIdInAndReferenceVideo_Type(opportunityIds, ReferenceVideoType.GOLDEN_PITCH);
        Set<Long> opportunityIdsWithGoldenPitch = goldenPitchSubmissions.stream()
                .map(SalesSubmissionEntity::getOpportunityId)
                .collect(Collectors.toSet());

        Set<Long> skillsetRefIds = referenceVideoRepository
                .findAllByTypeOrderByNameAsc(ReferenceVideoType.SKILLSET).stream()
                .map(ref -> ref.getId())
                .collect(Collectors.toSet());

        List<SalesSubmissionEntity> skillsetSubmissions = salesSubmissionRepository
                .findByOpportunityIdInAndReferenceVideo_Type(opportunityIds, ReferenceVideoType.SKILLSET);
        Map<Long, Set<Long>> opportunityIdToSkillsetRefIdsWithSubmission = skillsetSubmissions.stream()
                .collect(Collectors.groupingBy(
                        SalesSubmissionEntity::getOpportunityId,
                        Collectors.mapping(s -> s.getReferenceVideo().getId(), Collectors.toSet())));

        Map<Long, Integer> opportunityIdToStep = new HashMap<>();
        for (Long oppId : opportunityIds) {
            boolean hasGoldenPitch = opportunityIdsWithGoldenPitch.contains(oppId);
            Set<Long> skillsetRefsWithSubmission = opportunityIdToSkillsetRefIdsWithSubmission.getOrDefault(oppId, Set.of());
            boolean hasAllSkillsets = skillsetRefIds.isEmpty() || skillsetRefsWithSubmission.containsAll(skillsetRefIds);
            boolean hasQ = opportunityIdsWithQ.contains(oppId);

            int step;
            if (hasGoldenPitch && hasAllSkillsets) {
                step = 3;
            } else if (hasQ) {
                step = 1;
            } else {
                step = 2;
            }
            opportunityIdToStep.put(oppId, step);
        }

        List<AdminOpportunityResponse> opportunityResponses = entities.stream()
                .map(e -> AdminOpportunityResponse.builder()
                        .id(e.getId())
                        .salesUserName(e.getSales() != null ? e.getSales().getUsername() : null)
                        .company(e.getCompany())
                        .industry(e.getIndustry())
                        .createdAt(e.getCreatedAt())
                        .step(opportunityIdToStep.getOrDefault(e.getId(), 2))
                        .questionsGenerated(opportunityIdsWithQ.contains(e.getId()))
                        .build())
                .toList();

        SalesUserMetricsResponse salesUserMetrics = buildSalesUserMetrics(salesUserEntity);
        return AdminOpportunitiesWithMetricsResponse.builder()
                .salesUserMetrics(salesUserMetrics)
                .opportunities(opportunityResponses)
                .build();
    }

    private SalesUserMetricsResponse buildSalesUserMetrics(UserEntity salesUser) {
        if (salesUser == null) {
            return null;
        }
        Long salesTenantId = salesUser.getTenantId();
        Long salesUserId = salesUser.getId();
        Optional<SalesDashboardMetricsEntity> metricsOpt = salesDashboardMetricsRepository.findByTenantIdAndUserId(salesTenantId, salesUserId);

        if (metricsOpt.isEmpty()) {
            return SalesUserMetricsResponse.builder()
                    .salesUserId(salesUserId)
                    .salesUserName(salesUser.getUsername())
                    .submissionCount(0)
                    .build();
        }
        SalesDashboardMetricsEntity m = metricsOpt.get();
        return SalesUserMetricsResponse.builder()
                .salesUserId(salesUserId)
                .salesUserName(salesUser.getUsername())
                .averageScore(m.getAverageScore())
                .vocalDeliveryAvg(m.getVocalDeliveryAvg())
                .confidenceIndexAvg(m.getConfidenceIndexAvg())
                .facialEngagementAvg(m.getFacialEngagementAvg())
                .contentQualityAvg(m.getContentQualityAvg())
                .speechFluencyAvg(m.getSpeechFluencyAvg())
                .audienceEngagementAvg(m.getAudienceEngagementAvg())
                .submissionCount(m.getSubmissionCount())
                .build();
    }
}
