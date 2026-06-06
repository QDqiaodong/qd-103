<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useQuestionnaireStore } from '../stores/questionnaire'
import type { Questionnaire, QuestionnaireStatus, CoverTheme } from '../types'
import { COVER_THEMES, DEFAULT_COVER_CONFIG } from '../types'

function getHeatLevel(q: Questionnaire): { level: number; label: string; color: string } {
  const count = q.responseCount || 0
  const createdAt = new Date(q.createdAt).getTime()
  const now = Date.now()
  const daysActive = Math.max(1, (now - createdAt) / (1000 * 60 * 60 * 24))
  const avgPerDay = count / daysActive

  if (avgPerDay >= 20) return { level: 5, label: '爆热', color: '#EF4444' }
  if (avgPerDay >= 10) return { level: 4, label: '火热', color: '#F97316' }
  if (avgPerDay >= 5) return { level: 3, label: '活跃', color: '#F59E0B' }
  if (avgPerDay >= 2) return { level: 2, label: '平稳', color: '#10B981' }
  return { level: 1, label: '冷清', color: '#9CA3AF' }
}

function getDeadlineInfo(q: Questionnaire): { daysLeft: number | null; urgency: 'critical' | 'warning' | 'normal' | 'none'; label: string } {
  if (!q.deadline || q.status !== 'active') {
    return { daysLeft: null, urgency: 'none', label: '' }
  }
  const deadline = new Date(q.deadline).getTime()
  const now = Date.now()
  const daysLeft = Math.ceil((deadline - now) / (1000 * 60 * 60 * 24))

  if (daysLeft <= 0) {
    return { daysLeft: 0, urgency: 'critical', label: '已截止' }
  }
  if (daysLeft <= 2) {
    return { daysLeft, urgency: 'critical', label: `剩 ${daysLeft} 天` }
  }
  if (daysLeft <= 7) {
    return { daysLeft, urgency: 'warning', label: `剩 ${daysLeft} 天` }
  }
  return { daysLeft, urgency: 'normal', label: `剩 ${daysLeft} 天` }
}

function needsAttention(q: Questionnaire): boolean {
  const deadlineInfo = getDeadlineInfo(q)
  const heat = getHeatLevel(q)
  return deadlineInfo.urgency === 'critical' && heat.level <= 2
}

const router = useRouter()
const store = useQuestionnaireStore()

const filterStatus = ref<QuestionnaireStatus | 'all'>('all')

onMounted(() => {
  store.fetchQuestionnaires()
})

const filteredQuestionnaires = computed(() => {
  if (filterStatus.value === 'all') {
    return store.questionnaires
  }
  return store.questionnaires.filter(q => q.status === filterStatus.value)
})

function getStatusBadge(status: QuestionnaireStatus) {
  const badges: Record<QuestionnaireStatus, { class: string; text: string }> = {
    draft: { class: 'badge-info', text: '草稿' },
    active: { class: 'badge-success', text: '收集中' },
    closed: { class: 'badge-danger', text: '已关闭' },
    expired: { class: 'badge-warning', text: '已过期' }
  }
  return badges[status]
}

function goToCreate() {
  router.push('/create')
}

function goToEdit(id: string) {
  router.push(`/edit/${id}`)
}

function goToFill(id: string) {
  router.push(`/fill/${id}`)
}

function goToStatistics(id: string) {
  router.push(`/statistics/${id}`)
}

function goToFingerprint(id: string) {
  router.push(`/fingerprint/${id}`)
}

function copyLink(id: string) {
  const link = `${window.location.origin}/fill/${id}`
  navigator.clipboard.writeText(link)
  alert('链接已复制到剪贴板')
}

async function deleteQuestionnaire(id: string) {
  if (confirm('确定要删除这个问卷吗？')) {
    const success = await store.removeQuestionnaire(id)
    if (!success) {
      alert(store.error || '删除失败')
    }
  }
}

function getThemeLabel(q: Questionnaire): string {
  const theme = q.coverConfig?.theme || DEFAULT_COVER_CONFIG.theme
  return COVER_THEMES[theme as CoverTheme]?.label || '专业调研'
}

function getThemeColor(q: Questionnaire): string {
  const theme = q.coverConfig?.theme || DEFAULT_COVER_CONFIG.theme
  return COVER_THEMES[theme as CoverTheme]?.accentColor || '#4F46E5'
}
</script>

<template>
  <div class="home-page">
    <header class="hero">
      <div class="container">
        <div class="hero-content">
          <h1 class="hero-title">问卷投票平台</h1>
          <p class="hero-subtitle">轻量化自主式问卷投票工具，快速创建专业问卷/投票</p>
          <button class="btn btn-primary hero-btn" @click="goToCreate">
            创建问卷
          </button>
        </div>
      </div>
    </header>

    <main class="container">
      <div class="toolbar">
        <div class="filter-tabs">
          <button
            :class="['tab', { active: filterStatus === 'all' }]"
            @click="filterStatus = 'all'"
          >
            全部
          </button>
          <button
            :class="['tab', { active: filterStatus === 'active' }]"
            @click="filterStatus = 'active'"
          >
            收集中
          </button>
          <button
            :class="['tab', { active: filterStatus === 'draft' }]"
            @click="filterStatus = 'draft'"
          >
            草稿
          </button>
          <button
            :class="['tab', { active: filterStatus === 'closed' }]"
            @click="filterStatus = 'closed'"
          >
            已结束
          </button>
        </div>
      </div>

      <div v-if="store.loading" class="loading">
        加载中...
      </div>

      <div v-else-if="filteredQuestionnaires.length === 0" class="empty-state">
        <p>暂无问卷</p>
        <button class="btn btn-primary" @click="goToCreate">创建第一个问卷</button>
      </div>

      <div v-else class="questionnaire-grid">
        <div
          v-for="q in filteredQuestionnaires"
          :key="q.id"
          class="questionnaire-card card"
        >
          <div class="card-header">
            <h3 class="card-title">
              {{ q.title || '未命名问卷' }}
              <span v-if="needsAttention(q)" class="attention-badge" title="临近截止且回收偏低，需重点关注">⚠ 待关注</span>
            </h3>
            <div class="card-badges">
              <span class="theme-badge" :style="{ background: getThemeColor(q) + '15', color: getThemeColor(q) }">
                {{ getThemeLabel(q) }}
              </span>
              <span :class="['badge', getStatusBadge(q.status).class]">
                {{ getStatusBadge(q.status).text }}
              </span>
            </div>
          </div>

          <p class="card-description">{{ q.description || '暂无描述' }}</p>

          <div class="heat-section">
            <div class="heat-header">
              <span class="heat-label">回收热度</span>
              <span class="heat-value" :style="{ color: getHeatLevel(q).color }">
                {{ getHeatLevel(q).label }}
              </span>
            </div>
            <div class="heat-bar">
              <div
                v-for="i in 5"
                :key="i"
                class="heat-segment"
                :class="{ active: i <= getHeatLevel(q).level }"
                :style="{ background: i <= getHeatLevel(q).level ? getHeatLevel(q).color : undefined }"
              ></div>
            </div>
          </div>

          <div class="card-meta">
            <span class="meta-item">
              {{ q.questions?.length || 0 }} 题
            </span>
            <span class="meta-item">
              {{ q.responseCount || 0 }} 人填写
            </span>
            <span
              v-if="q.deadline"
              class="meta-item deadline-meta"
              :class="'deadline-' + getDeadlineInfo(q).urgency"
            >
              <span class="deadline-icon" v-if="getDeadlineInfo(q).urgency !== 'none'">⏰</span>
              {{ getDeadlineInfo(q).label || new Date(q.deadline).toLocaleDateString() }}
            </span>
          </div>

          <div class="card-actions">
            <button
              v-if="q.status === 'active'"
              class="btn btn-outline"
              @click="goToFill(q.id)"
            >
              填写
            </button>
            <button
              class="btn btn-outline"
              @click="goToEdit(q.id)"
            >
              编辑
            </button>
            <button
              class="btn btn-outline"
              @click="goToStatistics(q.id)"
            >
              统计
            </button>
            <button
              class="btn btn-outline"
              @click="goToFingerprint(q.id)"
            >
              指纹
            </button>
            <button
              class="btn btn-outline"
              @click="copyLink(q.id)"
            >
              复制链接
            </button>
            <button
              class="btn btn-danger"
              @click="deleteQuestionnaire(q.id)"
            >
              删除
            </button>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.home-page {
  min-height: 100vh;
}

.hero {
  background: linear-gradient(135deg, var(--color-primary) 0%, #6366F1 100%);
  padding: 60px 0;
  color: white;
  margin-bottom: 40px;
}

.hero-content {
  text-align: center;
}

.hero-title {
  font-size: 36px;
  font-weight: 700;
  margin-bottom: 12px;
}

.hero-subtitle {
  font-size: 18px;
  opacity: 0.9;
  margin-bottom: 24px;
}

.hero-btn {
  background: white;
  color: var(--color-primary);
  font-size: 16px;
  padding: 12px 32px;
}

.hero-btn:hover {
  background: #F1F5F9;
}

.toolbar {
  margin-bottom: 24px;
}

.filter-tabs {
  display: flex;
  gap: 8px;
  background: white;
  padding: 6px;
  border-radius: var(--radius);
  width: fit-content;
}

.tab {
  padding: 8px 16px;
  border: none;
  background: transparent;
  border-radius: 6px;
  font-size: 14px;
  color: var(--color-text-secondary);
  transition: all 0.2s;
}

.tab:hover {
  color: var(--color-text);
}

.tab.active {
  background: var(--color-primary);
  color: white;
}

.loading,
.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: var(--color-text-secondary);
}

.empty-state p {
  margin-bottom: 16px;
}

.questionnaire-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 20px;
  padding-bottom: 40px;
}

.questionnaire-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.card-badges {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

.theme-badge {
  display: inline-flex;
  align-items: center;
  padding: 3px 8px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 500;
  white-space: nowrap;
}

.card-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text);
  word-break: break-word;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.attention-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  background: #FEF3C7;
  color: #D97706;
  font-size: 11px;
  font-weight: 600;
  border-radius: 4px;
  animation: pulse-badge 2s ease-in-out infinite;
}

@keyframes pulse-badge {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}

.heat-section {
  background: var(--color-bg);
  border-radius: 8px;
  padding: 10px 12px;
}

.heat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.heat-label {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.heat-value {
  font-size: 13px;
  font-weight: 600;
}

.heat-bar {
  display: flex;
  gap: 4px;
  height: 8px;
}

.heat-segment {
  flex: 1;
  background: var(--color-border);
  border-radius: 2px;
  transition: background 0.3s ease;
}

.heat-segment.active {
  opacity: 1;
}

.card-description {
  font-size: 14px;
  color: var(--color-text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 13px;
  color: var(--color-text-secondary);
  align-items: center;
}

.deadline-meta {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-weight: 500;
}

.deadline-icon {
  font-size: 14px;
}

.deadline-critical {
  color: #DC2626;
  font-weight: 600;
}

.deadline-warning {
  color: #D97706;
}

.deadline-normal {
  color: var(--color-text-secondary);
}

.card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid var(--color-border);
}

.card-actions .btn {
  padding: 6px 12px;
  font-size: 13px;
}
</style>
