package com.survey.dto;

import lombok.Data;
import java.util.List;

@Data
public class QuestionDTO {
    private String id;
    private String type;
    private String content;
    private Integer orderIndex;
    private Boolean required;
    private List<OptionDTO> options;
}
