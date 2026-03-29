package com.grobird.psf.video.entity;

import com.grobird.psf.config.tenant.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Per-admin aggregated dashboard metrics from their sales team.
 * One row per (tenant_id, admin_user_id); updated when any sales user's metrics change.
 */
@Entity
@Table(name = "admin_dashboard_metrics", uniqueConstraints = {
        @UniqueConstraint(name = "uq_admin_dashboard_metrics_tenant_admin", columnNames = {"tenant_id", "admin_user_id"})
})
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class AdminDashboardMetricsEntity extends TenantAwareEntity {

    @Column(name = "admin_user_id", nullable = false)
    private Long adminUserId;

    @Column(name = "team_match_score", precision = 3, scale = 2)
    private BigDecimal teamMatchScore;

    @Column(name = "sales_user_count")
    private Integer salesUserCount;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
