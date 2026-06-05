package com.survey.repository;

import com.survey.model.Answer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AnswerRepository extends JpaRepository<Answer, String> {

    List<Answer> findByResponseId(String responseId);

    @Query("SELECT a FROM Answer a WHERE a.question.id = :questionId")
    List<Answer> findByQuestionId(@Param("questionId") String questionId);

    @Query("SELECT a FROM Answer a JOIN a.response r WHERE r.questionnaire.id = :questionnaireId")
    List<Answer> findByQuestionnaireId(@Param("questionnaireId") String questionnaireId);
}
