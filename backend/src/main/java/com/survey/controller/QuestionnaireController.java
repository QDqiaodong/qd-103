package com.survey.controller;

import com.survey.dto.*;
import com.survey.model.SubmitFingerprint;
import com.survey.service.FingerprintService;
import com.survey.service.QuestionnaireService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/questionnaires")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class QuestionnaireController {

    private final QuestionnaireService questionnaireService;
    private final FingerprintService fingerprintService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<QuestionnaireDTO>>> getAll(
            @RequestParam(required = false) String viewerToken) {
        List<QuestionnaireDTO> questionnaires = questionnaireService.getAllQuestionnaires(viewerToken);
        return ResponseEntity.ok(ApiResponse.success(questionnaires));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<QuestionnaireDTO>> getById(
            @PathVariable String id,
            @RequestParam(required = false) String viewerToken,
            @RequestParam(required = false) String accessPassword) {
        QuestionnaireDTO questionnaire = questionnaireService.getQuestionnaire(id, viewerToken, accessPassword);
        if (questionnaire == null) {
            return ResponseEntity.ok(ApiResponse.error("问卷不存在"));
        }
        return ResponseEntity.ok(ApiResponse.success(questionnaire));
    }

    @PostMapping("/{id}/verify-password")
    public ResponseEntity<ApiResponse<Boolean>> verifyPassword(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        String password = body.get("password");
        boolean valid = questionnaireService.verifyAccessPassword(id, password);
        return ResponseEntity.ok(ApiResponse.success(valid));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<QuestionnaireDTO>> create(@RequestBody QuestionnaireDTO dto) {
        QuestionnaireDTO created = questionnaireService.createQuestionnaire(dto);
        return ResponseEntity.ok(ApiResponse.success(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<QuestionnaireDTO>> update(@PathVariable String id, @RequestBody QuestionnaireDTO dto) {
        QuestionnaireDTO updated = questionnaireService.updateQuestionnaire(id, dto);
        if (updated == null) {
            return ResponseEntity.ok(ApiResponse.error("问卷不存在"));
        }
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Boolean>> delete(@PathVariable String id) {
        boolean success = questionnaireService.deleteQuestionnaire(id);
        if (!success) {
            return ResponseEntity.ok(ApiResponse.error("问卷不存在"));
        }
        return ResponseEntity.ok(ApiResponse.success(true));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<ApiResponse<Boolean>> submit(@PathVariable String id,
                                                        @RequestBody SubmitRequest request,
                                                        HttpServletRequest httpRequest) {
        String ipAddress = getClientIp(httpRequest);
        SubmitResult result = questionnaireService.submitQuestionnaire(id, request, ipAddress);
        if (!result.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.error(result.getErrorMessage()));
        }
        return ResponseEntity.ok(ApiResponse.success(true));
    }

    @GetMapping("/{id}/fingerprints")
    public ResponseEntity<ApiResponse<List<FingerprintDTO>>> getFingerprints(
            @PathVariable String id,
            @RequestParam(required = false) String viewerToken) {
        if (!isResultsVisible(id, viewerToken)) {
            return ResponseEntity.ok(ApiResponse.success(List.of()));
        }
        List<SubmitFingerprint> fingerprints = fingerprintService.getFingerprintsByQuestionnaireId(id);
        List<FingerprintDTO> dtos = fingerprints.stream()
                .map(this::toFingerprintDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    @GetMapping("/{id}/fingerprints/risky")
    public ResponseEntity<ApiResponse<List<FingerprintDTO>>> getRiskyFingerprints(
            @PathVariable String id,
            @RequestParam(required = false) String viewerToken) {
        if (!isResultsVisible(id, viewerToken)) {
            return ResponseEntity.ok(ApiResponse.success(List.of()));
        }
        List<SubmitFingerprint> fingerprints = fingerprintService.getRiskyFingerprints(id);
        List<FingerprintDTO> dtos = fingerprints.stream()
                .map(this::toFingerprintDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    @GetMapping("/{id}/fingerprints/statistics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFingerprintStatistics(
            @PathVariable String id,
            @RequestParam(required = false) String viewerToken) {
        if (!isResultsVisible(id, viewerToken)) {
            Map<String, Object> emptyStats = Map.of(
                    "totalFingerprints", 0,
                    "distinctFingerprints", 0,
                    "duplicateFingerprintGroups", 0,
                    "totalDuplicateSubmissions", 0,
                    "riskDistribution", Map.of("normal", 0, "suspicious", 0, "high_risk", 0),
                    "dailyTrend", List.of()
            );
            return ResponseEntity.ok(ApiResponse.success(emptyStats));
        }
        Map<String, Object> stats = fingerprintService.getFingerprintStatistics(id);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    private boolean isResultsVisible(String questionnaireId, String viewerToken) {
        QuestionnaireDTO questionnaire = questionnaireService.getQuestionnaire(questionnaireId, viewerToken);
        if (questionnaire == null) {
            return false;
        }
        return questionnaire.getResponseCount() != null;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private FingerprintDTO toFingerprintDTO(SubmitFingerprint fingerprint) {
        FingerprintDTO dto = new FingerprintDTO();
        dto.setId(fingerprint.getId());
        dto.setQuestionnaireId(fingerprint.getQuestionnaireId());
        dto.setRespondentId(fingerprint.getRespondentId());
        dto.setResponseId(fingerprint.getResponseId());
        dto.setFingerprintHash(fingerprint.getFingerprintHash());
        dto.setSubmittedAt(fingerprint.getSubmittedAt());
        dto.setSubmitDurationSeconds(fingerprint.getSubmitDurationSeconds());
        dto.setAnswerCount(fingerprint.getAnswerCount());
        dto.setRiskLevel(fingerprint.getRiskLevel());
        dto.setRiskReasons(fingerprint.getRiskReasons());
        dto.setIsDuplicateFingerprint(fingerprint.getIsDuplicateFingerprint());
        dto.setDuplicateCount(fingerprint.getDuplicateCount());
        dto.setIsHighFrequency(fingerprint.getIsHighFrequency());
        dto.setFrequencyWindowMinutes(fingerprint.getFrequencyWindowMinutes());
        dto.setFrequencyCount(fingerprint.getFrequencyCount());
        dto.setIsAnomalyCluster(fingerprint.getIsAnomalyCluster());
        dto.setClusterSize(fingerprint.getClusterSize());
        return dto;
    }

    @GetMapping("/{id}/statistics")
    public ResponseEntity<ApiResponse<StatisticsResponse>> getStatistics(
            @PathVariable String id,
            @RequestParam(required = false) String viewerToken) {
        StatisticsResponse statistics = questionnaireService.getStatistics(id, viewerToken);
        if (statistics == null) {
            return ResponseEntity.ok(ApiResponse.error("问卷不存在"));
        }
        return ResponseEntity.ok(ApiResponse.success(statistics));
    }

    @PostMapping("/inspect")
    public ResponseEntity<ApiResponse<Map<String, Object>>> inspectAndCorrect() {
        QuestionnaireService.StatusCorrectionResult result = questionnaireService.correctQuestionnaireStatuses();
        Map<String, Object> data = Map.of(
                "inspectTime", result.getInspectTime(),
                "expiredCount", result.getExpiredCount(),
                "reactivatedCount", result.getReactivatedCount(),
                "totalCorrected", result.getTotalCorrected()
        );
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
