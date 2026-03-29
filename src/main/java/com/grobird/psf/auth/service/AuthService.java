package com.grobird.psf.auth.service;

import com.grobird.psf.auth.dto.LoginRequest;
import com.grobird.psf.auth.dto.TokenPairResponse;
import com.grobird.psf.config.security.JwtService;
import com.grobird.psf.user.dto.UserDTO;
import com.grobird.psf.user.entity.UserEntity;
import com.grobird.psf.user.enums.InvitationStatus;
import com.grobird.psf.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Authenticate by email and password; returns access and refresh tokens.
     * Updates lastLoginAt for "active organization" dashboard stats.
     * For SALES users with PENDING invitation status, updates to ACCEPTED on first login.
     */
    @Transactional
    public TokenPairResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByEmail(request.getEmail().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        user.setLastLoginAt(Instant.now());

        if (user.getInvitationStatus() == InvitationStatus.PENDING) {
            user.setInvitationStatus(InvitationStatus.ACCEPTED);
            log.info("User {} accepted invitation on first login", user.getEmail());
        }

        userRepository.save(user);
        UserDTO dto = toUserDTO(user);
        String accessToken = jwtService.generateToken(dto);
        String refreshToken = jwtService.generateRefreshToken(dto);
        return TokenPairResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(dto)
                .build();
    }

    /**
     * Reset password for the current authenticated user.
     * @param userId the user id of the authenticated user
     * @param currentPassword the user's current password
     * @param newPassword the new password to set
     */
    @Transactional
    public void resetOwnPassword(Long userId, String currentPassword, String newPassword) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("User {} reset their own password", user.getEmail());
    }

    private UserDTO toUserDTO(UserEntity user) {
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
