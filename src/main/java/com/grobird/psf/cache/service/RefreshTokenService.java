package com.grobird.psf.cache.service;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

@Service
@Profile("!local & !test")
public class RefreshTokenService {

    private final StringRedisTemplate redis;
    private final long ttlSeconds;

    public RefreshTokenService(
            StringRedisTemplate redis,
            @Value("${jwt.refresh-token-expiration-ms}") long ttlSeconds
    ) {
        this.redis = redis;
        this.ttlSeconds = ttlSeconds;
    }

    public void save(String key, String value) {
        redis.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
    }

    public String get(String key) {
        return redis.opsForValue().get(key);
    }

    public void delete(String key) {
        redis.delete(key);
    }
}

