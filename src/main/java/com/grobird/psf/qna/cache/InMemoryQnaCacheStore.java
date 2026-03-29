package com.grobird.psf.qna.cache;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory QNA cache for local profile (no Redis).
 */
@Component
@Profile("local | test")
public class InMemoryQnaCacheStore implements QnaCacheStore {

    private final ConcurrentHashMap<String, String> questions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> info = new ConcurrentHashMap<>();

    @Override
    public Optional<String> getQuestions(Long opportunityId) {
        return Optional.ofNullable(questions.get("qna:questions:" + opportunityId));
    }

    @Override
    public void putQuestions(Long opportunityId, String questionsJson) {
        questions.put("qna:questions:" + opportunityId, questionsJson);
    }

    @Override
    public void evictQuestions(Long opportunityId) {
        questions.remove("qna:questions:" + opportunityId);
    }

    @Override
    public Optional<String> getInfo(Long opportunityId) {
        return Optional.ofNullable(info.get("qna:info:" + opportunityId));
    }

    @Override
    public void putInfo(Long opportunityId, String infoJson) {
        info.put("qna:info:" + opportunityId, infoJson);
    }

    @Override
    public void evictInfo(Long opportunityId) {
        info.remove("qna:info:" + opportunityId);
    }
}
