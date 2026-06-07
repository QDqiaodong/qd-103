<script setup lang="ts">
import { ref, onMounted, computed, nextTick, watch, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as api from '../services/api'
import type { Questionnaire, Fingerprint, FingerprintStatistics } from '../types'
import * as echarts from 'echarts'

const route = useRoute()
const router = useRouter()

const riskLevelLabels: Record<string, { label: string; color: string; bgColor: string }> = {
  normal: { label: '正常', color: '#10B981', bgColor: '#D1FAE5' },
  suspicious: { label: '可疑', color: '#F59E0B', bgColor: '#FEF3C7' },
  high_risk: { label: '高风险', color: '#EF4444', bgColor: '#FEE2E2' }
}

const questionnaire = ref<Questionnaire | null>(null)
const fingerprintStats = ref<FingerprintStatistics | null>(null)
const fingerprints = ref<Fingerprint[]>([])
const riskyFingerprints = ref<Fingerprint[]>([])
const loading = ref(true)
const activeTab = ref<'overview' | 'fingerprints' | 'risky' | 'clusters'>('overview')
const filterRiskLevel = ref<string>('all')
const searchKeyword = ref('')

const resultsVisible = computed(() => {
  return questionnaire.value?.responseCount !== null && questionnaire.value?.responseCount !== undefined
})

const isCreator = computed(() => {
  return api.isCreator(questionnaireId.value)
})

let riskChart: echarts.ECharts | null = null
let trendChart: echarts.ECharts | null = null

const questionnaireId = computed(() => route.params.id as string)

const filteredFingerprints = computed(() => {
  let list = [...fingerprints.value]
  
  if (filterRiskLevel.value !== 'all') {
    list = list.filter(f => f.riskLevel === filterRiskLevel.value)
  }
  
  if (searchKeyword.value.trim()) {
    const keyword = searchKeyword.value.toLowerCase().trim()
    list = list.filter(f => 
      f.fingerprintHash.toLowerCase().includes(keyword) ||
      f.respondentId.toLowerCase().includes(keyword) ||
      (f.riskReasons && f.riskReasons.toLowerCase().includes(keyword))
    )
  }
  
  return list
})

const riskLevelList = computed(() => {
  return Object.entries(riskLevelLabels).map(([key, value]) => ({
    level: key,
    ...value
  }))
})

onMounted(async () => {
  await loadData()
})

async function loadData() {
  loading.value = true
  try {
    const viewerToken = api.getCreatorToken(questionnaireId.value) || undefined
    questionnaire.value = await api.getQuestionnaire(questionnaireId.value, viewerToken)

    if (resultsVisible.value) {
      fingerprintStats.value = await api.getFingerprintStatistics(questionnaireId.value, viewerToken)
      fingerprints.value = await api.getFingerprints(questionnaireId.value, viewerToken)
      riskyFingerprints.value = await api.getRiskyFingerprints(questionnaireId.value, viewerToken)
    }
  } finally {
    loading.value = false
    await nextTick()
    if (activeTab.value === 'overview' && resultsVisible.value) {
      renderCharts()
    }
  }
}

function renderCharts() {
  renderRiskChart()
  renderTrendChart()
}

function resizeCharts() {
  if (riskChart) riskChart.resize()
  if (trendChart) trendChart.resize()
}

function disposeCharts() {
  if (riskChart) {
    riskChart.dispose()
    riskChart = null
  }
  if (trendChart) {
    trendChart.dispose()
    trendChart = null
  }
}

function renderRiskChart() {
  const chartDom = document.getElementById('risk-chart')
  if (!chartDom || !fingerprintStats.value) return

  if (riskChart) {
    riskChart.dispose()
  }
  riskChart = echarts.init(chartDom)
  const distribution = fingerprintStats.value.riskDistribution || {}
  
  const data = [
    { value: distribution.normal || 0, name: '正常', itemStyle: { color: '#10B981' } },
    { value: distribution.suspicious || 0, name: '可疑', itemStyle: { color: '#F59E0B' } },
    { value: distribution.high_risk || 0, name: '高风险', itemStyle: { color: '#EF4444' } }
  ]

  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      right: '5%',
      top: 'center'
    },
    series: [
      {
        name: '风险分布',
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['35%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 8,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: {
          show: false
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 16,
            fontWeight: 'bold'
          }
        },
        data: data
      }
    ]
  }

  riskChart.setOption(option)
}

function renderTrendChart() {
  const chartDom = document.getElementById('trend-chart')
  if (!chartDom || !fingerprintStats.value) return

  if (trendChart) {
    trendChart.dispose()
  }
  trendChart = echarts.init(chartDom)
  const dailyTrend = fingerprintStats.value.dailyTrend || []
  
  const sorted = [...dailyTrend].sort((a, b) => a.date.localeCompare(b.date))
  const dates = sorted.map(d => d.date.substring(5))
  const counts = sorted.map(d => d.count)

  const option = {
    tooltip: {
      trigger: 'axis'
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dates,
      axisLabel: {
        fontSize: 11
      }
    },
    yAxis: {
      type: 'value',
      axisLabel: {
        fontSize: 11
      }
    },
    series: [
      {
        name: '提交数',
        type: 'line',
        stack: 'Total',
        data: counts,
        smooth: true,
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(79, 70, 229, 0.5)' },
            { offset: 1, color: 'rgba(79, 70, 229, 0.05)' }
          ])
        },
        lineStyle: {
          color: '#4F46E5',
          width: 2
        },
        itemStyle: {
          color: '#4F46E5'
        }
      }
    ]
  }

  trendChart.setOption(option)
}

watch(activeTab, async (newTab) => {
  if (newTab === 'overview' && !loading.value) {
    await nextTick()
    if (!riskChart || !trendChart) {
      renderCharts()
    } else {
      resizeCharts()
    }
  }
})

onUnmounted(() => {
  disposeCharts()
})

function getRiskBadgeClass(level: string) {
  return riskLevelLabels[level] || riskLevelLabels.normal
}

function formatTime(dateStr: string) {
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

function formatDuration(seconds: number) {
  if (!seconds) return '-'
  if (seconds < 60) return `${seconds}秒`
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${mins}分${secs}秒`
}

function goBack() {
  router.push('/')
}

function goToStatistics() {
  router.push(`/statistics/${questionnaireId.value}`)
}

function goToEdit() {
  router.push(`/edit/${questionnaireId.value}`)
}
</script>

<template>
  <div class="fingerprint-page">
    <header class="page-header-bar">
      <div class="container">
        <div class="header-content">
          <div class="header-left">
            <h1 class="page-title">
              <span class="title-icon">🔍</span>
              提交指纹档案中心
            </h1>
            <p class="page-subtitle" v-if="questionnaire">
              {{ questionnaire.title }}
            </p>
          </div>
          <div class="header-actions">
            <button class="btn btn-outline" @click="goBack">
              返回
            </button>
            <button class="btn btn-outline" @click="goToStatistics">
              数据统计
            </button>
          </div>
        </div>
      </div>
    </header>

    <main class="container">
      <div v-if="loading" class="loading">
        加载中...
      </div>

      <template v-else-if="!resultsVisible">
        <div class="results-hidden card">
          <div class="results-hidden-icon">🔍</div>
          <h2 class="results-hidden-title">指纹档案暂未公开</h2>
          <p class="results-hidden-desc">
            该问卷的指纹档案数据暂不可见
          </p>
          <div v-if="!isCreator" class="results-hidden-tip">
            💡 如需查看完整指纹数据，请联系问卷创建者
          </div>
          <div v-else class="results-hidden-tip">
            👑 您是问卷创建者，可以在编辑页面调整结果公开策略
          </div>
          <button v-if="isCreator" class="btn btn-primary results-hidden-btn" @click="goToEdit">
            前往编辑设置
          </button>
        </div>
      </template>

      <template v-else>
        <div class="tabs-nav">
          <button
            :class="['tab-btn', { active: activeTab === 'overview' }]"
            @click="activeTab = 'overview'"
          >
            <span class="tab-icon">📊</span>
            概览
          </button>
          <button
            :class="['tab-btn', { active: activeTab === 'fingerprints' }]"
            @click="activeTab = 'fingerprints'"
          >
            <span class="tab-icon">👆</span>
            全部指纹
            <span class="tab-badge">{{ fingerprints.length }}</span>
          </button>
          <button
            :class="['tab-btn', { active: activeTab === 'risky' }]"
            @click="activeTab = 'risky'"
          >
            <span class="tab-icon">⚠️</span>
            风险提交
            <span v-if="riskyFingerprints.length > 0" class="tab-badge risk">
              {{ riskyFingerprints.length }}
            </span>
          </button>
          <button
            :class="['tab-btn', { active: activeTab === 'clusters' }]"
            @click="activeTab = 'clusters'"
          >
            <span class="tab-icon">🎯</span>
            异常聚集
          </button>
        </div>

        <div v-show="activeTab === 'overview'" class="tab-content">
          <div class="stats-overview">
            <div class="stat-card card">
              <div class="stat-icon total">📝</div>
              <div class="stat-info">
                <div class="stat-value">{{ fingerprintStats?.totalFingerprints || 0 }}</div>
                <div class="stat-label">总提交次数</div>
              </div>
            </div>
            <div class="stat-card card">
              <div class="stat-icon unique">🆔</div>
              <div class="stat-info">
                <div class="stat-value">{{ fingerprintStats?.distinctFingerprints || 0 }}</div>
                <div class="stat-label">独立指纹数</div>
              </div>
            </div>
            <div class="stat-card card">
              <div class="stat-icon duplicate">🔄</div>
              <div class="stat-info">
                <div class="stat-value">{{ fingerprintStats?.duplicateFingerprintGroups || 0 }}</div>
                <div class="stat-label">重复指纹组</div>
              </div>
            </div>
            <div class="stat-card card">
              <div class="stat-icon risk">⚠️</div>
              <div class="stat-info">
                <div class="stat-value risk-value">
                  {{ (fingerprintStats?.riskDistribution?.suspicious || 0) + (fingerprintStats?.riskDistribution?.high_risk || 0) }}
                </div>
                <div class="stat-label">风险提交</div>
              </div>
            </div>
          </div>

          <div class="charts-row">
            <div class="chart-card card">
              <h3 class="chart-title">风险等级分布</h3>
              <div id="risk-chart" class="chart-container"></div>
              <div class="risk-legend">
                <div v-for="item in riskLevelList" :key="item.level" class="legend-item">
                  <span class="legend-dot" :style="{ backgroundColor: item.color }"></span>
                  <span class="legend-label">{{ item.label }}</span>
                  <span class="legend-value">
                    {{ fingerprintStats?.riskDistribution?.[item.level] || 0 }}
                  </span>
                </div>
              </div>
            </div>

            <div class="chart-card card">
              <h3 class="chart-title">提交趋势</h3>
              <div id="trend-chart" class="chart-container"></div>
            </div>
          </div>

          <div class="risk-summary card">
            <h3 class="section-title">
              <span class="title-icon">🔍</span>
              风险检测摘要
            </h3>
            <div class="risk-items">
              <div class="risk-item">
                <div class="risk-item-header">
                  <span class="risk-item-icon">🔄</span>
                  <span class="risk-item-title">重复指纹检测</span>
                </div>
                <div class="risk-item-body">
                  <p>发现 <strong>{{ fingerprintStats?.duplicateFingerprintGroups || 0 }}</strong> 组重复指纹</p>
                  <p>涉及 <strong>{{ fingerprintStats?.totalDuplicateSubmissions || 0 }}</strong> 次重复提交</p>
                </div>
              </div>
              <div class="risk-item">
                <div class="risk-item-header">
                  <span class="risk-item-icon">⚡</span>
                  <span class="risk-item-title">短时高频提交</span>
                </div>
                <div class="risk-item-body">
                  <p>检测短时间内同一来源的多次提交行为</p>
                  <p class="hint">1分钟内≥3次或5分钟内≥5次将被标记</p>
                </div>
              </div>
              <div class="risk-item">
                <div class="risk-item-header">
                  <span class="risk-item-icon">🎯</span>
                  <span class="risk-item-title">答案模式聚集</span>
                </div>
                <div class="risk-item-body">
                  <p>检测相同答案模式的异常聚集</p>
                  <p class="hint">同模式占比>30%且≥3份将被标记</p>
                </div>
              </div>
              <div class="risk-item">
                <div class="risk-item-header">
                  <span class="risk-item-icon">⏱️</span>
                  <span class="risk-item-title">填写时长异常</span>
                </div>
                <div class="risk-item-body">
                  <p>检测过短的填写时长</p>
                  <p class="hint">题目>3且时长<10秒将被标记</p>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-show="activeTab === 'fingerprints'" class="tab-content">
          <div class="filter-bar card">
            <div class="filter-group">
              <span class="filter-label">风险等级：</span>
              <button
                :class="['filter-btn', { active: filterRiskLevel === 'all' }]"
                @click="filterRiskLevel = 'all'"
              >
                全部
              </button>
              <button
                v-for="item in riskLevelList"
                :key="item.level"
                :class="['filter-btn', { active: filterRiskLevel === item.level }]"
                @click="filterRiskLevel = item.level"
              >
                {{ item.label }}
              </button>
            </div>
            <div class="search-group">
              <input
                v-model="searchKeyword"
                type="text"
                class="search-input"
                placeholder="搜索指纹哈希、受访者ID、风险原因..."
              />
            </div>
          </div>

          <div v-if="filteredFingerprints.length > 0" class="fingerprint-list">
            <div
              v-for="fp in filteredFingerprints"
              :key="fp.id"
              class="fingerprint-card card"
            >
              <div class="fp-header">
                <div class="fp-hash">
                  <span class="fp-icon">👆</span>
                  <span class="hash-text">{{ fp.fingerprintHash }}</span>
                </div>
                <span
                  class="risk-badge"
                  :style="{
                    color: getRiskBadgeClass(fp.riskLevel).color,
                    backgroundColor: getRiskBadgeClass(fp.riskLevel).bgColor
                  }"
                >
                  {{ getRiskBadgeClass(fp.riskLevel).label }}
                </span>
              </div>

              <div class="fp-meta">
                <div class="meta-item">
                  <span class="meta-label">提交时间</span>
                  <span class="meta-value">{{ formatTime(fp.submittedAt) }}</span>
                </div>
                <div class="meta-item">
                  <span class="meta-label">填写时长</span>
                  <span class="meta-value">{{ formatDuration(fp.submitDurationSeconds) }}</span>
                </div>
                <div class="meta-item">
                  <span class="meta-label">回答题目</span>
                  <span class="meta-value">{{ fp.answerCount }} 题</span>
                </div>
                <div class="meta-item">
                  <span class="meta-label">受访者ID</span>
                  <span class="meta-value mono">{{ fp.respondentId }}</span>
                </div>
              </div>

              <div v-if="fp.riskReasons" class="fp-risks">
                <div class="risks-title">风险标记：</div>
                <div class="risk-tags">
                  <span
                    v-for="(reason, idx) in fp.riskReasons.split('; ')"
                    :key="idx"
                    class="risk-tag"
                  >
                    {{ reason }}
                  </span>
                </div>
              </div>

              <div class="fp-flags">
                <span v-if="fp.isDuplicateFingerprint" class="flag flag-warning">
                  重复指纹 ({{ fp.duplicateCount }}次)
                </span>
                <span v-if="fp.isHighFrequency" class="flag flag-danger">
                  高频提交 ({{ fp.frequencyWindowMinutes }}分钟{{ fp.frequencyCount }}次)
                </span>
                <span v-if="fp.isAnomalyCluster" class="flag flag-info">
                  模式聚集 ({{ fp.clusterSize }}份)
                </span>
                <span v-if="!fp.isDuplicateFingerprint && !fp.isHighFrequency && !fp.isAnomalyCluster" class="flag flag-success">
                  无异常标记
                </span>
              </div>
            </div>
          </div>

          <div v-else class="empty-state card">
            <div class="empty-icon">📭</div>
            <p>暂无符合条件的指纹记录</p>
          </div>
        </div>

        <div v-show="activeTab === 'risky'" class="tab-content">
          <div v-if="riskyFingerprints.length > 0" class="risk-list">
            <div class="risk-list-header">
              <h3>风险提交记录</h3>
              <span class="risk-count">共 {{ riskyFingerprints.length }} 条</span>
            </div>
            <div
              v-for="fp in riskyFingerprints"
              :key="fp.id"
              class="risk-card card"
              :class="{ 'high-risk': fp.riskLevel === 'high_risk' }"
            >
              <div class="risk-card-header">
                <div class="risk-level-indicator" :class="fp.riskLevel">
                  {{ getRiskBadgeClass(fp.riskLevel).label }}
                </div>
                <span class="risk-time">{{ formatTime(fp.submittedAt) }}</span>
              </div>
              <div class="risk-card-body">
                <div class="risk-fp-hash">
                  <span class="label">指纹：</span>
                  <span class="value mono">{{ fp.fingerprintHash }}</span>
                </div>
                <div class="risk-reasons">
                  <span class="label">风险原因：</span>
                  <ul>
                    <li v-for="(reason, idx) in fp.riskReasons?.split('; ') || []" :key="idx">
                      {{ reason }}
                    </li>
                  </ul>
                </div>
              </div>
              <div class="risk-card-footer">
                <span>填写时长：{{ formatDuration(fp.submitDurationSeconds) }}</span>
                <span>{{ fp.answerCount }} 道题</span>
              </div>
            </div>
          </div>

          <div v-else class="empty-state card">
            <div class="empty-icon">✅</div>
            <p>暂无风险提交记录</p>
            <p class="hint">所有提交均为正常状态</p>
          </div>
        </div>

        <div v-show="activeTab === 'clusters'" class="tab-content">
          <div class="clusters-info card">
            <h3 class="section-title">
              <span class="title-icon">🎯</span>
              异常聚集检测
            </h3>
            <p class="section-desc">
              系统自动检测相同答案模式的异常聚集，帮助识别可能的批量刷票行为。
            </p>
            
            <div class="cluster-stats">
              <div class="cluster-stat">
                <div class="stat-num">{{ fingerprintStats?.duplicateFingerprintGroups || 0 }}</div>
                <div class="stat-desc">重复指纹组</div>
              </div>
              <div class="cluster-stat">
                <div class="stat-num">{{ fingerprintStats?.totalDuplicateSubmissions || 0 }}</div>
                <div class="stat-desc">涉及重复提交</div>
              </div>
              <div class="cluster-stat">
                <div class="stat-num">
                  {{ fingerprints.filter(f => f.isAnomalyCluster).length }}
                </div>
                <div class="stat-desc">答案模式异常</div>
              </div>
              <div class="cluster-stat">
                <div class="stat-num">
                  {{ fingerprints.filter(f => f.isHighFrequency).length }}
                </div>
                <div class="stat-desc">高频提交</div>
              </div>
            </div>
          </div>

          <div class="cluster-records card">
            <h3 class="section-title">
              <span class="title-icon">📋</span>
              异常记录明细
            </h3>
            
            <div v-if="fingerprints.filter(f => f.isDuplicateFingerprint || f.isHighFrequency || f.isAnomalyCluster).length > 0" class="cluster-list">
              <div
                v-for="fp in fingerprints.filter(f => f.isDuplicateFingerprint || f.isHighFrequency || f.isAnomalyCluster)"
                :key="fp.id"
                class="cluster-item"
              >
                <div class="cluster-item-header">
                  <span class="cluster-fp mono">{{ fp.fingerprintHash }}</span>
                  <span class="cluster-time">{{ formatTime(fp.submittedAt) }}</span>
                </div>
                <div class="cluster-item-body">
                  <span v-if="fp.isDuplicateFingerprint" class="cluster-tag dup">
                    重复指纹 ×{{ fp.duplicateCount }}
                  </span>
                  <span v-if="fp.isHighFrequency" class="cluster-tag freq">
                    高频 ({{ fp.frequencyWindowMinutes }}分/{{ fp.frequencyCount }}次)
                  </span>
                  <span v-if="fp.isAnomalyCluster" class="cluster-tag cluster">
                    模式聚集 ({{ fp.clusterSize }}份)
                  </span>
                </div>
              </div>
            </div>
            
            <div v-else class="empty-state small">
              <div class="empty-icon">✨</div>
              <p>暂无异常聚集记录</p>
            </div>
          </div>
        </div>
      </template>
    </main>
  </div>
</template>

<style scoped>
.fingerprint-page {
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

.header-left {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--color-text);
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0;
}

.title-icon {
  font-size: 24px;
}

.page-subtitle {
  font-size: 14px;
  color: var(--color-text-secondary);
  margin: 0;
}

.header-actions {
  display: flex;
  gap: 12px;
}

.loading {
  text-align: center;
  padding: 60px 20px;
}

.tabs-nav {
  display: flex;
  gap: 4px;
  background: white;
  border-radius: var(--radius);
  padding: 6px;
  margin: 20px 0;
  box-shadow: var(--shadow-sm);
}

.tab-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px 16px;
  font-size: 14px;
  border: none;
  background: transparent;
  color: var(--color-text-secondary);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.tab-btn:hover {
  background: var(--color-bg);
  color: var(--color-text);
}

.tab-btn.active {
  background: var(--color-primary);
  color: white;
  font-weight: 500;
}

.tab-icon {
  font-size: 16px;
}

.tab-badge {
  background: rgba(0, 0, 0, 0.1);
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
}

.tab-btn.active .tab-badge {
  background: rgba(255, 255, 255, 0.25);
}

.tab-badge.risk {
  background: #EF4444;
  color: white;
}

.tab-content {
  padding-bottom: 40px;
}

.stats-overview {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 24px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px;
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  background: #EEF2FF;
}

.stat-icon.total {
  background: #EEF2FF;
}

.stat-icon.unique {
  background: #D1FAE5;
}

.stat-icon.duplicate {
  background: #FEF3C7;
}

.stat-icon.risk {
  background: #FEE2E2;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: var(--color-text);
  line-height: 1.2;
}

.stat-value.risk-value {
  color: #EF4444;
}

.stat-label {
  font-size: 14px;
  color: var(--color-text-secondary);
  margin-top: 4px;
}

.charts-row {
  display: grid;
  grid-template-columns: 1fr 1.5fr;
  gap: 20px;
  margin-bottom: 24px;
}

.chart-card {
  padding: 24px;
}

.chart-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 16px 0;
  color: var(--color-text);
}

.chart-container {
  width: 100%;
  height: 280px;
}

.risk-legend {
  display: flex;
  justify-content: center;
  gap: 24px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--color-border);
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.legend-label {
  color: var(--color-text-secondary);
}

.legend-value {
  font-weight: 600;
  color: var(--color-text);
}

.risk-summary {
  padding: 24px;
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 20px 0;
  color: var(--color-text);
  display: flex;
  align-items: center;
  gap: 10px;
}

.risk-items {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.risk-item {
  padding: 20px;
  background: var(--color-bg);
  border-radius: 12px;
  border: 1px solid var(--color-border);
}

.risk-item-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.risk-item-icon {
  font-size: 22px;
}

.risk-item-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
}

.risk-item-body p {
  margin: 4px 0;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.risk-item-body strong {
  color: var(--color-primary);
  font-weight: 600;
}

.risk-item-body .hint {
  color: #9CA3AF;
  font-size: 12px;
}

.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

.filter-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-label {
  font-size: 14px;
  color: var(--color-text-secondary);
}

.filter-btn {
  padding: 6px 14px;
  font-size: 13px;
  border: 1px solid var(--color-border);
  background: white;
  color: var(--color-text-secondary);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.filter-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.filter-btn.active {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: white;
}

.search-group {
  display: flex;
  align-items: center;
}

.search-input {
  padding: 8px 14px;
  font-size: 13px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  width: 300px;
  transition: border-color 0.2s;
}

.search-input:focus {
  outline: none;
  border-color: var(--color-primary);
}

.fingerprint-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.fingerprint-card {
  padding: 20px;
}

.fp-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-border);
}

.fp-hash {
  display: flex;
  align-items: center;
  gap: 10px;
}

.fp-icon {
  font-size: 20px;
}

.hash-text {
  font-family: 'SF Mono', Monaco, monospace;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
}

.risk-badge {
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
}

.fp-meta {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.meta-label {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.meta-value {
  font-size: 14px;
  color: var(--color-text);
  font-weight: 500;
}

.meta-value.mono {
  font-family: 'SF Mono', Monaco, monospace;
  font-size: 12px;
}

.fp-risks {
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #FFFBEB;
  border-radius: 8px;
  border: 1px solid #FDE68A;
}

.risks-title {
  font-size: 13px;
  font-weight: 600;
  color: #92400E;
  margin-bottom: 8px;
}

.risk-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.risk-tag {
  font-size: 12px;
  color: #B45309;
  background: #FEF3C7;
  padding: 4px 10px;
  border-radius: 4px;
}

.fp-flags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.flag {
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 4px;
  font-weight: 500;
}

.flag-success {
  background: #D1FAE5;
  color: #065F46;
}

.flag-warning {
  background: #FEF3C7;
  color: #92400E;
}

.flag-danger {
  background: #FEE2E2;
  color: #991B1B;
}

.flag-info {
  background: #DBEAFE;
  color: #1E40AF;
}

.empty-state {
  text-align: center;
  padding: 60px 20px;
}

.empty-state.small {
  padding: 40px 20px;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.empty-state p {
  margin: 4px 0;
  color: var(--color-text-secondary);
  font-size: 14px;
}

.empty-state .hint {
  font-size: 13px;
  color: #9CA3AF;
}

.risk-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.risk-list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.risk-list-header h3 {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
  color: var(--color-text);
}

.risk-count {
  font-size: 14px;
  color: var(--color-text-secondary);
}

.risk-card {
  padding: 0;
  overflow: hidden;
}

.risk-card.high-risk {
  border: 2px solid #FEE2E2;
}

.risk-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  background: #FEF2F2;
}

.risk-card.high-risk .risk-card-header {
  background: #FEE2E2;
}

.risk-level-indicator {
  font-size: 14px;
  font-weight: 600;
  padding: 4px 12px;
  border-radius: 6px;
}

.risk-level-indicator.suspicious {
  background: #FEF3C7;
  color: #92400E;
}

.risk-level-indicator.high_risk {
  background: #FECACA;
  color: #991B1B;
}

.risk-time {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.risk-card-body {
  padding: 20px;
}

.risk-fp-hash {
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.risk-fp-hash .label {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.risk-fp-hash .value {
  font-size: 14px;
  font-weight: 500;
}

.mono {
  font-family: 'SF Mono', Monaco, monospace;
}

.risk-reasons {
  margin-top: 12px;
}

.risk-reasons .label {
  font-size: 13px;
  color: var(--color-text-secondary);
  display: block;
  margin-bottom: 8px;
}

.risk-reasons ul {
  margin: 0;
  padding-left: 20px;
}

.risk-reasons li {
  font-size: 14px;
  color: var(--color-text);
  margin-bottom: 4px;
}

.risk-card-footer {
  padding: 12px 20px;
  background: var(--color-bg);
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.clusters-info {
  padding: 24px;
  margin-bottom: 20px;
}

.section-desc {
  font-size: 14px;
  color: var(--color-text-secondary);
  margin: 0 0 20px 0;
  line-height: 1.6;
}

.cluster-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.cluster-stat {
  text-align: center;
  padding: 20px;
  background: var(--color-bg);
  border-radius: 10px;
}

.cluster-stat .stat-num {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-primary);
  margin-bottom: 6px;
}

.cluster-stat .stat-desc {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.cluster-records {
  padding: 24px;
}

.cluster-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 16px;
}

.cluster-item {
  padding: 16px 20px;
  background: var(--color-bg);
  border-radius: 10px;
  border: 1px solid var(--color-border);
}

.cluster-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.cluster-fp {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
}

.cluster-time {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.cluster-item-body {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cluster-tag {
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 4px;
  font-weight: 500;
}

.cluster-tag.dup {
  background: #FEF3C7;
  color: #92400E;
}

.cluster-tag.freq {
  background: #FEE2E2;
  color: #991B1B;
}

.cluster-tag.cluster {
  background: #DBEAFE;
  color: #1E40AF;
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
    grid-template-columns: repeat(2, 1fr);
  }
  
  .charts-row {
    grid-template-columns: 1fr;
  }
  
  .risk-items {
    grid-template-columns: 1fr;
  }
  
  .fp-meta {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .cluster-stats {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .tabs-nav {
    flex-wrap: wrap;
  }
  
  .tab-btn {
    flex: none;
    font-size: 13px;
    padding: 10px 12px;
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
