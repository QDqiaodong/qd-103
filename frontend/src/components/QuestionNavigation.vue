<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import type { Question } from '../types'
import { ChevronDown, ChevronRight, Check, AlertCircle, List } from 'lucide-vue-next'

const props = defineProps<{
  questions: Question[]
  answers: Record<string, string | string[]>
  accentColor?: string
}>()

const emit = defineEmits<{
  jump: [questionId: string]
}>()

const isExpanded = ref(true)
const currentQuestionId = ref<string | null>(null)
const observer = ref<IntersectionObserver | null>(null)

const unansweredCount = computed(() => {
  return props.questions.filter(q => {
    const answer = props.answers[q.id]
    return !answer || (Array.isArray(answer) && answer.length === 0)
  }).length
})

const requiredCount = computed(() => {
  return props.questions.filter(q => q.required).length
})

const unansweredRequiredCount = computed(() => {
  return props.questions.filter(q => {
    if (!q.required) return false
    const answer = props.answers[q.id]
    return !answer || (Array.isArray(answer) && answer.length === 0)
  }).length
})

function isQuestionAnswered(questionId: string): boolean {
  const answer = props.answers[questionId]
  return !!answer && (!Array.isArray(answer) || answer.length > 0)
}

function getQuestionTypeLabel(type: string): string {
  const labels: Record<string, string> = {
    single: '单选',
    multiple: '多选',
    text: '填空'
  }
  return labels[type] || type
}

function jumpToQuestion(questionId: string) {
  emit('jump', questionId)
}

function toggleExpand() {
  isExpanded.value = !isExpanded.value
}

function setupObserver() {
  if (typeof window === 'undefined' || !('IntersectionObserver' in window)) return

  observer.value = new IntersectionObserver(
    (entries) => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          currentQuestionId.value = entry.target.getAttribute('data-question-id')
        }
      })
    },
    {
      rootMargin: '-40% 0px -50% 0px',
      threshold: 0
    }
  )

  setTimeout(() => {
    props.questions.forEach(q => {
      const el = document.getElementById(`question-${q.id}`)
      if (el) observer.value?.observe(el)
    })
  }, 500)
}

onMounted(() => {
  setupObserver()
})

onUnmounted(() => {
  if (observer.value) {
    observer.value.disconnect()
  }
})
</script>

<template>
  <div class="question-nav" :class="{ collapsed: !isExpanded }">
    <button class="nav-toggle" @click="toggleExpand">
      <List :size="16" />
      <span v-if="isExpanded">题目导航</span>
      <component :is="isExpanded ? ChevronDown : ChevronRight" :size="16" />
    </button>

    <div v-if="isExpanded" class="nav-content">
      <div class="nav-stats">
        <div class="stat-item">
          <span class="stat-num">{{ questions.length }}</span>
          <span class="stat-label">总题数</span>
        </div>
        <div class="stat-item">
          <span class="stat-num">{{ questions.length - unansweredCount }}</span>
          <span class="stat-label">已完成</span>
        </div>
        <div class="stat-item required" v-if="unansweredRequiredCount > 0">
          <span class="stat-num">{{ unansweredRequiredCount }}</span>
          <span class="stat-label">必答未完成</span>
        </div>
      </div>

      <div class="nav-list">
        <button
          v-for="(question, index) in questions"
          :key="question.id"
          :class="['nav-item', {
            active: currentQuestionId === question.id,
            answered: isQuestionAnswered(question.id),
            unanswered: !isQuestionAnswered(question.id),
            required: question.required
          }]"
          @click="jumpToQuestion(question.id)"
        >
          <span class="item-number">{{ index + 1 }}</span>
          <span class="item-type">{{ getQuestionTypeLabel(question.type) }}</span>
          <span class="item-text" :title="question.content">
            {{ question.content || '未填写题目' }}
          </span>
          <span v-if="question.required && !isQuestionAnswered(question.id)" class="item-status required">
            <AlertCircle :size="14" />
          </span>
          <span v-else-if="isQuestionAnswered(question.id)" class="item-status answered">
            <Check :size="14" />
          </span>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.question-nav {
  background: white;
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  overflow: hidden;
  max-height: calc(100vh - 120px);
  display: flex;
  flex-direction: column;
}

.question-nav.collapsed {
  max-height: none;
}

.nav-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 12px 16px;
  border: none;
  background: #F8FAFC;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
  cursor: pointer;
  transition: background 0.2s;
  border-bottom: 1px solid var(--color-border);
}

.nav-toggle:hover {
  background: #F1F5F9;
}

.nav-toggle svg:last-child {
  margin-left: auto;
  color: var(--color-text-secondary);
}

.nav-content {
  flex: 1;
  overflow-y: auto;
  min-width: 260px;
}

.nav-stats {
  display: flex;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border);
}

.stat-item {
  flex: 1;
  text-align: center;
  padding: 8px;
  background: var(--color-bg);
  border-radius: 6px;
}

.stat-item.required {
  background: #FEF2F2;
}

.stat-num {
  display: block;
  font-size: 20px;
  font-weight: 700;
  color: var(--color-text);
}

.stat-item.required .stat-num {
  color: var(--color-danger);
}

.stat-label {
  font-size: 11px;
  color: var(--color-text-secondary);
}

.nav-list {
  padding: 8px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px 12px;
  border: none;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
  text-align: left;
}

.nav-item:hover {
  background: var(--color-bg);
}

.nav-item.active {
  background: #EEF2FF;
}

.nav-item.active .item-number {
  background: var(--color-primary);
  color: white;
}

.item-number {
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-border);
  border-radius: 50%;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-secondary);
}

.nav-item.answered .item-number {
  background: var(--color-success);
  color: white;
}

.nav-item.required.unanswered .item-number {
  background: #FEE2E2;
  color: var(--color-danger);
}

.item-type {
  flex-shrink: 0;
  font-size: 10px;
  padding: 2px 6px;
  background: var(--color-bg);
  border-radius: 4px;
  color: var(--color-text-secondary);
}

.item-text {
  flex: 1;
  font-size: 13px;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.item-status {
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.item-status.answered {
  color: var(--color-success);
}

.item-status.required {
  color: var(--color-danger);
}
</style>
