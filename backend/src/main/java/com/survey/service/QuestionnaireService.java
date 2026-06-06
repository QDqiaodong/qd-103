package com.survey.service;

import com.survey.dto.*;
import com.survey.model.*;
import com.survey.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuestionnaireService {

    private final QuestionnaireRepository questionnaireRepository;
    private final QuestionRepository questionRepository;
    private final SurveyResponseRepository responseRepository;
    private final AnswerRepository answerRepository;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;
    private final FingerprintService fingerprintService;
    private final SnapshotService snapshotService;

    public List<QuestionnaireDTO> getAllQuestionnaires() {
        List<Questionnaire> questionnaires = questionnaireRepository.findAllByOrderByCreatedAtDesc();

        LocalDateTime now = LocalDateTime.now();
        for (Questionnaire q : questionnaires) {
            if ("active".equals(q.getStatus())
                    && q.getDeadline() != null
                    && q.getDeadline().isBefore(now)) {
                snapshotService.createSnapshot(q.getId(), "expired");
            }
        }

        return questionnaires.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public QuestionnaireDTO getQuestionnaire(String id) {
        Questionnaire questionnaire = questionnaireRepository.findByIdWithQuestions(id);
        if (questionnaire == null) {
            return null;
        }

        checkAndCreateExpiredSnapshot(questionnaire);

        return toDTO(questionnaire);
    }

    private void checkAndCreateExpiredSnapshot(Questionnaire questionnaire) {
        if ("active".equals(questionnaire.getStatus())
                && questionnaire.getDeadline() != null
                && questionnaire.getDeadline().isBefore(LocalDateTime.now())) {
            snapshotService.createSnapshot(questionnaire.getId(), "expired");
        }
    }

    @Transactional
    public QuestionnaireDTO createQuestionnaire(QuestionnaireDTO dto) {
        Questionnaire questionnaire = new Questionnaire();
        questionnaire.setId(UUID.randomUUID().toString());
        questionnaire.setTitle(dto.getTitle());
        questionnaire.setDescription(dto.getDescription());
        questionnaire.setDeadline(dto.getDeadline());
        questionnaire.setStatus(dto.getStatus() != null ? dto.getStatus() : "active");
        questionnaire.setCreatedAt(LocalDateTime.now());
        questionnaire.setCoverConfig(serializeCoverConfig(dto.getCoverConfig()));

        questionnaire = questionnaireRepository.save(questionnaire);

        if (dto.getQuestions() != null) {
            for (QuestionDTO qdto : dto.getQuestions()) {
                Question question = new Question();
                question.setId(UUID.randomUUID().toString());
                question.setQuestionnaire(questionnaire);
                question.setType(qdto.getType());
                question.setContent(qdto.getContent());
                question.setOrderIndex(qdto.getOrderIndex());
                question.setRequired(qdto.getRequired() != null ? qdto.getRequired() : true);

                if (qdto.getOptions() != null) {
                    for (OptionDTO odto : qdto.getOptions()) {
                        OptionItem option = new OptionItem();
                        option.setId(UUID.randomUUID().toString());
                        option.setQuestion(question);
                        option.setContent(odto.getContent());
                        option.setOrderIndex(odto.getOrderIndex());
                        question.getOptions().add(option);
                    }
                }

                question = questionRepository.save(question);
                questionnaire.getQuestions().add(question);
            }
        }

        return toDTO(questionnaire);
    }

    @Transactional
    public QuestionnaireDTO updateQuestionnaire(String id, QuestionnaireDTO dto) {
        Questionnaire questionnaire = questionnaireRepository.findByIdWithQuestions(id);
        if (questionnaire == null) {
            return null;
        }

        String oldStatus = questionnaire.getStatus();

        questionnaire.setTitle(dto.getTitle());
        questionnaire.setDescription(dto.getDescription());
        questionnaire.setDeadline(dto.getDeadline());
        if (dto.getStatus() != null) {
            questionnaire.setStatus(dto.getStatus());
        }
        if (dto.getCoverConfig() != null) {
            questionnaire.setCoverConfig(serializeCoverConfig(dto.getCoverConfig()));
        }

        questionnaire = questionnaireRepository.save(questionnaire);

        if (dto.getQuestions() != null) {
            List<String> oldQuestionIds = questionnaire.getQuestions().stream()
                    .map(Question::getId)
                    .toList();
            if (!oldQuestionIds.isEmpty()) {
                answerRepository.deleteByQuestionIds(oldQuestionIds);
            }
            questionnaire.getQuestions().clear();

            for (QuestionDTO qdto : dto.getQuestions()) {
                Question question = new Question();
                question.setId(UUID.randomUUID().toString());
                question.setQuestionnaire(questionnaire);
                question.setType(qdto.getType());
                question.setContent(qdto.getContent());
                question.setOrderIndex(qdto.getOrderIndex());
                question.setRequired(qdto.getRequired() != null ? qdto.getRequired() : true);

                if (qdto.getOptions() != null) {
                    for (OptionDTO odto : qdto.getOptions()) {
                        OptionItem option = new OptionItem();
                        option.setId(UUID.randomUUID().toString());
                        option.setQuestion(question);
                        option.setContent(odto.getContent());
                        option.setOrderIndex(odto.getOrderIndex());
                        question.getOptions().add(option);
                    }
                }

                questionnaire.getQuestions().add(question);
            }
        }

        questionnaire = questionnaireRepository.save(questionnaire);

        if (dto.getStatus() != null && "closed".equals(dto.getStatus()) && !"closed".equals(oldStatus)) {
            snapshotService.createSnapshot(id, "closed");
        }

        return toDTO(questionnaire);
    }

    @Transactional
    public boolean deleteQuestionnaire(String id) {
        Questionnaire questionnaire = questionnaireRepository.findById(id).orElse(null);
        if (questionnaire == null) {
            return false;
        }

        answerRepository.deleteAll(answerRepository.findByQuestionnaireId(id));
        responseRepository.deleteAll(responseRepository.findByQuestionnaireId(id));
        questionnaireRepository.delete(questionnaire);
        return true;
    }

    @Transactional
    public boolean submitQuestionnaire(String id, SubmitRequest request, String ipAddress) {
        Questionnaire questionnaire = questionnaireRepository.findById(id).orElse(null);
        if (questionnaire == null || !"active".equals(questionnaire.getStatus())) {
            return false;
        }

        if (questionnaire.getDeadline() != null && questionnaire.getDeadline().isBefore(LocalDateTime.now())) {
            return false;
        }

        if (redisService.isSubmitted(id, request.getRespondentId())) {
            return false;
        }

        if (responseRepository.existsByQuestionnaireIdAndRespondentId(id, request.getRespondentId())) {
            redisService.markSubmitted(id, request.getRespondentId());
            return false;
        }

        SurveyResponse response = new SurveyResponse();
        response.setId(UUID.randomUUID().toString());
        response.setQuestionnaire(questionnaire);
        response.setRespondentId(request.getRespondentId());
        response.setSubmittedAt(LocalDateTime.now());

        response = responseRepository.save(response);

        for (SubmitRequest.AnswerDTO answerDTO : request.getAnswers()) {
            Answer answer = new Answer();
            answer.setId(UUID.randomUUID().toString());
            answer.setResponse(response);
            answer.setQuestion(new Question());
            answer.getQuestion().setId(answerDTO.getQuestionId());
            
            Object value = answerDTO.getValue();
            String valueStr;
            if (value instanceof List<?>) {
                List<?> list = (List<?>) value;
                valueStr = String.join(",", list.stream().map(Object::toString).toList());
            } else {
                valueStr = value != null ? value.toString() : "";
            }
            answer.setValue(valueStr);
            answerRepository.save(answer);
        }

        redisService.markSubmitted(id, request.getRespondentId());

        try {
            fingerprintService.createAndSaveFingerprint(
                    id,
                    response.getId(),
                    request,
                    ipAddress,
                    request.getUserAgent(),
                    request.getSubmitDurationSeconds()
            );
        } catch (Exception e) {
        }

        return true;
    }

    public StatisticsResponse getStatistics(String id) {
        Questionnaire questionnaire = questionnaireRepository.findByIdWithQuestions(id);
        if (questionnaire == null) {
            return null;
        }

        List<SurveyResponse> responses = responseRepository.findByQuestionnaireId(id);
        List<Answer> allAnswers = answerRepository.findByQuestionnaireId(id);

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

            Map<String, Integer> statistics = new HashMap<>();
            List<String> textAnswers = new ArrayList<>();

            if ("text".equals(question.getType())) {
                for (Answer answer : questionAnswers) {
                    String value = answer.getValue();
                    if (value != null && !value.trim().isEmpty()) {
                        textAnswers.add(value);
                    }
                }
                statistics.put("text_responses", textAnswers.size());
            } else {
                for (OptionItem option : question.getOptions()) {
                    statistics.put(option.getContent(), 0);
                }

                for (Answer answer : questionAnswers) {
                    String value = answer.getValue();
                    if (value != null && !value.trim().isEmpty()) {
                        if (value.contains(",")) {
                            for (String v : value.split(",")) {
                                String trimmed = v.trim();
                                if (!trimmed.isEmpty()) {
                                    statistics.merge(trimmed, 1, Integer::sum);
                                }
                            }
                        } else {
                            statistics.merge(value.trim(), 1, Integer::sum);
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

    private QuestionnaireDTO toDTO(Questionnaire questionnaire) {
        QuestionnaireDTO dto = new QuestionnaireDTO();
        dto.setId(questionnaire.getId());
        dto.setTitle(questionnaire.getTitle());
        dto.setDescription(questionnaire.getDescription());
        dto.setDeadline(questionnaire.getDeadline());
        dto.setStatus(questionnaire.getStatus());
        dto.setCreatedAt(questionnaire.getCreatedAt());

        Integer responseCount = questionnaireRepository.countResponsesByQuestionnaireId(questionnaire.getId());
        dto.setResponseCount(responseCount != null ? responseCount : 0);

        List<QuestionDTO> questionDTOs = new ArrayList<>();
        for (Question question : questionnaire.getQuestions()) {
            QuestionDTO qdto = new QuestionDTO();
            qdto.setId(question.getId());
            qdto.setType(question.getType());
            qdto.setContent(question.getContent());
            qdto.setOrderIndex(question.getOrderIndex());
            qdto.setRequired(question.getRequired());

            List<OptionDTO> optionDTOs = new ArrayList<>();
            for (OptionItem option : question.getOptions()) {
                OptionDTO odto = new OptionDTO();
                odto.setId(option.getId());
                odto.setContent(option.getContent());
                odto.setOrderIndex(option.getOrderIndex());
                optionDTOs.add(odto);
            }
            qdto.setOptions(optionDTOs);
            questionDTOs.add(qdto);
        }
        dto.setQuestions(questionDTOs);
        dto.setCoverConfig(deserializeCoverConfig(questionnaire.getCoverConfig()));

        return dto;
    }

    private String serializeCoverConfig(Map<String, Object> coverConfig) {
        if (coverConfig == null) return null;
        try {
            return objectMapper.writeValueAsString(coverConfig);
        } catch (Exception e) {
            return null;
        }
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
}
