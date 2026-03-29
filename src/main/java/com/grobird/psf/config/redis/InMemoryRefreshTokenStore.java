package com.grobird.psf.config.redis;

import com.grobird.psf.user.dto.UserDTO;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory refresh token store for local profile (no Redis).
 * Tokens are lost on restart.
 */
@Component
@Profile("local | test")
public class InMemoryRefreshTokenStore implements RefreshTokenStore {

    private final Map<String, Map<String, Object>> store = new ConcurrentHashMap<>();

    @Override
    public void save(String tokenHash, UserDTO user) {
        String tenantIdStr = (user.getTenantId() == null || user.getTenantId().isBlank()) ? "" : user.getTenantId();
        store.put(tokenHash, Map.of(
                "userId", String.valueOf(user.getId()),
                "tenantId", tenantIdStr,
                "email", user.getEmail(),
                "role", user.getRole() != null ? user.getRole() : ""
        ));
    }

    @Override
    public Optional<Map<String, Object>> findByTokenHash(String tokenHash) {
        Map<String, Object> raw = store.get(tokenHash);
        if (raw == null) return Optional.empty();
        String tenantStr = (String) raw.get("tenantId");
        Long tenantId = (tenantStr == null || tenantStr.isBlank()) ? null : Long.parseLong(tenantStr);
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("userId", Long.parseLong((String) raw.get("userId")));
        data.put("tenantId", tenantId);
        data.put("email", raw.get("email"));
        data.put("role", raw.getOrDefault("role", ""));
        return Optional.of(data);
    }

    @Override
    public void revoke(String tokenHash) {
        store.remove(tokenHash);
    }
}
