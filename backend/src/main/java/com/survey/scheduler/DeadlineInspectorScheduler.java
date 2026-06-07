package com.survey.scheduler;

import com.survey.service.QuestionnaireService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeadlineInspectorScheduler {

    private final QuestionnaireService questionnaireService;

    @Scheduled(cron = "${survey.deadline-inspect.cron:0 */5 * * * *}")
    public void inspectAndCorrectStatuses() {
        log.info("开始执行问卷截止巡检与状态纠偏任务");
        try {
            QuestionnaireService.StatusCorrectionResult result = questionnaireService.correctQuestionnaireStatuses();
            log.info("问卷截止巡检完成：过期纠正{}份，恢复收集中{}份，共计{}份",
                    result.getExpiredCount(),
                    result.getReactivatedCount(),
                    result.getTotalCorrected());
        } catch (Exception e) {
            log.error("问卷截止巡检任务执行异常", e);
        }
    }
}
