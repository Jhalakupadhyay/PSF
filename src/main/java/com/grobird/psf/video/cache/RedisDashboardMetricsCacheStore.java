package com.grobird.psf.video.cache;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@Profile("!local & !test")
public class RedisDashboardMetricsCacheStore implements DashboardMetricsCacheStore {

    private static final Duration TTL = Duration.ofHours(24);
    private static final String PREFIX = "dashboard:metrics:";

    private final StringRedisTemplate redis;

    public RedisDashboardMetricsCacheStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private static String key(Long tenantId, Long userId) {
        return PREFIX + tenantId + ":" + userId;
    }

    @Override
    public Optional<String> get(Long tenantId, Long userId) {
        return Optional.ofNullable(redis.opsForValue().get(key(tenantId, userId)));
    }

    @Override
    public void put(Long tenantId, Long userId, String json) {
        redis.opsForValue().set(key(tenantId, userId), json, TTL);
    }

    @Override
    public void invalidate(Long tenantId, Long userId) {
        redis.delete(key(tenantId, userId));
    }
}
