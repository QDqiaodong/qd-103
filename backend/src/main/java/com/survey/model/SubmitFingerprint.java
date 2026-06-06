package com.survey.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "submit_fingerprint")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmitFingerprint {

    @Id
    private String id;

    @Column(name = "questionnaire_id", nullable = false)
    private String questionnaireId;

    @Column(name = "respondent_id", nullable = false)
    private String respondentId;

    @Column(name = "response_id")
    private String responseId;

    @Column(name = "ip_hash")
    private String ipHash;

    @Column(name = "user_agent_hash")
    private String userAgentHash;

    @Column(name = "fingerprint_hash")
    private String fingerprintHash;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "submit_duration_seconds")
    private Integer submitDurationSeconds;

    @Column(name = "answer_pattern_hash")
    private String answerPatternHash;

    @Column(name = "answer_count")
    private Integer answerCount;

    @Column(name = "risk_level")
    private String riskLevel;

    @Column(name = "risk_reasons")
    private String riskReasons;

    @Column(name = "is_duplicate_fingerprint")
    private Boolean isDuplicateFingerprint;

    @Column(name = "duplicate_count")
    private Integer duplicateCount;

    @Column(name = "is_high_frequency")
    private Boolean isHighFrequency;

    @Column(name = "frequency_window_minutes")
    private Integer frequencyWindowMinutes;

    @Column(name = "frequency_count")
    private Integer frequencyCount;

    @Column(name = "is_anomaly_cluster")
    private Boolean isAnomalyCluster;

    @Column(name = "cluster_size")
    private Integer clusterSize;

    @PrePersist
    protected void onCreate() {
        if (submittedAt == null) {
            submittedAt = LocalDateTime.now();
        }
        if (riskLevel == null) {
            riskLevel = "normal";
        }
        if (isDuplicateFingerprint == null) {
            isDuplicateFingerprint = false;
        }
        if (duplicateCount == null) {
            duplicateCount = 0;
        }
        if (isHighFrequency == null) {
            isHighFrequency = false;
        }
        if (isAnomalyCluster == null) {
            isAnomalyCluster = false;
        }
    }
}
