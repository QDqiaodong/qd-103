package com.survey.service;

import com.survey.dto.*;
import com.survey.model.*;
import com.survey.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SnapshotService {

    private final QuestionnaireSnapshotRepository snapshotRepository;
    private final QuestionnaireRepository questionnaireRepository;
    private final QuestionRepository questionRepository;
    private final SurveyResponseRepository responseRepository;
    private final AnswerRepository answerRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public QuestionnaireSnapshot createSnapshot(String questionnaireId, String reason) {
        Questionnaire questionnaire = questionnaireRepository.findByIdWithQuestions(questionnaireId);
        if (questionnaire == null) {
            return null;
        }

        String snapshotKey;
        boolean isManual = "manual".equals(reason);
        if (isManual) {
            snapshotKey = "manual_" + System.currentTimeMillis();
        } else if ("expired".equals(reason)) {
            snapshotKey = "expired";
            if (snapshotRepository.existsByQuestionnaireIdAndSnapshotReason(questionnaireId, snapshotKey)) {
                return null;
            }
        } else {
            snapshotKey = reason + "_" + questionnaire.getStatus();
            if (snapshotRepository.existsByQuestionnaireIdAndSnapshotReason(questionnaireId, snapshotKey)) {
                return null;
            }
        }

        QuestionnaireSnapshot snapshot = new QuestionnaireSnapshot();
        snapshot.setId(UUID.randomUUID().toString());
        snapshot.setQuestionnaireId(questionnaireId);
        snapshot.setTitle(questionnaire.getTitle());
        snapshot.setDescription(questionnaire.getDescription());
        snapshot.setDeadline(questionnaire.getDeadline());
        snapshot.setStatus(questionnaire.getStatus());
        snapshot.setOriginalStatus(questionnaire.getStatus());
        snapshot.setSnapshotReason(snapshotKey);
        snapshot.setCreatedAt(questionnaire.getCreatedAt());
        snapshot.setSnapshotAt(LocalDateTime.now());
        snapshot.setCoverConfig(questionnaire.getCoverConfig());

        List<QuestionDTO> questionDTOs = new ArrayList<>();
        for (Question question : questionnaire.getQuestions()) {
            QuestionDTO qdto = new QuestionDTO();
            qdto.setId(question.getId());
            qdto.setType(question.getType());
            qdto.setContent(question.getContent());
            qdto.setOrderIndex(question.getOrderIndex());
            qdto.setRequired(question.getRequired());
            qdto.setShowCondition(question.getShowCondition());

            List<OptionDTO> optionDTOs = new ArrayList<>();
            for (OptionItem option : question.getOptions()) {
                OptionDTO odto = new OptionDTO();
                odto.setId(option.getId());
                odto.setContent(option.getContent());
                odto.setOrderIndex(option.getOrderIndex());
                odto.setTerminateSurvey(option.getTerminateSurvey());
                odto.setTerminateMessage(option.getTerminateMessage());
                optionDTOs.add(odto);
            }
            qdto.setOptions(optionDTOs);
            questionDTOs.add(qdto);
        }

        try {
            snapshot.setQuestionsJson(objectMapper.writeValueAsString(questionDTOs));
        } catch (Exception e) {
            snapshot.setQuestionsJson("[]");
        }

        StatisticsResponse statistics = calculateStatistics(questionnaire);
        try {
            snapshot.setStatisticsJson(objectMapper.writeValueAsString(statistics));
        } catch (Exception e) {
            snapshot.setStatisticsJson("{}");
        }

        snapshot.setResponseCount(statistics.getTotalResponses());
        snapshot.setQuestionCount(questionnaire.getQuestions().size());
        snapshot.setMaxResponses(questionnaire.getMaxResponses());
        snapshot.setClosedMessage(questionnaire.getClosedMessage());

        return snapshotRepository.save(snapshot);
    }

    private StatisticsResponse calculateStatistics(Questionnaire questionnaire) {
        List<SurveyResponse> responses = responseRepository.findByQuestionnaireId(questionnaire.getId());
        List<Answer> allAnswers = answerRepository.findByQuestionnaireId(questionnaire.getId());

        StatisticsResponse stats = new StatisticsResponse();
        stats.setTotalResponses(responses.size());

        List<StatisticsResponse.QuestionStatistics> questionStats = new ArrayList<>();

        for (Question question : questionnaire.getQuestions()) {
            StatisticsResponse.QuestionStatistics qs = new StatisticsResponse.QuestionStatistics();
            qs.setQuestionId(question.getId());
            qs.setType(question.getType());
            qs.setContent(question.getContent());

            List<Answer> questionAnswers = allAnswers.stream()
                    .filter(a -> a.getQuestion().getId().equals(question.getId()))
                    .toList();

            qs.setTotalResponses(questionAnswers.size());

            Map<String, Integer> statistics = new LinkedHashMap<>();
            List<String> textAnswers = new ArrayList<>();

            if ("text".equals(question.getType())) {
                for (Answer answer : questionAnswers) {
                    String value = answer.getValue();
                    if (value != null && !value.trim().isEmpty()) {
                        textAnswers.add(value);
                    }
                }
                statistics.put("text_responses", textAnswers.size());

                List<StatisticsResponse.QuestionStatistics.DedupedTextAnswer> deduped = dedupeTextAnswers(textAnswers);
                qs.setDedupedTextAnswers(deduped);
                qs.setDistinctTextAnswerCount(deduped.size());
            } else {
                Map<String, String> optionIdToContent = new HashMap<>();
                Set<String> optionIds = new HashSet<>();
                Set<String> optionContents = new HashSet<>();
                for (OptionItem option : question.getOptions()) {
                    optionIdToContent.put(option.getId(), option.getContent());
                    optionIds.add(option.getId());
                    optionContents.add(option.getContent());
                    statistics.put(option.getContent(), 0);
                }

                for (Answer answer : questionAnswers) {
                    String value = answer.getValue();
                    if (value != null && !value.trim().isEmpty()) {
                        if (value.contains(",")) {
                            for (String v : value.split(",")) {
                                String trimmed = v.trim();
                                if (!trimmed.isEmpty()) {
                                    String optionContent = resolveOptionContent(trimmed, optionIds, optionContents, optionIdToContent);
                                    if (optionContent != null) {
                                        statistics.merge(optionContent, 1, Integer::sum);
                                    }
                                }
                            }
                        } else {
                            String trimmed = value.trim();
                            String optionContent = resolveOptionContent(trimmed, optionIds, optionContents, optionIdToContent);
                            if (optionContent != null) {
                                statistics.merge(optionContent, 1, Integer::sum);
                            }
                        }
                    }
                }
            }

            qs.setStatistics(statistics);
            qs.setTextAnswers(textAnswers);
            questionStats.add(qs);
        }

        stats.setQuestions(questionStats);
        return stats;
    }

    private String resolveOptionContent(String value, Set<String> optionIds, Set<String> optionContents, Map<String, String> optionIdToContent) {
        if (optionIds.contains(value)) {
            return optionIdToContent.get(value);
        }
        if (optionContents.contains(value)) {
            return value;
        }
        return null;
    }

    private List<StatisticsResponse.QuestionStatistics.DedupedTextAnswer> dedupeTextAnswers(List<String> textAnswers) {
        if (textAnswers == null || textAnswers.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, Integer> countMap = new LinkedHashMap<>();
        Map<String, Integer> firstOccurrenceMap = new LinkedHashMap<>();
        int total = textAnswers.size();
        int orderIndex = 0;

        for (String answer : textAnswers) {
            String normalized = normalizeWhitespace(answer);
            countMap.merge(normalized, 1, Integer::sum);
            if (!firstOccurrenceMap.containsKey(normalized)) {
                firstOccurrenceMap.put(normalized, orderIndex++);
            }
        }

        List<Map.Entry<String, Integer>> sortedEntries = new ArrayList<>(countMap.entrySet());
        sortedEntries.sort((a, b) -> {
            int countCompare = Integer.compare(b.getValue(), a.getValue());
            if (countCompare != 0) {
                return countCompare;
            }
            return Integer.compare(firstOccurrenceMap.get(a.getKey()), firstOccurrenceMap.get(b.getKey()));
        });

        List<StatisticsResponse.QuestionStatistics.DedupedTextAnswer> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : sortedEntries) {
            StatisticsResponse.QuestionStatistics.DedupedTextAnswer item = new StatisticsResponse.QuestionStatistics.DedupedTextAnswer();
            item.setContent(entry.getKey());
            item.setCount(entry.getValue());
            item.setPercentage(total > 0 ? (entry.getValue() * 100.0 / total) : 0.0);
            result.add(item);
        }

        return result;
    }

    private String normalizeWhitespace(String text) {
        if (text == null) {
            return "";
        }
        return text.trim().replaceAll("\\s+", " ");
    }

    public List<SnapshotDTO> getSnapshotsByQuestionnaireId(String questionnaireId) {
        List<QuestionnaireSnapshot> snapshots = snapshotRepository.findByQuestionnaireIdOrderBySnapshotAtDesc(questionnaireId);
        return snapshots.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public SnapshotDetailDTO getSnapshotDetail(String id) {
        QuestionnaireSnapshot snapshot = snapshotRepository.findById(id).orElse(null);
        if (snapshot == null) {
            return null;
        }
        return toDetailDTO(snapshot);
    }

    private SnapshotDTO toDTO(QuestionnaireSnapshot snapshot) {
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
        dto.setCoverConfig(deserializeCoverConfig(snapshot.getCoverConfig()));
        dto.setMaxResponses(snapshot.getMaxResponses());
        dto.setClosedMessage(snapshot.getClosedMessage());
        return dto;
    }

    private SnapshotDetailDTO toDetailDTO(QuestionnaireSnapshot snapshot) {
        SnapshotDetailDTO dto = new SnapshotDetailDTO();
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
        dto.setCoverConfig(deserializeCoverConfig(snapshot.getCoverConfig()));
        dto.setMaxResponses(snapshot.getMaxResponses());
        dto.setClosedMessage(snapshot.getClosedMessage());

        try {
            List<QuestionDTO> questions = objectMapper.readValue(
                    snapshot.getQuestionsJson(),
                    new TypeReference<List<QuestionDTO>>() {}
            );
            dto.setQuestions(questions);
        } catch (Exception e) {
            dto.setQuestions(new ArrayList<>());
        }

        try {
            StatisticsResponse statistics = objectMapper.readValue(
                    snapshot.getStatisticsJson(),
                    StatisticsResponse.class
            );
            dto.setStatistics(statistics);
        } catch (Exception e) {
            dto.setStatistics(new StatisticsResponse());
        }

        return dto;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deserializeCoverConfig(String coverConfig) {
        if (coverConfig == null || coverConfig.trim().isEmpty()) return null;
        try {
            return objectMapper.readValue(coverConfig, Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional
    public void checkAndCreateExpiredSnapshots() {
        List<Questionnaire> questionnaires = questionnaireRepository.findAll();
        LocalDateTime now = LocalDateTime.now();

        for (Questionnaire q : questionnaires) {
            if ("active".equals(q.getStatus()) && q.getDeadline() != null && q.getDeadline().isBefore(now)) {
                createSnapshot(q.getId(), "expired");
            }
        }
    }
}
