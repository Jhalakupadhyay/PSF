package com.grobird.psf.video.cache;

import java.util.Optional;

/**
 * Cache for per-admin team dashboard metrics (JSON).
 * Redis in production; in-memory for local/test.
 */
public interface AdminDashboardMetricsCacheStore {

    Optional<String> get(Long tenantId, Long adminUserId);

    void put(Long tenantId, Long adminUserId, String json);

    void invalidate(Long tenantId, Long adminUserId);
}
