<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQuestionnaireStore } from '../stores/questionnaire'
import type { QuestionType, QuestionnaireStatus, Question, QuestionOption, CoverConfig } from '../types'
import { DEFAULT_COVER_CONFIG } from '../types'
import QuestionEditor from '../components/QuestionEditor.vue'
import CoverEditor from '../components/CoverEditor.vue'
import CoverPreview from '../components/CoverPreview.vue'

const route = useRoute()
const router = useRouter()
const store = useQuestionnaireStore()

const title = ref('')
const description = ref('')
const deadline = ref('')
const status = ref<QuestionnaireStatus>('active')
const questions = ref<(Question & { options?: QuestionOption[] })[]>([])
const saving = ref(false)
const activeTab = ref<'questions' | 'cover'>('questions')
const coverConfig = ref<CoverConfig>({ ...DEFAULT_COVER_CONFIG })

const questionnaireId = computed(() => route.params.id as string)

onMounted(async () => {
  await store.fetchQuestionnaire(questionnaireId.value)
  if (store.currentQuestionnaire) {
    title.value = store.currentQuestionnaire.title
    description.value = store.currentQuestionnaire.description
    deadline.value = store.currentQuestionnaire.deadline ? store.currentQuestionnaire.deadline.slice(0, 16) : ''
    status.value = store.currentQuestionnaire.status
    questions.value = JSON.parse(JSON.stringify(store.currentQuestionnaire.questions))
    if (store.currentQuestionnaire.coverConfig) {
      coverConfig.value = { ...DEFAULT_COVER_CONFIG, ...store.currentQuestionnaire.coverConfig }
    }
  }
})

const canSave = computed(() => {
  return title.value.trim() && questions.value.length > 0
})

function addQuestion(type: QuestionType) {
  const question: Question & { options?: QuestionOption[] } = {
    id: 'q_' + Math.random().toString(36).substring(2) + Date.now().toString(36),
    type,
    content: '',
    orderIndex: questions.value.length,
    required: true
  }
  if (type !== 'text') {
    question.options = [
      { id: 'o_' + Math.random().toString(36).substring(2), content: '选项1', orderIndex: 0 },
      { id: 'o_' + Math.random().toString(36).substring(2), content: '选项2', orderIndex: 1 }
    ]
  }
  questions.value.push(question)
}

function updateQuestion(index: number, data: any) {
  Object.assign(questions.value[index], data)
}

function deleteQuestion(index: number) {
  questions.value.splice(index, 1)
  questions.value.forEach((q, i) => {
    q.orderIndex = i
  })
}

function moveQuestion(index: number, direction: 'up' | 'down') {
  const newIndex = direction === 'up' ? index - 1 : index + 1
  if (newIndex < 0 || newIndex >= questions.value.length) return
  const temp = questions.value[index]
  questions.value[index] = questions.value[newIndex]
  questions.value[newIndex] = temp
}

async function saveQuestionnaire() {
  if (!canSave.value) return

  saving.value = true
  try {
    const data = {
      title: title.value,
      description: description.value,
      deadline: deadline.value || undefined,
      status: status.value,
      questions: questions.value,
      coverConfig: coverConfig.value
    }

    const result = await store.updateQuestionnaire(questionnaireId.value, data)
    if (result) {
      alert('保存成功')
    }
  } finally {
    saving.value = false
  }
}

function goToFill() {
  router.push(`/fill/${questionnaireId.value}`)
}

function goToStatistics() {
  router.push(`/statistics/${questionnaireId.value}`)
}

function copyLink() {
  const link = `${window.location.origin}/fill/${questionnaireId.value}`
  navigator.clipboard.writeText(link)
  alert('链接已复制到剪贴板')
}
</script>

<template>
  <div class="edit-page">
    <header class="page-header-bar">
      <div class="container">
        <div class="header-content">
          <h1 class="page-title">编辑问卷</h1>
          <div class="header-actions">
            <button class="btn btn-outline" @click="router.back()">
              返回
            </button>
            <button class="btn btn-outline" @click="copyLink">
              复制链接
            </button>
            <button class="btn btn-primary" @click="goToFill">
              填写问卷
            </button>
            <button class="btn btn-outline" @click="goToStatistics">
              查看统计
            </button>
            <button
              class="btn btn-success"
              :disabled="!canSave || saving"
              @click="saveQuestionnaire"
            >
              {{ saving ? '保存中...' : '保存' }}
            </button>
          </div>
        </div>
      </div>
    </header>

    <main class="container">
      <div class="edit-layout">
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
              placeholder="请输入问卷描述"
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
            <label class="form-label">问卷状态</label>
            <select v-model="status" class="form-input">
              <option value="draft">草稿</option>
              <option value="active">收集中</option>
              <option value="closed">已关闭</option>
            </select>
          </div>
        </aside>

        <section class="main-panel">
          <div class="main-tabs">
            <button
              :class="['main-tab', { active: activeTab === 'questions' }]"
              @click="activeTab = 'questions'"
            >
              题目编辑
              <span v-if="questions.length" class="tab-badge">{{ questions.length }}</span>
            </button>
            <button
              :class="['main-tab', { active: activeTab === 'cover' }]"
              @click="activeTab = 'cover'"
            >
              封面设置
            </button>
          </div>

          <div v-if="activeTab === 'questions'" class="tab-panel">
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
          </div>

          <div v-if="activeTab === 'cover'" class="tab-panel">
            <div class="cover-layout">
              <div class="cover-editor-area">
                <CoverEditor v-model="coverConfig" />
              </div>
              <div class="cover-preview-area">
                <CoverPreview
                  :cover-config="coverConfig"
                  :title="title"
                  :description="description"
                />
              </div>
            </div>
          </div>
        </section>
      </div>
    </main>
  </div>
</template>

<style scoped>
.edit-page {
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

.edit-layout {
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

.main-panel {
  background: white;
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  overflow: hidden;
}

.main-tabs {
  display: flex;
  border-bottom: 1px solid var(--color-border);
  background: #F8FAFC;
}

.main-tab {
  padding: 14px 24px;
  border: none;
  background: transparent;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: all 0.2s;
  border-bottom: 2px solid transparent;
  display: flex;
  align-items: center;
  gap: 6px;
}

.main-tab:hover {
  color: var(--color-text);
  background: #F1F5F9;
}

.main-tab.active {
  color: var(--color-primary);
  border-bottom-color: var(--color-primary);
  background: white;
}

.tab-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 10px;
  background: var(--color-primary);
  color: white;
  font-size: 11px;
  font-weight: 600;
}

.tab-panel {
  padding: 24px;
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

.cover-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  align-items: start;
}

.cover-editor-area {
  min-width: 0;
}

.cover-preview-area {
  position: sticky;
  top: 24px;
}

@media (max-width: 1024px) {
  .cover-layout {
    grid-template-columns: 1fr;
  }

  .cover-preview-area {
    position: static;
  }
}

@media (max-width: 768px) {
  .edit-layout {
    grid-template-columns: 1fr;
  }

  .settings-panel {
    position: static;
  }
}
</style>
