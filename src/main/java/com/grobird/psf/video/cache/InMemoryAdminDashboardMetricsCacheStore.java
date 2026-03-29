package com.grobird.psf.video.cache;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("local | test")
public class InMemoryAdminDashboardMetricsCacheStore implements AdminDashboardMetricsCacheStore {

    private final ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();

    private static String key(Long tenantId, Long adminUserId) {
        return "admin:dashboard:" + tenantId + ":" + adminUserId;
    }

    @Override
    public Optional<String> get(Long tenantId, Long adminUserId) {
        return Optional.ofNullable(store.get(key(tenantId, adminUserId)));
    }

    @Override
    public void put(Long tenantId, Long adminUserId, String json) {
        store.put(key(tenantId, adminUserId), json);
    }

    @Override
    public void invalidate(Long tenantId, Long adminUserId) {
        store.remove(key(tenantId, adminUserId));
    }
}
