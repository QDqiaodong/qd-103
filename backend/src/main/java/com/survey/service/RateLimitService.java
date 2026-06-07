package com.survey.service;

import com.survey.model.Questionnaire;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final RedisService redisService;

    @Value("${survey.rate-limit.global-window-seconds:60}")
    private int globalWindowSeconds;

    @Value("${survey.rate-limit.global-limit-just-released:300}")
    private int globalLimitJustReleased;

    @Value("${survey.rate-limit.global-limit-peak:200}")
    private int globalLimitPeak;

    @Value("${survey.rate-limit.global-limit-near-deadline:100}")
    private int globalLimitNearDeadline;

    @Value("${survey.rate-limit.ip-window-seconds:60}")
    private int ipWindowSeconds;

    @Value("${survey.rate-limit.ip-limit-just-released:30}")
    private int ipLimitJustReleased;

    @Value("${survey.rate-limit.ip-limit-peak:20}")
    private int ipLimitPeak;

    @Value("${survey.rate-limit.ip-limit-near-deadline:10}")
    private int ipLimitNearDeadline;

    @Value("${survey.rate-limit.surge-detection-window-seconds:10}")
    private int surgeDetectionWindowSeconds;

    @Value("${survey.rate-limit.surge-threshold-multiplier:2.5}")
    private double surgeThresholdMultiplier;

    @Value("${survey.rate-limit.surge-tighten-ratio:0.4}")
    private double surgeTightenRatio;

    public enum Phase {
        JUST_RELEASED,
        PEAK,
        NEAR_DEADLINE,
        DEFAULT
    }

    public Phase determinePhase(Questionnaire questionnaire) {
        if (questionnaire == null) {
            return Phase.DEFAULT;
        }

        LocalDateTime createdAt = questionnaire.getCreatedAt();
        LocalDateTime deadline = questionnaire.getDeadline();
        LocalDateTime now = LocalDateTime.now();

        if (createdAt == null) {
            return Phase.DEFAULT;
        }

        if (deadline == null) {
            long hoursSinceCreated = ChronoUnit.HOURS.between(createdAt, now);
            if (hoursSinceCreated < 24) {
                return Phase.JUST_RELEASED;
            } else if (hoursSinceCreated < 72) {
                return Phase.PEAK;
            } else {
                return Phase.DEFAULT;
            }
        }

        long totalDurationMinutes = ChronoUnit.MINUTES.between(createdAt, deadline);
        if (totalDurationMinutes <= 0) {
            return Phase.DEFAULT;
        }

        long elapsedMinutes = ChronoUnit.MINUTES.between(createdAt, now);
        if (elapsedMinutes < 0) {
            return Phase.JUST_RELEASED;
        }

        double progress = (double) elapsedMinutes / totalDurationMinutes;

        if (progress < 1.0 / 3.0) {
            return Phase.JUST_RELEASED;
        } else if (progress < 2.0 / 3.0) {
            return Phase.PEAK;
        } else {
            return Phase.NEAR_DEADLINE;
        }
    }

    public int getGlobalLimit(Phase phase) {
        return switch (phase) {
            case JUST_RELEASED -> globalLimitJustReleased;
            case PEAK -> globalLimitPeak;
            case NEAR_DEADLINE -> globalLimitNearDeadline;
            case DEFAULT -> globalLimitPeak;
        };
    }

    public int getIpLimit(Phase phase) {
        return switch (phase) {
            case JUST_RELEASED -> ipLimitJustReleased;
            case PEAK -> ipLimitPeak;
            case NEAR_DEADLINE -> ipLimitNearDeadline;
            case DEFAULT -> ipLimitPeak;
        };
    }

    public boolean tryAcquire(Questionnaire questionnaire, String ipAddress) {
        Phase phase = determinePhase(questionnaire);
        String questionnaireId = questionnaire.getId();

        int baseGlobalLimit = getGlobalLimit(phase);
        int baseIpLimit = getIpLimit(phase);

        int effectiveGlobalLimit = baseGlobalLimit;
        int effectiveIpLimit = baseIpLimit;

        if (phase == Phase.NEAR_DEADLINE) {
            recordSurgeRequest(questionnaireId);
            boolean isSurge = detectSurge(questionnaireId, baseGlobalLimit);
            if (isSurge) {
                effectiveGlobalLimit = (int) (baseGlobalLimit * surgeTightenRatio);
                effectiveIpLimit = (int) (baseIpLimit * surgeTightenRatio);
                log.warn("问卷 {} 检测到异常冲量，限流收紧: 全局 {}/{}s -> {}/{}s, IP {}/{}s -> {}/{}s",
                        questionnaireId,
                        baseGlobalLimit, globalWindowSeconds,
                        effectiveGlobalLimit, globalWindowSeconds,
                        baseIpLimit, ipWindowSeconds,
                        effectiveIpLimit, ipWindowSeconds);
            }
        }

        boolean globalAllowed = redisService.isRateLimitAllowed(
                "global:" + questionnaireId,
                effectiveGlobalLimit,
                globalWindowSeconds
        );

        if (!globalAllowed) {
            log.warn("问卷 {} 全局限流触发，当前阶段: {}, 限制: {}/{}s",
                    questionnaireId, phase, effectiveGlobalLimit, globalWindowSeconds);
            return false;
        }

        boolean ipAllowed = redisService.isRateLimitAllowed(
                "ip:" + questionnaireId + ":" + ipAddress,
                effectiveIpLimit,
                ipWindowSeconds
        );

        if (!ipAllowed) {
            log.warn("问卷 {} IP限流触发，IP: {}, 当前阶段: {}, 限制: {}/{}s",
                    questionnaireId, ipAddress, phase, effectiveIpLimit, ipWindowSeconds);
            return false;
        }

        return true;
    }

    private void recordSurgeRequest(String questionnaireId) {
        String shortWindowKey = "surge:short:" + questionnaireId;
        redisService.isRateLimitAllowed(shortWindowKey, Integer.MAX_VALUE, surgeDetectionWindowSeconds);
    }

    private boolean detectSurge(String questionnaireId, int baseGlobalLimit) {
        String shortWindowKey = "surge:short:" + questionnaireId;

        int shortWindowCount = redisService.getRateLimitCount(shortWindowKey, surgeDetectionWindowSeconds);
        int expectedShortWindowCount = (int) ((double) baseGlobalLimit / globalWindowSeconds * surgeDetectionWindowSeconds);
        expectedShortWindowCount = Math.max(expectedShortWindowCount, 5);

        int surgeThreshold = (int) (expectedShortWindowCount * surgeThresholdMultiplier);

        return shortWindowCount >= surgeThreshold;
    }
}
