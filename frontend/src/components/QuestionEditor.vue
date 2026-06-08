<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import type { Question, QuestionOption, ShowCondition } from '../types'
import { ChevronUp, ChevronDown, Trash2, GripVertical, Copy, GitBranch } from 'lucide-vue-next'

const props = defineProps<{
  question: Question
  index: number
  total: number
  allQuestions: Question[]
}>()

const emit = defineEmits<{
  update: [data: Partial<Question>]
  delete: []
  move: [direction: 'up' | 'down']
  clone: []
}>()

const localContent = ref(props.question.content)
const localOptions = ref<QuestionOption[]>(props.question.options || [])
const showLogicPanel = ref(false)

watch(() => props.question, (newQ) => {
  localContent.value = newQ.content
  localOptions.value = newQ.options || []
}, { deep: true })

watch(localContent, (val) => {
  emit('update', { content: val })
})

watch(localOptions, (val) => {
  emit('update', { options: val })
}, { deep: true })

function updateOption(index: number, content: string) {
  localOptions.value[index].content = content
}

function addOption() {
  localOptions.value.push({
    id: 'o_' + Math.random().toString(36).substring(2),
    content: `选项${localOptions.value.length + 1}`,
    orderIndex: localOptions.value.length,
    terminateSurvey: false,
    terminateMessage: ''
  })
}

function removeOption(index: number) {
  if (localOptions.value.length <= 2) return
  localOptions.value.splice(index, 1)
  localOptions.value.forEach((opt, i) => {
    opt.orderIndex = i
  })
}

function toggleRequired() {
  emit('update', { required: !props.question.required })
}

function toggleOptionTerminate(index: number) {
  const opt = localOptions.value[index]
  opt.terminateSurvey = !opt.terminateSurvey
  if (!opt.terminateSurvey) {
    opt.terminateMessage = ''
  }
}

function updateTerminateMessage(index: number, message: string) {
  localOptions.value[index].terminateMessage = message
}

const previousQuestions = computed(() => {
  return props.allQuestions.filter((q, i) => i < props.index && q.type !== 'text')
})

const showConditionObj = computed<ShowCondition | null>(() => {
  if (!props.question.showCondition) return null
  try {
    return JSON.parse(props.question.showCondition)
  } catch {
    return null
  }
})

const dependOnQuestion = computed(() => {
  if (!showConditionObj.value) return null
  return props.allQuestions.find(q => q.id === showConditionObj.value!.dependOnQuestionId) || null
})

function setShowCondition(questionId: string | null) {
  if (!questionId) {
    emit('update', { showCondition: null })
    return
  }
  const condition: ShowCondition = {
    dependOnQuestionId: questionId,
    optionIds: []
  }
  emit('update', { showCondition: JSON.stringify(condition) })
}

function toggleOptionInCondition(optionId: string) {
  if (!showConditionObj.value) return
  const condition = { ...showConditionObj.value }
  const idx = condition.optionIds.indexOf(optionId)
  if (idx === -1) {
    condition.optionIds.push(optionId)
  } else {
    condition.optionIds.splice(idx, 1)
  }
  emit('update', { showCondition: JSON.stringify(condition) })
}

function clearShowCondition() {
  emit('update', { showCondition: null })
}
</script>

<template>
  <div class="question-editor">
    <div class="question-header">
      <div class="question-number">
        <GripVertical :size="16" class="grip-icon" />
        <span>{{ index + 1 }}.</span>
        <span class="question-type-badge">
          {{ question.type === 'single' ? '单选' : question.type === 'multiple' ? '多选' : '填空' }}
        </span>
        <span v-if="question.showCondition" class="logic-badge">
          <GitBranch :size="12" />
          条件显示
        </span>
      </div>
      <div class="question-actions">
        <button
          class="icon-btn"
          :disabled="index === 0"
          @click="emit('move', 'up')"
        >
          <ChevronUp :size="18" />
        </button>
        <button
          class="icon-btn"
          :disabled="index === total - 1"
          @click="emit('move', 'down')"
        >
          <ChevronDown :size="18" />
        </button>
        <button class="icon-btn" @click="emit('clone')" title="克隆题目">
          <Copy :size="18" />
        </button>
        <button class="icon-btn danger" @click="emit('delete')">
          <Trash2 :size="18" />
        </button>
      </div>
    </div>

    <div class="question-body">
      <input
        v-model="localContent"
        type="text"
        class="form-input question-input"
        placeholder="请输入题目内容"
      />

      <div v-if="question.type !== 'text'" class="options-editor">
        <div
          v-for="(option, optIndex) in localOptions"
          :key="option.id"
          class="option-item"
        >
          <span class="option-marker">
            {{ question.type === 'single' ? '○' : '☐' }}
          </span>
          <input
            :value="option.content"
            type="text"
            class="form-input option-input"
            placeholder="选项内容"
            @input="updateOption(optIndex, ($event.target as HTMLInputElement).value)"
          />
          <button
            v-if="localOptions.length > 2"
            class="icon-btn small danger"
            @click="removeOption(optIndex)"
          >
            ×
          </button>
        </div>
        <button class="btn btn-outline add-option-btn" @click="addOption">
          + 添加选项
        </button>
      </div>

      <div class="question-footer">
        <label class="required-toggle">
          <input
            type="checkbox"
            :checked="question.required"
            @change="toggleRequired"
          />
          <span>必填</span>
        </label>
        <button class="logic-toggle-btn" @click="showLogicPanel = !showLogicPanel">
          <GitBranch :size="14" />
          分支逻辑
          <ChevronDown :size="14" :class="{ 'rotated': showLogicPanel }" />
        </button>
      </div>

      <div v-if="showLogicPanel" class="logic-panel">
        <div class="logic-section">
          <h4 class="logic-section-title">📌 显示条件</h4>
          <p class="logic-section-desc">
            设置本题的显示条件，只有当前面的题目选中指定选项时，本题才会显示给答题者。
          </p>

          <div v-if="previousQuestions.length === 0" class="logic-empty">
            暂无可依赖的前置题目（前面需要有选择题）
          </div>

          <div v-else class="logic-form">
            <div class="form-group">
              <label class="form-label">依赖题目</label>
              <select
                :value="showConditionObj?.dependOnQuestionId || ''"
                class="form-input"
                @change="setShowCondition(($event.target as HTMLSelectElement).value || null)"
              >
                <option value="">不设置条件（始终显示）</option>
                <option
                  v-for="q in previousQuestions"
                  :key="q.id"
                  :value="q.id"
                >
                  第 {{ allQuestions.indexOf(q) + 1 }} 题：{{ q.content || '未命名题目' }}
                </option>
              </select>
            </div>

            <div v-if="dependOnQuestion && dependOnQuestion.options?.length" class="form-group">
              <label class="form-label">选中以下哪些选项时显示本题</label>
              <div class="option-check-list">
                <label
                  v-for="opt in dependOnQuestion.options"
                  :key="opt.id"
                  class="option-check-item"
                >
                  <input
                    type="checkbox"
                    :checked="showConditionObj?.optionIds.includes(opt.id)"
                    @change="toggleOptionInCondition(opt.id)"
                  />
                  <span>{{ opt.content }}</span>
                </label>
              </div>
              <p v-if="!showConditionObj?.optionIds.length" class="logic-hint">
                ⚠️ 请至少选择一个选项作为触发条件
              </p>
              <button
                v-if="showConditionObj"
                class="btn btn-outline btn-small clear-btn"
                @click="clearShowCondition"
              >
                清除条件
              </button>
            </div>
          </div>
        </div>

        <div v-if="question.type !== 'text'" class="logic-section">
          <h4 class="logic-section-title">⏹️ 提前结束</h4>
          <p class="logic-section-desc">
            当答题者选中某个选项时，直接结束问卷，后续题目不再显示。适用于报名筛选、资格判定等场景。
          </p>

          <div class="terminate-options">
            <div
              v-for="(option, optIndex) in localOptions"
              :key="option.id"
              class="terminate-option-item"
              :class="{ active: option.terminateSurvey }"
            >
              <label class="terminate-toggle">
                <input
                  type="checkbox"
                  :checked="option.terminateSurvey"
                  @change="toggleOptionTerminate(optIndex)"
                />
                <span class="terminate-label">
                  选中「{{ option.content || '选项' + (optIndex + 1) }}」后结束问卷
                </span>
              </label>
              <div v-if="option.terminateSurvey" class="terminate-message-input">
                <label class="form-label">结束说明（可选）</label>
                <input
                  :value="option.terminateMessage"
                  type="text"
                  class="form-input"
                  placeholder="结束时显示给用户的文字"
                  @input="updateTerminateMessage(optIndex, ($event.target as HTMLInputElement).value)"
                />
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.question-editor {
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  padding: 16px;
}

.question-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.question-number {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
  flex-wrap: wrap;
}

.grip-icon {
  color: var(--color-text-secondary);
  cursor: grab;
}

.question-type-badge {
  font-size: 12px;
  padding: 2px 8px;
  background: var(--color-primary);
  color: white;
  border-radius: 4px;
  font-weight: normal;
}

.logic-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  padding: 2px 8px;
  background: #FEF3C7;
  color: #D97706;
  border-radius: 4px;
  font-weight: 500;
}

.question-actions {
  display: flex;
  gap: 4px;
}

.icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: 6px;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: all 0.2s;
}

.icon-btn:hover:not(:disabled) {
  background: var(--color-border);
  color: var(--color-text);
}

.icon-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.icon-btn.danger:hover:not(:disabled) {
  background: #FEE2E2;
  color: var(--color-danger);
}

.icon-btn.small {
  width: 24px;
  height: 24px;
  font-size: 16px;
}

.question-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.question-input {
  font-size: 15px;
}

.options-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-left: 20px;
}

.option-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.option-marker {
  font-size: 16px;
  color: var(--color-text-secondary);
  width: 20px;
  text-align: center;
}

.option-input {
  flex: 1;
}

.add-option-btn {
  margin-top: 8px;
  width: fit-content;
  padding: 6px 12px;
  font-size: 13px;
}

.question-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 8px;
  border-top: 1px dashed var(--color-border);
}

.required-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.required-toggle input {
  cursor: pointer;
}

.logic-toggle-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  font-size: 13px;
  color: var(--color-primary);
  background: #EFF6FF;
  border: 1px solid #BFDBFE;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.logic-toggle-btn:hover {
  background: #DBEAFE;
}

.logic-toggle-btn .rotated {
  transform: rotate(180deg);
  transition: transform 0.2s;
}

.logic-panel {
  margin-top: 12px;
  padding: 16px;
  background: #FAFAFA;
  border: 1px dashed var(--color-border);
  border-radius: 8px;
}

.logic-section {
  margin-bottom: 20px;
}

.logic-section:last-child {
  margin-bottom: 0;
}

.logic-section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0 0 6px;
}

.logic-section-desc {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin: 0 0 12px;
  line-height: 1.5;
}

.logic-empty {
  font-size: 13px;
  color: var(--color-text-secondary);
  padding: 12px;
  background: white;
  border-radius: 6px;
  text-align: center;
}

.logic-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text);
}

.form-input {
  padding: 8px 12px;
  font-size: 13px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  outline: none;
  transition: border-color 0.2s;
}

.form-input:focus {
  border-color: var(--color-primary);
}

.option-check-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  background: white;
  border-radius: 6px;
  max-height: 160px;
  overflow-y: auto;
}

.option-check-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--color-text);
  cursor: pointer;
}

.option-check-item input {
  cursor: pointer;
}

.logic-hint {
  font-size: 12px;
  color: #D97706;
  margin: 6px 0 0;
}

.clear-btn {
  align-self: flex-start;
  margin-top: 8px;
  padding: 4px 10px;
  font-size: 12px;
  color: var(--color-danger);
  border-color: #FECACA;
}

.clear-btn:hover {
  background: #FEE2E2;
}

.terminate-options {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.terminate-option-item {
  padding: 12px;
  background: white;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  transition: all 0.2s;
}

.terminate-option-item.active {
  border-color: #F87171;
  background: #FEF2F2;
}

.terminate-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-size: 13px;
}

.terminate-toggle input {
  cursor: pointer;
}

.terminate-label {
  color: var(--color-text);
  font-weight: 500;
}

.terminate-message-input {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--color-border);
}

.btn-small {
  padding: 4px 10px;
  font-size: 12px;
}
</style>
