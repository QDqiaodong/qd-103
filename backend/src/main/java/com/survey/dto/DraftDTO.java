package com.survey.dto;

import lombok.Data;
import java.util.List;

@Data
public class DraftDTO {
    private String questionnaireId;
    private String respondentId;
    private List<SubmitRequest.AnswerDTO> answers;
    private String lastQuestionId;
    private Integer elapsedSeconds;
    private String updatedAt;
}
