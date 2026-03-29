package com.grobird.psf.video.cache;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("local | test")
public class InMemoryDashboardMetricsCacheStore implements DashboardMetricsCacheStore {

    private final ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();

    private static String key(Long tenantId, Long userId) {
        return "dashboard:metrics:" + tenantId + ":" + userId;
    }

    @Override
    public Optional<String> get(Long tenantId, Long userId) {
        return Optional.ofNullable(store.get(key(tenantId, userId)));
    }

    @Override
    public void put(Long tenantId, Long userId, String json) {
        store.put(key(tenantId, userId), json);
    }

    @Override
    public void invalidate(Long tenantId, Long userId) {
        store.remove(key(tenantId, userId));
    }
}
