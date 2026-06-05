package com.survey.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisService {

    private final StringRedisTemplate redisTemplate;

    private static final String SUBMIT_LOCK_PREFIX = "submit:lock:";
    private static final Duration SUBMIT_LOCK_TTL = Duration.ofHours(24);

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
}
