package com.grobird.psf.video.cache;

import java.util.Optional;

/**
 * Cache for per-sales-user dashboard metrics (JSON).
 * Redis in production; in-memory for local/test.
 */
public interface DashboardMetricsCacheStore {

    Optional<String> get(Long tenantId, Long userId);

    void put(Long tenantId, Long userId, String json);

    void invalidate(Long tenantId, Long userId);
}
