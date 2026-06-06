<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQuestionnaireStore } from '../stores/questionnaire'
import type { QuestionType, QuestionnaireStatus, Question, QuestionOption, CoverConfig, Snapshot } from '../types'
import { DEFAULT_COVER_CONFIG } from '../types'
import QuestionEditor from '../components/QuestionEditor.vue'
import CoverEditor from '../components/CoverEditor.vue'
import CoverPreview from '../components/CoverPreview.vue'
import * as api from '../services/api'

const route = useRoute()
const router = useRouter()
const store = useQuestionnaireStore()

const title = ref('')
const description = ref('')
const deadline = ref('')
const status = ref<QuestionnaireStatus>('active')
const questions = ref<(Question & { options?: QuestionOption[] })[]>([])
const saving = ref(false)
const activeTab = ref<'questions' | 'cover' | 'snapshots'>('questions')
const coverConfig = ref<CoverConfig>({ ...DEFAULT_COVER_CONFIG })

const snapshots = ref<Snapshot[]>([])
const snapshotsLoading = ref(false)
const creatingSnapshot = ref(false)

const questionnaireId = computed(() => route.params.id as string)

onMounted(async () => {
  const tabParam = route.query.tab as string
  if (tabParam === 'snapshots' || tabParam === 'cover' || tabParam === 'questions') {
    activeTab.value = tabParam
  }

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
  await loadSnapshots()
})

async function loadSnapshots() {
  snapshotsLoading.value = true
  try {
    snapshots.value = await api.getSnapshots(questionnaireId.value)
  } catch (e) {
    console.error('加载快照列表失败', e)
  } finally {
    snapshotsLoading.value = false
  }
}

async function createSnapshot() {
  if (!confirm('确定要创建当前问卷的快照吗？快照将固化当前的题目结构和统计数据。')) {
    return
  }
  creatingSnapshot.value = true
  try {
    const result = await api.createSnapshot(questionnaireId.value)
    if (result) {
      alert('快照创建成功')
      await loadSnapshots()
    }
  } catch (e: any) {
    alert(e.message || '创建快照失败')
  } finally {
    creatingSnapshot.value = false
  }
}

function goToSnapshot(id: string) {
  router.push(`/snapshot/${id}`)
}

function getSnapshotReasonLabel(reason: string): string {
  if (reason.startsWith('manual_')) {
    return '手动快照'
  }
  const reasonMap: Record<string, string> = {
    'closed_closed': '手动关闭封卷',
    'expired': '到期自动封卷'
  }
  return reasonMap[reason] || reason
}

function getSnapshotReasonClass(reason: string): string {
  if (reason.startsWith('manual_')) {
    return 'reason-manual'
  }
  if (reason.startsWith('closed')) {
    return 'reason-closed'
  }
  if (reason === 'expired' || reason.startsWith('expired')) {
    return 'reason-expired'
  }
  return 'reason-manual'
}

function formatSnapshotDate(dateStr?: string): string {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

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

function cloneQuestion(index: number) {
  const original = questions.value[index]
  const cloned: (typeof questions.value)[0] = {
    ...JSON.parse(JSON.stringify(original)),
    id: 'q_' + Math.random().toString(36).substring(2) + Date.now().toString(36)
  }
  
  if (cloned.options) {
    cloned.options = cloned.options.map(opt => ({
      ...opt,
      id: 'o_' + Math.random().toString(36).substring(2)
    }))
  }
  
  questions.value.splice(index + 1, 0, cloned)
  
  questions.value.forEach((q, i) => {
    q.orderIndex = i
  })
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
      await loadSnapshots()
    } else {
      alert(store.error || '保存失败')
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
            <button
              :class="['main-tab', { active: activeTab === 'snapshots' }]"
              @click="activeTab = 'snapshots'"
            >
              历史快照
              <span v-if="snapshots.length" class="tab-badge">{{ snapshots.length }}</span>
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
                @clone="cloneQuestion(index)"
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

          <div v-if="activeTab === 'snapshots'" class="tab-panel">
            <div class="snapshots-header">
              <div>
                <h2 class="panel-title">历史封卷快照</h2>
                <p class="snapshots-desc">
                  快照会固化当时的题目结构、问卷状态和统计结果，不可编辑，用于活动结案、数据复盘和过程留痕
                </p>
              </div>
              <button
                class="btn btn-primary"
                :disabled="creatingSnapshot"
                @click="createSnapshot"
              >
                {{ creatingSnapshot ? '创建中...' : '+ 创建快照' }}
              </button>
            </div>

            <div v-if="snapshotsLoading" class="snapshots-loading">
              加载中...
            </div>

            <div v-else-if="snapshots.length === 0" class="snapshots-empty">
              <div class="empty-icon">📦</div>
              <p>暂无快照记录</p>
              <p class="hint">问卷关闭或过期时会自动生成快照，也可以手动创建</p>
            </div>

            <div v-else class="snapshots-list">
              <div
                v-for="snapshot in snapshots"
                :key="snapshot.id"
                class="snapshot-item card"
                @click="goToSnapshot(snapshot.id)"
              >
                <div class="snapshot-item-header">
                  <div class="snapshot-item-title">
                    <span class="snapshot-icon">📜</span>
                    <span class="snapshot-title-text">{{ snapshot.title || '未命名问卷' }}</span>
                  </div>
                  <span
                    class="snapshot-reason-badge"
                    :class="getSnapshotReasonClass(snapshot.snapshotReason)"
                  >
                    {{ getSnapshotReasonLabel(snapshot.snapshotReason) }}
                  </span>
                </div>
                <div class="snapshot-item-meta">
                  <span class="meta-item">
                    <span class="meta-label">封卷时间：</span>
                    <span class="meta-value">{{ formatSnapshotDate(snapshot.snapshotAt) }}</span>
                  </span>
                  <span class="meta-item">
                    <span class="meta-label">题目数：</span>
                    <span class="meta-value">{{ snapshot.questionCount || 0 }} 题</span>
                  </span>
                  <span class="meta-item">
                    <span class="meta-label">填写数：</span>
                    <span class="meta-value">{{ snapshot.responseCount || 0 }} 人</span>
                  </span>
                </div>
                <div class="snapshot-item-action">
                  查看快照 →
                </div>
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

.snapshots-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
  gap: 16px;
}

.snapshots-desc {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-top: 4px;
  line-height: 1.5;
}

.snapshots-loading,
.snapshots-empty {
  text-align: center;
  padding: 60px 20px;
  color: var(--color-text-secondary);
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 12px;
}

.snapshots-empty p {
  margin: 4px 0;
}

.snapshots-empty .hint {
  font-size: 12px;
  opacity: 0.7;
}

.snapshots-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.snapshot-item {
  padding: 16px 20px;
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1px solid var(--color-border);
}

.snapshot-item:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  transform: translateY(-1px);
}

.snapshot-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  gap: 12px;
}

.snapshot-item-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
  min-width: 0;
}

.snapshot-icon {
  font-size: 18px;
  flex-shrink: 0;
}

.snapshot-title-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.snapshot-reason-badge {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 500;
  flex-shrink: 0;
}

.reason-closed {
  background: #FEF2F2;
  color: #DC2626;
}

.reason-expired {
  background: #FFFBEB;
  color: #D97706;
}

.reason-manual {
  background: #EFF6FF;
  color: #2563EB;
}

.snapshot-item-meta {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.snapshot-item-meta .meta-item {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.snapshot-item-meta .meta-value {
  color: var(--color-text);
  font-weight: 500;
}

.snapshot-item-action {
  text-align: right;
  font-size: 13px;
  color: var(--color-primary);
  font-weight: 500;
  padding-top: 12px;
  border-top: 1px dashed var(--color-border);
}

@media (max-width: 768px) {
  .snapshots-header {
    flex-direction: column;
    align-items: stretch;
  }

  .snapshot-item-meta {
    gap: 12px;
  }
}
</style>
