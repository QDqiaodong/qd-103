<script setup lang="ts">
import type { Question } from '../../types'

const props = defineProps<{
  question: Question
  value: string
}>()

const emit = defineEmits<{
  update: [value: string]
}>()

function handleInput(event: Event) {
  const target = event.target as HTMLTextAreaElement
  emit('update', target.value)
}
</script>

<template>
  <div class="text-question">
    <textarea
      :value="value"
      class="form-input text-input"
      placeholder="请输入您的回答..."
      rows="4"
      @input="handleInput"
    ></textarea>
    <div class="char-count">
      {{ value.length }} 字
    </div>
  </div>
</template>

<style scoped>
.text-question {
  position: relative;
}

.text-input {
  resize: vertical;
  min-height: 120px;
  line-height: 1.6;
}

.char-count {
  position: absolute;
  bottom: 12px;
  right: 12px;
  font-size: 12px;
  color: var(--color-text-secondary);
}
</style>
