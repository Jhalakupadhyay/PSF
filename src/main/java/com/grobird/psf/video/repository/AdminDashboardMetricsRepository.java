package com.grobird.psf.video.repository;

import com.grobird.psf.video.entity.AdminDashboardMetricsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminDashboardMetricsRepository extends JpaRepository<AdminDashboardMetricsEntity, Long> {

    Optional<AdminDashboardMetricsEntity> findByTenantIdAndAdminUserId(Long tenantId, Long adminUserId);
}
