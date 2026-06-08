package com.survey.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class SnapshotDetailDTO {

    private String id;
    private String questionnaireId;
    private String title;
    private String description;
    private LocalDateTime deadline;
    private String status;
    private String originalStatus;
    private String snapshotReason;
    private LocalDateTime createdAt;
    private LocalDateTime snapshotAt;
    private Integer responseCount;
    private Integer questionCount;
    private Map<String, Object> coverConfig;
    private Integer maxResponses;
    private String closedMessage;
    private List<QuestionDTO> questions;
    private StatisticsResponse statistics;
}
