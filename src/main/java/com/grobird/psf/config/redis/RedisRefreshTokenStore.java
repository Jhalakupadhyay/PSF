package com.grobird.psf.config.redis;

import com.grobird.psf.user.dto.UserDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
@Profile("!local & !test")
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String KEY_PREFIX = "refresh:";

    private final StringRedisTemplate redis;
    private final HashOperations<String, String, String> hashOps;
    private final long refreshExpirationMillis;

    public RedisRefreshTokenStore(
            StringRedisTemplate redis,
            @Value("${jwt.refresh-token-expiration-ms}") long refreshExpirationMillis
    ) {
        this.redis                   = redis;
        this.hashOps                 = redis.opsForHash();
        this.refreshExpirationMillis = refreshExpirationMillis;
    }

    @Override
    public void save(String tokenHash, UserDTO user) {
        String key = KEY_PREFIX + tokenHash;
        String tenantIdStr = (user.getTenantId() == null || user.getTenantId().isBlank())
                ? "" : user.getTenantId();
        hashOps.putAll(key, Map.of(
                "userId",   String.valueOf(user.getId()),
                "tenantId", tenantIdStr,
                "email",    user.getEmail(),
                "role",     user.getRole() != null ? user.getRole() : ""
        ));
        redis.expire(key, Duration.ofMillis(refreshExpirationMillis));
    }

    @Override
    public Optional<Map<String, Object>> findByTokenHash(String tokenHash) {
        String key = KEY_PREFIX + tokenHash;
        Map<String, String> raw = hashOps.entries(key);
        if (raw.isEmpty()) return Optional.empty();
        String tenantStr = raw.get("tenantId");
        Long tenantId = (tenantStr == null || tenantStr.isBlank()) ? null : Long.parseLong(tenantStr);
        Map<String, Object> data = new HashMap<>();
        data.put("userId", Long.parseLong(raw.get("userId")));
        data.put("tenantId", tenantId);
        data.put("email", raw.get("email"));
        data.put("role", raw.getOrDefault("role", ""));
        return Optional.of(data);
    }

    @Override
    public void revoke(String tokenHash) {
        redis.delete(KEY_PREFIX + tokenHash);
    }
}
