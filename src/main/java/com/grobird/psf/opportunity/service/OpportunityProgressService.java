package com.grobird.psf.opportunity.service;

import com.grobird.psf.config.security.UserPrincipal;
import com.grobird.psf.config.tenant.TenantContext;
import com.grobird.psf.opportunity.dto.GoldenPitchProgress;
import com.grobird.psf.opportunity.dto.OpportunityProgressResponse;
import com.grobird.psf.opportunity.dto.SkillsetProgressItem;
import com.grobird.psf.opportunity.entity.OpportunityEntity;
import com.grobird.psf.opportunity.repository.OpportunityRepository;
import com.grobird.psf.qna.repository.QnaQuestionsRepository;
import com.grobird.psf.user.enums.Role;
import com.grobird.psf.user.repository.UserRepository;
import com.grobird.psf.video.entity.ReferenceVideoType;
import com.grobird.psf.video.entity.SalesSubmissionEntity;
import com.grobird.psf.video.entity.SalesSubmissionStatus;
import com.grobird.psf.video.repository.SalesSubmissionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OpportunityProgressService {

    private final OpportunityRepository opportunityRepository;
    private final UserRepository userRepository;
    private final QnaQuestionsRepository qnaQuestionsRepository;
    private final SalesSubmissionRepository salesSubmissionRepository;

    public OpportunityProgressService(OpportunityRepository opportunityRepository,
                                      UserRepository userRepository,
                                      QnaQuestionsRepository qnaQuestionsRepository,
                                      SalesSubmissionRepository salesSubmissionRepository) {
        this.opportunityRepository = opportunityRepository;
        this.userRepository = userRepository;
        this.qnaQuestionsRepository = qnaQuestionsRepository;
        this.salesSubmissionRepository = salesSubmissionRepository;
    }

    @Transactional(readOnly = true)
    public List<OpportunityProgressResponse> getProgress(UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return getProgress(principal.getUserId(), principal.getRole());
    }

    public List<OpportunityProgressResponse> getProgress(Long currentUserId, String currentUserRole) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No tenant context");
        }
        boolean isAdmin = Role.ADMIN.name().equalsIgnoreCase(currentUserRole);
        List<OpportunityEntity> entities;
        if (Role.SALES.name().equalsIgnoreCase(currentUserRole)) {
            entities = opportunityRepository.findBySales_Id(currentUserId);
        } else if (isAdmin) {
            entities = opportunityRepository.findBySales_ReportedToUserId(currentUserId);
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only sales or admin can list opportunities");
        }
        if (entities.isEmpty()) {
            return List.of();
        }
        List<Long> opportunityIds = entities.stream().map(OpportunityEntity::getId).toList();

        Set<Long> opportunityIdsWithQ = new HashSet<>(
                qnaQuestionsRepository.findDistinctOpportunityIdsByOpportunityIdIn(opportunityIds));

        List<SalesSubmissionEntity> goldenPitchSubmissions =
                salesSubmissionRepository.findByOpportunityIdInAndReferenceVideo_Type(opportunityIds, ReferenceVideoType.GOLDEN_PITCH);
        Map<Long, SalesSubmissionEntity> latestGoldenPitchByOpp = goldenPitchSubmissions.stream()
                .collect(Collectors.groupingBy(
                        SalesSubmissionEntity::getOpportunityId,
                        Collectors.collectingAndThen(
                                Collectors.maxBy(Comparator.comparing(SalesSubmissionEntity::getCreatedAt)),
                                opt -> opt.orElse(null))));

        List<SalesSubmissionEntity> skillsetSubmissions =
                salesSubmissionRepository.findByOpportunityIdInAndReferenceVideo_Type(opportunityIds, ReferenceVideoType.SKILLSET);
        Map<Long, Map<Long, SalesSubmissionEntity>> oppToRefToLatestSkillset = skillsetSubmissions.stream()
                .collect(Collectors.groupingBy(
                        SalesSubmissionEntity::getOpportunityId,
                        Collectors.toMap(
                                s -> s.getReferenceVideo().getId(),
                                s -> s,
                                (a, b) -> a.getCreatedAt() != null && b.getCreatedAt() != null
                                        && a.getCreatedAt().isAfter(b.getCreatedAt()) ? a : b)));

        List<OpportunityProgressResponse> result = new ArrayList<>();
        for (OpportunityEntity opp : entities) {
            Long oppId = opp.getId();
            boolean questionsGenerated = opportunityIdsWithQ.contains(oppId);
            GoldenPitchProgress goldenPitch = toGoldenPitchProgress(latestGoldenPitchByOpp.get(oppId));
            List<SkillsetProgressItem> skillsets = toSkillsetProgressItems(
                    oppToRefToLatestSkillset.getOrDefault(oppId, Map.of()).values().stream().toList());
            String salesUserName = isAdmin && opp.getSales() != null ? opp.getSales().getUsername() : null;
            result.add(OpportunityProgressResponse.builder()
                    .opportunityId(oppId)
                    .salesUserName(salesUserName)
                    .company(opp.getCompany())
                    .industry(opp.getIndustry())
                    .questionsGenerated(questionsGenerated)
                    .goldenPitch(goldenPitch)
                    .skillsets(skillsets)
                    .build());
        }
        return result;
    }

    private static GoldenPitchProgress toGoldenPitchProgress(SalesSubmissionEntity s) {
        if (s == null) {
            return null;
        }
        boolean completed = s.getStatus() == SalesSubmissionStatus.completed;
        return GoldenPitchProgress.builder()
                .uploaded(true)
                .status(s.getStatus().name())
                .comparisonScore(toDouble(s.getComparisonScore()))
                .completedAt(completed && s.getUpdatedAt() != null ? s.getUpdatedAt() : null)
                .build();
    }

    private static List<SkillsetProgressItem> toSkillsetProgressItems(List<SalesSubmissionEntity> submissions) {
        if (submissions == null || submissions.isEmpty()) {
            return List.of();
        }
        return submissions.stream()
                .map(s -> SkillsetProgressItem.builder()
                        .skillsetName(s.getReferenceVideo() != null ? s.getReferenceVideo().getName() : null)
                        .referenceVideoId(s.getReferenceVideo() != null ? s.getReferenceVideo().getId() : null)
                        .status(s.getStatus().name())
                        .comparisonScore(toDouble(s.getComparisonScore()))
                        .submittedAt(s.getCreatedAt())
                        .build())
                .toList();
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
