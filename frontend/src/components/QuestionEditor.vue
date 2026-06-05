<script setup lang="ts">
import { ref, watch } from 'vue'
import type { Question, QuestionOption } from '../types'
import { ChevronUp, ChevronDown, Trash2, GripVertical } from 'lucide-vue-next'

const props = defineProps<{
  question: Question
  index: number
  total: number
}>()

const emit = defineEmits<{
  update: [data: Partial<Question>]
  delete: []
  move: [direction: 'up' | 'down']
}>()

const localContent = ref(props.question.content)
const localOptions = ref<QuestionOption[]>(props.question.options || [])

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
    orderIndex: localOptions.value.length
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
  justify-content: flex-end;
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
</style>
