package com.survey.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class QuestionnaireDTO {
    private String id;
    private String title;
    private String description;
    private LocalDateTime deadline;
    private String status;
    private String resultVisibility;
    private String creatorToken;
    private LocalDateTime createdAt;
    private List<QuestionDTO> questions;
    private Integer responseCount;
    private Map<String, Object> coverConfig;
    private Boolean passwordProtected;
    private String accessPassword;
    private Integer maxResponses;
    private String closedMessage;
}
