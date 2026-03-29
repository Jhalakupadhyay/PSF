package com.grobird.psf.notification.repository;

import com.grobird.psf.notification.entity.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {

    List<NotificationEntity> findByTargetUserIdOrderByCreatedAtDesc(Long targetUserId);

    List<NotificationEntity> findByTargetUserIdAndReadAtIsNullOrderByCreatedAtDesc(Long targetUserId);

    List<NotificationEntity> findByTargetUserIdAndReadAtIsNotNullOrderByCreatedAtDesc(Long targetUserId);

    Optional<NotificationEntity> findByIdAndTargetUserId(Long id, Long targetUserId);

    List<NotificationEntity> findByTargetUserIdAndReadAtIsNull(Long targetUserId);

    long countByTargetUserIdAndReadAtIsNull(Long targetUserId);

    void deleteByOpportunityId(Long opportunityId);

    void deleteBySalesSubmissionIdIn(java.util.Collection<Long> salesSubmissionIds);
}
