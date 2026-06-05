<script setup lang="ts">
import type { Question } from '../../types'

const props = defineProps<{
  question: Question
  value: string
}>()

const emit = defineEmits<{
  update: [value: string]
}>()

function selectOption(content: string) {
  emit('update', content)
}
</script>

<template>
  <div class="single-choice">
    <label
      v-for="option in question.options"
      :key="option.id"
      :class="['option-item', { selected: value === option.content }]"
    >
      <input
        type="radio"
        :name="question.id"
        :value="option.content"
        :checked="value === option.content"
        class="option-radio"
        @change="selectOption(option.content)"
      />
      <span class="option-marker">
        <span class="marker-dot"></span>
      </span>
      <span class="option-text">{{ option.content }}</span>
    </label>
  </div>
</template>

<style scoped>
.single-choice {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.option-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: var(--color-bg);
  border: 2px solid transparent;
  border-radius: var(--radius);
  cursor: pointer;
  transition: all 0.2s;
}

.option-item:hover {
  background: #EEF2FF;
}

.option-item.selected {
  background: #EEF2FF;
  border-color: var(--color-primary);
}

.option-radio {
  display: none;
}

.option-marker {
  width: 20px;
  height: 20px;
  border: 2px solid var(--color-border);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.option-item.selected .option-marker {
  border-color: var(--color-primary);
}

.marker-dot {
  width: 10px;
  height: 10px;
  background: var(--color-primary);
  border-radius: 50%;
  transform: scale(0);
  transition: transform 0.2s;
}

.option-item.selected .marker-dot {
  transform: scale(1);
}

.option-text {
  font-size: 15px;
  color: var(--color-text);
}
</style>
