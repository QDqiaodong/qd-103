<script setup lang="ts">
import { ref, onMounted, computed, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as api from '../services/api'
import type { SnapshotDetail, QuestionStatistic } from '../types'
import * as echarts from 'echarts'
import WordCloudView from '../components/WordCloudView.vue'
import { analyzeWordCloud, type WordCloudData } from '../lib/wordCloud'

const route = useRoute()
const router = useRouter()

const snapshot = ref<SnapshotDetail | null>(null)
const loading = ref(true)
const wordCloudDataMap = ref<Map<string, WordCloudData>>(new Map())

const snapshotId = computed(() => route.params.id as string)

function getWordCloudData(questionId: string): WordCloudData {
  return wordCloudDataMap.value.get(questionId) || {
    highFrequencyWords: [],
    corePhrases: [],
    answerHeats: [],
    totalAnswers: 0,
    avgWordCount: 0
  }
}

function getSnapshotReasonLabel(reason: string): string {
  if (reason.startsWith('manual_')) {
    return '手动快照'
  }
  const reasonMap: Record<string, string> = {
    'closed_closed': '手动关闭封卷',
    'expired': '到期自动封卷',
    'quota_full_closed': '额满自动封卷'
  }
  return reasonMap[reason] || reason
}

function getSnapshotReasonClass(reason: string): string {
  if (reason.startsWith('manual_')) {
    return 'reason-manual'
  }
  if (reason.startsWith('closed')) {
    return 'reason-closed'
  }
  if (reason === 'expired' || reason.startsWith('expired')) {
    return 'reason-expired'
  }
  if (reason.startsWith('quota_full')) {
    return 'reason-quota'
  }
  return 'reason-manual'
}

function formatDate(dateStr?: string): string {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

onMounted(async () => {
  await loadData()
})

async function loadData() {
  loading.value = true
  try {
    snapshot.value = await api.getSnapshot(snapshotId.value)

    if (snapshot.value?.statistics) {
      wordCloudDataMap.value.clear()
      snapshot.value.statistics.questions.forEach(q => {
        if (q.type === 'text' && q.textAnswers) {
          const wordCloudData = analyzeWordCloud(q.textAnswers)
          wordCloudDataMap.value.set(q.questionId, wordCloudData)
        }
      })
    }

    await nextTick()
    renderCharts()
  } finally {
    loading.value = false
  }
}

function renderCharts() {
  if (!snapshot.value?.statistics) return

  snapshot.value.statistics.questions.forEach((q) => {
    const chartDom = document.getElementById(`chart-${q.questionId}`)
    if (!chartDom) return

    const chart = echarts.init(chartDom)
    const isTextQuestion = q.type === 'text'

    if (isTextQuestion) {
      return
    }

    const option = {
      tooltip: {
        trigger: 'item',
        formatter: '{b}: {c} ({d}%)'
      },
      legend: {
        orient: 'vertical',
        left: 'left'
      },
      series: [
        {
          name: q.content,
          type: 'pie',
          radius: '60%',
          data: Object.entries(q.statistics).map(([name, value]) => ({
            name,
            value
          })),
          emphasis: {
            itemStyle: {
              shadowBlur: 10,
              shadowOffsetX: 0,
              shadowColor: 'rgba(0, 0, 0, 0.5)'
            }
          }
        }
      ],
      color: ['#4F46E5', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6', '#EC4899', '#06B6D4', '#84CC16']
    }

    chart.setOption(option)
  })
}

function goBack() {
  if (snapshot.value) {
    router.push(`/edit/${snapshot.value.questionnaireId}`)
  } else {
    router.push('/')
  }
}

function goToQuestionnaire() {
  if (snapshot.value) {
    router.push(`/edit/${snapshot.value.questionnaireId}`)
  }
}

function goToShare() {
  if (snapshot.value) {
    router.push(`/share/snapshot/${snapshot.value.id}`)
  }
}
</script>

<template>
  <div class="snapshot-page">
    <header class="page-header-bar">
      <div class="container">
        <div class="header-content">
          <div class="header-title-group">
            <h1 class="page-title">历史封卷快照</h1>
            <span class="snapshot-badge" :class="getSnapshotReasonClass(snapshot?.snapshotReason || '')">
              📦 {{ getSnapshotReasonLabel(snapshot?.snapshotReason || '') }}
            </span>
          </div>
          <div class="header-actions">
            <button class="btn btn-outline" @click="goBack">
              返回
            </button>
            <button class="btn btn-outline" @click="goToShare">
              分享结果
            </button>
            <button class="btn btn-outline" @click="goToQuestionnaire">
              查看原问卷
            </button>
          </div>
        </div>
      </div>
    </header>

    <div class="snapshot-banner">
      <div class="container">
        <div class="banner-content">
          <div class="banner-icon">📜</div>
          <div class="banner-text">
            <div class="banner-title">这是一份只读历史快照</div>
            <div class="banner-desc">
              该快照固化了封卷时的题目结构、问卷状态和统计结果，不可编辑，用于活动结案、数据复盘和过程留痕
            </div>
          </div>
          <div class="banner-time">
            <div class="time-label">封卷时间</div>
            <div class="time-value">{{ formatDate(snapshot?.snapshotAt) }}</div>
          </div>
        </div>
      </div>
    </div>

    <main class="container">
      <div v-if="loading" class="loading">
        加载中...
      </div>

      <template v-else-if="snapshot">
        <div class="stats-overview">
          <div class="stat-card card">
            <div class="stat-value">{{ snapshot.statistics?.totalResponses || 0 }}</div>
            <div class="stat-label">总填写人数</div>
          </div>
          <div class="stat-card card">
            <div class="stat-value">{{ snapshot.questionCount || 0 }}</div>
            <div class="stat-label">题目数量</div>
          </div>
          <div class="stat-card card">
            <div class="stat-value status-value">
              <span :class="['status-dot', `status-${snapshot.status}`]"></span>
              {{ snapshot.status === 'closed' ? '已关闭' : snapshot.status === 'expired' ? '已过期' : snapshot.status }}
            </div>
            <div class="stat-label">封卷时状态</div>
          </div>
        </div>

        <div class="questionnaire-info card">
          <div class="info-header">
            <h2 class="info-title">{{ snapshot.title }}</h2>
          </div>
          <p v-if="snapshot.description" class="info-desc">{{ snapshot.description }}</p>
          <div class="info-meta">
            <div class="meta-item">
              <span class="meta-label">创建时间：</span>
              <span class="meta-value">{{ formatDate(snapshot.createdAt) }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">截止时间：</span>
              <span class="meta-value">{{ formatDate(snapshot.deadline) }}</span>
            </div>
          </div>
        </div>

        <div class="questions-section">
          <h2 class="section-title">
            <span class="title-icon">📋</span>
            题目结构（只读）
          </h2>

          <div
            v-for="(q, index) in snapshot.questions"
            :key="q.id"
            class="question-card card"
          >
            <div class="question-header">
              <span class="question-number">Q{{ index + 1 }}</span>
              <span :class="['question-type-badge', `type-${q.type}`]">
                {{ q.type === 'single' ? '单选题' : q.type === 'multiple' ? '多选题' : '填空题' }}
              </span>
              <span v-if="q.required" class="required-badge">必填</span>
            </div>
            <div class="question-content">{{ q.content || '（未设置题目标题）' }}</div>

            <div v-if="q.options && q.options.length > 0" class="options-list">
              <div
                v-for="(opt, optIndex) in q.options"
                :key="opt.id"
                class="option-item"
              >
                <span class="option-letter">{{ String.fromCharCode(65 + optIndex) }}</span>
                <span class="option-content">{{ opt.content }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="charts-section">
          <h2 class="section-title">
            <span class="title-icon">📊</span>
            统计数据（封卷时）
          </h2>

          <div
            v-for="q in snapshot.statistics?.questions"
            :key="q.questionId"
            class="chart-card card"
          >
            <div class="chart-header">
              <h3 class="chart-title">
                {{ q.content }}
                <span class="question-type">
                  {{ q.type === 'single' ? '单选' : q.type === 'multiple' ? '多选' : '填空' }}
                </span>
              </h3>
              <div class="chart-total">共 {{ q.totalResponses }} 填写</div>
            </div>

            <div
              v-if="q.type !== 'text'"
              :id="`chart-${q.questionId}`"
              class="chart-container"
            ></div>

            <div v-else class="text-section">
              <div class="word-cloud-preview">
                <div class="section-subtitle">
                  <span class="subtitle-icon">☁️</span>
                  观点云预览
                </div>
                <WordCloudView :data="getWordCloudData(q.questionId)" />
              </div>

              <div class="text-browser readonly">
                <div class="browser-header">
                  <div class="browser-title">
                    <span class="browser-icon">📝</span>
                    文本回答
                    <span class="answer-count-badge">
                      共 {{ q.totalResponses }} 条
                      <span v-if="q.dedupedTextAnswers && q.distinctTextAnswerCount != null" class="deduped-badge">
                        去重后 {{ q.distinctTextAnswerCount }} 种
                      </span>
                    </span>
                  </div>
                </div>

                <div class="text-answer-list">
                  <template v-if="q.dedupedTextAnswers && q.dedupedTextAnswers.length > 0">
                    <div
                      v-for="(item, index) in q.dedupedTextAnswers.slice(0, 5)"
                      :key="index"
                      class="text-answer-item deduped-item"
                    >
                      <span class="answer-number">{{ index + 1 }}.</span>
                      <span class="answer-content">{{ item.content }}</span>
                      <span class="answer-meta">
                        <span class="answer-count-tag">{{ item.count }} 次</span>
                        <span class="answer-percent">{{ item.percentage.toFixed(1) }}%</span>
                      </span>
                    </div>
                    <div
                      v-if="(q.dedupedTextAnswers?.length || 0) > 5"
                      class="more-hint"
                    >
                      还有 {{ (q.dedupedTextAnswers?.length || 0) - 5 }} 种回答...
                    </div>
                  </template>
                  <template v-else>
                    <div
                      v-for="(answer, index) in (q.textAnswers || []).slice(0, 5)"
                      :key="index"
                      class="text-answer-item"
                    >
                      <span class="answer-number">{{ index + 1 }}.</span>
                      <span class="answer-content">{{ answer }}</span>
                      <span class="answer-length">{{ answer.length }}字</span>
                    </div>
                    <div
                      v-if="(q.textAnswers?.length || 0) > 5"
                      class="more-hint"
                    >
                      还有 {{ (q.textAnswers?.length || 0) - 5 }} 条回答...
                    </div>
                  </template>
                </div>
              </div>
            </div>

            <div class="answer-breakdown">
              <div
                v-for="(count, option) in q.statistics"
                :key="option"
                class="breakdown-item"
              >
                <div class="breakdown-label">{{ option }}</div>
                <div class="breakdown-bar">
                  <div
                    class="breakdown-fill"
                    :style="{ width: `${q.totalResponses > 0 ? (count / q.totalResponses) * 100 : 0}%` }"
                  ></div>
                </div>
                <div class="breakdown-value">
                  {{ count }} ({{ q.totalResponses > 0 ? ((count / q.totalResponses) * 100).toFixed(1) : 0 }}%)
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>
    </main>
  </div>
</template>

<style scoped>
.snapshot-page {
  min-height: 100vh;
  background: var(--color-bg);
}

.page-header-bar {
  background: white;
  border-bottom: 1px solid var(--color-border);
  padding: 16px 0;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-title-group {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
}

.snapshot-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
}

.reason-closed {
  background: #FEF2F2;
  color: #DC2626;
}

.reason-expired {
  background: #FFFBEB;
  color: #D97706;
}

.reason-manual {
  background: #EFF6FF;
  color: #2563EB;
}

.reason-quota {
  background: #ECFDF5;
  color: #059669;
}

.header-actions {
  display: flex;
  gap: 12px;
}

.snapshot-banner {
  background: linear-gradient(135deg, #EEF2FF 0%, #E0E7FF 100%);
  border-bottom: 1px solid #C7D2FE;
  padding: 16px 0;
}

.banner-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.banner-icon {
  font-size: 36px;
  flex-shrink: 0;
}

.banner-text {
  flex: 1;
  min-width: 0;
}

.banner-title {
  font-size: 16px;
  font-weight: 700;
  color: #3730A3;
  margin-bottom: 4px;
}

.banner-desc {
  font-size: 13px;
  color: #6366F1;
  line-height: 1.5;
}

.banner-time {
  text-align: right;
  flex-shrink: 0;
}

.time-label {
  font-size: 12px;
  color: #6366F1;
  margin-bottom: 4px;
}

.time-value {
  font-size: 14px;
  font-weight: 600;
  color: #3730A3;
}

.loading {
  text-align: center;
  padding: 60px 20px;
}

.stats-overview {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin: 24px 0;
}

.stat-card {
  text-align: center;
  padding: 32px;
}

.stat-value {
  font-size: 36px;
  font-weight: 700;
  color: var(--color-primary);
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.status-value {
  font-size: 20px;
  text-transform: capitalize;
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: inline-block;
}

.status-closed {
  background: #DC2626;
}

.status-expired {
  background: #D97706;
}

.status-active {
  background: #10B981;
}

.status-draft {
  background: #6B7280;
}

.stat-label {
  font-size: 14px;
  color: var(--color-text-secondary);
}

.questionnaire-info {
  margin-bottom: 24px;
  padding: 24px;
}

.info-header {
  margin-bottom: 8px;
}

.info-title {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
}

.info-desc {
  color: var(--color-text-secondary);
  margin-bottom: 16px;
  line-height: 1.6;
}

.info-meta {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
  padding-top: 16px;
  border-top: 1px solid var(--color-border);
}

.meta-item {
  font-size: 13px;
}

.meta-label {
  color: var(--color-text-secondary);
}

.meta-value {
  color: var(--color-text);
  font-weight: 500;
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.title-icon {
  font-size: 20px;
}

.questions-section {
  margin-bottom: 32px;
}

.question-card {
  padding: 20px;
  margin-bottom: 16px;
  background: white;
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}

.question-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.question-number {
  background: var(--color-primary);
  color: white;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
}

.question-type-badge {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}

.type-single {
  background: #DBEAFE;
  color: #1D4ED8;
}

.type-multiple {
  background: #D1FAE5;
  color: #047857;
}

.type-text {
  background: #FEF3C7;
  color: #92400E;
}

.required-badge {
  color: #EF4444;
  font-size: 11px;
  font-weight: 500;
}

.question-content {
  font-size: 15px;
  color: var(--color-text);
  line-height: 1.6;
  margin-bottom: 16px;
}

.options-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.option-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: var(--color-bg);
  border-radius: 6px;
  border: 1px solid var(--color-border);
}

.option-letter {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: white;
  border: 1px solid var(--color-border);
  border-radius: 50%;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-secondary);
  flex-shrink: 0;
}

.option-content {
  font-size: 14px;
  color: var(--color-text);
}

.charts-section {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding-bottom: 40px;
}

.chart-card {
  padding: 24px;
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
}

.chart-title {
  font-size: 16px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
}

.question-type {
  font-size: 12px;
  padding: 2px 8px;
  background: var(--color-primary);
  color: white;
  border-radius: 4px;
  font-weight: normal;
}

.chart-total {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.chart-container {
  width: 100%;
  height: 300px;
  margin-bottom: 20px;
}

.text-section {
  display: flex;
  flex-direction: column;
  gap: 20px;
  margin-bottom: 20px;
}

.word-cloud-preview {
  background: white;
  border-radius: var(--radius);
  padding: 20px;
  border: 1px solid var(--color-border);
}

.section-subtitle {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 16px;
  color: var(--color-text);
}

.subtitle-icon {
  font-size: 16px;
}

.text-answer-list {
  display: flex;
  flex-direction: column;
  max-height: 300px;
  overflow-y: auto;
  background: var(--color-bg);
  border-radius: var(--radius);
  border: 1px solid var(--color-border);
}

.text-answer-list.readonly {
  max-height: 240px;
}

.text-answer-item {
  display: flex;
  gap: 8px;
  padding: 12px 18px;
  background: white;
  border-bottom: 1px solid var(--color-border);
  align-items: flex-start;
}

.text-answer-item:last-child {
  border-bottom: none;
}

.answer-number {
  font-weight: 600;
  color: var(--color-primary);
  flex-shrink: 0;
  font-size: 13px;
  padding-top: 1px;
}

.answer-content {
  font-size: 14px;
  color: var(--color-text);
  word-break: break-word;
  line-height: 1.6;
  flex: 1;
}

.answer-length {
  font-size: 11px;
  color: var(--color-text-secondary);
  flex-shrink: 0;
  padding-top: 2px;
}

.text-browser {
  background: var(--color-bg);
  border-radius: var(--radius);
  overflow: hidden;
  border: 1px solid var(--color-border);
}

.browser-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 18px;
  background: white;
  border-bottom: 1px solid var(--color-border);
}

.browser-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
}

.browser-icon {
  font-size: 18px;
}

.answer-count-badge {
  background: var(--color-primary);
  color: white;
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 500;
}

.deduped-badge {
  background: #10B981;
  color: white;
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 8px;
  margin-left: 4px;
  font-weight: 500;
}

.deduped-item .answer-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  flex-shrink: 0;
  padding-top: 1px;
}

.answer-count-tag {
  font-size: 12px;
  font-weight: 600;
  color: #059669;
  background: #ECFDF5;
  padding: 2px 8px;
  border-radius: 10px;
}

.answer-percent {
  font-size: 11px;
  color: var(--color-text-secondary);
}

.more-hint {
  padding: 12px 18px;
  text-align: center;
  font-size: 13px;
  color: var(--color-text-secondary);
  background: #FAFAFA;
}

.answer-breakdown {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.breakdown-item {
  display: grid;
  grid-template-columns: 120px 1fr 100px;
  gap: 12px;
  align-items: center;
}

.breakdown-label {
  font-size: 14px;
  color: var(--color-text);
  word-break: break-word;
}

.breakdown-bar {
  height: 24px;
  background: var(--color-bg);
  border-radius: 4px;
  overflow: hidden;
}

.breakdown-fill {
  height: 100%;
  background: var(--color-primary);
  border-radius: 4px;
  transition: width 0.5s ease;
}

.breakdown-value {
  font-size: 13px;
  color: var(--color-text-secondary);
  text-align: right;
}

@media (max-width: 768px) {
  .stats-overview {
    grid-template-columns: 1fr;
  }

  .breakdown-item {
    grid-template-columns: 1fr;
    gap: 4px;
  }

  .breakdown-bar {
    order: 3;
  }

  .banner-content {
    flex-wrap: wrap;
  }

  .banner-time {
    width: 100%;
    text-align: left;
    padding-top: 8px;
    border-top: 1px solid rgba(199, 210, 254, 0.5);
  }
}
</style>
