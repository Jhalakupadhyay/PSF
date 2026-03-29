package com.grobird.psf.video.cache;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@Profile("!local & !test")
public class RedisAdminDashboardMetricsCacheStore implements AdminDashboardMetricsCacheStore {

    private static final Duration TTL = Duration.ofHours(24);
    private static final String PREFIX = "admin:dashboard:";

    private final StringRedisTemplate redis;

    public RedisAdminDashboardMetricsCacheStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private static String key(Long tenantId, Long adminUserId) {
        return PREFIX + tenantId + ":" + adminUserId;
    }

    @Override
    public Optional<String> get(Long tenantId, Long adminUserId) {
        return Optional.ofNullable(redis.opsForValue().get(key(tenantId, adminUserId)));
    }

    @Override
    public void put(Long tenantId, Long adminUserId, String json) {
        redis.opsForValue().set(key(tenantId, adminUserId), json, TTL);
    }

    @Override
    public void invalidate(Long tenantId, Long adminUserId) {
        redis.delete(key(tenantId, adminUserId));
    }
}
