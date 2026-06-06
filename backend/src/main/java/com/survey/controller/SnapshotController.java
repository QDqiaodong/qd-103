package com.survey.controller;

import com.survey.dto.*;
import com.survey.model.QuestionnaireSnapshot;
import com.survey.service.SnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/snapshots")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SnapshotController {

    private final SnapshotService snapshotService;

    @GetMapping("/questionnaire/{questionnaireId}")
    public ResponseEntity<ApiResponse<List<SnapshotDTO>>> getByQuestionnaireId(@PathVariable String questionnaireId) {
        List<SnapshotDTO> snapshots = snapshotService.getSnapshotsByQuestionnaireId(questionnaireId);
        return ResponseEntity.ok(ApiResponse.success(snapshots));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SnapshotDetailDTO>> getById(@PathVariable String id) {
        SnapshotDetailDTO snapshot = snapshotService.getSnapshotDetail(id);
        if (snapshot == null) {
            return ResponseEntity.ok(ApiResponse.error("快照不存在"));
        }
        return ResponseEntity.ok(ApiResponse.success(snapshot));
    }

    @PostMapping("/questionnaire/{questionnaireId}")
    public ResponseEntity<ApiResponse<SnapshotDTO>> createSnapshot(
            @PathVariable String questionnaireId,
            @RequestParam(required = false, defaultValue = "manual") String reason) {
        QuestionnaireSnapshot snapshot = snapshotService.createSnapshot(questionnaireId, reason);
        if (snapshot == null) {
            return ResponseEntity.ok(ApiResponse.error("创建快照失败或快照已存在"));
        }
        SnapshotDTO dto = new SnapshotDTO();
        dto.setId(snapshot.getId());
        dto.setQuestionnaireId(snapshot.getQuestionnaireId());
        dto.setTitle(snapshot.getTitle());
        dto.setDescription(snapshot.getDescription());
        dto.setDeadline(snapshot.getDeadline());
        dto.setStatus(snapshot.getStatus());
        dto.setOriginalStatus(snapshot.getOriginalStatus());
        dto.setSnapshotReason(snapshot.getSnapshotReason());
        dto.setCreatedAt(snapshot.getCreatedAt());
        dto.setSnapshotAt(snapshot.getSnapshotAt());
        dto.setResponseCount(snapshot.getResponseCount());
        dto.setQuestionCount(snapshot.getQuestionCount());
        return ResponseEntity.ok(ApiResponse.success(dto));
    }
}
