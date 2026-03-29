package com.grobird.psf.video.cache;

import java.util.Optional;

/**
 * Optional cache for P section sales submission results (polling).
 * Redis implementation for production; in-memory for local/test.
 */
public interface PSectionCacheStore {

    Optional<String> getSalesGoldenResult(Long opportunityId);

    void putSalesGoldenResult(Long opportunityId, String resultJson);

    Optional<String> getSalesSkillsetResult(Long opportunityId, Long referenceVideoId);

    void putSalesSkillsetResult(Long opportunityId, Long referenceVideoId, String resultJson);
}
