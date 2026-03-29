package com.grobird.psf.video.cache;

import java.util.Optional;

/**
 * Cache for admin pitch upload stats (JSON).
 * Redis in production; in-memory for local/test.
 * Key pattern: admin:pitch-stats:{tenantId}:{adminUserId}:{type}:{salesUserId|"team"}
 */
public interface AdminPitchUploadStatsCacheStore {

    Optional<String> get(Long tenantId, Long adminUserId, String type, Long salesUserId);

    void put(Long tenantId, Long adminUserId, String type, Long salesUserId, String json);

    void invalidateAllForAdmin(Long tenantId, Long adminUserId);
}
