package com.grobird.psf.qna.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Stores generated Q&A questions mapped to an opportunity.
 * Each row = one generation batch (context + AI questions as JSON).
 */
@Entity
@Table(name = "qna_questions")
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class QnaQuestionsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "opportunity_id", nullable = false)
    private Long opportunityId;

    /** JSON string of the full questions response from qna_info */
    @Column(name = "questions_json", nullable = false, columnDefinition = "TEXT")
    private String questionsJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
