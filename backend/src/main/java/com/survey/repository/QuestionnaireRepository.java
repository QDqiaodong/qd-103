package com.survey.repository;

import com.survey.model.Questionnaire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface QuestionnaireRepository extends JpaRepository<Questionnaire, String> {

    List<Questionnaire> findAllByOrderByCreatedAtDesc();

    @Query("SELECT q FROM Questionnaire q LEFT JOIN FETCH q.questions WHERE q.id = :id")
    Questionnaire findByIdWithQuestions(@Param("id") String id);

    @Query("SELECT COUNT(r) FROM SurveyResponse r WHERE r.questionnaire.id = :questionnaireId")
    Integer countResponsesByQuestionnaireId(@Param("questionnaireId") String questionnaireId);

    @Query("SELECT q FROM Questionnaire q WHERE q.status = 'active' AND q.deadline IS NOT NULL AND q.deadline < :now")
    List<Questionnaire> findActiveAndExpired(@Param("now") LocalDateTime now);

    @Query("SELECT q FROM Questionnaire q WHERE q.status = 'expired' AND q.deadline IS NOT NULL AND q.deadline > :now")
    List<Questionnaire> findExpiredButDeadlineNotReached(@Param("now") LocalDateTime now);
}
