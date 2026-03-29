package com.grobird.psf.video.repository;

import com.grobird.psf.video.entity.SalesDashboardMetricsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SalesDashboardMetricsRepository extends JpaRepository<SalesDashboardMetricsEntity, Long> {

    Optional<SalesDashboardMetricsEntity> findByTenantIdAndUserId(Long tenantId, Long userId);

    @Query("SELECT m FROM SalesDashboardMetricsEntity m " +
           "JOIN UserEntity u ON m.userId = u.id AND m.tenantId = u.tenantId " +
           "WHERE u.reportedToUserId = :adminUserId " +
           "ORDER BY m.averageScore DESC NULLS LAST")
    List<SalesDashboardMetricsEntity> findByAdminUserId(Long adminUserId);
}
