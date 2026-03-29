package com.grobird.psf.qna.repository;

import com.grobird.psf.qna.entity.QnaInfoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QnaInfoRepository extends JpaRepository<QnaInfoEntity, Long> {

    Optional<QnaInfoEntity> findByOpportunityId(Long opportunityId);

    void deleteByOpportunityId(Long opportunityId);
}
