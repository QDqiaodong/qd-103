<script setup lang="ts">
import { ref, onMounted, computed, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as api from '../services/api'
import type { Questionnaire, StatisticsResponse, QuestionStatistic } from '../types'
import * as echarts from 'echarts'

const route = useRoute()
const router = useRouter()

const questionnaire = ref<Questionnaire | null>(null)
const statistics = ref<StatisticsResponse | null>(null)
const loading = ref(true)

const questionnaireId = computed(() => route.params.id as string)

onMounted(async () => {
  await loadData()
})

async function loadData() {
  loading.value = true
  try {
    questionnaire.value = await api.getQuestionnaire(questionnaireId.value)
    statistics.value = await api.getStatistics(questionnaireId.value)
    await nextTick()
    renderCharts()
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
            <div class="stat-value">{{ statistics.totalResponses }}</div>
            <div class="stat-label">总填写人数</div>
          </div>
          <div class="stat-card card">
            <div class="stat-value">{{ questionnaire.questions.length }}</div>
            <div class="stat-label">题目数量</div>
          </div>
          <div class="stat-card card">
            <div class="stat-value">{{ questionnaire.status }}</div>
            <div class="stat-label">问卷状态</div>
          </div>
        </div>

        <div class="questionnaire-info card">
          <h2 class="info-title">{{ questionnaire.title }}</h2>
          <p v-if="questionnaire.description" class="info-desc">{{ questionnaire.description }}</p>
        </div>

        <div class="charts-section">
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

            <div v-else class="text-answers">
              <p class="text-hint">文本回答列表（共 {{ q.totalResponses }} 条）</p>
              <div v-if="q.textAnswers && q.textAnswers.length > 0" class="text-answer-list">
                <div
                  v-for="(answer, index) in q.textAnswers"
                  :key="index"
                  class="text-answer-item"
                >
                  <span class="answer-number">{{ index + 1 }}.</span>
                  <span class="answer-content">{{ answer }}</span>
                </div>
              </div>
              <p v-else class="text-empty">暂无回答内容</p>
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

.text-answers {
  padding: 20px;
  background: var(--color-bg);
  border-radius: var(--radius);
  margin-bottom: 20px;
}

.text-hint {
  color: var(--color-text-secondary);
  font-size: 14px;
  margin-bottom: 12px;
}

.text-answer-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.text-answer-item {
  display: flex;
  gap: 8px;
  padding: 10px 12px;
  background: white;
  border-radius: 6px;
  border-left: 3px solid var(--color-primary);
}

.answer-number {
  font-weight: 600;
  color: var(--color-primary);
  flex-shrink: 0;
}

.answer-content {
  font-size: 14px;
  color: var(--color-text);
  word-break: break-word;
  line-height: 1.5;
}

.text-empty {
  color: var(--color-text-secondary);
  font-size: 14px;
  text-align: center;
  padding: 16px;
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
}
</style>
