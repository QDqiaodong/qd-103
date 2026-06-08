package com.survey.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisService {

    private final StringRedisTemplate redisTemplate;

    private static final String SUBMIT_LOCK_PREFIX = "submit:lock:";
    private static final Duration SUBMIT_LOCK_TTL = Duration.ofHours(24);

    private static final String RATE_LIMIT_PREFIX = "ratelimit:";
    private static final String DRAFT_PREFIX = "draft:";
    private static final Duration DRAFT_TTL = Duration.ofDays(7);

    public boolean isSubmitted(String questionnaireId, String respondentId) {
        String key = SUBMIT_LOCK_PREFIX + questionnaireId + ":" + respondentId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void markSubmitted(String questionnaireId, String respondentId) {
        String key = SUBMIT_LOCK_PREFIX + questionnaireId + ":" + respondentId;
        redisTemplate.opsForValue().set(key, "1", SUBMIT_LOCK_TTL);
    }

    public void removeSubmitted(String questionnaireId, String respondentId) {
        String key = SUBMIT_LOCK_PREFIX + questionnaireId + ":" + respondentId;
        redisTemplate.delete(key);
    }

    public boolean isRateLimitAllowed(String resourceKey, int limit, int windowSeconds) {
        String key = RATE_LIMIT_PREFIX + resourceKey;
        long now = System.currentTimeMillis();
        long windowStart = now - windowSeconds * 1000L;

        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();

        zSetOps.removeRangeByScore(key, 0, windowStart);

        Long currentCount = zSetOps.zCard(key);

        if (currentCount != null && currentCount >= limit) {
            return false;
        }

        String member = UUID.randomUUID().toString() + ":" + now;
        zSetOps.add(key, member, now);

        redisTemplate.expire(key, windowSeconds + 1, TimeUnit.SECONDS);

        return true;
    }

    public int getRateLimitCount(String resourceKey, int windowSeconds) {
        String key = RATE_LIMIT_PREFIX + resourceKey;
        long now = System.currentTimeMillis();
        long windowStart = now - windowSeconds * 1000L;

        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();

        zSetOps.removeRangeByScore(key, 0, windowStart);

        Long count = zSetOps.zCard(key);
        return count != null ? count.intValue() : 0;
    }

    public void saveDraft(String questionnaireId, String respondentId, String draftJson) {
        String key = DRAFT_PREFIX + questionnaireId + ":" + respondentId;
        redisTemplate.opsForValue().set(key, draftJson, DRAFT_TTL);
    }

    public String getDraft(String questionnaireId, String respondentId) {
        String key = DRAFT_PREFIX + questionnaireId + ":" + respondentId;
        return redisTemplate.opsForValue().get(key);
    }

    public void deleteDraft(String questionnaireId, String respondentId) {
        String key = DRAFT_PREFIX + questionnaireId + ":" + respondentId;
        redisTemplate.delete(key);
    }
}
