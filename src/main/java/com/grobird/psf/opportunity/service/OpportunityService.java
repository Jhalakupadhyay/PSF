package com.grobird.psf.opportunity.service;

import com.grobird.psf.config.security.UserPrincipal;
import com.grobird.psf.config.tenant.TenantContext;
import com.grobird.psf.notification.repository.NotificationRepository;
import com.grobird.psf.opportunity.dto.OpportunityRequest;
import com.grobird.psf.opportunity.dto.OpportunityResponse;
import com.grobird.psf.opportunity.entity.OpportunityEntity;
import com.grobird.psf.opportunity.repository.OpportunityRepository;
import com.grobird.psf.qna.repository.QnaInfoRepository;
import com.grobird.psf.qna.repository.QnaQuestionsRepository;
import com.grobird.psf.user.entity.UserEntity;
import com.grobird.psf.user.enums.Role;
import com.grobird.psf.user.repository.UserRepository;
import com.grobird.psf.video.entity.ReferenceVideoType;
import com.grobird.psf.video.entity.SalesSubmissionEntity;
import com.grobird.psf.video.repository.ReferenceVideoRepository;
import com.grobird.psf.video.repository.SalesSubmissionRepository;
import com.grobird.psf.video.service.SalesDashboardMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OpportunityService {

    private static final Logger log = LoggerFactory.getLogger(OpportunityService.class);

    private final OpportunityRepository opportunityRepository;
    private final UserRepository userRepository;
    private final QnaQuestionsRepository qnaQuestionsRepository;
    private final QnaInfoRepository qnaInfoRepository;
    private final SalesSubmissionRepository salesSubmissionRepository;
    private final ReferenceVideoRepository referenceVideoRepository;
    private final NotificationRepository notificationRepository;
    private final SalesDashboardMetricsService salesDashboardMetricsService;

    public OpportunityService(OpportunityRepository opportunityRepository,
                              UserRepository userRepository,
                              QnaQuestionsRepository qnaQuestionsRepository,
                              QnaInfoRepository qnaInfoRepository,
                              SalesSubmissionRepository salesSubmissionRepository,
                              ReferenceVideoRepository referenceVideoRepository,
                              NotificationRepository notificationRepository,
                              @Lazy SalesDashboardMetricsService salesDashboardMetricsService) {
        this.opportunityRepository = opportunityRepository;
        this.userRepository = userRepository;
        this.qnaQuestionsRepository = qnaQuestionsRepository;
        this.qnaInfoRepository = qnaInfoRepository;
        this.salesSubmissionRepository = salesSubmissionRepository;
        this.referenceVideoRepository = referenceVideoRepository;
        this.notificationRepository = notificationRepository;
        this.salesDashboardMetricsService = salesDashboardMetricsService;
    }

    @Transactional
    public OpportunityResponse create(OpportunityRequest request, UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return create(request, principal.getUserId(), principal.getRole());
    }

    @Transactional
    public void delete(Long opportunityId, UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        delete(opportunityId, principal.getUserId(), principal.getRole());
    }

    public List<OpportunityResponse> list(UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return list(principal.getUserId(), principal.getRole());
    }

    @Transactional
    public OpportunityResponse create(OpportunityRequest request, Long currentUserId, String currentUserRole) {
        if (!Role.SALES.name().equalsIgnoreCase(currentUserRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only sales can create opportunities");
        }
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No tenant context");
        }
        var sales = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Current user not found"));
        if (!Role.SALES.name().equals(sales.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only sales can create opportunities");
        }
        String company = request.getCompany().trim();
        String industry = request.getIndustry().trim();
        if (opportunityRepository.existsBySales_IdAndCompanyIgnoreCaseAndIndustryIgnoreCase(sales.getId(), company, industry)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "One opportunity per company and industry per sales person. An opportunity already exists for this company and industry.");
        }
        OpportunityEntity entity = OpportunityEntity.builder()
                .tenantId(tenantId)
                .industry(industry)
                .company(company)
                .sales(sales)
                .createdAt(Instant.now())
                .build();
        OpportunityEntity saved = opportunityRepository.save(entity);
        return toResponse(saved, 2);
    }

    @Transactional
    public void delete(Long opportunityId, Long currentUserId, String currentUserRole) {
        if (!Role.ADMIN.name().equalsIgnoreCase(currentUserRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admin can delete opportunities");
        }

        OpportunityEntity opportunity = opportunityRepository.findById(opportunityId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Opportunity not found"));

        UserEntity salesUser = opportunity.getSales();
        if (salesUser == null || !currentUserId.equals(salesUser.getReportedToUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only delete opportunities belonging to your sales users");
        }

        Long salesUserId = salesUser.getId();
        Long salesTenantId = salesUser.getTenantId();

        // FK order: notifications → sales_submissions → qna_questions → qna_info → opportunity
        notificationRepository.deleteByOpportunityId(opportunityId);
        salesSubmissionRepository.deleteByOpportunityId(opportunityId);
        qnaQuestionsRepository.deleteByOpportunityId(opportunityId);
        qnaInfoRepository.deleteByOpportunityId(opportunityId);
        opportunityRepository.delete(opportunity);

        log.info("Deleted opportunity {} and all related data (sales user {})", opportunityId, salesUserId);

        salesDashboardMetricsService.recomputeAndInvalidateCache(salesTenantId, salesUserId);
    }

    public List<OpportunityResponse> list(Long currentUserId, String currentUserRole) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No tenant context");
        }
        List<OpportunityEntity> entities;
        if (Role.SALES.name().equalsIgnoreCase(currentUserRole)) {
            entities = opportunityRepository.findBySales_Id(currentUserId);
        } else if (Role.ADMIN.name().equalsIgnoreCase(currentUserRole)) {
            entities = opportunityRepository.findBySales_ReportedToUserId(currentUserId);
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only sales or admin can list opportunities");
        }
        if (entities.isEmpty()) {
            return List.of();
        }
        List<Long> opportunityIds = entities.stream().map(OpportunityEntity::getId).toList();

        Set<Long> opportunityIdsWithQ = new HashSet<>(qnaQuestionsRepository.findDistinctOpportunityIdsByOpportunityIdIn(opportunityIds));

        List<SalesSubmissionEntity> goldenPitchSubmissions = salesSubmissionRepository.findByOpportunityIdInAndReferenceVideo_Type(opportunityIds, ReferenceVideoType.GOLDEN_PITCH);
        Set<Long> opportunityIdsWithGoldenPitch = goldenPitchSubmissions.stream()
                .map(SalesSubmissionEntity::getOpportunityId)
                .collect(Collectors.toSet());

        Set<Long> skillsetRefIds = referenceVideoRepository.findAllByTypeOrderByNameAsc(ReferenceVideoType.SKILLSET).stream()
                .map(ref -> ref.getId())
                .collect(Collectors.toSet());

        List<SalesSubmissionEntity> skillsetSubmissions = salesSubmissionRepository.findByOpportunityIdInAndReferenceVideo_Type(opportunityIds, ReferenceVideoType.SKILLSET);
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

        return entities.stream()
                .map(e -> toResponse(e, opportunityIdToStep.getOrDefault(e.getId(), 2)))
                .toList();
    }

    private OpportunityResponse toResponse(OpportunityEntity e, int step) {
        return OpportunityResponse.builder()
                .id(e.getId())
                .industry(e.getIndustry())
                .company(e.getCompany())
                .salesUserId(e.getSales() != null ? e.getSales().getId() : null)
                .tenantId(e.getTenantId() != null ? e.getTenantId().toString() : null)
                .step(step)
                .build();
    }
}
