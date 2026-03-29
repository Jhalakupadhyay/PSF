package com.grobird.psf.opportunity.entity;

import com.grobird.psf.config.tenant.TenantAwareEntity;
import com.grobird.psf.user.entity.UserEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Entity
@Table(name = "opportunities")
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class OpportunityEntity extends TenantAwareEntity {

    @Column(name = "industry", nullable = false)
    private String industry;

    @Column(name = "company", nullable = false)
    private String company;

    /**
     * Each opportunity is tied to exactly one sales user (many opportunities per sales).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_user_id", nullable = false)
    private UserEntity sales;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
