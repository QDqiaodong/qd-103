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
                processExpiredQuestionnaire(q.getId());
            }
        }

        questionnaires = questionnaireRepository.findAllByOrderByCreatedAtDesc();

        return questionnaires.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public QuestionnaireDTO getQuestionnaire(String id) {
        Questionnaire questionnaire = questionnaireRepository.findByIdWithQuestions(id);
        if (questionnaire == null) {
            return null;
        }

        if ("active".equals(questionnaire.getStatus())
                && questionnaire.getDeadline() != null
                && questionnaire.getDeadline().isBefore(LocalDateTime.now())) {
            processExpiredQuestionnaire(id);
            questionnaire = questionnaireRepository.findByIdWithQuestions(id);
        }

        return toDTO(questionnaire);
    }

    @Transactional
    public void processExpiredQuestionnaire(String questionnaireId) {
        Questionnaire questionnaire = questionnaireRepository.findByIdWithQuestions(questionnaireId);
        if (questionnaire == null) {
            return;
        }

        if (!"active".equals(questionnaire.getStatus())) {
            return;
        }

        if (questionnaire.getDeadline() == null
                || !questionnaire.getDeadline().isBefore(LocalDateTime.now())) {
            return;
        }

        questionnaire.setStatus("expired");
        questionnaireRepository.save(questionnaire);

        snapshotService.createSnapshot(questionnaireId, "expired");
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
            Map<String, Question> existingQuestions = questionnaire.getQuestions().stream()
                    .collect(Collectors.toMap(Question::getId, q -> q));

            Set<String> dtoQuestionIds = dto.getQuestions().stream()
                    .map(QuestionDTO::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            List<String> deletedQuestionIds = existingQuestions.keySet().stream()
                    .filter(qid -> !dtoQuestionIds.contains(qid))
                    .toList();
            if (!deletedQuestionIds.isEmpty()) {
                answerRepository.deleteByQuestionIds(deletedQuestionIds);
            }

            questionnaire.getQuestions().removeIf(q -> deletedQuestionIds.contains(q.getId()));

            List<Question> updatedQuestions = new ArrayList<>();
            for (QuestionDTO qdto : dto.getQuestions()) {
                Question question;
                boolean isNewQuestion = qdto.getId() == null || !existingQuestions.containsKey(qdto.getId());

                if (isNewQuestion) {
                    question = new Question();
                    question.setId(UUID.randomUUID().toString());
                    question.setQuestionnaire(questionnaire);
                } else {
                    question = existingQuestions.get(qdto.getId());
                }

                String oldType = question.getType();
                question.setType(qdto.getType());
                question.setContent(qdto.getContent());
                question.setOrderIndex(qdto.getOrderIndex());
                question.setRequired(qdto.getRequired() != null ? qdto.getRequired() : true);

                if (!isNewQuestion && oldType != null && !oldType.equals(qdto.getType())) {
                    answerRepository.deleteByQuestionId(question.getId());
                    question.getOptions().clear();
                }

                if (qdto.getOptions() != null) {
                    Map<String, OptionItem> existingOptions = question.getOptions().stream()
                            .collect(Collectors.toMap(OptionItem::getId, o -> o));

                    Set<String> dtoOptionIds = qdto.getOptions().stream()
                            .map(OptionDTO::getId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());

                    question.getOptions().removeIf(o -> !dtoOptionIds.contains(o.getId()));

                    List<OptionItem> updatedOptions = new ArrayList<>();
                    for (OptionDTO odto : qdto.getOptions()) {
                        OptionItem option;
                        if (odto.getId() == null || !existingOptions.containsKey(odto.getId())) {
                            option = new OptionItem();
                            option.setId(UUID.randomUUID().toString());
                            option.setQuestion(question);
                        } else {
                            option = existingOptions.get(odto.getId());
                        }
                        option.setContent(odto.getContent());
                        option.setOrderIndex(odto.getOrderIndex());
                        updatedOptions.add(option);
                    }
                    question.getOptions().clear();
                    question.getOptions().addAll(updatedOptions);
                }

                updatedQuestions.add(question);
            }

            questionnaire.getQuestions().clear();
            questionnaire.getQuestions().addAll(updatedQuestions);
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
