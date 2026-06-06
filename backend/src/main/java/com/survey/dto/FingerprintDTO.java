package com.survey.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class FingerprintDTO {
    private String id;
    private String questionnaireId;
    private String respondentId;
    private String responseId;
    private String fingerprintHash;
    private LocalDateTime submittedAt;
    private Integer submitDurationSeconds;
    private Integer answerCount;
    private String riskLevel;
    private String riskReasons;
    private Boolean isDuplicateFingerprint;
    private Integer duplicateCount;
    private Boolean isHighFrequency;
    private Integer frequencyWindowMinutes;
    private Integer frequencyCount;
    private Boolean isAnomalyCluster;
    private Integer clusterSize;
}
