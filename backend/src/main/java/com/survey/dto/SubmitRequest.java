package com.survey.dto;

import lombok.Data;
import java.util.List;

@Data
public class SubmitRequest {
    private String respondentId;
    private List<AnswerDTO> answers;
    private Integer submitDurationSeconds;
    private String userAgent;

    @Data
    public static class AnswerDTO {
        private String questionId;
        private Object value;
    }
}
