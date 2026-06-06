package com.survey.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class FingerprintStatisticsResponse {
    private long totalFingerprints;
    private long distinctFingerprints;
    private Map<String, Long> riskDistribution;
    private int duplicateFingerprintGroups;
    private long totalDuplicateSubmissions;
    private List<Map<String, Object>> dailyTrend;
}
