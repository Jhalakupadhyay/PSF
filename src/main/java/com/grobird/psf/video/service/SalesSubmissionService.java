package com.grobird.psf.video.service;

import com.grobird.psf.config.security.UserPrincipal;
import com.grobird.psf.config.tenant.TenantContext;
import com.grobird.psf.opportunity.repository.OpportunityRepository;
import com.grobird.psf.video.entity.ReferenceVideoEntity;
import com.grobird.psf.video.entity.ReferenceVideoType;
import com.grobird.psf.video.entity.SalesSubmissionEntity;
import com.grobird.psf.video.entity.SalesSubmissionStatus;
import com.grobird.psf.video.repository.ReferenceVideoRepository;
import com.grobird.psf.video.repository.SalesSubmissionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Sales submissions: create submission, trigger async job that registers reference (if needed),
 * uploads sales video to pitch-analyzer, runs analysis with golden_pitch_deck_id, polls for result, saves to DB.
 */
@Service
public class SalesSubmissionService {

    private final SalesSubmissionRepository submissionRepository;
    private final ReferenceVideoRepository referenceVideoRepository;
    private final OpportunityRepository opportunityRepository;
    private final SalesAnalysisService salesAnalysisService;

    public SalesSubmissionService(SalesSubmissionRepository submissionRepository,
                                  ReferenceVideoRepository referenceVideoRepository,
                                  OpportunityRepository opportunityRepository,
                                  SalesAnalysisService salesAnalysisService) {
        this.submissionRepository = submissionRepository;
        this.referenceVideoRepository = referenceVideoRepository;
        this.opportunityRepository = opportunityRepository;
        this.salesAnalysisService = salesAnalysisService;
    }

    public SalesSubmissionEntity createSubmissionForGoldenPitch(Long opportunityId, String videoS3Key, UserPrincipal principal) {
        requireSalesOrAdmin(principal);
        Long tenantId = requireTenant();
        requireOpportunityAccess(opportunityId, principal);
        ReferenceVideoEntity reference = referenceVideoRepository.findFirstByTypeOrderByIdAsc(ReferenceVideoType.GOLDEN_PITCH)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No golden pitch reference configured for this tenant"));
        if (reference.getVideoS3Key() == null || reference.getVideoS3Key().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin has not uploaded the golden pitch reference yet");
        }
        if (!reference.isProcessed() || reference.getAnalyzerDeckId() == null || reference.getAnalyzerDeckId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Golden pitch reference is still being prepared for comparison. Please try again later.");
        }
        SalesSubmissionEntity submission = SalesSubmissionEntity.builder()
                .tenantId(tenantId)
                .opportunityId(opportunityId)
                .referenceVideo(reference)
                .videoS3Key(videoS3Key)
                .status(SalesSubmissionStatus.pending)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        submission = submissionRepository.save(submission);
        salesAnalysisService.runAnalysisAsync(submission.getId(), tenantId);
        return submission;
    }

    public SalesSubmissionEntity createSubmissionForSkillset(Long opportunityId, Long referenceVideoId, String videoS3Key, UserPrincipal principal) {
        requireSalesOrAdmin(principal);
        Long tenantId = requireTenant();
        requireOpportunityAccess(opportunityId, principal);
        ReferenceVideoEntity reference = referenceVideoRepository.findByIdAndType(referenceVideoId, ReferenceVideoType.SKILLSET)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Skillset reference not found"));
        if (reference.getVideoS3Key() == null || reference.getVideoS3Key().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin has not uploaded the skillset reference yet");
        }
        if (!reference.isProcessed() || reference.getAnalyzerDeckId() == null || reference.getAnalyzerDeckId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Skillset reference is still being prepared for comparison. Please try again later.");
        }
        SalesSubmissionEntity submission = SalesSubmissionEntity.builder()
                .tenantId(tenantId)
                .opportunityId(opportunityId)
                .referenceVideo(reference)
                .videoS3Key(videoS3Key)
                .status(SalesSubmissionStatus.pending)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        submission = submissionRepository.save(submission);
        salesAnalysisService.runAnalysisAsync(submission.getId(), tenantId);
        return submission;
    }

    public SalesSubmissionEntity getSubmission(Long id, UserPrincipal principal) {
        requireSalesOrAdmin(principal);
        SalesSubmissionEntity submission = submissionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found"));
        requireOpportunityAccess(submission.getOpportunityId(), principal);
        return submission;
    }

    public Optional<SalesSubmissionEntity> findLatestGoldenPitchSubmission(Long opportunityId, UserPrincipal principal) {
        requireSalesOrAdmin(principal);
        requireOpportunityAccess(opportunityId, principal);
        return submissionRepository.findFirstByOpportunityIdAndReferenceVideo_TypeOrderByCreatedAtDesc(
                opportunityId, ReferenceVideoType.GOLDEN_PITCH);
    }

    public List<SalesSubmissionEntity> listSubmissionsForOpportunity(Long opportunityId, UserPrincipal principal) {
        requireSalesOrAdmin(principal);
        requireOpportunityAccess(opportunityId, principal);
        return submissionRepository.findByOpportunityIdOrderByCreatedAtDesc(opportunityId);
    }

    /**
     * List tenant's skillset reference videos (for sales to choose which to submit against).
     */
    public List<ReferenceVideoEntity> listSkillsetReferences(UserPrincipal principal) {
        requireSalesOrAdmin(principal);
        requireTenant();
        return referenceVideoRepository.findAllByTypeOrderByNameAsc(ReferenceVideoType.SKILLSET);
    }

    /**
     * Latest submission for this opportunity and skillset reference (for status polling).
     */
    public Optional<SalesSubmissionEntity> findLatestSubmissionForSkillset(Long opportunityId, Long referenceVideoId, UserPrincipal principal) {
        requireSalesOrAdmin(principal);
        requireOpportunityAccess(opportunityId, principal);
        referenceVideoRepository.findByIdAndType(referenceVideoId, ReferenceVideoType.SKILLSET)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Skillset reference not found"));
        return submissionRepository.findFirstByOpportunityIdAndReferenceVideo_IdOrderByCreatedAtDesc(opportunityId, referenceVideoId);
    }

    private void requireSalesOrAdmin(UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        String role = principal.getRole();
        if (role == null || (!role.equalsIgnoreCase("SALES") && !role.equalsIgnoreCase("ADMIN"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only sales or admin can submit");
        }
    }

    private Long requireTenant() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tenant context");
        }
        return tenantId;
    }

    private void requireOpportunityAccess(Long opportunityId, UserPrincipal principal) {
        Long userId = principal.getUserId();
        String role = principal.getRole();
        boolean canAccess = "SALES".equalsIgnoreCase(role) && opportunityRepository.existsByIdAndSales_Id(opportunityId, userId)
                || "ADMIN".equalsIgnoreCase(role) && opportunityRepository.existsByIdAndSales_ReportedToUserId(opportunityId, userId);
        if (!canAccess) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this opportunity");
        }
    }

    /**
     * For use by controllers: validates that the principal can access the opportunity. Throws if not.
     */
    public void requireOpportunityAccessForController(Long opportunityId, UserPrincipal principal) {
        requireSalesOrAdmin(principal);
        requireOpportunityAccess(opportunityId, principal);
    }
}
