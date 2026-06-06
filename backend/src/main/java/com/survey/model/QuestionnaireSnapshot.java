package com.survey.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "questionnaire_snapshot")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionnaireSnapshot {

    @Id
    private String id;

    @Column(name = "questionnaire_id", nullable = false)
    private String questionnaireId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private LocalDateTime deadline;

    @Column(nullable = false)
    private String status;

    @Column(name = "original_status")
    private String originalStatus;

    @Column(name = "snapshot_reason")
    private String snapshotReason;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "snapshot_at")
    private LocalDateTime snapshotAt;

    @Column(name = "questions_json", columnDefinition = "TEXT")
    private String questionsJson;

    @Column(name = "statistics_json", columnDefinition = "TEXT")
    private String statisticsJson;

    @Column(name = "cover_config", columnDefinition = "TEXT")
    private String coverConfig;

    @Column(name = "response_count")
    private Integer responseCount;

    @Column(name = "question_count")
    private Integer questionCount;
}
