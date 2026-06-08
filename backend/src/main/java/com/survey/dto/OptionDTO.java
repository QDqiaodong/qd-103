package com.survey.dto;

import lombok.Data;

@Data
public class OptionDTO {
    private String id;
    private String content;
    private Integer orderIndex;
    private Boolean terminateSurvey;
    private String terminateMessage;
}
