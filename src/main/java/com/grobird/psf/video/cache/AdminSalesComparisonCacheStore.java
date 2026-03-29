package com.grobird.psf.video.cache;

import java.util.Optional;

/**
 * Cache for admin sales comparison metrics (JSON).
 * Redis in production; in-memory for local/test.
 * Key pattern: admin:comparison:{tenantId}:{adminUserId}:{type}:{salesUserId|"team"}
 */
public interface AdminSalesComparisonCacheStore {

    Optional<String> get(Long tenantId, Long adminUserId, String type, Long salesUserId);

    void put(Long tenantId, Long adminUserId, String type, Long salesUserId, String json);

    void invalidateAllForAdmin(Long tenantId, Long adminUserId);
}
