package com.grobird.psf.config.redis;

import com.grobird.psf.user.dto.UserDTO;

import java.util.Optional;

/**
 * Abstraction for storing refresh token hashes and user data.
 * Redis implementation for production; in-memory for local profile (no Redis).
 */
public interface RefreshTokenStore {

    void save(String tokenHash, UserDTO user);

    Optional<java.util.Map<String, Object>> findByTokenHash(String tokenHash);

    void revoke(String tokenHash);
}
