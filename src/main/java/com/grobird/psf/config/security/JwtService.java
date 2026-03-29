package com.grobird.psf.config.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;

import com.grobird.psf.config.redis.RefreshTokenStore;
import com.grobird.psf.user.dto.UserDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final String secret;
    private final long expirationMillis;
    private final RefreshTokenStore refreshTokenStore;
    private final long refreshExpirationMillis;

    public JwtService(@Value("${jwt.secret}") String secret, @Value("${jwt.access-token-expiration-ms}") long expirationMillis,
                      RefreshTokenStore refreshTokenStore, @Value("${jwt.refresh-token-expiration-ms}") long refreshExpirationMillis) {
        this.secret = secret;
        this.expirationMillis = expirationMillis;
        this.refreshTokenStore = refreshTokenStore;
        this.refreshExpirationMillis = refreshExpirationMillis;
    }

    public String generateToken(UserDTO user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        if (user.getTenantId() != null && !user.getTenantId().isBlank()) {
            claims.put("tenantId", Long.parseLong(user.getTenantId()));
        } else {
            claims.put("tenantId", null); // SUPER_ADMIN has no tenant
        }
        claims.put("role", user.getRole());
        long now = System.currentTimeMillis();
        Date issuedAt = new Date(now);
        Date expiry = new Date(now + expirationMillis);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getEmail())
                .setIssuedAt(issuedAt)
                .setExpiration(expiry)
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(encodeSecret(secret))), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Generates a cryptographically random opaque token, persists only
     * its SHA-256 hash in Redis, and returns the raw token to the caller.
     */
    public String generateRefreshToken(UserDTO user) {
        String raw   = Base64.getUrlEncoder().encodeToString(new SecureRandom().generateSeed(40));
        String hash  = TokenHashUtil.sha256(raw);

        refreshTokenStore.save(hash, user);

        return raw;   // only the caller (response body) ever sees this
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.getExpiration() != null && claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Validates an incoming refresh token:
     *   1. Hash it.
     *   2. Look up the hash in Redis.  If missing → expired or revoked.
     *   3. Return the stored claims so the caller can issue new tokens.
     */
    public Optional<Map<String, Object>> validateRefreshToken(String raw) {
        String hash = TokenHashUtil.sha256(raw);
        return refreshTokenStore.findByTokenHash(hash);
    }

    /**
     * Revokes a single refresh token (called after successful rotation
     * so the old token can never be reused).
     */
    public void revokeRefreshToken(String raw) {
        refreshTokenStore.revoke(TokenHashUtil.sha256(raw));
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(Decoders.BASE64.decode(encodeSecret(secret))))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Produces a key suitable for HS256: at least 256 bits (32 bytes).
     * If the secret is valid Base64 and decodes to >= 32 bytes, use it as-is.
     * Otherwise use SHA-256(secret bytes) so any length secret is safe (RFC 7518).
     */
    private byte[] secretKeyBytes(String raw) {
        byte[] decoded;
        try {
            decoded = Decoders.BASE64.decode(raw);
        } catch (Exception e) {
            decoded = raw.getBytes(StandardCharsets.UTF_8);
        }
        if (decoded.length >= 32) {
            return decoded;
        }
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return sha.digest(raw.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private String encodeSecret(String raw) {
        // For jjwt we need to pass base64; we now use secretKeyBytes for the actual key
        return java.util.Base64.getEncoder().encodeToString(secretKeyBytes(raw));
    }
}
