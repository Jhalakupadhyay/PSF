package com.grobird.psf.qna.cache;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@Profile("!local & !test")
public class RedisQnaCacheStore implements QnaCacheStore {

    private static final Duration TTL = Duration.ofHours(48);
    private static final String QUESTIONS_PREFIX = "qna:questions:";
    private static final String INFO_PREFIX = "qna:info:";

    private final StringRedisTemplate redis;

    public RedisQnaCacheStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Optional<String> getQuestions(Long opportunityId) {
        return Optional.ofNullable(redis.opsForValue().get(QUESTIONS_PREFIX + opportunityId));
    }

    @Override
    public void putQuestions(Long opportunityId, String questionsJson) {
        redis.opsForValue().set(QUESTIONS_PREFIX + opportunityId, questionsJson, TTL);
    }

    @Override
    public void evictQuestions(Long opportunityId) {
        redis.delete(QUESTIONS_PREFIX + opportunityId);
    }

    @Override
    public Optional<String> getInfo(Long opportunityId) {
        return Optional.ofNullable(redis.opsForValue().get(INFO_PREFIX + opportunityId));
    }

    @Override
    public void putInfo(Long opportunityId, String infoJson) {
        redis.opsForValue().set(INFO_PREFIX + opportunityId, infoJson, TTL);
    }

    @Override
    public void evictInfo(Long opportunityId) {
        redis.delete(INFO_PREFIX + opportunityId);
    }
}
