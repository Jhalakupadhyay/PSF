package com.grobird.psf.video.entity;

import com.grobird.psf.config.tenant.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Admin reference video: golden pitch (one per tenant) or skillset (many per tenant).
 * Maps to pitch-analyzer via analyzer_video_id and analyzer_deck_id.
 * No processing state or result JSON — admin video is reference extraction only.
 */
@Entity
@Table(name = "reference_videos")
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ReferenceVideoEntity extends TenantAwareEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private ReferenceVideoType type;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "video_s3_key", length = 1024)
    private String videoS3Key;

    @Column(name = "analyzer_video_id", length = 36)
    private String analyzerVideoId;

    @Column(name = "analyzer_deck_id", length = 36)
    private String analyzerDeckId;

    @Column(name = "is_processed", nullable = false)
    private boolean processed;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
