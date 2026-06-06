<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQuestionnaireStore } from '../stores/questionnaire'
import type { Answer, CoverConfig } from '../types'
import { DEFAULT_COVER_CONFIG } from '../types'
import FormRenderer from '../engine/FormRenderer.vue'
import QuestionNavigation from '../components/QuestionNavigation.vue'

const route = useRoute()
const router = useRouter()
const store = useQuestionnaireStore()

const answers = ref<Record<string, string | string[]>>({})
const submitting = ref(false)
const submitted = ref(false)
const errorMsg = ref('')
const startTime = ref(Date.now())

const questionnaireId = computed(() => route.params.id as string)

const showNavigation = computed(() => {
  return store.currentQuestionnaire && store.currentQuestionnaire.questions.length >= 5
})

function jumpToQuestion(questionId: string) {
  const el = document.getElementById(`question-${questionId}`)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }
}

onMounted(async () => {
  await store.fetchQuestionnaire(questionnaireId.value)
})

const canSubmit = computed(() => {
  if (!store.currentQuestionnaire) return false
  for (const q of store.currentQuestionnaire.questions) {
    if (q.required) {
      const answer = answers.value[q.id]
      if (!answer || (Array.isArray(answer) && answer.length === 0)) {
        return false
      }
    }
  }
  return true
})

const isExpired = computed(() => {
  if (!store.currentQuestionnaire?.deadline) return false
  return new Date(store.currentQuestionnaire.deadline) < new Date()
})

const isActive = computed(() => {
  return store.currentQuestionnaire?.status === 'active' && !isExpired.value
})

const coverConfig = computed<CoverConfig>(() => {
  return { ...DEFAULT_COVER_CONFIG, ...(store.currentQuestionnaire?.coverConfig || {}) }
})

const coverStyle = computed(() => {
  const c = coverConfig.value
  if (c.backgroundStyle === 'gradient') {
    return { background: `linear-gradient(135deg, ${c.gradientStart}, ${c.gradientEnd})` }
  }
  return { backgroundColor: c.backgroundColor }
})

const isDarkBg = computed(() => {
  const c = coverConfig.value
  if (c.backgroundStyle === 'gradient') {
    return isColorDark(c.gradientStart) || isColorDark(c.gradientEnd)
  }
  return isColorDark(c.backgroundColor)
})

function isColorDark(hex: string): boolean {
  const c = hex.replace('#', '')
  if (c.length !== 6) return false
  const r = parseInt(c.substring(0, 2), 16)
  const g = parseInt(c.substring(2, 4), 16)
  const b = parseInt(c.substring(4, 6), 16)
  return (r * 299 + g * 587 + b * 114) / 1000 < 128
}

function handleAnswer(questionId: string, value: string | string[]) {
  answers.value[questionId] = value
}

async function submitForm() {
  if (!canSubmit.value || submitting.value) return

  submitting.value = true
  errorMsg.value = ''

  try {
    const answerList: Answer[] = Object.entries(answers.value).map(([questionId, value]) => ({
      questionId,
      value
    }))

    const submitDurationSeconds = Math.floor((Date.now() - startTime.value) / 1000)

    const success = await store.submitQuestionnaire(questionnaireId.value, answerList, submitDurationSeconds)
    if (success) {
      submitted.value = true
    } else {
      errorMsg.value = store.error || '提交失败'
    }
  } finally {
    submitting.value = false
  }
}

function goHome() {
  router.push('/')
}
</script>

<template>
  <div class="fill-page">
    <div :class="['fill-container', { 'with-nav': showNavigation }]">
      <aside v-if="showNavigation" class="nav-sidebar">
        <QuestionNavigation
          :questions="store.currentQuestionnaire.questions"
          :answers="answers"
          :accent-color="coverConfig.accentColor"
          @jump="jumpToQuestion"
        />
      </aside>

      <main class="main-content">
      <div v-if="store.loading" class="loading">
        加载中...
      </div>

      <div v-else-if="!store.currentQuestionnaire" class="error-state">
        <p>问卷不存在或已被删除</p>
        <button class="btn btn-primary" @click="goHome">返回首页</button>
      </div>

      <div v-else-if="submitted" class="success-state">
        <div class="success-icon">✓</div>
        <h2>提交成功</h2>
        <p>感谢您的参与！</p>
        <button class="btn btn-primary" @click="goHome">返回首页</button>
      </div>

      <div v-else-if="!isActive" class="error-state">
        <div class="error-icon">!</div>
        <h2>问卷已结束</h2>
        <p>该问卷已停止收集，感谢您的关注。</p>
        <button class="btn btn-primary" @click="goHome">返回首页</button>
      </div>

      <template v-else>
        <header
          :class="['questionnaire-cover', coverConfig.layout, { 'dark-bg': isDarkBg }]"
          :style="coverStyle"
        >
          <div
            v-if="coverConfig.headerImage && coverConfig.layout !== 'split'"
            :class="['cover-header-img', coverConfig.layout]"
          >
            <img :src="coverConfig.headerImage" alt="封面头图" />
          </div>

          <div :class="['cover-content', coverConfig.layout]">
            <div
              v-if="coverConfig.layout === 'split' && coverConfig.headerImage"
              class="split-image-area"
            >
              <img :src="coverConfig.headerImage" alt="封面头图" />
            </div>

            <div :class="['cover-text-area', coverConfig.layout, `title-${coverConfig.titlePosition}`]">
              <h1
                :class="['cover-title', { 'light-text': isDarkBg }]"
                :style="{ color: isDarkBg ? '#FFFFFF' : coverConfig.accentColor }"
              >
                {{ store.currentQuestionnaire.title }}
              </h1>

              <p
                v-if="coverConfig.showDescription && store.currentQuestionnaire.description"
                :class="['cover-desc', { 'light-text': isDarkBg }]"
              >
                {{ store.currentQuestionnaire.description }}
              </p>

              <p
                v-if="coverConfig.openingText"
                :class="['cover-opening', { 'light-text': isDarkBg }]"
                :style="{ borderColor: coverConfig.accentColor }"
              >
                {{ coverConfig.openingText }}
              </p>

              <div
                v-if="store.currentQuestionnaire.deadline"
                :class="['deadline-info', { 'light-text': isDarkBg }]"
              >
                截止时间：{{ new Date(store.currentQuestionnaire.deadline).toLocaleString() }}
              </div>
            </div>
          </div>

          <div class="cover-accent-bar" :style="{ background: coverConfig.accentColor }"></div>
        </header>

        <div class="progress-bar">
          <div class="progress-text">
            {{ Object.keys(answers).length }} / {{ store.currentQuestionnaire.questions.length }} 题已填写
          </div>
          <div class="progress-track">
            <div
              class="progress-fill"
              :style="{ width: `${(Object.keys(answers).length / store.currentQuestionnaire.questions.length) * 100}%` }"
            ></div>
          </div>
        </div>

        <FormRenderer
          :questions="store.currentQuestionnaire.questions"
          :answers="answers"
          @answer="handleAnswer"
        />

        <div v-if="errorMsg" class="error-message">
          {{ errorMsg }}
        </div>

        <div class="submit-area">
          <button
            class="btn btn-success submit-btn"
            :disabled="!canSubmit || submitting"
            @click="submitForm"
          >
            {{ submitting ? '提交中...' : '提交问卷' }}
          </button>
        </div>
      </template>
      </main>
    </div>
  </div>
</template>

<style scoped>
.fill-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #F8FAFC 0%, #EEF2FF 100%);
  padding: 40px 20px;
}

.fill-container {
  max-width: 700px;
  margin: 0 auto;
}

.fill-container.with-nav {
  max-width: 1100px;
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 24px;
  align-items: start;
}

.nav-sidebar {
  position: sticky;
  top: 24px;
}

.main-content {
  min-width: 0;
}

@media (max-width: 1024px) {
  .fill-container.with-nav {
    grid-template-columns: 1fr;
    max-width: 700px;
  }

  .nav-sidebar {
    position: static;
  }
}

.loading,
.error-state,
.success-state {
  text-align: center;
  padding: 60px 20px;
  background: white;
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}

.success-icon {
  width: 80px;
  height: 80px;
  margin: 0 auto 20px;
  background: var(--color-success);
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40px;
}

.error-icon {
  width: 80px;
  height: 80px;
  margin: 0 auto 20px;
  background: var(--color-danger);
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40px;
}

.success-state h2,
.error-state h2 {
  font-size: 24px;
  margin-bottom: 8px;
}

.success-state p,
.error-state p {
  color: var(--color-text-secondary);
  margin-bottom: 24px;
}

.questionnaire-cover {
  border-radius: var(--radius) var(--radius) 0 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.cover-header-img {
  width: 100%;
  max-height: 220px;
  overflow: hidden;
}

.cover-header-img.hero {
  max-height: 280px;
}

.cover-header-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.cover-content {
  flex: 1;
  display: flex;
  padding: 32px;
}

.cover-content.centered {
  align-items: center;
  justify-content: center;
  text-align: center;
}

.cover-content.left-aligned {
  align-items: flex-start;
  text-align: left;
}

.cover-content.split {
  flex-direction: row;
  gap: 24px;
  align-items: stretch;
}

.split-image-area {
  flex: 1;
  min-height: 160px;
  border-radius: 8px;
  overflow: hidden;
}

.split-image-area img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 8px;
}

.cover-text-area {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-width: 100%;
}

.cover-text-area.centered {
  align-items: center;
}

.cover-text-area.left-aligned {
  align-items: flex-start;
}

.cover-text-area.split {
  flex: 1;
  justify-content: center;
}

.cover-text-area.title-top {
  justify-content: flex-start;
}

.cover-text-area.title-middle {
  justify-content: center;
}

.cover-text-area.title-bottom {
  justify-content: flex-end;
}

.cover-title {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.3;
  margin: 0;
  word-break: break-word;
}

.cover-desc {
  font-size: 16px;
  line-height: 1.6;
  margin: 0;
  opacity: 0.75;
  word-break: break-word;
}

.cover-opening {
  font-size: 14px;
  line-height: 1.6;
  margin: 0;
  padding: 12px 16px;
  border-left: 3px solid;
  background: rgba(0, 0, 0, 0.04);
  border-radius: 0 6px 6px 0;
  word-break: break-word;
}

.cover-opening.light-text {
  background: rgba(255, 255, 255, 0.12);
}

.deadline-info {
  font-size: 14px;
  margin: 0;
  opacity: 0.7;
}

.light-text {
  color: rgba(255, 255, 255, 0.9) !important;
}

.cover-accent-bar {
  height: 4px;
  width: 100%;
}

.progress-bar {
  background: white;
  padding: 16px 32px;
  border-bottom: 1px solid var(--color-border);
}

.progress-text {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: 8px;
  text-align: right;
}

.progress-track {
  height: 6px;
  background: var(--color-border);
  border-radius: 3px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: var(--color-primary);
  border-radius: 3px;
  transition: width 0.3s ease;
}

.error-message {
  background: #FEE2E2;
  color: var(--color-danger);
  padding: 12px 20px;
  border-radius: var(--radius);
  margin: 20px 0;
  text-align: center;
}

.submit-area {
  background: white;
  padding: 24px 32px;
  border-radius: 0 0 var(--radius) var(--radius);
  box-shadow: var(--shadow);
}

.submit-btn {
  width: 100%;
  padding: 14px;
  font-size: 16px;
}
</style>
