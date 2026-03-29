package com.grobird.psf.video.cache;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@Profile("!local & !test")
public class RedisPSectionCacheStore implements PSectionCacheStore {

    private static final Duration TTL = Duration.ofHours(24);
    private static final String GOLDEN_PREFIX = "psection:golden:";
    private static final String SKILLSET_PREFIX = "psection:skillset:";

    private final StringRedisTemplate redis;

    public RedisPSectionCacheStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Optional<String> getSalesGoldenResult(Long opportunityId) {
        return Optional.ofNullable(redis.opsForValue().get(GOLDEN_PREFIX + opportunityId));
    }

    @Override
    public void putSalesGoldenResult(Long opportunityId, String resultJson) {
        redis.opsForValue().set(GOLDEN_PREFIX + opportunityId, resultJson, TTL);
    }

    @Override
    public Optional<String> getSalesSkillsetResult(Long opportunityId, Long referenceVideoId) {
        return Optional.ofNullable(redis.opsForValue().get(SKILLSET_PREFIX + opportunityId + ":" + referenceVideoId));
    }

    @Override
    public void putSalesSkillsetResult(Long opportunityId, Long referenceVideoId, String resultJson) {
        redis.opsForValue().set(SKILLSET_PREFIX + opportunityId + ":" + referenceVideoId, resultJson, TTL);
    }
}
