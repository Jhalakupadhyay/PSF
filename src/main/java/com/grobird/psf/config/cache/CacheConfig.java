package com.grobird.psf.config.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String ORGANIZATIONS_SUMMARY = "organizationsSummary";
    public static final int CACHE_TTL_MINUTES = 5;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(ORGANIZATIONS_SUMMARY);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(CACHE_TTL_MINUTES, TimeUnit.MINUTES)
                .maximumSize(100));
        return manager;
    }
}
