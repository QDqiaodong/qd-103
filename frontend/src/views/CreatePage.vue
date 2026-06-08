<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useQuestionnaireStore } from '../stores/questionnaire'
import type { Question, QuestionType, CoverConfig } from '../types'
import { DEFAULT_COVER_CONFIG, COVER_THEMES } from '../types'
import QuestionEditor from '../components/QuestionEditor.vue'

const router = useRouter()
const store = useQuestionnaireStore()

const title = ref('')
const description = ref('')
const deadline = ref('')
const questions = ref<Question[]>([])
const saving = ref(false)
const selectedTheme = ref<keyof typeof COVER_THEMES>('professional')
const maxResponsesEnabled = ref(false)
const maxResponses = ref<number>(100)
const closedMessage = ref('')

const canSave = computed(() => {
  return title.value.trim() && questions.value.length > 0
})

function addQuestion(type: QuestionType) {
  const question: Question = {
    id: 'q_' + Math.random().toString(36).substring(2) + Date.now().toString(36),
    type,
    content: '',
    orderIndex: questions.value.length,
    required: true,
    showCondition: null
  }
  if (type !== 'text') {
    question.options = [
      { id: 'o_' + Math.random().toString(36).substring(2), content: '选项1', orderIndex: 0, terminateSurvey: false, terminateMessage: '' },
      { id: 'o_' + Math.random().toString(36).substring(2), content: '选项2', orderIndex: 1, terminateSurvey: false, terminateMessage: '' }
    ]
  }
  questions.value.push(question)
}

function updateQuestion(index: number, data: Partial<Question>) {
  Object.assign(questions.value[index], data)
}

function deleteQuestion(index: number) {
  const deletedId = questions.value[index].id
  questions.value.splice(index, 1)
  questions.value.forEach((q, i) => {
    q.orderIndex = i
    if (q.showCondition) {
      try {
        const cond = JSON.parse(q.showCondition)
        if (cond.dependOnQuestionId === deletedId) {
          q.showCondition = null
        }
      } catch {}
    }
  })
}

function moveQuestion(index: number, direction: 'up' | 'down') {
  const newIndex = direction === 'up' ? index - 1 : index + 1
  if (newIndex < 0 || newIndex >= questions.value.length) return
  const temp = questions.value[index]
  questions.value[index] = questions.value[newIndex]
  questions.value[newIndex] = temp
  questions.value[index].orderIndex = index
  questions.value[newIndex].orderIndex = newIndex
}

async function saveQuestionnaire() {
  if (!canSave.value) return

  saving.value = true
  try {
    const themeConfig = COVER_THEMES[selectedTheme.value]
    const coverConfig: CoverConfig = {
      ...DEFAULT_COVER_CONFIG,
      theme: selectedTheme.value,
      accentColor: themeConfig.accentColor,
      backgroundStyle: themeConfig.backgroundStyle,
      gradientStart: themeConfig.gradientStart,
      gradientEnd: themeConfig.gradientEnd,
      backgroundColor: themeConfig.backgroundColor,
      layout: themeConfig.layout
    }

    const data = {
      title: title.value,
      description: description.value,
      deadline: deadline.value || undefined,
      status: 'active' as const,
      questions: questions.value.map((q, i) => ({
        ...q,
        orderIndex: i
      })),
      coverConfig,
      maxResponses: maxResponsesEnabled.value ? maxResponses.value : undefined,
      closedMessage: closedMessage.value.trim() || undefined
    }

    const result = await store.createQuestionnaire(data)
    if (result) {
      router.push(`/edit/${result.id}`)
    }
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="create-page">
    <header class="page-header-bar">
      <div class="container">
        <div class="header-content">
          <h1 class="page-title">创建问卷</h1>
          <div class="header-actions">
            <button class="btn btn-outline" @click="router.back()">
              取消
            </button>
            <button
              class="btn btn-success"
              :disabled="!canSave || saving"
              @click="saveQuestionnaire"
            >
              {{ saving ? '保存中...' : '保存并发布' }}
            </button>
          </div>
        </div>
      </div>
    </header>

    <main class="container">
      <div class="create-layout">
        <aside class="settings-panel card">
          <h2 class="panel-title">问卷设置</h2>

          <div class="form-group">
            <label class="form-label">问卷标题 *</label>
            <input
              v-model="title"
              type="text"
              class="form-input"
              placeholder="请输入问卷标题"
            />
          </div>

          <div class="form-group">
            <label class="form-label">问卷描述</label>
            <textarea
              v-model="description"
              class="form-input form-textarea"
              placeholder="请输入问卷描述（可选）"
            ></textarea>
          </div>

          <div class="form-group">
            <label class="form-label">截止时间</label>
            <input
              v-model="deadline"
              type="datetime-local"
              class="form-input"
            />
          </div>

          <div class="form-group">
            <label class="form-label">最大回收份数</label>
            <div class="quota-toggle">
              <label class="toggle-label">
                <input
                  type="checkbox"
                  v-model="maxResponsesEnabled"
                  class="toggle-checkbox"
                />
                <span class="toggle-text">启用限额收集</span>
              </label>
              <p class="quota-hint">开启后，达到设定份数时问卷将自动停止收集</p>
            </div>

            <div v-if="maxResponsesEnabled" class="quota-inputs">
              <div class="form-group">
                <label class="form-label">最大份数</label>
                <input
                  v-model.number="maxResponses"
                  type="number"
                  min="1"
                  class="form-input"
                  placeholder="请输入最大回收份数"
                />
              </div>
              <div class="form-group">
                <label class="form-label">封卷说明</label>
                <textarea
                  v-model="closedMessage"
                  class="form-input form-textarea"
                  placeholder="问卷满额后显示给用户的说明文字（可选）"
                  rows="3"
                ></textarea>
              </div>
            </div>
          </div>

          <div class="form-group">
            <label class="form-label">封面主题</label>
            <div class="theme-quick-select">
              <button
                v-for="(config, key) in COVER_THEMES"
                :key="key"
                :class="['theme-quick-btn', { active: selectedTheme === key }]"
                @click="selectedTheme = key"
              >
                <span
                  class="theme-dot"
                  :style="{ background: config.accentColor }"
                ></span>
                {{ config.label }}
              </button>
            </div>
            <p class="form-hint">创建后可在编辑页详细配置封面</p>
          </div>
        </aside>

        <section class="questions-panel">
          <div class="questions-header">
            <h2 class="panel-title">题目列表</h2>
            <span class="questions-count">{{ questions.length }} 题</span>
          </div>

          <div v-if="questions.length === 0" class="empty-questions">
            <p>暂无题目</p>
            <p class="hint">请点击下方按钮添加题目</p>
          </div>

          <div v-else class="questions-list">
            <QuestionEditor
              v-for="(question, index) in questions"
              :key="question.id"
              :question="question"
              :index="index"
              :total="questions.length"
              :all-questions="questions"
              @update="(data) => updateQuestion(index, data)"
              @delete="deleteQuestion(index)"
              @move="(dir) => moveQuestion(index, dir)"
            />
          </div>

          <div class="add-question-buttons">
            <button
              class="btn btn-outline add-btn"
              @click="addQuestion('single')"
            >
              + 单选题
            </button>
            <button
              class="btn btn-outline add-btn"
              @click="addQuestion('multiple')"
            >
              + 多选题
            </button>
            <button
              class="btn btn-outline add-btn"
              @click="addQuestion('text')"
            >
              + 填空题
            </button>
          </div>
        </section>
      </div>
    </main>
  </div>
</template>

<style scoped>
.create-page {
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

.create-layout {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 24px;
  padding: 24px 0;
}

.settings-panel {
  height: fit-content;
  position: sticky;
  top: 24px;
}

.panel-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 20px;
}

.theme-quick-select {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.theme-quick-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 10px;
  border: 1px solid var(--color-border);
  border-radius: 16px;
  background: white;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.theme-quick-btn:hover {
  border-color: var(--color-primary);
}

.theme-quick-btn.active {
  border-color: var(--color-primary);
  background: #EEF2FF;
  color: var(--color-primary);
}

.theme-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.form-hint {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-top: 6px;
}

.quota-toggle {
  padding: 12px;
  border: 2px solid var(--color-border);
  border-radius: var(--radius);
  background: white;
}

.quota-hint {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin: 8px 0 0 26px;
  line-height: 1.5;
}

.quota-inputs {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--color-border);
}

.quota-inputs .form-group {
  margin-bottom: 12px;
}

.quota-inputs .form-group:last-child {
  margin-bottom: 0;
}

.questions-panel {
  background: white;
  border-radius: var(--radius);
  padding: 24px;
  min-height: 400px;
}

.questions-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.questions-count {
  font-size: 14px;
  color: var(--color-text-secondary);
}

.empty-questions {
  text-align: center;
  padding: 60px 20px;
  color: var(--color-text-secondary);
}

.empty-questions .hint {
  font-size: 13px;
  margin-top: 8px;
}

.questions-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.add-question-buttons {
  display: flex;
  gap: 12px;
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px dashed var(--color-border);
}

.add-btn {
  flex: 1;
}

@media (max-width: 768px) {
  .create-layout {
    grid-template-columns: 1fr;
  }

  .settings-panel {
    position: static;
  }
}
</style>
