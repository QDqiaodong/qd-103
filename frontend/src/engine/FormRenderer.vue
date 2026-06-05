<script setup lang="ts">
import type { Question } from '../types'
import SingleChoice from './questions/SingleChoice.vue'
import MultipleChoice from './questions/MultipleChoice.vue'
import TextQuestion from './questions/TextQuestion.vue'

defineProps<{
  questions: Question[]
  answers: Record<string, string | string[]>
}>()

const emit = defineEmits<{
  answer: [questionId: string, value: string | string[]]
}>()

function handleAnswer(questionId: string, value: string | string[]) {
  emit('answer', questionId, value)
}
</script>

<template>
  <div class="form-renderer">
    <div
      v-for="(question, index) in questions"
      :key="question.id"
      class="question-block"
    >
      <div class="question-label">
        <span class="question-number">{{ index + 1 }}.</span>
        <span v-if="question.required" class="required-mark">*</span>
      </div>

      <div class="question-content">
        <p class="question-text">{{ question.content || '未填写题目内容' }}</p>

        <SingleChoice
          v-if="question.type === 'single'"
          :question="question"
          :value="(answers[question.id] as string) || ''"
          @update="(val) => handleAnswer(question.id, val)"
        />

        <MultipleChoice
          v-else-if="question.type === 'multiple'"
          :question="question"
          :value="(answers[question.id] as string[]) || []"
          @update="(val) => handleAnswer(question.id, val)"
        />

        <TextQuestion
          v-else-if="question.type === 'text'"
          :question="question"
          :value="(answers[question.id] as string) || ''"
          @update="(val) => handleAnswer(question.id, val)"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.form-renderer {
  background: white;
}

.question-block {
  padding: 24px 32px;
  border-bottom: 1px solid var(--color-border);
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.question-label {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 12px;
}

.question-number {
  font-weight: 600;
  font-size: 16px;
  color: var(--color-text);
}

.required-mark {
  color: var(--color-danger);
  font-size: 18px;
}

.question-content {
  padding-left: 20px;
}

.question-text {
  font-size: 16px;
  color: var(--color-text);
  margin-bottom: 16px;
  line-height: 1.6;
}
</style>
