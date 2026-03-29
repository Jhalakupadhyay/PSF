package com.grobird.psf.user.service;

import com.grobird.psf.config.security.UserPrincipal;
import com.grobird.psf.config.tenant.TenantContext;
import com.grobird.psf.mail.service.EmailService;
import com.grobird.psf.opportunity.repository.OpportunityRepository;
import com.grobird.psf.organization.repository.OrganizationRepository;
import com.grobird.psf.user.dto.AddSalesRequest;
import com.grobird.psf.user.dto.SalesUserDetailResponse;
import com.grobird.psf.user.dto.UserDTO;
import com.grobird.psf.user.entity.UserEntity;
import com.grobird.psf.user.enums.InvitationStatus;
import com.grobird.psf.user.enums.Role;
import com.grobird.psf.user.repository.UserRepository;
import com.grobird.psf.video.repository.SalesDashboardMetricsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#$%";
    private static final int PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OrganizationRepository organizationRepository;
    private final EmailService emailService;
    private final SalesDashboardMetricsRepository salesDashboardMetricsRepository;
    private final OpportunityRepository opportunityRepository;

    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       OrganizationRepository organizationRepository, EmailService emailService,
                       SalesDashboardMetricsRepository salesDashboardMetricsRepository,
                       OpportunityRepository opportunityRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.organizationRepository = organizationRepository;
        this.emailService = emailService;
        this.salesDashboardMetricsRepository = salesDashboardMetricsRepository;
        this.opportunityRepository = opportunityRepository;
    }

    /**
     * Creates a user. Validates principal is present, then delegates to createUser(dto, userId, role).
     */
    public UserDTO createUser(UserDTO dto, UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return createUser(dto, principal.getUserId(), principal.getRole());
    }

    /**
     * Admin adds a sales person under them.
     * Generates random password and sends it via email BEFORE creating the user.
     * If email fails, the user is NOT created.
     */
    public UserDTO addSales(AddSalesRequest request, UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (!Role.ADMIN.name().equalsIgnoreCase(principal.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admin can add sales");
        }

        String randomPassword = generateRandomPassword();

        try {
            emailService.sendWelcomeEmail(request.getEmail(), randomPassword, request.getFirstName());
        } catch (MailException e) {
            log.error("Failed to send welcome email to {}: {}", request.getEmail(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Failed to send invitation email. User not created. Please verify the email address and try again.");
        }

        UserDTO dto = UserDTO.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(randomPassword)
                .contactNumber(request.getContactNumber())
                .department(request.getDepartment())
                .employeeId(request.getEmployeeId())
                .role(Role.SALES.name())
                .reportedToUserId(principal.getUserId())
                .invitationStatus(InvitationStatus.PENDING)
                .build();
        return createUser(dto, principal.getUserId(), principal.getRole());
    }

    private String generateRandomPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            password.append(PASSWORD_CHARS.charAt(random.nextInt(PASSWORD_CHARS.length())));
        }
        return password.toString();
    }

    /**
     * Lists sales under the current admin. Validates principal is ADMIN.
     */
    public List<UserDTO> listMySales(UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (!Role.ADMIN.name().equalsIgnoreCase(principal.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admin can list their sales");
        }
        return findSalesByReportedToUserId(principal.getUserId());
    }

    /**
     * Lists all users across tenants. Validates principal is SUPER_ADMIN.
     */
    public List<UserDTO> listAllUsers(UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authentication required");
        }
        if (!Role.SUPER_ADMIN.name().equalsIgnoreCase(principal.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Super Admin can list all users");
        }
        return listAllUsersForSuperAdmin();
    }

    /**
     * Creates a user. Only ADMIN and SALES can be created via API (SUPER_ADMIN is created manually).
     * - SUPER_ADMIN: can create ADMIN in any tenant (must pass tenantId in dto).
     * - ADMIN: can create only SALES under themselves (reportedToUserId = current user), in current tenant.
     *
     * @param currentUserId id of the user performing the create (null if no auth)
     * @param currentUserRole role of the current user (SUPER_ADMIN, ADMIN, or null)
     */
    public UserDTO createUser(UserDTO dto, Long currentUserId, String currentUserRole) {
        // Only these two roles are allowed via API
        if (!Role.isCreatableViaApi(dto.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Role must be ADMIN or SALES.");
        }

        String role = Role.fromString(dto.getRole()).name();

        Long tenantId;

        if (Role.SUPER_ADMIN.name().equals(currentUserRole)) {
            // Super Admin: can create ADMIN in any tenant; tenantId (organization id) required in dto
            if (Role.ADMIN.name().equals(role)) {
                if (dto.getTenantId() == null || dto.getTenantId().isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenantId (organization id) required when creating ADMIN as Super Admin");
                }
                tenantId = Long.parseLong(dto.getTenantId().trim());
                if (!organizationRepository.existsById(tenantId)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Organization not found for tenantId: " + tenantId);
                }
            } else {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Super Admin can only create ADMIN users (with tenantId).");
            }
        } else {
            // ADMIN (or unauthenticated): use current tenant; only ADMIN and SALES creatable
            tenantId = TenantContext.getTenantId();
            if (tenantId == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No tenant context available");
            }
        }

        // Email must be unique per tenant (so Super Admin cannot create duplicate admin in same tenant)
        String email = dto.getEmail().trim();
        if (userRepository.existsByTenantIdAndEmailIgnoreCase(tenantId, email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Email already registered in this tenant. Use a different email.");
        }

        UserEntity user;
        if (Role.SALES.name().equals(role)) {
            Long adminId = dto.getReportedToUserId();
            if (adminId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SALES user must have reportedToUserId (admin id)");
            }
            if (currentUserId == null || !currentUserId.equals(adminId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the admin a sales user reports to can create that sales user");
            }
            validateSalesFields(dto);
            if (dto.getPassword() == null || dto.getPassword().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Initial password is required for sales");
            }
            String username = ((dto.getFirstName() != null ? dto.getFirstName() : "").trim() + " " + (dto.getLastName() != null ? dto.getLastName() : "").trim()).trim();
            userRepository.findByEmployeeId(dto.getEmployeeId()).ifPresent(existing ->
                    { throw new ResponseStatusException(HttpStatus.CONFLICT, "Employee ID already exists in this tenant"); }
            );
            UserEntity admin = userRepository.findByIdAndRole(adminId, Role.ADMIN.name())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reported-to user must exist and have role ADMIN"));
            user = UserEntity.builder()
                    .tenantId(tenantId)
                    .username(username.isBlank() ? email : username)
                    .email(email)
                    .password(passwordEncoder.encode(dto.getPassword()))
                    .role(role)
                    .reportedToUserId(admin.getId())
                    .contactNumber(dto.getContactNumber())
                    .department(dto.getDepartment())
                    .employeeId(dto.getEmployeeId())
                    .invitationStatus(dto.getInvitationStatus() != null ? dto.getInvitationStatus() : InvitationStatus.PENDING)
                    .createdAt(Instant.now())
                    .build();
        } else {
            if (dto.getPassword() == null || dto.getPassword().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");
            }
            user = UserEntity.builder()
                    .tenantId(tenantId)
                    .username(dto.getName() != null ? dto.getName() : email)
                    .email(email)
                    .password(passwordEncoder.encode(dto.getPassword()))
                    .role(role)
                    .reportedToUserId(null)
                    .createdAt(Instant.now())
                    .build();
        }

        UserEntity saved = userRepository.save(user);

        return toDTO(saved);
    }

    private void validateSalesFields(UserDTO dto) {
        if (dto.getContactNumber() == null || dto.getContactNumber().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contact number is required for sales");
        if (dto.getDepartment() == null || dto.getDepartment().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Department is required for sales");
        if (dto.getEmployeeId() == null || dto.getEmployeeId().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Employee ID is required for sales");
    }

    /**
     * Finds a user by username. The Hibernate tenant filter ensures
     * only users in the current tenant are visible.
     */
    public UserDTO findByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(this::toDTO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    /**
     * Lists sales users that report to the given admin user id.
     * Tenant filter applies (unless caller is Super Admin with no tenant).
     */
    public List<UserDTO> findSalesByReportedToUserId(Long adminUserId) {
        return userRepository.findByReportedToUserId(adminUserId).stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * Lists all users across all tenants. Only valid when tenant context is null (Super Admin).
     * Super Admin is the only role with null tenant and can see every tenant and users under them.
     */
    public List<UserDTO> listAllUsersForSuperAdmin() {
        if (TenantContext.getTenantId() != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Super Admin can list all users across tenants");
        }
        return userRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * Admin resets password for a sales user under them.
     * Generates new random password, sends email, and resets invitation status to PENDING.
     *
     * @param salesUserId the id of the sales user to reset
     * @param principal the admin principal
     * @throws ResponseStatusException if validation fails or email cannot be sent
     */
    public void adminResetSalesPassword(Long salesUserId, UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (!Role.ADMIN.name().equalsIgnoreCase(principal.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admin can reset sales user passwords");
        }

        UserEntity salesUser = userRepository.findById(salesUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sales user not found"));

        if (!Role.SALES.name().equals(salesUser.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Can only reset password for sales users");
        }

        if (!principal.getUserId().equals(salesUser.getReportedToUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Can only reset password for sales users under your team");
        }

        String newPassword = generateRandomPassword();

        try {
            String firstName = salesUser.getUsername().split(" ")[0];
            emailService.sendPasswordResetEmail(salesUser.getEmail(), newPassword, firstName);
        } catch (MailException e) {
            log.error("Failed to send password reset email to {}: {}", salesUser.getEmail(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Failed to send password reset email. Password not changed.");
        }

        salesUser.setPassword(passwordEncoder.encode(newPassword));
        salesUser.setInvitationStatus(InvitationStatus.PENDING);
        userRepository.save(salesUser);

        log.info("Admin {} reset password for sales user {}", principal.getUsername(), salesUser.getEmail());
    }

    /**
     * Lists detailed information for all sales users under the admin.
     * Includes: name, department, email, phone, createdAt, invitationStatus, averageScore, latestOpportunityDate.
     */
    public List<SalesUserDetailResponse> listMySalesDetails(UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (!Role.ADMIN.name().equalsIgnoreCase(principal.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admin can list their sales details");
        }

        List<UserEntity> salesUsers = userRepository.findByReportedToUserId(principal.getUserId());

        if (salesUsers.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> salesUserIds = salesUsers.stream().map(UserEntity::getId).toList();

        Map<Long, BigDecimal> averageScores = salesDashboardMetricsRepository
                .findByAdminUserId(principal.getUserId())
                .stream()
                .filter(m -> m.getAverageScore() != null)
                .collect(Collectors.toMap(
                        m -> m.getUserId(),
                        m -> m.getAverageScore(),
                        (a, b) -> a
                ));

        Map<Long, Instant> latestOpportunityDates = opportunityRepository
                .findLatestOpportunityDatesBySalesUserIds(salesUserIds)
                .stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Instant) row[1],
                        (a, b) -> a
                ));

        return salesUsers.stream()
                .map(user -> SalesUserDetailResponse.builder()
                        .id(user.getId())
                        .name(user.getUsername())
                        .department(user.getDepartment())
                        .email(user.getEmail())
                        .phoneNumber(user.getContactNumber())
                        .createdAt(user.getCreatedAt())
                        .invitationStatus(user.getInvitationStatus())
                        .averageScore(averageScores.get(user.getId()))
                        .latestOpportunityDate(latestOpportunityDates.get(user.getId()))
                        .build())
                .toList();
    }

    // ── mapper ───────────────────────────────────────────────────────

    private UserDTO toDTO(UserEntity user) {
        return UserDTO.builder()
                .id(user.getId())
                .tenantId(user.getTenantId() != null ? user.getTenantId().toString() : null)
                .name(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .reportedToUserId(user.getReportedToUserId())
                .contactNumber(user.getContactNumber())
                .department(user.getDepartment())
                .employeeId(user.getEmployeeId())
                .invitationStatus(user.getInvitationStatus())
                .build();
    }
}
