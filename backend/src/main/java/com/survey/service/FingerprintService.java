package com.survey.service;

import com.survey.dto.SubmitRequest;
import com.survey.model.SubmitFingerprint;
import com.survey.repository.SubmitFingerprintRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FingerprintService {

    private final SubmitFingerprintRepository fingerprintRepository;

    @Transactional
    public SubmitFingerprint createAndSaveFingerprint(String questionnaireId,
                                                       String responseId,
                                                       SubmitRequest request,
                                                       String ipAddress,
                                                       String userAgent,
                                                       Integer submitDurationSeconds) {
        SubmitFingerprint fingerprint = new SubmitFingerprint();
        fingerprint.setId(UUID.randomUUID().toString());
        fingerprint.setQuestionnaireId(questionnaireId);
        fingerprint.setRespondentId(request.getRespondentId());
        fingerprint.setResponseId(responseId);
        fingerprint.setSubmittedAt(LocalDateTime.now());
        fingerprint.setSubmitDurationSeconds(submitDurationSeconds);

        String ipHash = hashString(ipAddress != null ? ipAddress : "unknown");
        String uaHash = hashString(userAgent != null ? userAgent : "unknown");
        fingerprint.setIpHash(ipHash);
        fingerprint.setUserAgentHash(uaHash);

        String combinedFingerprint = generateCombinedFingerprint(
                request.getRespondentId(),
                ipHash,
                uaHash,
                request.getAnswers()
        );
        fingerprint.setFingerprintHash(combinedFingerprint);

        String patternHash = generateAnswerPatternHash(request.getAnswers());
        fingerprint.setAnswerPatternHash(patternHash);
        fingerprint.setAnswerCount(request.getAnswers() != null ? request.getAnswers().size() : 0);

        analyzeRisks(fingerprint, questionnaireId, ipHash);

        return fingerprintRepository.save(fingerprint);
    }

    private void analyzeRisks(SubmitFingerprint fingerprint, String questionnaireId, String ipHash) {
        List<String> riskReasons = new ArrayList<>();
        int riskScore = 0;

        long duplicateCount = fingerprintRepository
                .findByQuestionnaireIdAndFingerprintHash(questionnaireId, fingerprint.getFingerprintHash())
                .size();
        if (duplicateCount > 0) {
            fingerprint.setIsDuplicateFingerprint(true);
            fingerprint.setDuplicateCount((int) duplicateCount);
            riskReasons.add("重复指纹（同设备/同环境已提交 " + duplicateCount + " 次）");
            riskScore += duplicateCount > 2 ? 30 : 15;
        }

        LocalDateTime oneMinuteAgo = LocalDateTime.now().minusMinutes(1);
        long oneMinuteCount = fingerprintRepository.countByQuestionnaireIdAndIpHashAndSubmittedAtAfter(
                questionnaireId, ipHash, oneMinuteAgo);
        if (oneMinuteCount >= 3) {
            fingerprint.setIsHighFrequency(true);
            fingerprint.setFrequencyWindowMinutes(1);
            fingerprint.setFrequencyCount((int) oneMinuteCount);
            riskReasons.add("短时高频提交（1分钟内 " + oneMinuteCount + " 次）");
            riskScore += 25;
        }

        LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
        long fiveMinuteCount = fingerprintRepository.countByQuestionnaireIdAndIpHashAndSubmittedAtAfter(
                questionnaireId, ipHash, fiveMinutesAgo);
        if (fiveMinuteCount >= 5) {
            fingerprint.setIsHighFrequency(true);
            fingerprint.setFrequencyWindowMinutes(5);
            fingerprint.setFrequencyCount((int) fiveMinuteCount);
            riskReasons.add("短时高频提交（5分钟内 " + fiveMinuteCount + " 次）");
            riskScore += 35;
        }

        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        List<SubmitFingerprint> recentFingerprints = fingerprintRepository
                .findByQuestionnaireIdAndSubmittedAtAfter(questionnaireId, oneHourAgo);

        Map<String, Long> patternCounts = recentFingerprints.stream()
                .filter(f -> f.getAnswerPatternHash() != null)
                .collect(Collectors.groupingBy(
                        SubmitFingerprint::getAnswerPatternHash,
                        Collectors.counting()
                ));

        String currentPattern = fingerprint.getAnswerPatternHash();
        long patternCount = patternCounts.getOrDefault(currentPattern, 0L);
        if (patternCount >= 3 && recentFingerprints.size() > 0) {
            double ratio = (double) patternCount / recentFingerprints.size();
            if (ratio > 0.3) {
                fingerprint.setIsAnomalyCluster(true);
                fingerprint.setClusterSize((int) patternCount);
                riskReasons.add("答案模式异常聚集（同模式 " + patternCount + " 份，占比 " +
                        String.format("%.1f%%", ratio * 100) + "）");
                riskScore += 20;
            }
        }

        if (fingerprint.getSubmitDurationSeconds() != null
                && fingerprint.getSubmitDurationSeconds() < 10
                && fingerprint.getAnswerCount() > 3) {
            riskReasons.add("填写时长过短（" + fingerprint.getSubmitDurationSeconds() + "秒）");
            riskScore += 15;
        }

        if (riskScore >= 60) {
            fingerprint.setRiskLevel("high_risk");
        } else if (riskScore >= 25) {
            fingerprint.setRiskLevel("suspicious");
        } else {
            fingerprint.setRiskLevel("normal");
        }

        fingerprint.setRiskReasons(String.join("; ", riskReasons));
    }

    private String generateCombinedFingerprint(String respondentId,
                                               String ipHash,
                                               String uaHash,
                                               List<SubmitRequest.AnswerDTO> answers) {
        StringBuilder sb = new StringBuilder();
        sb.append(respondentId).append("|");
        sb.append(ipHash).append("|");
        sb.append(uaHash);
        return hashString(sb.toString());
    }

    private String generateAnswerPatternHash(List<SubmitRequest.AnswerDTO> answers) {
        if (answers == null || answers.isEmpty()) {
            return hashString("empty");
        }

        StringBuilder sb = new StringBuilder();
        answers.stream()
                .sorted(Comparator.comparing(SubmitRequest.AnswerDTO::getQuestionId))
                .forEach(a -> {
                    sb.append(a.getQuestionId()).append(":");
                    Object value = a.getValue();
                    if (value instanceof List<?>) {
                        List<?> list = (List<?>) value;
                        List<String> sorted = list.stream()
                                .map(Object::toString)
                                .sorted()
                                .toList();
                        sb.append(String.join(",", sorted));
                    } else {
                        sb.append(value != null ? value.toString() : "");
                    }
                    sb.append("|");
                });

        return hashString(sb.toString());
    }

    private String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString().substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    public List<SubmitFingerprint> getFingerprintsByQuestionnaireId(String questionnaireId) {
        return fingerprintRepository.findByQuestionnaireIdOrderBySubmittedAtDesc(questionnaireId);
    }

    public List<SubmitFingerprint> getRiskyFingerprints(String questionnaireId) {
        return fingerprintRepository.findRiskyFingerprintsByQuestionnaireId(questionnaireId);
    }

    public Map<String, Object> getFingerprintStatistics(String questionnaireId) {
        Map<String, Object> stats = new HashMap<>();

        long totalFingerprints = fingerprintRepository.countByQuestionnaireId(questionnaireId);
        stats.put("totalFingerprints", totalFingerprints);

        long distinctFingerprints = fingerprintRepository.countDistinctFingerprintsByQuestionnaireId(questionnaireId);
        stats.put("distinctFingerprints", distinctFingerprints);

        List<Object[]> riskCounts = fingerprintRepository.countByRiskLevelGroup(questionnaireId);
        Map<String, Long> riskDistribution = new HashMap<>();
        riskDistribution.put("normal", 0L);
        riskDistribution.put("suspicious", 0L);
        riskDistribution.put("high_risk", 0L);
        for (Object[] row : riskCounts) {
            riskDistribution.put((String) row[0], (Long) row[1]);
        }
        stats.put("riskDistribution", riskDistribution);

        List<Object[]> duplicateFps = fingerprintRepository.findDuplicateFingerprintsByQuestionnaireId(questionnaireId);
        stats.put("duplicateFingerprintGroups", duplicateFps.size());

        long totalDuplicates = 0;
        for (Object[] row : duplicateFps) {
            totalDuplicates += ((Long) row[1]).intValue() - 1;
        }
        stats.put("totalDuplicateSubmissions", totalDuplicates);

        List<Object[]> dateCounts = fingerprintRepository.countByDateGroup(questionnaireId);
        List<Map<String, Object>> dailyTrend = new ArrayList<>();
        for (Object[] row : dateCounts) {
            Map<String, Object> day = new HashMap<>();
            day.put("date", row[0].toString());
            day.put("count", row[1]);
            dailyTrend.add(day);
        }
        stats.put("dailyTrend", dailyTrend);

        return stats;
    }

    public SubmitFingerprint getFingerprintById(String id) {
        return fingerprintRepository.findById(id).orElse(null);
    }

    public SubmitFingerprint getFingerprintByResponseId(String responseId) {
        return fingerprintRepository.findByResponseId(responseId).orElse(null);
    }
}
