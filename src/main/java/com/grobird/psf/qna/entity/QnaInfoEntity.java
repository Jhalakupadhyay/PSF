package com.grobird.psf.qna.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Stores company info (AI summary, board decisions, news, etc.) mapped to an opportunity.
 * One row per opportunity; updated when regenerated.
 */
@Entity
@Table(name = "qna_info")
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class QnaInfoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "opportunity_id", nullable = false, unique = true)
    private Long opportunityId;

    /** JSON string of the full company info response from qna_info */
    @Column(name = "info_json", nullable = false, columnDefinition = "TEXT")
    private String infoJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
