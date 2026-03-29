package com.grobird.psf.video.cache;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

@Component
@Profile("!local & !test")
public class RedisAdminPitchUploadStatsCacheStore implements AdminPitchUploadStatsCacheStore {

    private static final Duration TTL = Duration.ofHours(1);
    private static final String PREFIX = "admin:pitch-stats:";

    private final StringRedisTemplate redis;

    public RedisAdminPitchUploadStatsCacheStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private static String key(Long tenantId, Long adminUserId, String type, Long salesUserId) {
        String userPart = salesUserId != null ? salesUserId.toString() : "team";
        return PREFIX + tenantId + ":" + adminUserId + ":" + type + ":" + userPart;
    }

    private static String patternForAdmin(Long tenantId, Long adminUserId) {
        return PREFIX + tenantId + ":" + adminUserId + ":*";
    }

    @Override
    public Optional<String> get(Long tenantId, Long adminUserId, String type, Long salesUserId) {
        return Optional.ofNullable(redis.opsForValue().get(key(tenantId, adminUserId, type, salesUserId)));
    }

    @Override
    public void put(Long tenantId, Long adminUserId, String type, Long salesUserId, String json) {
        redis.opsForValue().set(key(tenantId, adminUserId, type, salesUserId), json, TTL);
    }

    @Override
    public void invalidateAllForAdmin(Long tenantId, Long adminUserId) {
        Set<String> keys = redis.keys(patternForAdmin(tenantId, adminUserId));
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }
}
