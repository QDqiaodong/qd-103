package com.survey.service;

import com.survey.model.Questionnaire;
import com.survey.repository.QuestionnaireRepository;
import com.survey.dto.SubmitRequest;
import com.survey.dto.SubmitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
class RateLimitIntegrationTest {

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private QuestionnaireService questionnaireService;

    @MockBean
    private RedisService redisService;

    @MockBean
    private QuestionnaireRepository questionnaireRepository;

    private Questionnaire testQuestionnaire;
    private final String testIp1 = "192.168.1.100";
    private final String testIp2 = "192.168.1.200";

    @BeforeEach
    void setUp() {
        testQuestionnaire = new Questionnaire();
        testQuestionnaire.setId(UUID.randomUUID().toString());
        testQuestionnaire.setTitle("测试问卷");
        testQuestionnaire.setStatus("active");
        testQuestionnaire.setCreatedAt(LocalDateTime.now().minusHours(48));
        testQuestionnaire.setDeadline(LocalDateTime.now().plusHours(24));

        when(questionnaireRepository.findById(testQuestionnaire.getId()))
                .thenReturn(Optional.of(testQuestionnaire));

        ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, Long> windowStart = new ConcurrentHashMap<>();

        when(redisService.isRateLimitAllowed(anyString(), anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    int limit = invocation.getArgument(1);
                    counters.putIfAbsent(key, new AtomicInteger(0));
                    int count = counters.get(key).incrementAndGet();
                    return count <= limit;
                });

        when(redisService.getRateLimitCount(anyString(), anyInt()))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    counters.putIfAbsent(key, new AtomicInteger(0));
                    return counters.get(key).get();
                });

        when(redisService.isSubmitted(anyString(), anyString())).thenReturn(false);
    }

    @Nested
    @DisplayName("限流扣减顺序测试")
    class RateLimitOrderTests {

        @Test
        @DisplayName("同IP冲量被IP限流拦截后，不应耗尽全局额度")
        void ipLimitShouldNotConsumeGlobalQuota() {
            String questionnaireId = testQuestionnaire.getId();
            String floodingIp = "10.0.0.1";
            String normalIp = "10.0.0.2";

            int ipLimit = 10;
            int globalLimit = 100;

            for (int i = 0; i < ipLimit + 5; i++) {
                rateLimitService.tryAcquireIp(testQuestionnaire, floodingIp);
            }

            boolean ipBlocked = rateLimitService.tryAcquireIp(testQuestionnaire, floodingIp);
            assertFalse(ipBlocked, "冲量IP应该被IP限流拦截");

            boolean globalForNormalIp = rateLimitService.tryAcquireGlobal(testQuestionnaire, normalIp);
            assertTrue(globalForNormalIp, "正常IP的全局限流应该有额度可用");
        }

        @Test
        @DisplayName("先IP限流 后全局限流 的顺序验证")
        void ipCheckBeforeGlobalCheck() {
            String badIp = "10.0.0.99";

            for (int i = 0; i < 100; i++) {
                rateLimitService.tryAcquireIp(testQuestionnaire, badIp);
            }

            boolean ipAllowed = rateLimitService.tryAcquireIp(testQuestionnaire, badIp);
            assertFalse(ipAllowed, "异常IP应被IP限流拦截");

            int globalCountBefore = redisService.getRateLimitCount("global:" + testQuestionnaire.getId(), 60);
            assertEquals(0, globalCountBefore, "被IP限流拦截的请求不应消耗全局额度");
        }
    }

    @Nested
    @DisplayName("分时段限流策略测试")
    class PhaseBasedRateLimitTests {

        @Test
        @DisplayName("刚发布期 - 限流阈值最宽松")
        void justReleasedPhaseHasLoosestLimit() {
            Questionnaire justReleasedQ = new Questionnaire();
            justReleasedQ.setId(UUID.randomUUID().toString());
            justReleasedQ.setTitle("刚发布问卷");
            justReleasedQ.setStatus("active");
            justReleasedQ.setCreatedAt(LocalDateTime.now().minusMinutes(10));
            justReleasedQ.setDeadline(LocalDateTime.now().plusDays(7));

            RateLimitService.Phase phase = rateLimitService.determinePhase(justReleasedQ);
            assertEquals(RateLimitService.Phase.JUST_RELEASED, phase);

            int globalLimit = rateLimitService.getGlobalLimit(phase);
            int ipLimit = rateLimitService.getIpLimit(phase);

            assertTrue(globalLimit >= 300, "刚发布期全局限流应最宽松");
            assertTrue(ipLimit >= 30, "刚发布期IP限流应最宽松");
        }

        @Test
        @DisplayName("高峰冲刺期 - 启用冲量检测")
        void peakPhaseEnablesSurgeDetection() {
            Questionnaire peakQ = new Questionnaire();
            peakQ.setId(UUID.randomUUID().toString());
            peakQ.setTitle("高峰问卷");
            peakQ.setStatus("active");
            peakQ.setCreatedAt(LocalDateTime.now().minusDays(2));
            peakQ.setDeadline(LocalDateTime.now().plusDays(1));

            RateLimitService.Phase phase = rateLimitService.determinePhase(peakQ);
            assertEquals(RateLimitService.Phase.PEAK, phase);

            for (int i = 0; i < 20; i++) {
                rateLimitService.tryAcquireIp(peakQ, "1.2.3." + i);
            }

            RateLimitService.RateLimitMetrics metrics = rateLimitService.getMetrics(peakQ, "1.2.3.100");
            assertNotNull(metrics);
            assertEquals("PEAK", metrics.getPhase());
            assertTrue(metrics.getSurgeCurrentCount() > 0, "高峰冲刺期应记录冲量检测数据");
        }

        @Test
        @DisplayName("临近截止期 - 限流阈值最严格")
        void nearDeadlinePhaseHasStrictestLimit() {
            Questionnaire nearDeadlineQ = new Questionnaire();
            nearDeadlineQ.setId(UUID.randomUUID().toString());
            nearDeadlineQ.setTitle("临近截止问卷");
            nearDeadlineQ.setStatus("active");
            nearDeadlineQ.setCreatedAt(LocalDateTime.now().minusDays(6));
            nearDeadlineQ.setDeadline(LocalDateTime.now().plusHours(2));

            RateLimitService.Phase phase = rateLimitService.determinePhase(nearDeadlineQ);
            assertEquals(RateLimitService.Phase.NEAR_DEADLINE, phase);

            int globalLimit = rateLimitService.getGlobalLimit(phase);
            int ipLimit = rateLimitService.getIpLimit(phase);

            assertTrue(globalLimit <= 100, "临近截止期全局限流应最严格");
            assertTrue(ipLimit <= 10, "临近截止期IP限流应最严格");
        }
    }

    @Nested
    @DisplayName("异常冲量快速收紧测试")
    class SurgeDetectionTests {

        @Test
        @DisplayName("高峰冲刺期检测到冲量后阈值收紧")
        void peakPhaseSurgeTightensThreshold() {
            Questionnaire peakQ = new Questionnaire();
            peakQ.setId("surge-test-" + UUID.randomUUID());
            peakQ.setTitle("冲量测试问卷");
            peakQ.setStatus("active");
            peakQ.setCreatedAt(LocalDateTime.now().minusDays(2));
            peakQ.setDeadline(LocalDateTime.now().plusDays(1));

            int baseGlobalLimit = rateLimitService.getGlobalLimit(RateLimitService.Phase.PEAK);
            int baseIpLimit = rateLimitService.getIpLimit(RateLimitService.Phase.PEAK);

            RateLimitService.RateLimitMetrics beforeMetrics = rateLimitService.getMetrics(peakQ, testIp1);
            assertFalse(beforeMetrics.isSurgeDetected(), "冲量前不应检测到冲量");
            assertEquals(baseGlobalLimit, beforeMetrics.getEffectiveGlobalLimit());
            assertEquals(baseIpLimit, beforeMetrics.getEffectiveIpLimit());

            for (int i = 0; i < 50; i++) {
                rateLimitService.tryAcquireIp(peakQ, "10.0." + (i / 20) + "." + (i % 20));
            }

            RateLimitService.RateLimitMetrics afterMetrics = rateLimitService.getMetrics(peakQ, testIp1);
            assertTrue(afterMetrics.isSurgeDetected(), "冲量后应检测到冲量");
            assertTrue(afterMetrics.getEffectiveGlobalLimit() < baseGlobalLimit,
                    "冲量后全局限流阈值应收紧");
            assertTrue(afterMetrics.getEffectiveIpLimit() < baseIpLimit,
                    "冲量后IP限流阈值应收紧");
        }

        @Test
        @DisplayName("冲量探针可验证收紧前后阈值变化")
        void surgeMetricsProbeVerifiesThresholdChange() {
            Questionnaire q = new Questionnaire();
            q.setId("probe-test-" + UUID.randomUUID());
            q.setTitle("探针测试问卷");
            q.setStatus("active");
            q.setCreatedAt(LocalDateTime.now().minusDays(3));
            q.setDeadline(LocalDateTime.now().plusHours(12));

            RateLimitService.RateLimitMetrics metrics = rateLimitService.getMetrics(q, testIp1);

            assertNotNull(metrics);
            assertTrue(metrics.getBaseGlobalLimit() > 0);
            assertTrue(metrics.getBaseIpLimit() > 0);
            assertTrue(metrics.getGlobalWindowSeconds() > 0);
            assertTrue(metrics.getIpWindowSeconds() > 0);
            assertTrue(metrics.getSurgeWindowSeconds() > 0);
            assertTrue(metrics.getSurgeThresholdMultiplier() > 0);
            assertTrue(metrics.getSurgeTightenRatio() > 0 && metrics.getSurgeTightenRatio() < 1);

            assertTrue(metrics.getEffectiveGlobalLimit() > 0);
            assertTrue(metrics.getEffectiveIpLimit() > 0);
            assertNotNull(metrics.getPhase());
        }
    }

    @Nested
    @DisplayName("重复提交不耗公共额度测试")
    class DuplicateSubmitQuotaTests {

        @Test
        @DisplayName("重复提交不应消耗全局限流额度")
        void duplicateSubmitDoesNotConsumeGlobalQuota() {
            String questionnaireId = testQuestionnaire.getId();
            String respondentId = "user-001";
            String ip = "192.168.1.50";

            SubmitRequest request = new SubmitRequest();
            request.setRespondentId(respondentId);

            when(redisService.isSubmitted(questionnaireId, respondentId)).thenReturn(true);

            int globalCountBefore = redisService.getRateLimitCount("global:" + questionnaireId, 60);

            SubmitResult result = questionnaireService.submitQuestionnaire(questionnaireId, request, ip);

            assertFalse(result.isSuccess());
            assertEquals(SubmitResult.ERROR_ALREADY_SUBMITTED, result.getErrorCode());

            int globalCountAfter = redisService.getRateLimitCount("global:" + questionnaireId, 60);
            assertEquals(globalCountBefore, globalCountAfter,
                    "重复提交不应消耗全局限流额度");
        }

        @Test
        @DisplayName("同IP冲量后，另一IP首次提交应成功")
        void anotherIpFirstSubmitSucceedsAfterIpFlood() {
            String questionnaireId = testQuestionnaire.getId();
            String floodingIp = "172.16.0.100";
            String normalIp = "172.16.0.200";

            int ipLimit = 20;
            for (int i = 0; i < ipLimit + 10; i++) {
                rateLimitService.tryAcquireIp(testQuestionnaire, floodingIp);
            }

            boolean floodingIpBlocked = !rateLimitService.tryAcquireIp(testQuestionnaire, floodingIp);
            assertTrue(floodingIpBlocked, "冲量IP应被IP限流拦截");

            boolean normalIpAllowed = rateLimitService.tryAcquireIp(testQuestionnaire, normalIp);
            assertTrue(normalIpAllowed, "另一IP首次提交应通过IP限流");

            boolean globalAllowed = rateLimitService.tryAcquireGlobal(testQuestionnaire, normalIp);
            assertTrue(globalAllowed, "另一IP首次提交应通过全局限流");
        }
    }

    @Nested
    @DisplayName("限流指标探针测试")
    class MetricsProbeTests {

        @Test
        @DisplayName("getMetrics 返回完整的限流指标")
        void getMetricsReturnsCompleteMetrics() {
            RateLimitService.RateLimitMetrics metrics = rateLimitService.getMetrics(testQuestionnaire, testIp1);

            assertNotNull(metrics);
            assertNotNull(metrics.getPhase());
            assertTrue(metrics.getBaseGlobalLimit() > 0);
            assertTrue(metrics.getEffectiveGlobalLimit() > 0);
            assertTrue(metrics.getGlobalCurrentCount() >= 0);
            assertTrue(metrics.getGlobalWindowSeconds() > 0);
            assertTrue(metrics.getBaseIpLimit() > 0);
            assertTrue(metrics.getEffectiveIpLimit() > 0);
            assertTrue(metrics.getIpCurrentCount() >= 0);
            assertTrue(metrics.getIpWindowSeconds() > 0);
            assertTrue(metrics.getSurgeCurrentCount() >= 0);
            assertTrue(metrics.getSurgeWindowSeconds() > 0);
            assertTrue(metrics.getSurgeThresholdMultiplier() > 0);
            assertTrue(metrics.getSurgeTightenRatio() > 0);
        }

        @Test
        @DisplayName("压测验证：冲量前后有效阈值变化可被探针观测")
        void surgeThresholdChangeObservableViaProbe() {
            Questionnaire q = new Questionnaire();
            q.setId("probe-surge-" + UUID.randomUUID());
            q.setTitle("探针冲量测试");
            q.setStatus("active");
            q.setCreatedAt(LocalDateTime.now().minusDays(2));
            q.setDeadline(LocalDateTime.now().plusDays(1));

            RateLimitService.RateLimitMetrics before = rateLimitService.getMetrics(q, testIp1);
            int beforeGlobalLimit = before.getEffectiveGlobalLimit();
            int beforeIpLimit = before.getEffectiveIpLimit();

            for (int i = 0; i < 100; i++) {
                rateLimitService.tryAcquireIp(q, "192.168." + (i / 30) + "." + (i % 30));
            }

            RateLimitService.RateLimitMetrics after = rateLimitService.getMetrics(q, testIp1);

            assertTrue(after.isSurgeDetected(), "压测冲量后应检测到冲量");
            assertTrue(after.getEffectiveGlobalLimit() < beforeGlobalLimit,
                    "冲量后全局限流阈值应下降: " + beforeGlobalLimit + " -> " + after.getEffectiveGlobalLimit());
            assertTrue(after.getEffectiveIpLimit() < beforeIpLimit,
                    "冲量后IP限流阈值应下降: " + beforeIpLimit + " -> " + after.getEffectiveIpLimit());
        }
    }
}
