package com.grobird.psf.video.repository;

import com.grobird.psf.video.entity.SalesSubmissionEntity;
import com.grobird.psf.video.entity.SalesSubmissionStatus;
import com.grobird.psf.video.entity.ReferenceVideoType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SalesSubmissionRepository extends JpaRepository<SalesSubmissionEntity, Long> {

    List<SalesSubmissionEntity> findByOpportunityIdOrderByCreatedAtDesc(Long opportunityId);

    List<SalesSubmissionEntity> findByOpportunityIdInAndStatus(Collection<Long> opportunityIds, SalesSubmissionStatus status);

    List<SalesSubmissionEntity> findByOpportunityIdInAndStatusInAndCreatedAtBetween(
            Collection<Long> opportunityIds, Collection<SalesSubmissionStatus> statuses, Instant from, Instant to);

    Optional<SalesSubmissionEntity> findFirstByOpportunityIdAndReferenceVideo_TypeOrderByCreatedAtDesc(
            Long opportunityId, ReferenceVideoType type);

    Optional<SalesSubmissionEntity> findFirstByOpportunityIdAndReferenceVideo_IdOrderByCreatedAtDesc(
            Long opportunityId, Long referenceVideoId);

    Optional<SalesSubmissionEntity> findByAnalyzerAnalysisId(String analyzerAnalysisId);

    List<SalesSubmissionEntity> findByOpportunityIdAndReferenceVideo_Type(Long opportunityId, ReferenceVideoType type);

    List<SalesSubmissionEntity> findByOpportunityIdInAndReferenceVideo_Type(Collection<Long> opportunityIds, ReferenceVideoType type);

    void deleteByOpportunityId(Long opportunityId);

    List<SalesSubmissionEntity> findByReferenceVideo_Id(Long referenceVideoId);

    void deleteByReferenceVideo_Id(Long referenceVideoId);
}
