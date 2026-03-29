package com.grobird.psf.qna.cache;

import java.util.Optional;

/**
 * Cache for QNA data (questions and info).
 * Redis implementation for production; in-memory for local profile (no Redis).
 */
public interface QnaCacheStore {

    Optional<String> getQuestions(Long opportunityId);

    void putQuestions(Long opportunityId, String questionsJson);

    void evictQuestions(Long opportunityId);

    Optional<String> getInfo(Long opportunityId);

    void putInfo(Long opportunityId, String infoJson);

    void evictInfo(Long opportunityId);
}
