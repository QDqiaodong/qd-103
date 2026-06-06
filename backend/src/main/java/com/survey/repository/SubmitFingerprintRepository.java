package com.survey.repository;

import com.survey.model.SubmitFingerprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubmitFingerprintRepository extends JpaRepository<SubmitFingerprint, String> {

    List<SubmitFingerprint> findByQuestionnaireIdOrderBySubmittedAtDesc(String questionnaireId);

    long countByQuestionnaireId(String questionnaireId);

    @Query("SELECT f FROM SubmitFingerprint f WHERE f.questionnaireId = :questionnaireId " +
            "AND f.fingerprintHash = :fingerprintHash ORDER BY f.submittedAt DESC")
    List<SubmitFingerprint> findByQuestionnaireIdAndFingerprintHash(
            @Param("questionnaireId") String questionnaireId,
            @Param("fingerprintHash") String fingerprintHash);

    @Query("SELECT COUNT(DISTINCT f.fingerprintHash) FROM SubmitFingerprint f WHERE f.questionnaireId = :questionnaireId")
    long countDistinctFingerprintsByQuestionnaireId(@Param("questionnaireId") String questionnaireId);

    @Query("SELECT f FROM SubmitFingerprint f WHERE f.questionnaireId = :questionnaireId " +
            "AND f.submittedAt >= :startTime ORDER BY f.submittedAt DESC")
    List<SubmitFingerprint> findByQuestionnaireIdAndSubmittedAtAfter(
            @Param("questionnaireId") String questionnaireId,
            @Param("startTime") LocalDateTime startTime);

    @Query("SELECT f.fingerprintHash, COUNT(f) as cnt FROM SubmitFingerprint f " +
            "WHERE f.questionnaireId = :questionnaireId " +
            "GROUP BY f.fingerprintHash HAVING COUNT(f) > 1 " +
            "ORDER BY cnt DESC")
    List<Object[]> findDuplicateFingerprintsByQuestionnaireId(@Param("questionnaireId") String questionnaireId);

    @Query("SELECT COUNT(f) FROM SubmitFingerprint f WHERE f.questionnaireId = :questionnaireId " +
            "AND f.ipHash = :ipHash AND f.submittedAt >= :startTime")
    long countByQuestionnaireIdAndIpHashAndSubmittedAtAfter(
            @Param("questionnaireId") String questionnaireId,
            @Param("ipHash") String ipHash,
            @Param("startTime") LocalDateTime startTime);

    @Query("SELECT f FROM SubmitFingerprint f WHERE f.questionnaireId = :questionnaireId " +
            "AND f.riskLevel != 'normal' ORDER BY f.submittedAt DESC")
    List<SubmitFingerprint> findRiskyFingerprintsByQuestionnaireId(@Param("questionnaireId") String questionnaireId);

    @Query("SELECT f.riskLevel, COUNT(f) FROM SubmitFingerprint f " +
            "WHERE f.questionnaireId = :questionnaireId GROUP BY f.riskLevel")
    List<Object[]> countByRiskLevelGroup(@Param("questionnaireId") String questionnaireId);

    Optional<SubmitFingerprint> findByResponseId(String responseId);

    @Query("SELECT f FROM SubmitFingerprint f WHERE f.questionnaireId = :questionnaireId " +
            "AND f.answerPatternHash = :patternHash ORDER BY f.submittedAt DESC")
    List<SubmitFingerprint> findByQuestionnaireIdAndAnswerPatternHash(
            @Param("questionnaireId") String questionnaireId,
            @Param("patternHash") String patternHash);

    @Query("SELECT FUNCTION('DATE', f.submittedAt) as submitDate, COUNT(f) as cnt " +
            "FROM SubmitFingerprint f WHERE f.questionnaireId = :questionnaireId " +
            "GROUP BY FUNCTION('DATE', f.submittedAt) ORDER BY submitDate DESC")
    List<Object[]> countByDateGroup(@Param("questionnaireId") String questionnaireId);
}
