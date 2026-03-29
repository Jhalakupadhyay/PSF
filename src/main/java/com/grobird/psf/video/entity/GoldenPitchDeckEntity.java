package com.grobird.psf.video.entity;

import com.grobird.psf.config.tenant.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Entity
@Table(name = "golden_pitch_deck")
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class GoldenPitchDeckEntity extends TenantAwareEntity {

    @Column(name = "video_s3_key", length = 1024)
    private String videoS3Key;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
