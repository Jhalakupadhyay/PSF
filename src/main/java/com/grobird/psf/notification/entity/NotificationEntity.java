package com.grobird.psf.notification.entity;

import com.grobird.psf.config.tenant.TenantAwareEntity;
import com.grobird.psf.video.entity.ReferenceVideoType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Entity
@Table(name = "notifications")
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class NotificationEntity extends TenantAwareEntity {

    /**
     * The user who receives this notification (can be sales user or admin).
     */
    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    /**
     * The sales user whose submission completed (for context in admin notifications).
     */
    @Column(name = "sales_user_id", nullable = false)
    private Long salesUserId;

    @Column(name = "opportunity_id", nullable = false)
    private Long opportunityId;

    @Column(name = "sales_submission_id", nullable = false)
    private Long salesSubmissionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 32)
    private ReferenceVideoType referenceType;

    @Column(name = "reference_video_id")
    private Long referenceVideoId;

    @Column(name = "reference_video_name", length = 255)
    private String referenceVideoName;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at")
    private Instant createdAt;
}
