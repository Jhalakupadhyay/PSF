package com.grobird.psf.video.cache;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory P section cache for local/test profile (no Redis).
 */
@Component
@Profile("local | test")
public class InMemoryPSectionCacheStore implements PSectionCacheStore {

    private final ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();

    @Override
    public Optional<String> getSalesGoldenResult(Long opportunityId) {
        return Optional.ofNullable(store.get("golden:" + opportunityId));
    }

    @Override
    public void putSalesGoldenResult(Long opportunityId, String resultJson) {
        store.put("golden:" + opportunityId, resultJson);
    }

    @Override
    public Optional<String> getSalesSkillsetResult(Long opportunityId, Long referenceVideoId) {
        return Optional.ofNullable(store.get("skillset:" + opportunityId + ":" + referenceVideoId));
    }

    @Override
    public void putSalesSkillsetResult(Long opportunityId, Long referenceVideoId, String resultJson) {
        store.put("skillset:" + opportunityId + ":" + referenceVideoId, resultJson);
    }
}
