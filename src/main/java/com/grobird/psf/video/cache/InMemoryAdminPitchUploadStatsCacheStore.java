package com.grobird.psf.video.cache;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("local | test")
public class InMemoryAdminPitchUploadStatsCacheStore implements AdminPitchUploadStatsCacheStore {

    private final ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();

    private static String key(Long tenantId, Long adminUserId, String type, Long salesUserId) {
        String userPart = salesUserId != null ? salesUserId.toString() : "team";
        return "admin:pitch-stats:" + tenantId + ":" + adminUserId + ":" + type + ":" + userPart;
    }

    private static String prefixForAdmin(Long tenantId, Long adminUserId) {
        return "admin:pitch-stats:" + tenantId + ":" + adminUserId + ":";
    }

    @Override
    public Optional<String> get(Long tenantId, Long adminUserId, String type, Long salesUserId) {
        return Optional.ofNullable(store.get(key(tenantId, adminUserId, type, salesUserId)));
    }

    @Override
    public void put(Long tenantId, Long adminUserId, String type, Long salesUserId, String json) {
        store.put(key(tenantId, adminUserId, type, salesUserId), json);
    }

    @Override
    public void invalidateAllForAdmin(Long tenantId, Long adminUserId) {
        String prefix = prefixForAdmin(tenantId, adminUserId);
        store.keySet().removeIf(k -> k.startsWith(prefix));
    }
}
