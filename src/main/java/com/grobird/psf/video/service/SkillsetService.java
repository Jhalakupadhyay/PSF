package com.grobird.psf.video.service;

import com.grobird.psf.config.security.UserPrincipal;
import com.grobird.psf.config.tenant.TenantContext;
import com.grobird.psf.notification.repository.NotificationRepository;
import com.grobird.psf.opportunity.repository.OpportunityRepository;
import com.grobird.psf.user.enums.Role;
import com.grobird.psf.video.dto.SkillsetResponse;
import com.grobird.psf.video.dto.UploadUrlResponse;
import com.grobird.psf.video.entity.ReferenceVideoEntity;
import com.grobird.psf.video.entity.ReferenceVideoType;
import com.grobird.psf.video.entity.SalesSubmissionEntity;
import com.grobird.psf.video.repository.ReferenceVideoRepository;
import com.grobird.psf.video.repository.SalesSubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SkillsetService {

    private static final Logger log = LoggerFactory.getLogger(SkillsetService.class);

    private final ReferenceVideoRepository repository;
    private final SalesSubmissionRepository salesSubmissionRepository;
    private final NotificationRepository notificationRepository;
    private final OpportunityRepository opportunityRepository;
    private final S3PresignedService s3PresignedService;
    private final SalesAnalysisService salesAnalysisService;
    private final SalesDashboardMetricsService salesDashboardMetricsService;

    public SkillsetService(ReferenceVideoRepository repository,
                           SalesSubmissionRepository salesSubmissionRepository,
                           NotificationRepository notificationRepository,
                           OpportunityRepository opportunityRepository,
                           S3PresignedService s3PresignedService,
                           SalesAnalysisService salesAnalysisService,
                           @Lazy SalesDashboardMetricsService salesDashboardMetricsService) {
        this.repository = repository;
        this.salesSubmissionRepository = salesSubmissionRepository;
        this.notificationRepository = notificationRepository;
        this.opportunityRepository = opportunityRepository;
        this.s3PresignedService = s3PresignedService;
        this.salesAnalysisService = salesAnalysisService;
        this.salesDashboardMetricsService = salesDashboardMetricsService;
    }

    @Transactional
    public SkillsetResponse createSkillset(String name, UserPrincipal principal) {
        requireAdmin(principal);
        Long tenantId = requireTenant();
        ReferenceVideoEntity entity = ReferenceVideoEntity.builder()
                .tenantId(tenantId)
                .type(ReferenceVideoType.SKILLSET)
                .name(name.trim())
                .processed(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        entity = repository.save(entity);
        S3PresignedService.UploadResult uploadResult = s3PresignedService.generateUploadUrlSkillset(tenantId, entity.getId());
        return toResponse(entity, uploadResult.uploadUrl(), uploadResult.key());
    }

    @Transactional
    public void saveVideo(Long skillsetId, String key, UserPrincipal principal) {
        requireAdmin(principal);
        Long tenantId = requireTenant();
        ReferenceVideoEntity entity = repository.findByIdAndType(skillsetId, ReferenceVideoType.SKILLSET)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Skillset not found"));
        entity.setVideoS3Key(key);
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
        // Run eager registration only after this transaction commits so async reads never see stale/null S3 key.
        Long referenceId = entity.getId();
        triggerReferenceRegistrationAfterCommit(referenceId, tenantId);
    }

    public List<SkillsetResponse> listSkillsets(UserPrincipal principal) {
        requireAdmin(principal);
        requireTenant();
        return repository.findAllByTypeOrderByNameAsc(ReferenceVideoType.SKILLSET).stream()
                .map(e -> toResponse(e, null, null))
                .toList();
    }

    @Transactional
    public void delete(Long skillsetId, UserPrincipal principal) {
        requireAdmin(principal);
        requireTenant();

        ReferenceVideoEntity entity = repository.findByIdAndType(skillsetId, ReferenceVideoType.SKILLSET)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Skillset not found"));

        Long refId = entity.getId();
        String s3Key = entity.getVideoS3Key();

        List<SalesSubmissionEntity> submissions = salesSubmissionRepository.findByReferenceVideo_Id(refId);

        Set<Long> affectedOpportunityIds = submissions.stream()
                .map(SalesSubmissionEntity::getOpportunityId)
                .collect(Collectors.toSet());

        List<String> submissionS3Keys = submissions.stream()
                .map(SalesSubmissionEntity::getVideoS3Key)
                .filter(k -> k != null && !k.isBlank())
                .toList();

        if (!submissions.isEmpty()) {
            List<Long> submissionIds = submissions.stream().map(SalesSubmissionEntity::getId).toList();
            notificationRepository.deleteBySalesSubmissionIdIn(submissionIds);
            salesSubmissionRepository.deleteByReferenceVideo_Id(refId);
        }
        repository.delete(entity);

        s3PresignedService.deleteObject(s3Key);
        submissionS3Keys.forEach(s3PresignedService::deleteObject);

        log.info("Deleted skillset {} (ref {}) with {} submissions", entity.getName(), refId, submissions.size());

        recomputeMetricsForOpportunities(affectedOpportunityIds);
    }

    private void recomputeMetricsForOpportunities(Set<Long> opportunityIds) {
        Set<Long> recomputedUsers = new HashSet<>();
        for (Long oppId : opportunityIds) {
            opportunityRepository.findById(oppId).ifPresent(opp -> {
                if (opp.getSales() != null) {
                    Long salesUserId = opp.getSales().getId();
                    Long salesTenantId = opp.getSales().getTenantId();
                    if (recomputedUsers.add(salesUserId)) {
                        salesDashboardMetricsService.recomputeAndInvalidateCache(salesTenantId, salesUserId);
                    }
                }
            });
        }
    }

    public UploadUrlResponse getUploadUrl(Long skillsetId, UserPrincipal principal) {
        requireAdmin(principal);
        Long tenantId = requireTenant();
        ReferenceVideoEntity entity = repository.findByIdAndType(skillsetId, ReferenceVideoType.SKILLSET)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Skillset not found"));
        S3PresignedService.UploadResult result = s3PresignedService.generateUploadUrlSkillset(tenantId, entity.getId());
        return UploadUrlResponse.builder()
                .uploadUrl(result.uploadUrl())
                .key(result.key())
                .build();
    }

    private SkillsetResponse toResponse(ReferenceVideoEntity e, String uploadUrl, String key) {
        String playbackUrl = (e.getVideoS3Key() != null && !e.getVideoS3Key().isBlank())
                ? s3PresignedService.generatePlaybackUrl(e.getVideoS3Key())
                : null;
        return SkillsetResponse.builder()
                .id(e.getId())
                .name(e.getName())
                .videoPlaybackUrl(playbackUrl)
                .uploadUrl(uploadUrl)
                .key(key)
                .build();
    }

    private void requireAdmin(UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (!Role.ADMIN.name().equalsIgnoreCase(principal.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admin can manage skillsets");
        }
    }

    private Long requireTenant() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tenant context");
        }
        return tenantId;
    }

    private void triggerReferenceRegistrationAfterCommit(Long referenceId, Long tenantId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            salesAnalysisService.registerReferenceAsync(referenceId, tenantId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                salesAnalysisService.registerReferenceAsync(referenceId, tenantId);
            }
        });
    }
}
