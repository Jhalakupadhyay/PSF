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
 * Per-sales-user aggregated dashboard metrics from completed pitch submissions.
 * One row per (tenant_id, user_id); updated when a new submission completes.
 */
@Entity
@Table(name = "sales_dashboard_metrics", uniqueConstraints = {
        @UniqueConstraint(name = "uq_sales_dashboard_metrics_tenant_user", columnNames = {"tenant_id", "user_id"})
})
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class SalesDashboardMetricsEntity extends TenantAwareEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "vocal_delivery_avg", precision = 5, scale = 2)
    private BigDecimal vocalDeliveryAvg;

    @Column(name = "confidence_index_avg", precision = 5, scale = 2)
    private BigDecimal confidenceIndexAvg;

    @Column(name = "facial_engagement_avg", precision = 5, scale = 2)
    private BigDecimal facialEngagementAvg;

    @Column(name = "content_quality_avg", precision = 5, scale = 2)
    private BigDecimal contentQualityAvg;

    @Column(name = "speech_fluency_avg", precision = 5, scale = 2)
    private BigDecimal speechFluencyAvg;

    @Column(name = "audience_engagement_avg", precision = 5, scale = 2)
    private BigDecimal audienceEngagementAvg;

    @Column(name = "submission_count", nullable = false)
    private int submissionCount;

    @Column(name = "average_score", precision = 3, scale = 2)
    private BigDecimal averageScore;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
