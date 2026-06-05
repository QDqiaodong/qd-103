package com.survey.controller;

import com.survey.dto.*;
import com.survey.service.QuestionnaireService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/questionnaires")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class QuestionnaireController {

    private final QuestionnaireService questionnaireService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<QuestionnaireDTO>>> getAll() {
        List<QuestionnaireDTO> questionnaires = questionnaireService.getAllQuestionnaires();
        return ResponseEntity.ok(ApiResponse.success(questionnaires));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<QuestionnaireDTO>> getById(@PathVariable String id) {
        QuestionnaireDTO questionnaire = questionnaireService.getQuestionnaire(id);
        if (questionnaire == null) {
            return ResponseEntity.ok(ApiResponse.error("问卷不存在"));
        }
        return ResponseEntity.ok(ApiResponse.success(questionnaire));
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
    public ResponseEntity<ApiResponse<Boolean>> submit(@PathVariable String id, @RequestBody SubmitRequest request) {
        boolean success = questionnaireService.submitQuestionnaire(id, request);
        if (!success) {
            return ResponseEntity.ok(ApiResponse.error("提交失败，您可能已经提交过"));
        }
        return ResponseEntity.ok(ApiResponse.success(true));
    }

    @GetMapping("/{id}/statistics")
    public ResponseEntity<ApiResponse<StatisticsResponse>> getStatistics(@PathVariable String id) {
        StatisticsResponse statistics = questionnaireService.getStatistics(id);
        if (statistics == null) {
            return ResponseEntity.ok(ApiResponse.error("问卷不存在"));
        }
        return ResponseEntity.ok(ApiResponse.success(statistics));
    }
}
