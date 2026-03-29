package com.grobird.psf.video.entity;

import com.grobird.psf.config.tenant.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Sales video submission: one per upload, linked to a reference_video for comparison.
 * Full result from pitch-analyzer stored in fullResult (JSON).
 */
@Entity
@Table(name = "sales_submissions")
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class SalesSubmissionEntity extends TenantAwareEntity {

    @Column(name = "opportunity_id", nullable = false)
    private Long opportunityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reference_video_id", nullable = false)
    private ReferenceVideoEntity referenceVideo;

    @Column(name = "video_s3_key", nullable = false, length = 1024)
    private String videoS3Key;

    @Column(name = "analyzer_video_id", length = 36)
    private String analyzerVideoId;

    @Column(name = "analyzer_analysis_id", length = 36)
    private String analyzerAnalysisId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SalesSubmissionStatus status;

    @Column(name = "overall_score", precision = 5, scale = 2)
    private BigDecimal overallScore;

    @Column(name = "comparison_score", precision = 5, scale = 2)
    private BigDecimal comparisonScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "full_result", columnDefinition = "jsonb")
    private Map<String, Object> fullResult;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
