<script setup lang="ts">
import { computed } from 'vue'
import type { CoverConfig } from '../types'
import { DEFAULT_COVER_CONFIG } from '../types'

const props = defineProps<{
  coverConfig: CoverConfig
  title: string
  description: string
}>()

const config = computed(() => ({ ...DEFAULT_COVER_CONFIG, ...props.coverConfig }))

const coverStyle = computed(() => {
  const c = config.value
  if (c.backgroundStyle === 'gradient') {
    return {
      background: `linear-gradient(135deg, ${c.gradientStart}, ${c.gradientEnd})`
    }
  }
  return {
    backgroundColor: c.backgroundColor
  }
})

const isDarkBg = computed(() => {
  const c = config.value
  if (c.backgroundStyle === 'gradient') {
    return isColorDark(c.gradientStart) || isColorDark(c.gradientEnd)
  }
  return isColorDark(c.backgroundColor)
})

function isColorDark(hex: string): boolean {
  const c = hex.replace('#', '')
  if (c.length !== 6) return false
  const r = parseInt(c.substring(0, 2), 16)
  const g = parseInt(c.substring(2, 4), 16)
  const b = parseInt(c.substring(4, 6), 16)
  return (r * 299 + g * 587 + b * 114) / 1000 < 128
}
</script>

<template>
  <div class="cover-preview-wrapper">
    <div class="cover-preview-label">实时预览</div>
    <div :class="['cover-preview', config.layout]" :style="coverStyle">
      <div v-if="config.headerImage && config.layout !== 'split'" :class="['cover-header-img', config.layout]">
        <img :src="config.headerImage" alt="封面头图" />
      </div>

      <div :class="['cover-content', config.layout, { 'dark-bg': isDarkBg }]">
        <div
          v-if="config.layout === 'split' && config.headerImage"
          class="split-image-area"
        >
          <img :src="config.headerImage" alt="封面头图" />
        </div>

        <div :class="['cover-text-area', config.layout, `title-${config.titlePosition}`]">
          <h2 :class="['preview-cover-title', { 'light-text': isDarkBg }]" :style="{ color: isDarkBg ? '#FFFFFF' : config.accentColor }">
            {{ title || '问卷标题' }}
          </h2>

          <p
            v-if="config.showDescription && description"
            :class="['preview-cover-desc', { 'light-text': isDarkBg }]"
          >
            {{ description }}
          </p>

          <p
            v-if="config.openingText"
            :class="['preview-cover-opening', { 'light-text': isDarkBg }]"
            :style="{ borderColor: config.accentColor }"
          >
            {{ config.openingText }}
          </p>
        </div>
      </div>

      <div class="cover-accent-bar" :style="{ background: config.accentColor }"></div>
    </div>
  </div>
</template>

<style scoped>
.cover-preview-wrapper {
  position: relative;
}

.cover-preview-label {
  position: absolute;
  top: -10px;
  left: 12px;
  background: var(--color-primary);
  color: white;
  font-size: 11px;
  font-weight: 600;
  padding: 2px 10px;
  border-radius: 10px;
  z-index: 1;
}

.cover-preview {
  border-radius: var(--radius);
  overflow: hidden;
  min-height: 320px;
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-md);
  position: relative;
}

.cover-header-img {
  width: 100%;
  max-height: 140px;
  overflow: hidden;
}

.cover-header-img.hero {
  max-height: 180px;
}

.cover-header-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.cover-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 24px;
}

.cover-content.centered {
  align-items: center;
  justify-content: center;
  text-align: center;
}

.cover-content.left-aligned {
  align-items: flex-start;
  text-align: left;
}

.cover-content.split {
  flex-direction: row;
  gap: 16px;
  align-items: stretch;
}

.cover-content.hero {
  padding-top: 12px;
}

.split-image-area {
  flex: 1;
  min-height: 120px;
  border-radius: 6px;
  overflow: hidden;
}

.split-image-area img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 6px;
}

.cover-text-area {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-width: 100%;
}

.cover-text-area.centered {
  align-items: center;
}

.cover-text-area.left-aligned {
  align-items: flex-start;
}

.cover-text-area.split {
  flex: 1;
  justify-content: center;
}

.cover-text-area.title-top {
  justify-content: flex-start;
}

.cover-text-area.title-middle {
  justify-content: center;
}

.cover-text-area.title-bottom {
  justify-content: flex-end;
}

.preview-cover-title {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.3;
  margin: 0;
  word-break: break-word;
}

.preview-cover-desc {
  font-size: 13px;
  line-height: 1.5;
  margin: 0;
  opacity: 0.75;
  word-break: break-word;
}

.preview-cover-opening {
  font-size: 12px;
  line-height: 1.5;
  margin: 0;
  padding: 10px 14px;
  border-left: 3px solid;
  background: rgba(0, 0, 0, 0.04);
  border-radius: 0 6px 6px 0;
  word-break: break-word;
}

.preview-cover-opening.light-text {
  background: rgba(255, 255, 255, 0.12);
}

.light-text {
  color: rgba(255, 255, 255, 0.9) !important;
}

.cover-accent-bar {
  height: 4px;
  width: 100%;
}
</style>
