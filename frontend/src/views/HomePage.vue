<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useQuestionnaireStore } from '../stores/questionnaire'
import type { Questionnaire, QuestionnaireStatus, CoverTheme } from '../types'
import { COVER_THEMES, DEFAULT_COVER_CONFIG } from '../types'

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

function copyLink(id: string) {
  const link = `${window.location.origin}/fill/${id}`
  navigator.clipboard.writeText(link)
  alert('链接已复制到剪贴板')
}

async function deleteQuestionnaire(id: string) {
  if (confirm('确定要删除这个问卷吗？')) {
    await store.removeQuestionnaire(id)
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
            <h3 class="card-title">{{ q.title || '未命名问卷' }}</h3>
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

          <div class="card-meta">
            <span class="meta-item">
              {{ q.questions?.length || 0 }} 题
            </span>
            <span class="meta-item">
              {{ q.responseCount || 0 }} 人填写
            </span>
            <span v-if="q.deadline" class="meta-item">
              截止: {{ new Date(q.deadline).toLocaleDateString() }}
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
