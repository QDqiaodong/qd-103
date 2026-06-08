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
    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;
    private final FingerprintService fingerprintService;
    private final SnapshotService snapshotService;

    public List<QuestionnaireDTO> getAllQuestionnaires() {
        return getAllQuestionnaires(null);
    }

    public List<QuestionnaireDTO> getAllQuestionnaires(String viewerToken) {
        List<Questionnaire> questionnaires = questionnaireRepository.findAllByOrderByCreatedAtDesc();

        LocalDateTime now = LocalDateTime.now();
        for (Questionnaire q : questionnaires) {
            if ("active".equals(q.getStatus())) {
                if (q.getDeadline() != null && q.getDeadline().isBefore(now)) {
                    processExpiredQuestionnaire(q.getId());
                } else if (q.getMaxResponses() != null && q.getMaxResponses() > 0) {
                    int count = responseRepository.countByQuestionnaireId(q.getId());
                    if (count >= q.getMaxResponses()) {
                        processQuotaFullQuestionnaire(q.getId());
                    }
                }
            }
        }

        questionnaires = questionnaireRepository.findAllByOrderByCreatedAtDesc();

        final String finalViewerToken = viewerToken;
        return questionnaires.stream()
                .map(q -> toDTO(q, false, finalViewerToken))
                .collect(Collectors.toList());
    }

    public QuestionnaireDTO getQuestionnaire(String id) {
        return getQuestionnaire(id, null);
    }

    public QuestionnaireDTO getQuestionnaire(String id, String viewerToken) {
        return getQuestionnaire(id, viewerToken, null);
    }

    public QuestionnaireDTO getQuestionnaire(String id, String viewerToken, String accessPassword) {
        Questionnaire questionnaire = questionnaireRepository.findByIdWithQuestions(id);
        if (questionnaire == null) {
            return null;
        }

        if ("active".equals(questionnaire.getStatus())) {
            if (questionnaire.getDeadline() != null && questionnaire.getDeadline().isBefore(LocalDateTime.now())) {
                processExpiredQuestionnaire(id);
                questionnaire = questionnaireRepository.findByIdWithQuestions(id);
            } else if (questionnaire.getMaxResponses() != null && questionnaire.getMaxResponses() > 0) {
                int count = responseRepository.countByQuestionnaireId(id);
                if (count >= questionnaire.getMaxResponses()) {
                    processQuotaFullQuestionnaire(id);
                    questionnaire = questionnaireRepository.findByIdWithQuestions(id);
                }
            }
        }

        boolean isCreator = viewerToken != null && viewerToken.equals(questionnaire.getCreatorToken());
        boolean hasPassword = questionnaire.getAccessPassword() != null && !questionnaire.getAccessPassword().trim().isEmpty();
        boolean passwordVerified = !hasPassword || isCreator ||
                (accessPassword != null && accessPassword.equals(questionnaire.getAccessPassword()));

        return toDTO(questionnaire, false, viewerToken, passwordVerified);
    }

    public boolean verifyAccessPassword(String id, String accessPassword) {
        Questionnaire questionnaire = questionnaireRepository.findById(id).orElse(null);
        if (questionnaire == null) {
            return false;
        }
        String storedPassword = questionnaire.getAccessPassword();
        if (storedPassword == null || storedPassword.trim().isEmpty()) {
            return true;
        }
        return storedPassword.equals(accessPassword);
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

    private boolean isQuotaFull(Questionnaire questionnaire) {
        if (questionnaire.getMaxResponses() == null || questionnaire.getMaxResponses() <= 0) {
            return false;
        }
        int count = responseRepository.countByQuestionnaireId(questionnaire.getId());
        return count >= questionnaire.getMaxResponses();
    }

    @Transactional
    public void processQuotaFullQuestionnaire(String questionnaireId) {
        Questionnaire questionnaire = questionnaireRepository.findByIdWithQuestions(questionnaireId);
        if (questionnaire == null) {
            return;
        }

        if (!"active".equals(questionnaire.getStatus())) {
            return;
        }

        if (questionnaire.getMaxResponses() == null || questionnaire.getMaxResponses() <= 0) {
            return;
        }

        int currentCount = responseRepository.countByQuestionnaireId(questionnaireId);
        if (currentCount < questionnaire.getMaxResponses()) {
            return;
        }

        questionnaire.setStatus("closed");
        questionnaireRepository.save(questionnaire);

        snapshotService.createSnapshot(questionnaireId, "quota_full");
    }

    @Transactional
    public QuestionnaireDTO createQuestionnaire(QuestionnaireDTO dto) {
        Questionnaire questionnaire = new Questionnaire();
        questionnaire.setId(UUID.randomUUID().toString());
        questionnaire.setTitle(dto.getTitle());
        questionnaire.setDescription(dto.getDescription());
        questionnaire.setDeadline(dto.getDeadline());
        questionnaire.setStatus(dto.getStatus() != null ? dto.getStatus() : "active");
        questionnaire.setResultVisibility(dto.getResultVisibility() != null ? dto.getResultVisibility() : "INSTANT_PUBLIC");
        questionnaire.setCreatorToken("ct_" + UUID.randomUUID().toString().replace("-", ""));
        questionnaire.setCreatedAt(LocalDateTime.now());
        questionnaire.setCoverConfig(serializeCoverConfig(dto.getCoverConfig()));
        questionnaire.setAccessPassword(dto.getAccessPassword());
        questionnaire.setMaxResponses(dto.getMaxResponses());
        questionnaire.setClosedMessage(dto.getClosedMessage());

        questionnaire = questionnaireRepository.save(questionnaire);

        Map<String, String> questionIdMap = new HashMap<>();
        Map<String, Map<String, String>> optionIdMap = new HashMap<>();

        if (dto.getQuestions() != null) {
            for (QuestionDTO qdto : dto.getQuestions()) {
                Question question = new Question();
                String newQuestionId = UUID.randomUUID().toString();
                question.setId(newQuestionId);
                question.setQuestionnaire(questionnaire);
                question.setType(qdto.getType());
                question.setContent(qdto.getContent());
                question.setOrderIndex(qdto.getOrderIndex());
                question.setRequired(qdto.getRequired() != null ? qdto.getRequired() : true);
                question.setShowCondition(qdto.getShowCondition());

                if (qdto.getId() != null) {
                    questionIdMap.put(qdto.getId(), newQuestionId);
                }

                Map<String, String> optMap = new HashMap<>();
                if (qdto.getOptions() != null) {
                    for (OptionDTO odto : qdto.getOptions()) {
                        OptionItem option = new OptionItem();
                        String newOptionId = UUID.randomUUID().toString();
                        option.setId(newOptionId);
                        option.setQuestion(question);
                        option.setContent(odto.getContent());
                        option.setOrderIndex(odto.getOrderIndex());
                        option.setTerminateSurvey(odto.getTerminateSurvey() != null ? odto.getTerminateSurvey() : false);
                        option.setTerminateMessage(odto.getTerminateMessage());
                        question.getOptions().add(option);

                        if (odto.getId() != null) {
                            optMap.put(odto.getId(), newOptionId);
                        }
                    }
                }
                if (qdto.getId() != null) {
                    optionIdMap.put(qdto.getId(), optMap);
                }

                question = questionRepository.save(question);
                questionnaire.getQuestions().add(question);
            }

            updateShowConditionIds(questionnaire.getQuestions(), questionIdMap, optionIdMap);
        }

        return toDTO(questionnaire, true, questionnaire.getCreatorToken());
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
        if (dto.getResultVisibility() != null) {
            questionnaire.setResultVisibility(dto.getResultVisibility());
        }
        if (dto.getCoverConfig() != null) {
            questionnaire.setCoverConfig(serializeCoverConfig(dto.getCoverConfig()));
        }
        if (dto.getAccessPassword() != null) {
            questionnaire.setAccessPassword(dto.getAccessPassword().trim().isEmpty() ? null : dto.getAccessPassword());
        }
        if (dto.getMaxResponses() != null) {
            questionnaire.setMaxResponses(dto.getMaxResponses() > 0 ? dto.getMaxResponses() : null);
        }
        if (dto.getClosedMessage() != null) {
            questionnaire.setClosedMessage(dto.getClosedMessage().trim().isEmpty() ? null : dto.getClosedMessage());
        }

        questionnaire = questionnaireRepository.save(questionnaire);

        Map<String, String> questionIdMap = new HashMap<>();
        Map<String, Map<String, String>> optionIdMap = new HashMap<>();

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

                if (qdto.getId() != null) {
                    questionIdMap.put(qdto.getId(), question.getId());
                }

                String oldType = question.getType();
                question.setType(qdto.getType());
                question.setContent(qdto.getContent());
                question.setOrderIndex(qdto.getOrderIndex());
                question.setRequired(qdto.getRequired() != null ? qdto.getRequired() : true);
                question.setShowCondition(qdto.getShowCondition());

                if (!isNewQuestion && oldType != null && !oldType.equals(qdto.getType())) {
                    answerRepository.deleteByQuestionId(question.getId());
                    question.getOptions().clear();
                }

                Map<String, String> optMap = new HashMap<>();
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
                        option.setTerminateSurvey(odto.getTerminateSurvey() != null ? odto.getTerminateSurvey() : false);
                        option.setTerminateMessage(odto.getTerminateMessage());
                        updatedOptions.add(option);

                        if (odto.getId() != null) {
                            optMap.put(odto.getId(), option.getId());
                        }
                    }
                    question.getOptions().clear();
                    question.getOptions().addAll(updatedOptions);
                }
                if (qdto.getId() != null) {
                    optionIdMap.put(qdto.getId(), optMap);
                }

                updatedQuestions.add(question);
            }

            questionnaire.getQuestions().clear();
            questionnaire.getQuestions().addAll(updatedQuestions);

            updateShowConditionIds(questionnaire.getQuestions(), questionIdMap, optionIdMap);
        }

        questionnaire = questionnaireRepository.save(questionnaire);

        if (dto.getStatus() != null && "closed".equals(dto.getStatus()) && !"closed".equals(oldStatus)) {
            snapshotService.createSnapshot(id, "closed");
        }

        return toDTO(questionnaire, false, questionnaire.getCreatorToken());
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
    public SubmitResult submitQuestionnaire(String id, SubmitRequest request, String ipAddress) {
        Questionnaire questionnaire = questionnaireRepository.findById(id).orElse(null);
        if (questionnaire == null) {
            return SubmitResult.fail(SubmitResult.ERROR_NOT_FOUND, "问卷不存在");
        }

        String storedPassword = questionnaire.getAccessPassword();
        boolean hasPassword = storedPassword != null && !storedPassword.trim().isEmpty();
        if (hasPassword) {
            if (request.getAccessPassword() == null || request.getAccessPassword().isEmpty()) {
                return SubmitResult.fail(SubmitResult.ERROR_PASSWORD_REQUIRED, "请输入访问口令");
            }
            if (!storedPassword.equals(request.getAccessPassword())) {
                return SubmitResult.fail(SubmitResult.ERROR_PASSWORD_INVALID, "访问口令错误");
            }
        }

        if (!"active".equals(questionnaire.getStatus())) {
            return SubmitResult.fail(SubmitResult.ERROR_NOT_ACTIVE, "问卷未激活");
        }

        if (questionnaire.getDeadline() != null && questionnaire.getDeadline().isBefore(LocalDateTime.now())) {
            return SubmitResult.fail(SubmitResult.ERROR_EXPIRED, "问卷已截止");
        }

        if (isQuotaFull(questionnaire)) {
            return SubmitResult.fail(SubmitResult.ERROR_QUOTA_FULL,
                    questionnaire.getClosedMessage() != null && !questionnaire.getClosedMessage().trim().isEmpty()
                            ? questionnaire.getClosedMessage()
                            : "问卷已达到最大回收份数，感谢您的关注");
        }

        if (!rateLimitService.tryAcquireIp(questionnaire, ipAddress)) {
            return SubmitResult.fail(SubmitResult.ERROR_RATE_LIMITED, "当前提交人数较多，请稍后再试");
        }

        if (redisService.isSubmitted(id, request.getRespondentId())) {
            return SubmitResult.fail(SubmitResult.ERROR_ALREADY_SUBMITTED, "您已经提交过");
        }

        if (responseRepository.existsByQuestionnaireIdAndRespondentId(id, request.getRespondentId())) {
            redisService.markSubmitted(id, request.getRespondentId());
            return SubmitResult.fail(SubmitResult.ERROR_ALREADY_SUBMITTED, "您已经提交过");
        }

        if (!rateLimitService.tryAcquireGlobal(questionnaire, ipAddress)) {
            return SubmitResult.fail(SubmitResult.ERROR_RATE_LIMITED, "当前提交人数较多，请稍后再试");
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

        if (questionnaire.getMaxResponses() != null && questionnaire.getMaxResponses() > 0) {
            int currentCount = responseRepository.countByQuestionnaireId(id);
            if (currentCount >= questionnaire.getMaxResponses()) {
                processQuotaFullQuestionnaire(id);
            }
        }

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

        return SubmitResult.success();
    }

    private boolean isStatisticsVisible(Questionnaire questionnaire, String viewerToken) {
        if (viewerToken != null && viewerToken.equals(questionnaire.getCreatorToken())) {
            return true;
        }

        String visibility = questionnaire.getResultVisibility();
        if (visibility == null) {
            visibility = "INSTANT_PUBLIC";
        }

        switch (visibility) {
            case "INSTANT_PUBLIC":
                return true;
            case "AFTER_DEADLINE":
                String status = questionnaire.getStatus();
                if ("closed".equals(status) || "expired".equals(status)) {
                    return true;
                }
                if (questionnaire.getDeadline() != null
                        && questionnaire.getDeadline().isBefore(LocalDateTime.now())) {
                    return true;
                }
                return false;
            case "PRIVATE":
                return false;
            default:
                return true;
        }
    }

    private String getVisibilityMessage(Questionnaire questionnaire, boolean isVisible) {
        if (isVisible) {
            return "结果可见";
        }

        String visibility = questionnaire.getResultVisibility();
        if (visibility == null) {
            visibility = "INSTANT_PUBLIC";
        }

        switch (visibility) {
            case "AFTER_DEADLINE":
                if (questionnaire.getDeadline() != null) {
                    return "结果将在截止后公开";
                }
                return "结果将在问卷结束后公开";
            case "PRIVATE":
                return "结果仅创建者可见";
            default:
                return "结果可见";
        }
    }

    public StatisticsResponse getStatistics(String id) {
        return getStatistics(id, null);
    }

    public StatisticsResponse getStatistics(String id, String viewerToken) {
        Questionnaire questionnaire = questionnaireRepository.findByIdWithQuestions(id);
        if (questionnaire == null) {
            return null;
        }

        boolean visible = isStatisticsVisible(questionnaire, viewerToken);

        StatisticsResponse stats = new StatisticsResponse();
        stats.setResultsVisible(visible);
        stats.setResultVisibility(questionnaire.getResultVisibility());
        stats.setVisibilityMessage(getVisibilityMessage(questionnaire, visible));

        if (!visible) {
            stats.setTotalResponses(0);
            stats.setQuestions(new ArrayList<>());
            return stats;
        }

        List<SurveyResponse> responses = responseRepository.findByQuestionnaireId(id);
        List<Answer> allAnswers = answerRepository.findByQuestionnaireId(id);

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

    private QuestionnaireDTO toDTO(Questionnaire questionnaire) {
        return toDTO(questionnaire, false, null, true);
    }

    private QuestionnaireDTO toDTO(Questionnaire questionnaire, boolean includeCreatorToken) {
        return toDTO(questionnaire, includeCreatorToken, null, true);
    }

    @SuppressWarnings("unchecked")
    private void updateShowConditionIds(List<Question> questions, Map<String, String> questionIdMap, Map<String, Map<String, String>> optionIdMap) {
        for (Question question : questions) {
            String showCondition = question.getShowCondition();
            if (showCondition == null || showCondition.trim().isEmpty()) {
                continue;
            }
            try {
                Map<String, Object> condition = objectMapper.readValue(showCondition, Map.class);
                String dependOnQuestionId = (String) condition.get("dependOnQuestionId");
                List<String> optionIds = (List<String>) condition.get("optionIds");

                if (dependOnQuestionId != null && questionIdMap.containsKey(dependOnQuestionId)) {
                    String newQuestionId = questionIdMap.get(dependOnQuestionId);
                    condition.put("dependOnQuestionId", newQuestionId);

                    if (optionIds != null && !optionIds.isEmpty()) {
                        Map<String, String> optMap = optionIdMap.get(dependOnQuestionId);
                        if (optMap != null) {
                            List<String> newOptionIds = new ArrayList<>();
                            for (String optId : optionIds) {
                                if (optMap.containsKey(optId)) {
                                    newOptionIds.add(optMap.get(optId));
                                } else {
                                    newOptionIds.add(optId);
                                }
                            }
                            condition.put("optionIds", newOptionIds);
                        }
                    }
                }

                question.setShowCondition(objectMapper.writeValueAsString(condition));
            } catch (Exception e) {
                // 解析失败，保持原样
            }
        }
    }

    private QuestionnaireDTO toDTO(Questionnaire questionnaire, boolean includeCreatorToken, String viewerToken) {
        return toDTO(questionnaire, includeCreatorToken, viewerToken, true);
    }

    private QuestionnaireDTO toDTO(Questionnaire questionnaire, boolean includeCreatorToken, String viewerToken, boolean passwordVerified) {
        QuestionnaireDTO dto = new QuestionnaireDTO();
        dto.setId(questionnaire.getId());
        dto.setTitle(questionnaire.getTitle());
        dto.setDescription(questionnaire.getDescription());
        dto.setDeadline(questionnaire.getDeadline());
        dto.setStatus(questionnaire.getStatus());
        dto.setResultVisibility(questionnaire.getResultVisibility());
        if (includeCreatorToken) {
            dto.setCreatorToken(questionnaire.getCreatorToken());
        }
        dto.setCreatedAt(questionnaire.getCreatedAt());

        boolean hasPassword = questionnaire.getAccessPassword() != null && !questionnaire.getAccessPassword().trim().isEmpty();
        boolean isCreator = viewerToken != null && viewerToken.equals(questionnaire.getCreatorToken());
        dto.setPasswordProtected(hasPassword);
        if (isCreator) {
            dto.setAccessPassword(questionnaire.getAccessPassword());
        }

        dto.setMaxResponses(questionnaire.getMaxResponses());
        dto.setClosedMessage(questionnaire.getClosedMessage());

        boolean resultsVisible = isStatisticsVisible(questionnaire, viewerToken);
        if (resultsVisible) {
            Integer responseCount = questionnaireRepository.countResponsesByQuestionnaireId(questionnaire.getId());
            dto.setResponseCount(responseCount != null ? responseCount : 0);
        } else {
            dto.setResponseCount(null);
        }

        List<QuestionDTO> questionDTOs = new ArrayList<>();
        if (passwordVerified) {
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
        }
        dto.setQuestions(questionDTOs);
        dto.setCoverConfig(deserializeCoverConfig(questionnaire.getCoverConfig()));

        return dto;
    }

    @Transactional
    public StatusCorrectionResult correctQuestionnaireStatuses() {
        LocalDateTime now = LocalDateTime.now();
        StatusCorrectionResult result = new StatusCorrectionResult();

        List<Questionnaire> activeExpired = questionnaireRepository.findActiveAndExpired(now);
        for (Questionnaire q : activeExpired) {
            q.setStatus("expired");
            questionnaireRepository.save(q);
            snapshotService.createSnapshot(q.getId(), "expired");
            result.addExpiredCount();
        }

        List<Questionnaire> allActive = questionnaireRepository.findAll().stream()
                .filter(q -> "active".equals(q.getStatus()))
                .toList();
        for (Questionnaire q : allActive) {
            if (q.getMaxResponses() != null && q.getMaxResponses() > 0) {
                int count = responseRepository.countByQuestionnaireId(q.getId());
                if (count >= q.getMaxResponses()) {
                    q.setStatus("closed");
                    questionnaireRepository.save(q);
                    snapshotService.createSnapshot(q.getId(), "quota_full");
                    result.addQuotaFullCount();
                }
            }
        }

        List<Questionnaire> expiredReactivated = questionnaireRepository.findExpiredButDeadlineNotReached(now);
        for (Questionnaire q : expiredReactivated) {
            q.setStatus("active");
            questionnaireRepository.save(q);
            result.addReactivatedCount();
        }

        result.setInspectTime(now);
        return result;
    }

    public static class StatusCorrectionResult {
        private LocalDateTime inspectTime;
        private int expiredCount;
        private int reactivatedCount;
        private int quotaFullCount;

        public LocalDateTime getInspectTime() {
            return inspectTime;
        }

        public void setInspectTime(LocalDateTime inspectTime) {
            this.inspectTime = inspectTime;
        }

        public int getExpiredCount() {
            return expiredCount;
        }

        public void addExpiredCount() {
            this.expiredCount++;
        }

        public int getReactivatedCount() {
            return reactivatedCount;
        }

        public void addReactivatedCount() {
            this.reactivatedCount++;
        }

        public int getQuotaFullCount() {
            return quotaFullCount;
        }

        public void addQuotaFullCount() {
            this.quotaFullCount++;
        }

        public int getTotalCorrected() {
            return expiredCount + reactivatedCount + quotaFullCount;
        }
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
