package com.grobird.psf.user.entity;

import com.grobird.psf.config.tenant.TenantAwareEntity;
import com.grobird.psf.user.enums.InvitationStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Entity
@Table(name = "users")
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class UserEntity extends TenantAwareEntity {

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "role", nullable = false)
    private String role;

    /**
     * For SALES users: the user id of the ADMIN they report to (same tenant).
     * Null for ADMIN and SUPER_ADMIN.
     */
    @Column(name = "reported_to_user_id")
    private Long reportedToUserId;

    // ── Sales-specific fields (when role is SALES) ─────────────────────────
    @Column(name = "contact_number")
    private String contactNumber;

    @Column(name = "department")
    private String department;

    @Column(name = "employee_id")
    private String employeeId;

    /** Last login time; used to compute active organizations (admin active in last 7 days). */
    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /**
     * For SALES users: tracks whether they have accepted the invitation by logging in.
     * PENDING = email sent, awaiting first login; ACCEPTED = user has logged in.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "invitation_status", length = 20)
    private InvitationStatus invitationStatus;

    /** When the user account was created. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
