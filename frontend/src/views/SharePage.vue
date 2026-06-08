<script setup lang="ts">
import { ref, onMounted, computed, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import * as api from '../services/api'
import type { Questionnaire, StatisticsResponse, QuestionStatistic, SnapshotDetail } from '../types'
import * as echarts from 'echarts'
import { analyzeWordCloud, type WordCloudData } from '../lib/wordCloud'

const route = useRoute()

const loading = ref(true)
const title = ref('')
const description = ref('')
const responseCount = ref(0)
const questionCount = ref(0)
const statistics = ref<StatisticsResponse | null>(null)
const coverConfig = ref<any>(null)
const wordCloudDataMap = ref<Map<string, WordCloudData>>(new Map())
const shareType = ref<'snapshot' | 'questionnaire'>('questionnaire')
const errorMessage = ref('')

const shareId = computed(() => route.params.id as string)

function getWordCloudData(questionId: string): WordCloudData {
  return wordCloudDataMap.value.get(questionId) || {
    highFrequencyWords: [],
    corePhrases: [],
    answerHeats: [],
    totalAnswers: 0,
    avgWordCount: 0
  }
}

onMounted(async () => {
  await loadData()
})

async function loadData() {
  loading.value = true
  errorMessage.value = ''
  try {
    const path = route.path
    if (path.startsWith('/share/snapshot/')) {
      shareType.value = 'snapshot'
      await loadSnapshotData()
    } else {
      shareType.value = 'questionnaire'
      await loadQuestionnaireData()
    }

    if (statistics.value && statistics.value.resultsVisible !== false) {
      wordCloudDataMap.value.clear()
      statistics.value.questions.forEach(q => {
        if (q.type === 'text' && q.textAnswers) {
          const wordCloudData = analyzeWordCloud(q.textAnswers)
          wordCloudDataMap.value.set(q.questionId, wordCloudData)
        }
      })
    }

    await nextTick()
    if (statistics.value && statistics.value.resultsVisible !== false) {
      renderCharts()
    }
  } catch (e: any) {
    errorMessage.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function loadSnapshotData() {
  const snapshot: SnapshotDetail | null = await api.getSnapshot(shareId.value)
  if (!snapshot) {
    throw new Error('快照不存在')
  }
  title.value = snapshot.title
  description.value = snapshot.description
  responseCount.value = snapshot.responseCount
  questionCount.value = snapshot.questionCount
  statistics.value = snapshot.statistics
  coverConfig.value = snapshot.coverConfig
}

async function loadQuestionnaireData() {
  const questionnaire: Questionnaire | null = await api.getQuestionnaire(shareId.value)
  if (!questionnaire) {
    throw new Error('问卷不存在')
  }
  title.value = questionnaire.title
  description.value = questionnaire.description
  responseCount.value = questionnaire.responseCount || 0
  questionCount.value = questionnaire.questions.length
  coverConfig.value = questionnaire.coverConfig

  const stats = await api.getStatistics(shareId.value)
  statistics.value = stats
}

function renderCharts() {
  if (!statistics.value) return

  statistics.value.questions.forEach((q) => {
    const chartDom = document.getElementById(`share-chart-${q.questionId}`)
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

function getTopOptions(q: QuestionStatistic, count: number = 3) {
  return Object.entries(q.statistics)
    .sort((a, b) => b[1] - a[1])
    .slice(0, count)
    .map(([name, value]) => ({
      name,
      value,
      percentage: q.totalResponses > 0 ? ((value / q.totalResponses) * 100).toFixed(1) : '0'
    }))
}

function formatDate(dateStr?: string): string {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  })
}
</script>

<template>
  <div class="share-page">
    <div class="share-container">
      <div v-if="loading" class="loading-state">
        <div class="loading-spinner"></div>
        <p>加载中...</p>
      </div>

      <div v-else-if="errorMessage" class="error-state">
        <div class="error-icon">😕</div>
        <h2 class="error-title">加载失败</h2>
        <p class="error-desc">{{ errorMessage }}</p>
      </div>

      <template v-else>
        <header class="share-header">
          <div class="share-brand">
            <span class="brand-icon">📊</span>
            <span class="brand-text">问卷结果分享</span>
          </div>
          <div class="share-badge">
            {{ shareType === 'snapshot' ? '封卷快照' : '实时结果' }}
          </div>
        </header>

        <div class="share-cover" v-if="coverConfig">
          <h1 class="share-title">{{ title }}</h1>
          <p v-if="description" class="share-desc">{{ description }}</p>
        </div>

        <div v-else class="share-cover simple">
          <h1 class="share-title">{{ title }}</h1>
          <p v-if="description" class="share-desc">{{ description }}</p>
        </div>

        <div class="stats-highlight">
          <div class="highlight-item">
            <div class="highlight-value">{{ responseCount }}</div>
            <div class="highlight-label">参与人数</div>
          </div>
          <div class="highlight-divider"></div>
          <div class="highlight-item">
            <div class="highlight-value">{{ questionCount }}</div>
            <div class="highlight-label">题目数量</div>
          </div>
        </div>

        <div v-if="statistics?.resultsVisible === false" class="results-hidden">
          <div class="results-hidden-icon">🔒</div>
          <h2 class="results-hidden-title">结果暂未公开</h2>
          <p class="results-hidden-desc">
            {{ statistics.visibilityMessage || '该问卷结果暂不可见' }}
          </p>
        </div>

        <div v-else class="results-section">
          <h2 class="section-title">
            <span class="title-icon">📈</span>
            关键统计结果
          </h2>

          <div
            v-for="q in statistics?.questions"
            :key="q.questionId"
            class="question-card"
          >
            <div class="question-header">
              <h3 class="question-title">{{ q.content }}</h3>
              <span class="question-type-tag">
                {{ q.type === 'single' ? '单选' : q.type === 'multiple' ? '多选' : '填空' }}
              </span>
            </div>

            <div class="question-stats">
              <span class="total-responses">共 {{ q.totalResponses }} 人回答</span>
            </div>

            <div v-if="q.type !== 'text'" class="chart-and-ranking">
              <div
                :id="`share-chart-${q.questionId}`"
                class="mini-chart"
              ></div>

              <div class="top-ranking">
                <div class="ranking-title">Top 选项</div>
                <div
                  v-for="(item, index) in getTopOptions(q, 3)"
                  :key="item.name"
                  class="ranking-item"
                >
                  <span class="ranking-number" :class="`rank-${index + 1}`">{{ index + 1 }}</span>
                  <span class="ranking-name">{{ item.name }}</span>
                  <span class="ranking-percent">{{ item.percentage }}%</span>
                </div>
              </div>
            </div>

            <div v-else class="text-highlights">
              <div class="highlights-title">
                <span class="highlights-icon">☁️</span>
                文本回答摘要
              </div>
              <div class="text-stats-row">
                <div class="text-stat-item">
                  <span class="text-stat-value">{{ q.totalResponses }}</span>
                  <span class="text-stat-label">条回答</span>
                </div>
                <div class="text-stat-item">
                  <span class="text-stat-value">{{ q.distinctTextAnswerCount || '-' }}</span>
                  <span class="text-stat-label">种不同观点</span>
                </div>
              </div>
              <div v-if="q.dedupedTextAnswers && q.dedupedTextAnswers.length > 0" class="top-text-answers">
                <div
                  v-for="(item, index) in q.dedupedTextAnswers.slice(0, 3)"
                  :key="index"
                  class="top-text-item"
                >
                  <span class="text-rank" :class="`rank-${index + 1}`">{{ index + 1 }}</span>
                  <span class="text-content">{{ item.content }}</span>
                  <span class="text-count">{{ item.count }} 次</span>
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

        <footer class="share-footer">
          <p>由问卷系统生成 · 感谢参与</p>
        </footer>
      </template>
    </div>
  </div>
</template>

<style scoped>
.share-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px 0;
  display: flex;
  justify-content: center;
  align-items: flex-start;
}

.share-container {
  width: 100%;
  max-width: 640px;
  background: white;
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
  overflow: hidden;
}

.loading-state,
.error-state {
  text-align: center;
  padding: 80px 20px;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid #f3f3f3;
  border-top: 3px solid #4F46E5;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin: 0 auto 16px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.error-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.error-title {
  font-size: 20px;
  font-weight: 600;
  color: #1f2937;
  margin: 0 0 8px;
}

.error-desc {
  color: #6b7280;
  margin: 0;
}

.share-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  background: #fafafa;
  border-bottom: 1px solid #e5e7eb;
}

.share-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #6b7280;
  font-weight: 500;
}

.brand-icon {
  font-size: 18px;
}

.share-badge {
  font-size: 12px;
  padding: 4px 12px;
  background: #4F46E5;
  color: white;
  border-radius: 12px;
  font-weight: 500;
}

.share-cover {
  padding: 32px 24px;
  background: linear-gradient(135deg, #4F46E5 0%, #7C3AED 100%);
  color: white;
  text-align: center;
}

.share-cover.simple {
  background: linear-gradient(135deg, #4F46E5 0%, #7C3AED 100%);
}

.share-title {
  font-size: 24px;
  font-weight: 700;
  margin: 0 0 12px;
  line-height: 1.4;
  word-break: break-word;
}

.share-desc {
  font-size: 14px;
  opacity: 0.9;
  margin: 0;
  line-height: 1.6;
  word-break: break-word;
}

.stats-highlight {
  display: flex;
  padding: 24px;
  background: #f8fafc;
  border-bottom: 1px solid #e5e7eb;
}

.highlight-item {
  flex: 1;
  text-align: center;
}

.highlight-value {
  font-size: 32px;
  font-weight: 700;
  color: #4F46E5;
  margin-bottom: 4px;
}

.highlight-label {
  font-size: 13px;
  color: #6b7280;
}

.highlight-divider {
  width: 1px;
  background: #e5e7eb;
  margin: 0 24px;
}

.results-hidden {
  text-align: center;
  padding: 60px 24px;
}

.results-hidden-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.results-hidden-title {
  font-size: 20px;
  font-weight: 600;
  color: #1f2937;
  margin: 0 0 8px;
}

.results-hidden-desc {
  color: #6b7280;
  margin: 0;
  line-height: 1.6;
}

.results-section {
  padding: 24px;
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
  margin: 0 0 20px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.title-icon {
  font-size: 20px;
}

.question-card {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 16px;
}

.question-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 8px;
  gap: 12px;
}

.question-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
  margin: 0;
  line-height: 1.5;
  flex: 1;
  word-break: break-word;
}

.question-type-tag {
  font-size: 11px;
  padding: 2px 8px;
  background: #eef2ff;
  color: #4F46E5;
  border-radius: 4px;
  font-weight: 500;
  flex-shrink: 0;
}

.question-stats {
  margin-bottom: 16px;
}

.total-responses {
  font-size: 13px;
  color: #6b7280;
}

.chart-and-ranking {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
  align-items: center;
}

.mini-chart {
  width: 180px;
  height: 180px;
  flex-shrink: 0;
}

.top-ranking {
  flex: 1;
  min-width: 0;
}

.ranking-title {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 8px;
}

.ranking-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  font-size: 13px;
}

.ranking-number {
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: 11px;
  font-weight: 700;
  color: white;
  flex-shrink: 0;
}

.rank-1 {
  background: #F59E0B;
}

.rank-2 {
  background: #9CA3AF;
}

.rank-3 {
  background: #D97706;
}

.ranking-name {
  flex: 1;
  color: #374151;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.ranking-percent {
  color: #4F46E5;
  font-weight: 600;
  flex-shrink: 0;
}

.text-highlights {
  margin-bottom: 16px;
}

.highlights-title {
  font-size: 14px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.highlights-icon {
  font-size: 16px;
}

.text-stats-row {
  display: flex;
  gap: 24px;
  margin-bottom: 12px;
}

.text-stat-item {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.text-stat-value {
  font-size: 20px;
  font-weight: 700;
  color: #4F46E5;
}

.text-stat-label {
  font-size: 12px;
  color: #6b7280;
}

.top-text-answers {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.top-text-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #f8fafc;
  border-radius: 8px;
  font-size: 13px;
}

.text-rank {
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: 10px;
  font-weight: 700;
  color: white;
  flex-shrink: 0;
}

.text-content {
  flex: 1;
  color: #374151;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.text-count {
  font-size: 11px;
  color: #6b7280;
  flex-shrink: 0;
}

.answer-breakdown {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.breakdown-item {
  display: grid;
  grid-template-columns: 100px 1fr 80px;
  gap: 8px;
  align-items: center;
  font-size: 12px;
}

.breakdown-label {
  color: #374151;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.breakdown-bar {
  height: 8px;
  background: #f3f4f6;
  border-radius: 4px;
  overflow: hidden;
}

.breakdown-fill {
  height: 100%;
  background: linear-gradient(90deg, #4F46E5, #7C3AED);
  border-radius: 4px;
  transition: width 0.5s ease;
}

.breakdown-value {
  text-align: right;
  color: #6b7280;
  font-size: 11px;
}

.share-footer {
  padding: 20px 24px;
  text-align: center;
  border-top: 1px solid #e5e7eb;
  background: #fafafa;
}

.share-footer p {
  margin: 0;
  font-size: 12px;
  color: #9ca3af;
}

@media (max-width: 640px) {
  .share-page {
    padding: 0;
  }

  .share-container {
    border-radius: 0;
    min-height: 100vh;
  }

  .chart-and-ranking {
    flex-direction: column;
    align-items: center;
  }

  .mini-chart {
    width: 200px;
    height: 200px;
  }

  .top-ranking {
    width: 100%;
  }

  .breakdown-item {
    grid-template-columns: 80px 1fr 70px;
  }
}
</style>
