package com.grobird.psf.user.repository;

import com.grobird.psf.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByUsername(String username);

    Optional<UserEntity> findByEmail(String email);

    /** Sales users who report to the given admin (tenant filter applied by aspect). */
    List<UserEntity> findByReportedToUserId(Long reportedToUserId);

    /** All users with role ADMIN in the current tenant (for validation). */
    Optional<UserEntity> findByIdAndRole(Long id, String role);

    /** For unique employee ID per tenant when adding sales (tenant filter applies). */
    Optional<UserEntity> findByEmployeeId(String employeeId);

    /** Email must be unique per tenant (case-insensitive). */
    boolean existsByTenantIdAndEmailIgnoreCase(Long tenantId, String email);

    /** For Super Admin: list admins in an organization (tenantId = org id). */
    List<UserEntity> findByTenantIdAndRole(Long tenantId, String role);

    /** Count admins in a tenant. */
    long countByTenantIdAndRole(Long tenantId, String role);

    /** Distinct tenant ids that have at least one admin with lastLoginAt >= since. */
    @Query("SELECT DISTINCT u.tenantId FROM UserEntity u WHERE u.role = 'ADMIN' AND u.lastLoginAt >= :since AND u.tenantId IS NOT NULL")
    List<Long> findDistinctTenantIdsWithAdminActiveSince(Instant since);
}
