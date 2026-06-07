package com.survey.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class StatisticsResponse {
    private Boolean resultsVisible;
    private String visibilityMessage;
    private String resultVisibility;
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
        private List<DedupedTextAnswer> dedupedTextAnswers;
        private Integer distinctTextAnswerCount;

        @Data
        public static class DedupedTextAnswer {
            private String content;
            private Integer count;
            private Double percentage;
        }
    }
}
