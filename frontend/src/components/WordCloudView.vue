<script setup lang="ts">
import { computed, ref } from 'vue'
import type { WordCloudData } from '../lib/wordCloud'

const props = defineProps<{
  data: WordCloudData
}>()

const activeTab = ref<'words' | 'phrases' | 'heat'>('words')

const wordColors = [
  '#4F46E5', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6',
  '#EC4899', '#06B6D4', '#84CC16', '#F97316', '#6366F1'
]

function getWordSize(weight: number): string {
  const minSize = 12
  const maxSize = 32
  const size = minSize + (weight / 100) * (maxSize - minSize)
  return `${size}px`
}

function getWordColor(index: number): string {
  return wordColors[index % wordColors.length]
}

const topHeatAnswers = computed(() => {
  return props.data.answerHeats.slice(0, 5)
})

const displayWords = computed(() => {
  return props.data.highFrequencyWords.slice(0, 30)
})
</script>

<template>
  <div class="word-cloud-view">
    <div class="cloud-stats">
      <div class="stat-item">
        <span class="stat-num">{{ data.totalAnswers }}</span>
        <span class="stat-label">有效回答</span>
      </div>
      <div class="stat-divider"></div>
      <div class="stat-item">
        <span class="stat-num">{{ data.avgWordCount }}</span>
        <span class="stat-label">平均字数</span>
      </div>
      <div class="stat-divider"></div>
      <div class="stat-item">
        <span class="stat-num">{{ data.highFrequencyWords.length }}</span>
        <span class="stat-label">高频词</span>
      </div>
    </div>

    <div class="tab-nav">
      <button
        :class="['tab-btn', { active: activeTab === 'words' }]"
        @click="activeTab = 'words'"
      >
        高频词云
      </button>
      <button
        :class="['tab-btn', { active: activeTab === 'phrases' }]"
        @click="activeTab = 'phrases'"
      >
        核心短语
      </button>
      <button
        :class="['tab-btn', { active: activeTab === 'heat' }]"
        @click="activeTab = 'heat'"
      >
        回答热度
      </button>
    </div>

    <div class="tab-content">
      <div v-if="activeTab === 'words'" class="words-cloud">
        <div v-if="displayWords.length === 0" class="empty-state">
          暂无足够的数据分析高频词
        </div>
        <div v-else class="word-cloud-container">
          <span
            v-for="(word, index) in displayWords"
            :key="word.word"
            class="cloud-word"
            :style="{
              fontSize: getWordSize(word.weight),
              color: getWordColor(index)
            }"
            :title="`出现 ${word.count} 次`"
          >
            {{ word.word }}
          </span>
        </div>
      </div>

      <div v-else-if="activeTab === 'phrases'" class="phrases-list">
        <div v-if="data.corePhrases.length === 0" class="empty-state">
          暂无足够的数据分析核心短语
        </div>
        <div v-else class="phrase-items">
          <div
            v-for="(phrase, index) in data.corePhrases"
            :key="phrase.phrase"
            class="phrase-item"
          >
            <span class="phrase-rank" :style="{ background: getWordColor(index) }">
              {{ index + 1 }}
            </span>
            <span class="phrase-text">{{ phrase.phrase }}</span>
            <span class="phrase-count">{{ phrase.count }} 次</span>
          </div>
        </div>
      </div>

      <div v-else-if="activeTab === 'heat'" class="heat-list">
        <div v-if="topHeatAnswers.length === 0" class="empty-state">
          暂无回答数据
        </div>
        <div v-else class="heat-items">
          <div
            v-for="(item, index) in topHeatAnswers"
            :key="item.index"
            class="heat-item"
          >
            <div class="heat-header">
              <span class="heat-rank" :style="{ background: getWordColor(index) }">
                {{ index + 1 }}
              </span>
              <span class="heat-score">热度 {{ item.heat }}</span>
              <span class="heat-word-count">{{ item.wordCount }} 字</span>
            </div>
            <div class="heat-bar">
              <div
                class="heat-fill"
                :style="{ width: `${item.heat}%`, background: getWordColor(index) }"
              ></div>
            </div>
            <p class="heat-content">{{ item.content }}</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.word-cloud-view {
  background: var(--color-bg);
  border-radius: var(--radius);
  padding: 20px;
}

.cloud-stats {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
  margin-bottom: 20px;
  padding: 16px;
  background: white;
  border-radius: 8px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.stat-num {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-primary);
}

.stat-label {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.stat-divider {
  width: 1px;
  height: 32px;
  background: var(--color-border);
}

.tab-nav {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--color-border);
  padding-bottom: 8px;
}

.tab-btn {
  padding: 8px 16px;
  border: none;
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 14px;
  cursor: pointer;
  border-radius: 6px 6px 0 0;
  transition: all 0.2s ease;
}

.tab-btn:hover {
  color: var(--color-primary);
  background: rgba(79, 70, 229, 0.05);
}

.tab-btn.active {
  color: var(--color-primary);
  font-weight: 600;
  background: rgba(79, 70, 229, 0.1);
}

.tab-content {
  min-height: 200px;
}

.empty-state {
  text-align: center;
  padding: 40px 20px;
  color: var(--color-text-secondary);
  font-size: 14px;
}

.word-cloud-container {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: center;
  align-items: center;
  padding: 20px;
  background: white;
  border-radius: 8px;
  min-height: 180px;
}

.cloud-word {
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.2s ease, opacity 0.2s ease;
  padding: 4px 8px;
  border-radius: 4px;
}

.cloud-word:hover {
  transform: scale(1.1);
  opacity: 0.8;
  background: rgba(0, 0, 0, 0.05);
}

.phrases-list {
  background: white;
  border-radius: 8px;
  padding: 16px;
}

.phrase-items {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.phrase-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  background: var(--color-bg);
  border-radius: 6px;
  transition: background 0.2s ease;
}

.phrase-item:hover {
  background: rgba(79, 70, 229, 0.05);
}

.phrase-rank {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 12px;
  font-weight: 600;
  border-radius: 50%;
  flex-shrink: 0;
}

.phrase-text {
  flex: 1;
  font-size: 14px;
  color: var(--color-text);
  font-weight: 500;
}

.phrase-count {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.heat-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.heat-item {
  background: white;
  border-radius: 8px;
  padding: 16px;
  border-left: 3px solid var(--color-primary);
}

.heat-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.heat-rank {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 12px;
  font-weight: 600;
  border-radius: 50%;
  flex-shrink: 0;
}

.heat-score {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
}

.heat-word-count {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-left: auto;
}

.heat-bar {
  height: 6px;
  background: var(--color-bg);
  border-radius: 3px;
  overflow: hidden;
  margin-bottom: 10px;
}

.heat-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.5s ease;
}

.heat-content {
  font-size: 14px;
  color: var(--color-text);
  line-height: 1.6;
  margin: 0;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

@media (max-width: 768px) {
  .cloud-stats {
    gap: 12px;
    padding: 12px;
  }

  .stat-num {
    font-size: 20px;
  }

  .tab-nav {
    gap: 4px;
  }

  .tab-btn {
    padding: 6px 12px;
    font-size: 13px;
  }

  .word-cloud-container {
    padding: 16px;
    gap: 8px;
  }
}
</style>
