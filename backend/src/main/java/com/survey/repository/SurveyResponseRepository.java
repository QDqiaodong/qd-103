package com.survey.repository;

import com.survey.model.SurveyResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SurveyResponseRepository extends JpaRepository<SurveyResponse, String> {

    List<SurveyResponse> findByQuestionnaireId(String questionnaireId);

    boolean existsByQuestionnaireIdAndRespondentId(String questionnaireId, String respondentId);

    int countByQuestionnaireId(String questionnaireId);
}
