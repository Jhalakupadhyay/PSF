package com.grobird.psf.config.tenant;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

/**
 * Base entity for all tenant-scoped tables.
 *
 * @FilterDef   — declares a reusable filter with a named parameter
 * @Filter      — activates that filter on this entity's queries
 *
 * The filter is enabled per-session in TenantFilterAspect — see below.
 */
@MappedSuperclass
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@FilterDef(
        name   = "tenantFilter",
        parameters = @ParamDef(name = "tenantId", type = long.class)
)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public abstract class TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null only for SUPER_ADMIN user; all other entities must have a tenant. */
    @Column(name = "tenant_id")
    private Long tenantId;
}
