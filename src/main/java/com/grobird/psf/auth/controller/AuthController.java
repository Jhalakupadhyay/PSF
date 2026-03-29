package com.grobird.psf.auth.controller;

import com.grobird.psf.auth.dto.LoginRequest;
import com.grobird.psf.auth.dto.LogoutRequest;
import com.grobird.psf.auth.dto.RefreshTokenRequest;
import com.grobird.psf.auth.dto.ResetPasswordRequest;
import com.grobird.psf.auth.dto.TokenPairResponse;
import com.grobird.psf.auth.service.AuthService;
import com.grobird.psf.config.security.JwtService;
import com.grobird.psf.config.security.UserPrincipal;
import com.grobird.psf.user.dto.UserDTO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("api/v1/auth")
public class AuthController {

    private final JwtService jwtService;
    private final AuthService authService;

    public AuthController(JwtService jwtService, AuthService authService) {
        this.jwtService = jwtService;
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenPairResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenPairResponse tokens = authService.login(request);
        return ResponseEntity.ok(tokens);
    }

    /**
     * Exchange a valid refresh token for a new access token and a new refresh token (rotation).
     * Call this when the access token expires. The client should store the new tokens and use
     * the new refresh token for the next refresh; the old refresh token is revoked.
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenPairResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        String raw = request.getRefreshToken().trim();
        return jwtService.validateRefreshToken(raw)
                .map(claims -> {
                    UserDTO user = userFromRefreshClaims(claims);
                    jwtService.revokeRefreshToken(raw);
                    String accessToken = jwtService.generateToken(user);
                    String newRefreshToken = jwtService.generateRefreshToken(user);
                    return ResponseEntity.ok(TokenPairResponse.builder()
                            .accessToken(accessToken)
                            .refreshToken(newRefreshToken)
                            .user(user)
                            .build());
                })
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token"));
    }

    /**
     * Revoke the refresh token so it can no longer be used to obtain new access tokens.
     * Client should clear both access and refresh tokens from storage after calling this.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        jwtService.revokeRefreshToken(request.getRefreshToken().trim());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/signup")
    public ResponseEntity<TokenPairResponse> signup(@RequestBody UserDTO userDTO) {
        String accessToken = jwtService.generateToken(userDTO);
        String refreshToken = jwtService.generateRefreshToken(userDTO);
        return ResponseEntity.ok(
                TokenPairResponse.builder()
                        .accessToken(accessToken)
                        .refreshToken(refreshToken)
                        .build());
    }

    /**
     * Reset password for the currently authenticated user.
     * Both ADMIN and SALES users can reset their own password.
     */
    @PostMapping("/reset-password")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES')")
    public ResponseEntity<Map<String, String>> resetOwnPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        authService.resetOwnPassword(principal.getUserId(), request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
    }

    private static UserDTO userFromRefreshClaims(Map<String, Object> claims) {
        Long userId = (Long) claims.get("userId");
        Long tenantId = (Long) claims.get("tenantId");
        String email = (String) claims.get("email");
        String role = (String) claims.get("role");
        return UserDTO.builder()
                .id(userId)
                .tenantId(tenantId != null ? tenantId.toString() : null)
                .name(email)
                .email(email)
                .role(role != null ? role : "")
                .build();
    }
}
