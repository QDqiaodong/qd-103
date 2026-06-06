package com.survey.repository;

import com.survey.model.QuestionnaireSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionnaireSnapshotRepository extends JpaRepository<QuestionnaireSnapshot, String> {

    List<QuestionnaireSnapshot> findByQuestionnaireIdOrderBySnapshotAtDesc(String questionnaireId);

    boolean existsByQuestionnaireIdAndSnapshotReason(String questionnaireId, String snapshotReason);
}
