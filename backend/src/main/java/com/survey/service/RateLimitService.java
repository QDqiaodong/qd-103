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
        if (!tryAcquireIp(questionnaire, ipAddress)) {
            return false;
        }
        return tryAcquireGlobal(questionnaire, ipAddress);
    }

    public boolean tryAcquireIp(Questionnaire questionnaire, String ipAddress) {
        Phase phase = determinePhase(questionnaire);
        String questionnaireId = questionnaire.getId();

        int baseIpLimit = getIpLimit(phase);
        int baseGlobalLimit = getGlobalLimit(phase);

        int effectiveIpLimit = baseIpLimit;

        boolean surgeDetected = false;
        if (phase == Phase.PEAK || phase == Phase.NEAR_DEADLINE) {
            recordSurgeRequest(questionnaireId);
            surgeDetected = detectSurge(questionnaireId, baseGlobalLimit);
            if (surgeDetected) {
                effectiveIpLimit = (int) (baseIpLimit * surgeTightenRatio);
                log.warn("问卷 {} [{}] 阶段检测到异常冲量，限流收紧: IP限制 {}/{}s -> {}/{}s",
                        questionnaireId, phase,
                        baseIpLimit, ipWindowSeconds,
                        effectiveIpLimit, ipWindowSeconds);
            }
        }

        boolean ipAllowed = redisService.isRateLimitAllowed(
                "ip:" + questionnaireId + ":" + ipAddress,
                effectiveIpLimit,
                ipWindowSeconds
        );

        if (!ipAllowed) {
            log.info("问卷 {} IP限流拦截，IP: {}, 阶段: {}, 限制: {}/{}s, 冲量状态: {}",
                    questionnaireId, ipAddress, phase, effectiveIpLimit, ipWindowSeconds, surgeDetected);
            return false;
        }

        return true;
    }

    public boolean tryAcquireGlobal(Questionnaire questionnaire, String ipAddress) {
        Phase phase = determinePhase(questionnaire);
        String questionnaireId = questionnaire.getId();

        int baseGlobalLimit = getGlobalLimit(phase);
        int baseIpLimit = getIpLimit(phase);

        int effectiveGlobalLimit = baseGlobalLimit;

        boolean surgeDetected = detectSurge(questionnaireId, baseGlobalLimit);
        if (surgeDetected) {
            effectiveGlobalLimit = (int) (baseGlobalLimit * surgeTightenRatio);
        }

        boolean globalAllowed = redisService.isRateLimitAllowed(
                "global:" + questionnaireId,
                effectiveGlobalLimit,
                globalWindowSeconds
        );

        if (!globalAllowed) {
            log.info("问卷 {} 全局限流拦截，阶段: {}, 限制: {}/{}s, 冲量状态: {}",
                    questionnaireId, phase, effectiveGlobalLimit, globalWindowSeconds, surgeDetected);
            return false;
        }

        log.debug("问卷 {} 提交放行，IP: {}, 阶段: {}, 全局限制: {}/{}s, IP限制: {}/{}s, 冲量状态: {}",
                questionnaireId, ipAddress, phase,
                effectiveGlobalLimit, globalWindowSeconds,
                surgeDetected ? (int) (baseIpLimit * surgeTightenRatio) : baseIpLimit, ipWindowSeconds,
                surgeDetected);
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

    public RateLimitMetrics getMetrics(Questionnaire questionnaire, String ipAddress) {
        Phase phase = determinePhase(questionnaire);
        String questionnaireId = questionnaire.getId();

        int baseGlobalLimit = getGlobalLimit(phase);
        int baseIpLimit = getIpLimit(phase);

        boolean surgeDetected = detectSurge(questionnaireId, baseGlobalLimit);

        int effectiveGlobalLimit = surgeDetected ? (int) (baseGlobalLimit * surgeTightenRatio) : baseGlobalLimit;
        int effectiveIpLimit = surgeDetected ? (int) (baseIpLimit * surgeTightenRatio) : baseIpLimit;

        int globalCurrentCount = redisService.getRateLimitCount("global:" + questionnaireId, globalWindowSeconds);
        int ipCurrentCount = redisService.getRateLimitCount("ip:" + questionnaireId + ":" + ipAddress, ipWindowSeconds);
        int surgeCurrentCount = redisService.getRateLimitCount("surge:short:" + questionnaireId, surgeDetectionWindowSeconds);

        RateLimitMetrics metrics = new RateLimitMetrics();
        metrics.setPhase(phase.name());
        metrics.setSurgeDetected(surgeDetected);
        metrics.setBaseGlobalLimit(baseGlobalLimit);
        metrics.setEffectiveGlobalLimit(effectiveGlobalLimit);
        metrics.setGlobalCurrentCount(globalCurrentCount);
        metrics.setGlobalWindowSeconds(globalWindowSeconds);
        metrics.setBaseIpLimit(baseIpLimit);
        metrics.setEffectiveIpLimit(effectiveIpLimit);
        metrics.setIpCurrentCount(ipCurrentCount);
        metrics.setIpWindowSeconds(ipWindowSeconds);
        metrics.setSurgeCurrentCount(surgeCurrentCount);
        metrics.setSurgeWindowSeconds(surgeDetectionWindowSeconds);
        metrics.setSurgeThresholdMultiplier(surgeThresholdMultiplier);
        metrics.setSurgeTightenRatio(surgeTightenRatio);

        return metrics;
    }

    @lombok.Data
    public static class RateLimitMetrics {
        private String phase;
        private boolean surgeDetected;
        private int baseGlobalLimit;
        private int effectiveGlobalLimit;
        private int globalCurrentCount;
        private int globalWindowSeconds;
        private int baseIpLimit;
        private int effectiveIpLimit;
        private int ipCurrentCount;
        private int ipWindowSeconds;
        private int surgeCurrentCount;
        private int surgeWindowSeconds;
        private double surgeThresholdMultiplier;
        private double surgeTightenRatio;
    }
}
