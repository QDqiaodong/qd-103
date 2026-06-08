<script setup lang="ts">
import { ref, onMounted, computed, watch, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQuestionnaireStore } from '../stores/questionnaire'
import type { Answer, CoverConfig, DraftData, DraftAnswer } from '../types'
import { DEFAULT_COVER_CONFIG } from '../types'
import { getVisibleQuestions, getTerminateInfo } from '../lib/utils'
import FormRenderer from '../engine/FormRenderer.vue'
import QuestionNavigation from '../components/QuestionNavigation.vue'
import * as api from '../services/api'

const route = useRoute()
const router = useRouter()
const store = useQuestionnaireStore()

const answers = ref<Record<string, string | string[]>>({})
const submitting = ref(false)
const submitted = ref(false)
const errorMsg = ref('')
const startTime = ref(Date.now())
const lastQuestionId = ref('')

const showPasswordModal = ref(false)
const passwordInput = ref('')
const passwordError = ref('')
const verifyingPassword = ref(false)
const verifiedPassword = ref('')

const showResumeModal = ref(false)
const resumeDraftInfo = ref<DraftData | null>(null)
const resuming = ref(false)

let saveDraftTimer: ReturnType<typeof setTimeout> | null = null
const SAVE_DRAFT_DEBOUNCE_MS = 2000

const questionnaireId = computed(() => route.params.id as string)
const respondentId = computed(() => api.getRespondentId())

function getStoredPassword(qid: string): string | null {
  try {
    const stored = localStorage.getItem(`survey_access_pwd_${qid}`)
    return stored
  } catch (e) {
    return null
  }
}

function setStoredPassword(qid: string, password: string) {
  try {
    localStorage.setItem(`survey_access_pwd_${qid}`, password)
  } catch (e) {}
}

const showNavigation = computed(() => {
  return visibleQuestions.value.length >= 5
})

function jumpToQuestion(questionId: string) {
  const el = document.getElementById(`question-${questionId}`)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }
}

onMounted(async () => {
  await loadQuestionnaire()
})

onUnmounted(() => {
  if (saveDraftTimer) {
    clearTimeout(saveDraftTimer)
  }
  if (store.currentQuestionnaire && isActive.value && !submitted.value) {
    saveDraftToStorage()
  }
})

async function loadQuestionnaire() {
  const storedPwd = getStoredPassword(questionnaireId.value)
  await store.fetchQuestionnaire(questionnaireId.value, storedPwd || undefined)

  if (store.currentQuestionnaire?.passwordProtected) {
    if (storedPwd && store.currentQuestionnaire.questions.length > 0) {
      verifiedPassword.value = storedPwd
      await checkForDraft()
    } else {
      showPasswordModal.value = true
    }
  } else {
    verifiedPassword.value = ''
    await checkForDraft()
  }
}

async function checkForDraft() {
  if (!store.currentQuestionnaire || !isActive.value) return
  if (submitted.value) return

  const draft = await loadDraft()
  if (draft && draft.answers && draft.answers.length > 0) {
    const totalQuestions = visibleQuestions.value.length || store.currentQuestionnaire.questions.length
    const completed = getCompletedCount(draft)
    if (completed > 0 && completed < totalQuestions) {
      resumeDraftInfo.value = draft
      showResumeModal.value = true
    }
  }
}

async function verifyPassword() {
  if (!passwordInput.value.trim()) {
    passwordError.value = '请输入访问口令'
    return
  }

  verifyingPassword.value = true
  passwordError.value = ''

  try {
    const valid = await api.verifyAccessPassword(questionnaireId.value, passwordInput.value.trim())
    if (valid) {
      verifiedPassword.value = passwordInput.value.trim()
      setStoredPassword(questionnaireId.value, passwordInput.value.trim())
      showPasswordModal.value = false
      passwordInput.value = ''
      await store.fetchQuestionnaire(questionnaireId.value, verifiedPassword.value)
      await checkForDraft()
    } else {
      passwordError.value = '口令错误，请重试'
    }
  } catch (e) {
    passwordError.value = '验证失败，请稍后重试'
  } finally {
    verifyingPassword.value = false
  }
}

const visibleQuestions = computed(() => {
  if (!store.currentQuestionnaire) return []
  return getVisibleQuestions(store.currentQuestionnaire.questions, answers.value)
})

const terminateInfo = computed(() => {
  if (!store.currentQuestionnaire) return { terminated: false, message: '' }
  return getTerminateInfo(store.currentQuestionnaire.questions, answers.value)
})

const canSubmit = computed(() => {
  if (!store.currentQuestionnaire) return false
  if (terminateInfo.value.terminated) return true
  for (const q of visibleQuestions.value) {
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

const isQuotaFull = computed(() => {
  if (!store.currentQuestionnaire?.maxResponses) return false
  if (!store.currentQuestionnaire.responseCount) return false
  return store.currentQuestionnaire.responseCount >= store.currentQuestionnaire.maxResponses
})

const closedReason = computed(() => {
  if (!store.currentQuestionnaire) return ''
  if (store.currentQuestionnaire.status === 'expired' || isExpired.value) {
    return '该问卷已截止，感谢您的关注。'
  }
  if (store.currentQuestionnaire.status === 'closed') {
    if (store.currentQuestionnaire.closedMessage) {
      return store.currentQuestionnaire.closedMessage
    }
    if (isQuotaFull.value) {
      return '问卷已达到最大回收份数，感谢您的关注。'
    }
    return '该问卷已停止收集，感谢您的关注。'
  }
  if (store.currentQuestionnaire.status === 'draft') {
    return '该问卷尚未发布。'
  }
  return '该问卷已停止收集，感谢您的关注。'
})

const isActive = computed(() => {
  return store.currentQuestionnaire?.status === 'active' && !isExpired.value && !isQuotaFull.value
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
  lastQuestionId.value = questionId
  scheduleSaveDraft()
}

function scheduleSaveDraft() {
  if (saveDraftTimer) {
    clearTimeout(saveDraftTimer)
  }
  saveDraftTimer = setTimeout(() => {
    saveDraftToStorage()
  }, SAVE_DRAFT_DEBOUNCE_MS)
}

function buildDraftData(): DraftData {
  const answerList: DraftAnswer[] = Object.entries(answers.value)
    .filter(([_, value]) => {
      if (Array.isArray(value)) {
        return value.length > 0
      }
      return value !== '' && value !== null && value !== undefined
    })
    .map(([questionId, value]) => ({
      questionId,
      value
    }))

  const elapsedSeconds = Math.floor((Date.now() - startTime.value) / 1000)

  return {
    questionnaireId: questionnaireId.value,
    respondentId: respondentId.value,
    answers: answerList,
    lastQuestionId: lastQuestionId.value,
    elapsedSeconds,
    updatedAt: new Date().toISOString()
  }
}

async function saveDraftToStorage() {
  if (!store.currentQuestionnaire || !isActive.value) return

  const draft = buildDraftData()
  
  api.saveLocalDraft(questionnaireId.value, draft)
  
  if (verifiedPassword.value || !store.currentQuestionnaire.passwordProtected) {
    try {
      await api.saveDraft(questionnaireId.value, draft)
    } catch (e) {
    }
  }
}

async function loadDraft(): Promise<DraftData | null> {
  let draft: DraftData | null = null
  
  try {
    draft = await api.getDraft(questionnaireId.value, respondentId.value)
  } catch (e) {
  }
  
  if (!draft) {
    draft = api.getLocalDraft(questionnaireId.value)
  }
  
  return draft
}

function applyDraft(draft: DraftData) {
  const newAnswers: Record<string, string | string[]> = {}
  for (const ans of draft.answers) {
    newAnswers[ans.questionId] = ans.value
  }
  answers.value = newAnswers
  
  if (draft.elapsedSeconds) {
    startTime.value = Date.now() - draft.elapsedSeconds * 1000
  }
  
  if (draft.lastQuestionId) {
    lastQuestionId.value = draft.lastQuestionId
  }
}

async function resumeSurvey() {
  if (!resumeDraftInfo.value) return
  
  resuming.value = true
  try {
    applyDraft(resumeDraftInfo.value)
    showResumeModal.value = false
    
    await nextTick()
    
    if (lastQuestionId.value) {
      setTimeout(() => {
        const el = document.getElementById(`question-${lastQuestionId.value}`)
        if (el) {
          el.scrollIntoView({ behavior: 'smooth', block: 'center' })
        }
      }, 300)
    }
  } finally {
    resuming.value = false
  }
}

function startNewSurvey() {
  api.removeLocalDraft(questionnaireId.value)
  api.deleteDraft(questionnaireId.value, respondentId.value)
  answers.value = {}
  lastQuestionId.value = ''
  startTime.value = Date.now()
  showResumeModal.value = false
}

function formatDraftTime(dateStr: string): string {
  const date = new Date(dateStr)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMs / 3600000)
  const diffDays = Math.floor(diffMs / 86400000)
  
  if (diffMins < 1) return '刚刚'
  if (diffMins < 60) return `${diffMins} 分钟前`
  if (diffHours < 24) return `${diffHours} 小时前`
  if (diffDays < 7) return `${diffDays} 天前`
  return date.toLocaleDateString('zh-CN')
}

function getCompletedCount(draft: DraftData): number {
  return draft.answers.filter(a => {
    if (Array.isArray(a.value)) {
      return a.value.length > 0
    }
    return a.value !== '' && a.value !== null && a.value !== undefined
  }).length
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

    const success = await store.submitQuestionnaire(
      questionnaireId.value,
      answerList,
      submitDurationSeconds,
      verifiedPassword.value || undefined
    )
    if (success) {
      submitted.value = true
      api.removeLocalDraft(questionnaireId.value)
      try {
        await api.deleteDraft(questionnaireId.value, respondentId.value)
      } catch (e) {}
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
    <Teleport to="body">
      <div v-if="showPasswordModal" class="password-modal-overlay" @click.self="showPasswordModal = false">
        <div class="password-modal">
          <div class="password-modal-header">
            <div class="password-icon">🔒</div>
            <h3>访问口令验证</h3>
            <p>该问卷需要口令才能访问</p>
          </div>
          <div class="password-modal-body">
            <input
              v-model="passwordInput"
              type="text"
              class="password-input"
              placeholder="请输入访问口令"
              @keyup.enter="verifyPassword"
              autofocus
            />
            <div v-if="passwordError" class="password-error">
              {{ passwordError }}
            </div>
          </div>
          <div class="password-modal-footer">
            <button
              class="btn btn-primary password-submit-btn"
              :disabled="verifyingPassword || !passwordInput.trim()"
              @click="verifyPassword"
            >
              {{ verifyingPassword ? '验证中...' : '确认' }}
            </button>
          </div>
        </div>
      </div>

      <div v-if="showResumeModal && resumeDraftInfo" class="resume-modal-overlay" @click.self="startNewSurvey">
        <div class="resume-modal">
          <div class="resume-modal-header">
            <div class="resume-icon">📝</div>
            <h3>检测到未完成的作答</h3>
            <p class="resume-subtitle">上次作答于 {{ formatDraftTime(resumeDraftInfo.updatedAt) }}</p>
          </div>
          <div class="resume-modal-body">
            <div class="resume-progress">
              <div class="resume-progress-text">
                已完成 {{ getCompletedCount(resumeDraftInfo) }} / {{ visibleQuestions.length || store.currentQuestionnaire?.questions.length || 0 }} 题
              </div>
              <div class="resume-progress-bar">
                <div
                  class="resume-progress-fill"
                  :style="{ width: `${visibleQuestions.length || store.currentQuestionnaire?.questions.length ? (getCompletedCount(resumeDraftInfo) / (visibleQuestions.length || store.currentQuestionnaire?.questions.length || 1)) * 100 : 0}%` }"
                ></div>
              </div>
            </div>
            <p class="resume-tip">是否继续上次的作答？您将回到上次离开时的位置。</p>
          </div>
          <div class="resume-modal-footer">
            <button class="btn btn-outline resume-btn-secondary" @click="startNewSurvey">
              重新开始
            </button>
            <button
              class="btn btn-primary resume-btn-primary"
              :disabled="resuming"
              @click="resumeSurvey"
            >
              {{ resuming ? '恢复中...' : '继续作答' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <div :class="['fill-container', { 'with-nav': showNavigation }]">
      <aside v-if="showNavigation" class="nav-sidebar">
        <QuestionNavigation
          :questions="visibleQuestions"
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
        <div class="closed-icon">
          <span v-if="isQuotaFull">📦</span>
          <span v-else-if="isExpired">⏰</span>
          <span v-else>!</span>
        </div>
        <h2>{{ isQuotaFull ? '问卷已满额' : (isExpired ? '问卷已截止' : '问卷已结束') }}</h2>
        <p class="closed-message">{{ closedReason }}</p>
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

              <div
                v-if="store.currentQuestionnaire.maxResponses && store.currentQuestionnaire.responseCount !== null && store.currentQuestionnaire.responseCount !== undefined"
                :class="['quota-info', { 'light-text': isDarkBg }]"
              >
                已回收 {{ store.currentQuestionnaire.responseCount }} / {{ store.currentQuestionnaire.maxResponses }} 份
              </div>
            </div>
          </div>

          <div class="cover-accent-bar" :style="{ background: coverConfig.accentColor }"></div>
        </header>

        <div class="progress-bar">
          <div class="progress-text">
            {{ Object.keys(answers).filter(k => answers[k] && (Array.isArray(answers[k]) ? answers[k].length > 0 : true)).length }} / {{ visibleQuestions.length }} 题已填写
          </div>
          <div class="progress-track">
            <div
              class="progress-fill"
              :style="{ width: visibleQuestions.length ? `${(Object.keys(answers).filter(k => answers[k] && (Array.isArray(answers[k]) ? answers[k].length > 0 : true)).length / visibleQuestions.length) * 100}%` : '0%' }"
            ></div>
          </div>
        </div>

        <FormRenderer
          :questions="store.currentQuestionnaire.questions"
          :answers="answers"
          @answer="handleAnswer"
        />

        <div v-if="terminateInfo.terminated" class="terminate-banner">
          <div class="terminate-icon">⏹️</div>
          <div class="terminate-content">
            <p class="terminate-title">问卷将在此结束</p>
            <p v-if="terminateInfo.message" class="terminate-message">{{ terminateInfo.message }}</p>
          </div>
        </div>

        <div v-if="errorMsg" class="error-message">
          {{ errorMsg }}
        </div>

        <div class="submit-area">
          <button
            class="btn btn-success submit-btn"
            :disabled="!canSubmit || submitting"
            @click="submitForm"
          >
            {{ submitting ? '提交中...' : (terminateInfo.terminated ? '提交并结束' : '提交问卷') }}
          </button>
        </div>
      </template>
      </main>
    </div>
  </div>
</template>

<style scoped>
.password-modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  padding: 20px;
}

.password-modal {
  background: white;
  border-radius: 16px;
  width: 100%;
  max-width: 400px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
  overflow: hidden;
}

.password-modal-header {
  text-align: center;
  padding: 32px 24px 20px;
}

.password-icon {
  font-size: 48px;
  margin-bottom: 12px;
}

.password-modal-header h3 {
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0 0 8px;
}

.password-modal-header p {
  font-size: 14px;
  color: var(--color-text-secondary);
  margin: 0;
}

.password-modal-body {
  padding: 0 24px 20px;
}

.password-input {
  width: 100%;
  padding: 14px 16px;
  font-size: 16px;
  border: 2px solid var(--color-border);
  border-radius: var(--radius);
  outline: none;
  transition: border-color 0.2s;
  box-sizing: border-box;
}

.password-input:focus {
  border-color: var(--color-primary);
}

.password-error {
  margin-top: 8px;
  font-size: 13px;
  color: var(--color-danger);
  text-align: center;
}

.password-modal-footer {
  padding: 0 24px 24px;
}

.password-submit-btn {
  width: 100%;
  padding: 14px;
  font-size: 16px;
  font-weight: 600;
}

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

.closed-icon {
  width: 80px;
  height: 80px;
  margin: 0 auto 20px;
  background: linear-gradient(135deg, #6366F1 0%, #8B5CF6 100%);
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40px;
}

.closed-message {
  color: var(--color-text-secondary);
  margin-bottom: 24px;
  white-space: pre-wrap;
  line-height: 1.6;
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

.quota-info {
  font-size: 14px;
  margin: 0;
  opacity: 0.8;
  font-weight: 500;
  padding: 6px 12px;
  background: rgba(0, 0, 0, 0.08);
  border-radius: 20px;
  display: inline-block;
  width: fit-content;
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

.terminate-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  background: linear-gradient(135deg, #FEF3C7 0%, #FDE68A 100%);
  border-bottom: 1px solid #F59E0B;
}

.terminate-icon {
  font-size: 28px;
  flex-shrink: 0;
}

.terminate-content {
  flex: 1;
}

.terminate-title {
  margin: 0 0 4px;
  font-size: 15px;
  font-weight: 600;
  color: #92400E;
}

.terminate-message {
  margin: 0;
  font-size: 13px;
  color: #B45309;
  line-height: 1.5;
}

.resume-modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  padding: 20px;
}

.resume-modal {
  background: white;
  border-radius: 16px;
  width: 100%;
  max-width: 440px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
  overflow: hidden;
  animation: slideUp 0.3s ease;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.resume-modal-header {
  text-align: center;
  padding: 32px 24px 20px;
  background: linear-gradient(135deg, #EEF2FF 0%, #E0E7FF 100%);
}

.resume-icon {
  font-size: 48px;
  margin-bottom: 12px;
}

.resume-modal-header h3 {
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0 0 4px;
}

.resume-subtitle {
  font-size: 14px;
  color: #6366F1;
  margin: 0;
}

.resume-modal-body {
  padding: 24px;
}

.resume-progress {
  margin-bottom: 16px;
}

.resume-progress-text {
  font-size: 14px;
  color: var(--color-text-secondary);
  margin-bottom: 8px;
  text-align: center;
}

.resume-progress-bar {
  height: 8px;
  background: var(--color-border);
  border-radius: 4px;
  overflow: hidden;
}

.resume-progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #4F46E5, #8B5CF6);
  border-radius: 4px;
  transition: width 0.3s ease;
}

.resume-tip {
  font-size: 14px;
  color: var(--color-text-secondary);
  text-align: center;
  line-height: 1.6;
  margin: 0;
}

.resume-modal-footer {
  display: flex;
  gap: 12px;
  padding: 0 24px 24px;
}

.resume-btn-secondary {
  flex: 1;
  padding: 12px;
  font-size: 15px;
  font-weight: 500;
}

.resume-btn-primary {
  flex: 1.2;
  padding: 12px;
  font-size: 15px;
  font-weight: 600;
}
</style>
