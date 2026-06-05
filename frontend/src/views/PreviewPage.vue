<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { useQuestionnaireStore } from '../stores/questionnaire'
import { computed } from 'vue'
import type { CoverConfig } from '../types'
import { DEFAULT_COVER_CONFIG } from '../types'

const route = useRoute()
const router = useRouter()
const store = useQuestionnaireStore()

const questionnaireId = computed(() => route.params.id as string)

async function loadAndPreview() {
  await store.fetchQuestionnaire(questionnaireId.value)
}

loadAndPreview()

function goToEdit() {
  router.push(`/edit/${questionnaireId.value}`)
}

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
</script>

<template>
  <div class="preview-page">
    <header class="preview-header">
      <div class="container">
        <div class="header-content">
          <h1 class="page-title">问卷预览</h1>
          <button class="btn btn-primary" @click="goToEdit">
            进入编辑
          </button>
        </div>
      </div>
    </header>

    <main class="container">
      <div v-if="store.loading" class="loading">
        加载中...
      </div>

      <div v-else-if="!store.currentQuestionnaire" class="error-state">
        <p>问卷不存在</p>
      </div>

      <div v-else class="preview-container">
        <div class="preview-notice">
          此页面仅供预览，不收集数据
        </div>

        <div class="preview-body">
          <header
            :class="['preview-cover', coverConfig.layout, { 'dark-bg': isDarkBg }]"
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
                <h2
                  :class="['cover-title', { 'light-text': isDarkBg }]"
                  :style="{ color: isDarkBg ? '#FFFFFF' : coverConfig.accentColor }"
                >
                  {{ store.currentQuestionnaire.title }}
                </h2>

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
              </div>
            </div>

            <div class="cover-accent-bar" :style="{ background: coverConfig.accentColor }"></div>
          </header>

          <div class="preview-questions">
            <div
              v-for="(question, index) in store.currentQuestionnaire.questions"
              :key="question.id"
              class="preview-question"
            >
              <div class="question-label">
                <span class="question-number">{{ index + 1 }}.</span>
                <span v-if="question.required" class="required-mark">*</span>
              </div>
              <p class="question-text">{{ question.content }}</p>

              <div v-if="question.type === 'single'" class="question-options">
                <div
                  v-for="option in question.options"
                  :key="option.id"
                  class="preview-option"
                >
                  ○ {{ option.content }}
                </div>
              </div>

              <div v-else-if="question.type === 'multiple'" class="question-options">
                <div
                  v-for="option in question.options"
                  :key="option.id"
                  class="preview-option"
                >
                  ☐ {{ option.content }}
                </div>
              </div>

              <div v-else class="question-options">
                <div class="preview-text-placeholder">
                  文本输入框
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.preview-page {
  min-height: 100vh;
  background: var(--color-bg);
}

.preview-header {
  background: white;
  border-bottom: 1px solid var(--color-border);
  padding: 16px 0;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.preview-container {
  max-width: 700px;
  margin: 0 auto;
  padding: 24px 0;
}

.preview-notice {
  background: #FEF3C7;
  color: #92400E;
  padding: 12px 20px;
  border-radius: var(--radius);
  text-align: center;
  margin-bottom: 20px;
  font-size: 14px;
}

.preview-body {
  background: white;
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  overflow: hidden;
}

.preview-cover {
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.cover-header-img {
  width: 100%;
  max-height: 200px;
  overflow: hidden;
}

.cover-header-img.hero {
  max-height: 240px;
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
  padding: 28px;
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
  gap: 20px;
  align-items: stretch;
}

.split-image-area {
  flex: 1;
  min-height: 140px;
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
  gap: 10px;
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
  font-size: 24px;
  font-weight: 700;
  line-height: 1.3;
  margin: 0;
  word-break: break-word;
}

.cover-desc {
  font-size: 15px;
  line-height: 1.6;
  margin: 0;
  opacity: 0.75;
  word-break: break-word;
}

.cover-opening {
  font-size: 13px;
  line-height: 1.6;
  margin: 0;
  padding: 10px 14px;
  border-left: 3px solid;
  background: rgba(0, 0, 0, 0.04);
  border-radius: 0 6px 6px 0;
  word-break: break-word;
}

.cover-opening.light-text {
  background: rgba(255, 255, 255, 0.12);
}

.light-text {
  color: rgba(255, 255, 255, 0.9) !important;
}

.cover-accent-bar {
  height: 4px;
  width: 100%;
}

.preview-questions {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.preview-question {
  padding: 16px;
  background: var(--color-bg);
  border-radius: var(--radius);
}

.question-label {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 8px;
}

.question-number {
  font-weight: 600;
}

.required-mark {
  color: var(--color-danger);
}

.question-text {
  margin-bottom: 12px;
  font-size: 15px;
}

.question-options {
  padding-left: 20px;
}

.preview-option {
  padding: 8px 0;
  color: var(--color-text-secondary);
}

.preview-text-placeholder {
  padding: 12px;
  background: white;
  border: 1px dashed var(--color-border);
  border-radius: var(--radius);
  color: var(--color-text-secondary);
  font-size: 14px;
  text-align: center;
}

.loading,
.error-state {
  text-align: center;
  padding: 60px 20px;
}
</style>
