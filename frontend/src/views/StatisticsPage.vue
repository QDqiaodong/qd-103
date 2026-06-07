<script setup lang="ts">
import { ref, onMounted, computed, watch, nextTick, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as api from '../services/api'
import type { Questionnaire, StatisticsResponse, QuestionStatistic, ResultVisibility, DedupedTextAnswer } from '../types'
import { RESULT_VISIBILITY_OPTIONS } from '../types'
import * as echarts from 'echarts'
import WordCloudView from '../components/WordCloudView.vue'
import { analyzeWordCloud, type WordCloudData } from '../lib/wordCloud'

const route = useRoute()
const router = useRouter()

const questionnaire = ref<Questionnaire | null>(null)
const statistics = ref<StatisticsResponse | null>(null)
const loading = ref(true)
const wordCloudDataMap = ref<Map<string, WordCloudData>>(new Map())

const textBrowserState = reactive<Map<string, {
  expanded: boolean
  sortBy: 'default' | 'latest' | 'longest' | 'count'
  keyword: string
  visibleCount: number
  viewMode: 'original' | 'deduped'
}>>(new Map())

function getTextState(questionId: string) {
  if (!textBrowserState.has(questionId)) {
    textBrowserState.set(questionId, {
      expanded: false,
      sortBy: 'default',
      keyword: '',
      visibleCount: 5,
      viewMode: 'deduped'
    })
  }
  return textBrowserState.get(questionId)!
}

function getSortedAnswers(questionId: string, textAnswers: string[]): string[] {
  const state = getTextState(questionId)
  let answers = [...textAnswers]

  if (state.sortBy === 'longest') {
    answers.sort((a, b) => b.length - a.length)
  } else if (state.sortBy === 'latest') {
    answers.reverse()
  }

  if (state.keyword.trim()) {
    const keyword = state.keyword.toLowerCase().trim()
    answers = answers.filter(a => a.toLowerCase().includes(keyword))
  }

  return answers
}

function getVisibleAnswers(questionId: string, textAnswers: string[]): string[] {
  const state = getTextState(questionId)
  const sorted = getSortedAnswers(questionId, textAnswers)
  if (state.expanded) {
    return sorted
  }
  return sorted.slice(0, state.visibleCount)
}

function toggleExpand(questionId: string) {
  const state = getTextState(questionId)
  state.expanded = !state.expanded
}

function setSortBy(questionId: string, sortBy: 'default' | 'latest' | 'longest' | 'count') {
  const state = getTextState(questionId)
  state.sortBy = sortBy
}

function setViewMode(questionId: string, viewMode: 'original' | 'deduped') {
  const state = getTextState(questionId)
  state.viewMode = viewMode
  if (viewMode === 'deduped' && state.sortBy === 'default') {
    state.sortBy = 'count'
  }
}

function getSortedDedupedAnswers(questionId: string, dedupedAnswers: DedupedTextAnswer[]): DedupedTextAnswer[] {
  const state = getTextState(questionId)
  let answers = [...dedupedAnswers]

  if (state.sortBy === 'longest') {
    answers.sort((a, b) => b.content.length - a.content.length)
  } else if (state.sortBy === 'count') {
    answers.sort((a, b) => b.count - a.count)
  }

  if (state.keyword.trim()) {
    const keyword = state.keyword.toLowerCase().trim()
    answers = answers.filter(a => a.content.toLowerCase().includes(keyword))
  }

  return answers
}

function getVisibleDedupedAnswers(questionId: string, dedupedAnswers: DedupedTextAnswer[]): DedupedTextAnswer[] {
  const state = getTextState(questionId)
  const sorted = getSortedDedupedAnswers(questionId, dedupedAnswers)
  if (state.expanded) {
    return sorted
  }
  return sorted.slice(0, state.visibleCount)
}

function highlightKeyword(text: string, keyword: string): string {
  if (!keyword.trim()) return text
  const regex = new RegExp(`(${keyword.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi')
  return text.replace(regex, '<mark class="keyword-highlight">$1</mark>')
}

const questionnaireId = computed(() => route.params.id as string)

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

const isCreator = computed(() => {
  return api.isCreator(questionnaireId.value)
})

async function loadData() {
  loading.value = true
  try {
    const creatorToken = api.getCreatorToken(questionnaireId.value) || undefined
    questionnaire.value = await api.getQuestionnaire(questionnaireId.value, creatorToken)
    statistics.value = await api.getStatistics(questionnaireId.value, creatorToken)
    
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
  } finally {
    loading.value = false
  }
}

function renderCharts() {
  if (!statistics.value) return

  statistics.value.questions.forEach((q, index) => {
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

function exportData() {
  if (!statistics.value || !questionnaire.value) return

  const lines = ['题目\t类型\t选项/内容\t数量\t占比']

  statistics.value.questions.forEach((q, qIndex) => {
    const total = Object.values(q.statistics).reduce((a, b) => a + b, 0)
    if (q.type === 'text') {
      lines.push(`${q.content}\t填空\t-\t${total}\t-`)
      if (q.dedupedTextAnswers && q.dedupedTextAnswers.length > 0) {
        lines.push(`\t去重统计\t共 ${q.distinctTextAnswerCount} 种不重复回答\t-\t-`)
        q.dedupedTextAnswers.forEach((item, aIndex) => {
          const escapedAnswer = item.content.replace(/\t/g, ' ').replace(/\n/g, ' ')
          lines.push(`\t\t${escapedAnswer}\t${item.count}\t${item.percentage.toFixed(1)}%`)
        })
        lines.push('')
        lines.push(`\t原始列表\t共 ${q.textAnswers?.length || 0} 条原始回答\t-\t-`)
      }
      if (q.textAnswers && q.textAnswers.length > 0) {
        q.textAnswers.forEach((answer, aIndex) => {
          const escapedAnswer = answer.replace(/\t/g, ' ').replace(/\n/g, ' ')
          lines.push(`\t\t${escapedAnswer}\t1\t${total > 0 ? ((1 / total) * 100).toFixed(1) + '%' : '0%'}`)
        })
      }
    } else {
      Object.entries(q.statistics).forEach(([option, count]) => {
        const percentage = total > 0 ? ((count / total) * 100).toFixed(1) + '%' : '0%'
        const typeLabel = q.type === 'single' ? '单选' : '多选'
        lines.push(`${q.content}\t${typeLabel}\t${option}\t${count}\t${percentage}`)
      })
    }
    lines.push('')
  })

  const tsv = lines.join('\n')
  const blob = new Blob([tsv], { type: 'text/plain' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${questionnaire.value.title || '问卷数据'}.txt`
  a.click()
  URL.revokeObjectURL(url)
}

function goBack() {
  router.push('/')
}

function goToFingerprint() {
  router.push(`/fingerprint/${questionnaireId.value}`)
}

function getVisibilityLabel(visibility?: ResultVisibility): string {
  const opt = RESULT_VISIBILITY_OPTIONS.find(o => o.value === visibility)
  return opt ? opt.label : '即时公开'
}

function goToEdit() {
  router.push(`/edit/${questionnaireId.value}`)
}
</script>

<template>
  <div class="statistics-page">
    <header class="page-header-bar">
      <div class="container">
        <div class="header-content">
          <h1 class="page-title">数据统计</h1>
          <div class="header-actions">
            <button class="btn btn-outline" @click="goBack">
              返回
            </button>
            <button class="btn btn-outline" @click="goToFingerprint">
              指纹档案
            </button>
            <button class="btn btn-primary" @click="exportData">
              导出数据
            </button>
          </div>
        </div>
      </div>
    </header>

    <main class="container">
      <div v-if="loading" class="loading">
        加载中...
      </div>

      <template v-else-if="questionnaire && statistics">
        <div class="stats-overview">
          <div class="stat-card card">
            <div class="stat-value">{{ statistics.resultsVisible === false ? '-' : statistics.totalResponses }}</div>
            <div class="stat-label">总填写人数</div>
          </div>
          <div class="stat-card card">
            <div class="stat-value">{{ questionnaire.questions.length }}</div>
            <div class="stat-label">题目数量</div>
          </div>
          <div class="stat-card card">
            <div class="stat-value status-value">
              <span
                :class="[
                  'visibility-badge',
                  `visibility-${statistics.resultVisibility || 'INSTANT_PUBLIC'}`
                ]"
              >
                {{ getVisibilityLabel(statistics.resultVisibility) }}
              </span>
            </div>
            <div class="stat-label">结果公开策略</div>
          </div>
        </div>

        <div class="questionnaire-info card">
          <h2 class="info-title">{{ questionnaire.title }}</h2>
          <p v-if="questionnaire.description" class="info-desc">{{ questionnaire.description }}</p>
        </div>

        <div v-if="statistics.resultsVisible === false" class="results-hidden card">
          <div class="results-hidden-icon">🔒</div>
          <h2 class="results-hidden-title">结果暂未公开</h2>
          <p class="results-hidden-desc">
            {{ statistics.visibilityMessage || '该问卷结果暂不可见' }}
          </p>
          <div v-if="!isCreator" class="results-hidden-tip">
            💡 如需查看完整统计数据，请联系问卷创建者
          </div>
          <div v-else class="results-hidden-tip">
            👑 您是问卷创建者，可以在编辑页面调整结果公开策略
          </div>
          <button v-if="isCreator" class="btn btn-primary results-hidden-btn" @click="goToEdit">
            前往编辑设置
          </button>
        </div>

        <div v-else class="charts-section">
          <h2 class="section-title">答题统计</h2>

          <div
            v-for="q in statistics.questions"
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

              <div class="text-browser">
                <div class="browser-header">
                  <div class="browser-title">
                    <span class="browser-icon">📝</span>
                    文本回答浏览
                    <span class="answer-count-badge">
                      共 {{ q.totalResponses }} 条
                      <span v-if="q.dedupedTextAnswers && q.distinctTextAnswerCount != null" class="deduped-badge">
                        去重后 {{ q.distinctTextAnswerCount }} 种
                      </span>
                    </span>
                  </div>
                  <button
                    class="btn-collapse"
                    @click="toggleExpand(q.questionId)"
                  >
                    {{ getTextState(q.questionId).expanded ? '收起' : '展开' }}
                    <span class="collapse-icon">{{ getTextState(q.questionId).expanded ? '▲' : '▼' }}</span>
                  </button>
                </div>

                <div class="browser-toolbar">
                  <div class="view-mode-group">
                    <span class="toolbar-label">视图：</span>
                    <button
                      :class="['mode-btn', { active: getTextState(q.questionId).viewMode === 'deduped' }]"
                      @click="setViewMode(q.questionId, 'deduped')"
                    >
                      去重聚合
                    </button>
                    <button
                      :class="['mode-btn', { active: getTextState(q.questionId).viewMode === 'original' }]"
                      @click="setViewMode(q.questionId, 'original')"
                    >
                      原始列表
                    </button>
                  </div>
                  <div class="sort-group">
                    <span class="toolbar-label">排序：</span>
                    <template v-if="getTextState(q.questionId).viewMode === 'original'">
                      <button
                        :class="['sort-btn', { active: getTextState(q.questionId).sortBy === 'default' }]"
                        @click="setSortBy(q.questionId, 'default')"
                      >
                        默认
                      </button>
                      <button
                        :class="['sort-btn', { active: getTextState(q.questionId).sortBy === 'latest' }]"
                        @click="setSortBy(q.questionId, 'latest')"
                      >
                        最新
                      </button>
                    </template>
                    <template v-else>
                      <button
                        :class="['sort-btn', { active: getTextState(q.questionId).sortBy === 'count' }]"
                        @click="setSortBy(q.questionId, 'count')"
                      >
                        按数量
                      </button>
                    </template>
                    <button
                      :class="['sort-btn', { active: getTextState(q.questionId).sortBy === 'longest' }]"
                      @click="setSortBy(q.questionId, 'longest')"
                    >
                      最长
                    </button>
                  </div>
                  <div class="search-group">
                    <input
                      type="text"
                      v-model="getTextState(q.questionId).keyword"
                      class="search-input"
                      placeholder="搜索关键词..."
                    />
                    <span
                      v-if="getTextState(q.questionId).keyword"
                      class="search-result-count"
                    >
                      <template v-if="getTextState(q.questionId).viewMode === 'original' && q.textAnswers">
                        {{ getSortedAnswers(q.questionId, q.textAnswers).length }} 条匹配
                      </template>
                      <template v-else-if="getTextState(q.questionId).viewMode === 'deduped' && q.dedupedTextAnswers">
                        {{ getSortedDedupedAnswers(q.questionId, q.dedupedTextAnswers).length }} 种匹配
                      </template>
                    </span>
                  </div>
                </div>

                <template v-if="getTextState(q.questionId).viewMode === 'original'">
                  <div v-if="q.textAnswers && q.textAnswers.length > 0" class="text-answer-list">
                    <div
                      v-for="(answer, index) in getVisibleAnswers(q.questionId, q.textAnswers)"
                      :key="index"
                      class="text-answer-item"
                    >
                      <span class="answer-number">{{ getSortedAnswers(q.questionId, q.textAnswers!).indexOf(answer) + 1 }}.</span>
                      <span
                        class="answer-content"
                        v-html="highlightKeyword(answer, getTextState(q.questionId).keyword)"
                      ></span>
                      <span class="answer-length">{{ answer.length }}字</span>
                    </div>

                    <div
                      v-if="!getTextState(q.questionId).expanded && getSortedAnswers(q.questionId, q.textAnswers).length > getTextState(q.questionId).visibleCount"
                      class="expand-hint"
                      @click="toggleExpand(q.questionId)"
                    >
                      还有 {{ getSortedAnswers(q.questionId, q.textAnswers).length - getTextState(q.questionId).visibleCount }} 条回答，点击展开查看全部
                    </div>
                  </div>
                  <p v-else class="text-empty">暂无回答内容</p>
                </template>

                <template v-else>
                  <div v-if="q.dedupedTextAnswers && q.dedupedTextAnswers.length > 0" class="text-answer-list">
                    <div
                      v-for="(item, index) in getVisibleDedupedAnswers(q.questionId, q.dedupedTextAnswers)"
                      :key="index"
                      class="text-answer-item deduped-item"
                    >
                      <span class="answer-number">{{ getSortedDedupedAnswers(q.questionId, q.dedupedTextAnswers!).findIndex(a => a.content === item.content) + 1 }}.</span>
                      <span
                        class="answer-content"
                        v-html="highlightKeyword(item.content, getTextState(q.questionId).keyword)"
                      ></span>
                      <span class="answer-meta">
                        <span class="answer-count-tag">{{ item.count }} 次</span>
                        <span class="answer-percent">{{ item.percentage.toFixed(1) }}%</span>
                      </span>
                    </div>

                    <div
                      v-if="!getTextState(q.questionId).expanded && getSortedDedupedAnswers(q.questionId, q.dedupedTextAnswers).length > getTextState(q.questionId).visibleCount"
                      class="expand-hint"
                      @click="toggleExpand(q.questionId)"
                    >
                      还有 {{ getSortedDedupedAnswers(q.questionId, q.dedupedTextAnswers).length - getTextState(q.questionId).visibleCount }} 种回答，点击展开查看全部
                    </div>
                  </div>
                  <p v-else class="text-empty">暂无回答内容</p>
                </template>
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
.statistics-page {
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

.header-actions {
  display: flex;
  gap: 12px;
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
}

.stat-label {
  font-size: 14px;
  color: var(--color-text-secondary);
}

.questionnaire-info {
  margin-bottom: 24px;
}

.info-title {
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 8px;
}

.info-desc {
  color: var(--color-text-secondary);
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 16px;
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

.btn-collapse {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  font-size: 13px;
  color: var(--color-primary);
  background: transparent;
  border: 1px solid var(--color-primary);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-collapse:hover {
  background: var(--color-primary);
  color: white;
}

.collapse-icon {
  font-size: 10px;
}

.browser-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 18px;
  background: #FAFAFA;
  border-bottom: 1px solid var(--color-border);
  flex-wrap: wrap;
  gap: 12px;
}

.toolbar-label {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.sort-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sort-btn {
  padding: 5px 12px;
  font-size: 12px;
  border: 1px solid var(--color-border);
  background: white;
  color: var(--color-text-secondary);
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.sort-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.sort-btn.active {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: white;
}

.view-mode-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mode-btn {
  padding: 5px 12px;
  font-size: 12px;
  border: 1px solid var(--color-border);
  background: white;
  color: var(--color-text-secondary);
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.mode-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.mode-btn.active {
  background: #10B981;
  border-color: #10B981;
  color: white;
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

.search-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.search-input {
  padding: 6px 12px;
  font-size: 13px;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  width: 200px;
  transition: border-color 0.2s;
}

.search-input:focus {
  outline: none;
  border-color: var(--color-primary);
}

.search-result-count {
  font-size: 12px;
  color: var(--color-primary);
  font-weight: 500;
}

.text-answer-list {
  display: flex;
  flex-direction: column;
  max-height: 400px;
  overflow-y: auto;
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

.keyword-highlight {
  background: #FEF08A;
  color: var(--color-text);
  padding: 0 2px;
  border-radius: 2px;
  font-weight: 500;
}

.expand-hint {
  padding: 14px 18px;
  text-align: center;
  font-size: 13px;
  color: var(--color-primary);
  cursor: pointer;
  background: #F0F9FF;
  transition: background 0.2s;
}

.expand-hint:hover {
  background: #E0F2FE;
}

.text-empty {
  color: var(--color-text-secondary);
  font-size: 14px;
  text-align: center;
  padding: 40px 20px;
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

.status-value {
  font-size: 14px;
  padding: 20px 0 8px;
}

.visibility-badge {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
}

.visibility-INSTANT_PUBLIC {
  background: #ECFDF5;
  color: #059669;
}

.visibility-AFTER_DEADLINE {
  background: #FFFBEB;
  color: #D97706;
}

.visibility-PRIVATE {
  background: #FEF2F2;
  color: #DC2626;
}

.results-hidden {
  text-align: center;
  padding: 60px 40px;
  margin: 24px 0;
}

.results-hidden-icon {
  font-size: 64px;
  margin-bottom: 16px;
}

.results-hidden-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-text);
  margin-bottom: 12px;
}

.results-hidden-desc {
  font-size: 15px;
  color: var(--color-text-secondary);
  margin-bottom: 20px;
  line-height: 1.6;
}

.results-hidden-tip {
  font-size: 13px;
  color: var(--color-primary);
  background: #EFF6FF;
  padding: 10px 16px;
  border-radius: 8px;
  display: inline-block;
  margin-bottom: 20px;
}

.results-hidden-btn {
  padding: 10px 24px;
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

  .results-hidden {
    padding: 40px 20px;
  }

  .results-hidden-icon {
    font-size: 48px;
  }

  .results-hidden-title {
    font-size: 20px;
  }
}
</style>
