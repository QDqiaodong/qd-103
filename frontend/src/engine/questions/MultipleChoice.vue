<script setup lang="ts">
import type { Question } from '../../types'

const props = defineProps<{
  question: Question
  value: string[]
}>()

const emit = defineEmits<{
  update: [value: string[]]
}>()

function toggleOption(content: string) {
  const newValue = [...props.value]
  const index = newValue.indexOf(content)
  if (index === -1) {
    newValue.push(content)
  } else {
    newValue.splice(index, 1)
  }
  emit('update', newValue)
}
</script>

<template>
  <div class="multiple-choice">
    <label
      v-for="option in question.options"
      :key="option.id"
      :class="['option-item', { selected: value.includes(option.content) }]"
    >
      <input
        type="checkbox"
        :value="option.content"
        :checked="value.includes(option.content)"
        class="option-checkbox"
        @change="toggleOption(option.content)"
      />
      <span class="option-marker">
        <span v-if="value.includes(option.content)" class="check-icon">✓</span>
      </span>
      <span class="option-text">{{ option.content }}</span>
    </label>
  </div>
</template>

<style scoped>
.multiple-choice {
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

.option-checkbox {
  display: none;
}

.option-marker {
  width: 20px;
  height: 20px;
  border: 2px solid var(--color-border);
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.option-item.selected .option-marker {
  background: var(--color-primary);
  border-color: var(--color-primary);
}

.check-icon {
  color: white;
  font-size: 12px;
  font-weight: bold;
}

.option-text {
  font-size: 15px;
  color: var(--color-text);
}
</style>
