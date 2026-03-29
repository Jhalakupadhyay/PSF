package com.grobird.psf.qna.repository;

import com.grobird.psf.qna.entity.QnaQuestionsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface QnaQuestionsRepository extends JpaRepository<QnaQuestionsEntity, Long> {

    /** All question batches for an opportunity, newest first. */
    List<QnaQuestionsEntity> findByOpportunityIdOrderByCreatedAtDesc(Long opportunityId);

    /** Opportunity IDs that have at least one question batch (for step computation). */
    @Query("SELECT DISTINCT q.opportunityId FROM QnaQuestionsEntity q WHERE q.opportunityId IN :opportunityIds")
    List<Long> findDistinctOpportunityIdsByOpportunityIdIn(Collection<Long> opportunityIds);

    void deleteByOpportunityId(Long opportunityId);
}
