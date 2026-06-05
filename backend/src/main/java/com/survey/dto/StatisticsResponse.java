package com.survey.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class StatisticsResponse {
    private Integer totalResponses;
    private List<QuestionStatistics> questions;

    @Data
    public static class QuestionStatistics {
        private String questionId;
        private String type;
        private String content;
        private Integer totalResponses;
        private Map<String, Integer> statistics;
        private List<String> textAnswers;
    }
}
