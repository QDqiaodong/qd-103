<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import type { CoverConfig, CoverLayout, CoverTheme } from '../types'
import { DEFAULT_COVER_CONFIG, COVER_THEMES, COVER_LAYOUTS } from '../types'
import { Palette, Layout, Type, Image, Eye } from 'lucide-vue-next'

const props = defineProps<{
  modelValue: CoverConfig
}>()

const emit = defineEmits<{
  'update:modelValue': [value: CoverConfig]
}>()

const localConfig = ref<CoverConfig>({ ...DEFAULT_COVER_CONFIG, ...props.modelValue })

watch(() => props.modelValue, (val) => {
  localConfig.value = { ...DEFAULT_COVER_CONFIG, ...val }
}, { deep: true })

watch(localConfig, (val) => {
  emit('update:modelValue', { ...val })
}, { deep: true })

const activeTab = ref<'theme' | 'layout' | 'content' | 'style'>('theme')

const themeKeys = computed(() => Object.keys(COVER_THEMES) as CoverTheme[])
const layoutKeys = computed(() => Object.keys(COVER_LAYOUTS) as CoverLayout[])

const presetImages = [
  { url: 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=modern+office+building+blue+sky+professional+business+survey&image_size=landscape_16_9', label: '商务办公' },
  { url: 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=university+campus+green+lawn+students+walking+spring&image_size=landscape_16_9', label: '校园风光' },
  { url: 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=colorful+festival+celebration+confetti+party+voting&image_size=landscape_16_9', label: '活动投票' },
  { url: 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=corporate+meeting+room+glass+building+interior+minimal&image_size=landscape_16_9', label: '企业场景' },
  { url: 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=abstract+geometric+shapes+gradient+clean+minimal+design&image_size=landscape_16_9', label: '抽象几何' },
  { url: 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=nature+mountains+lake+reflection+peaceful+serene+landscape&image_size=landscape_16_9', label: '自然风光' }
]

function selectTheme(theme: CoverTheme) {
  const themeConfig = COVER_THEMES[theme]
  localConfig.value = {
    ...localConfig.value,
    theme,
    accentColor: themeConfig.accentColor,
    backgroundStyle: themeConfig.backgroundStyle,
    gradientStart: themeConfig.gradientStart,
    gradientEnd: themeConfig.gradientEnd,
    backgroundColor: themeConfig.backgroundColor,
    layout: themeConfig.layout
  }
}

function selectLayout(layout: CoverLayout) {
  localConfig.value = { ...localConfig.value, layout }
}

function selectPresetImage(url: string) {
  localConfig.value = {
    ...localConfig.value,
    headerImage: localConfig.value.headerImage === url ? '' : url
  }
}

function handleImageUpload(event: Event) {
  const input = event.target as HTMLInputElement
  if (!input.files?.length) return
  const file = input.files[0]
  const reader = new FileReader()
  reader.onload = (e) => {
    localConfig.value = { ...localConfig.value, headerImage: e.target?.result as string }
  }
  reader.readAsDataURL(file)
}
</script>

<template>
  <div class="cover-editor">
    <div class="editor-tabs">
      <button
        :class="['tab-btn', { active: activeTab === 'theme' }]"
        @click="activeTab = 'theme'"
      >
        <Palette :size="16" />
        <span>主题</span>
      </button>
      <button
        :class="['tab-btn', { active: activeTab === 'layout' }]"
        @click="activeTab = 'layout'"
      >
        <Layout :size="16" />
        <span>布局</span>
      </button>
      <button
        :class="['tab-btn', { active: activeTab === 'content' }]"
        @click="activeTab = 'content'"
      >
        <Type :size="16" />
        <span>内容</span>
      </button>
      <button
        :class="['tab-btn', { active: activeTab === 'style' }]"
        @click="activeTab = 'style'"
      >
        <Image :size="16" />
        <span>样式</span>
      </button>
    </div>

    <div class="editor-body">
      <div v-if="activeTab === 'theme'" class="tab-content">
        <p class="section-hint">选择预设主题，快速区分问卷类型</p>
        <div class="theme-grid">
          <button
            v-for="key in themeKeys"
            :key="key"
            :class="['theme-card', { active: localConfig.theme === key }]"
            @click="selectTheme(key)"
          >
            <div
              class="theme-preview"
              :style="{
                background: COVER_THEMES[key].backgroundStyle === 'gradient'
                  ? `linear-gradient(135deg, ${COVER_THEMES[key].gradientStart}, ${COVER_THEMES[key].gradientEnd})`
                  : COVER_THEMES[key].backgroundColor
              }"
            >
              <div class="theme-preview-bar" :style="{ background: COVER_THEMES[key].accentColor }"></div>
              <div class="theme-preview-lines">
                <div class="preview-line long" :style="{ background: COVER_THEMES[key].accentColor + '40' }"></div>
                <div class="preview-line short" :style="{ background: COVER_THEMES[key].accentColor + '25' }"></div>
              </div>
            </div>
            <span class="theme-label">{{ COVER_THEMES[key].label }}</span>
          </button>
        </div>
      </div>

      <div v-if="activeTab === 'layout'" class="tab-content">
        <p class="section-hint">选择封面布局方式，控制首屏信息层次</p>
        <div class="layout-grid">
          <button
            v-for="key in layoutKeys"
            :key="key"
            :class="['layout-card', { active: localConfig.layout === key }]"
            @click="selectLayout(key)"
          >
            <div class="layout-icon">
              <div v-if="key === 'centered'" class="layout-visual centered-visual">
                <div class="lv-bar center"></div>
                <div class="lv-line center short"></div>
                <div class="lv-line center shorter"></div>
              </div>
              <div v-else-if="key === 'left-aligned'" class="layout-visual left-visual">
                <div class="lv-bar left"></div>
                <div class="lv-line left short"></div>
                <div class="lv-line left shorter"></div>
              </div>
              <div v-else-if="key === 'split'" class="layout-visual split-visual">
                <div class="lv-left">
                  <div class="lv-bar left"></div>
                  <div class="lv-line left short"></div>
                </div>
                <div class="lv-right">
                  <div class="lv-placeholder"></div>
                </div>
              </div>
              <div v-else class="layout-visual hero-visual">
                <div class="lv-hero-placeholder"></div>
                <div class="lv-bar center"></div>
                <div class="lv-line center short"></div>
              </div>
            </div>
            <div class="layout-info">
              <span class="layout-name">{{ COVER_LAYOUTS[key].label }}</span>
              <span class="layout-desc">{{ COVER_LAYOUTS[key].description }}</span>
            </div>
          </button>
        </div>

        <div class="form-group" style="margin-top: 20px;">
          <label class="form-label">标题位置</label>
          <div class="position-options">
            <button
              :class="['pos-btn', { active: localConfig.titlePosition === 'top' }]"
              @click="localConfig.titlePosition = 'top'"
            >顶部</button>
            <button
              :class="['pos-btn', { active: localConfig.titlePosition === 'middle' }]"
              @click="localConfig.titlePosition = 'middle'"
            >居中</button>
            <button
              :class="['pos-btn', { active: localConfig.titlePosition === 'bottom' }]"
              @click="localConfig.titlePosition = 'bottom'"
            >底部</button>
          </div>
        </div>
      </div>

      <div v-if="activeTab === 'content'" class="tab-content">
        <div class="form-group">
          <label class="form-label">开场说明</label>
          <textarea
            v-model="localConfig.openingText"
            class="form-input form-textarea"
            placeholder="填写者打开问卷后首先看到的引导文字，如'感谢您参与本次调研，预计需要3分钟'..."
            rows="4"
          ></textarea>
        </div>

        <div class="form-group">
          <label class="form-label">
            <input
              type="checkbox"
              v-model="localConfig.showDescription"
            />
            显示问卷描述
          </label>
        </div>

        <div class="form-group">
          <label class="form-label">头图设置</label>
          <div class="image-presets">
            <button
              v-for="img in presetImages"
              :key="img.label"
              :class="['img-preset-btn', { active: localConfig.headerImage === img.url }]"
              @click="selectPresetImage(img.url)"
            >
              <img :src="img.url" :alt="img.label" class="img-preset-thumb" />
              <span class="img-preset-label">{{ img.label }}</span>
            </button>
          </div>
          <div class="upload-area">
            <label class="upload-btn">
              上传自定义头图
              <input type="file" accept="image/*" class="hidden-input" @change="handleImageUpload" />
            </label>
            <button
              v-if="localConfig.headerImage"
              class="btn btn-outline remove-img-btn"
              @click="localConfig.headerImage = ''"
            >
              移除头图
            </button>
          </div>
        </div>
      </div>

      <div v-if="activeTab === 'style'" class="tab-content">
        <div class="form-group">
          <label class="form-label">强调色</label>
          <div class="color-picker-row">
            <input type="color" v-model="localConfig.accentColor" class="color-input" />
            <input type="text" v-model="localConfig.accentColor" class="form-input color-text" />
          </div>
        </div>

        <div class="form-group">
          <label class="form-label">背景风格</label>
          <div class="bg-style-options">
            <button
              :class="['bg-style-btn', { active: localConfig.backgroundStyle === 'solid' }]"
              @click="localConfig.backgroundStyle = 'solid'"
            >纯色</button>
            <button
              :class="['bg-style-btn', { active: localConfig.backgroundStyle === 'gradient' }]"
              @click="localConfig.backgroundStyle = 'gradient'"
            >渐变</button>
          </div>
        </div>

        <div v-if="localConfig.backgroundStyle === 'solid'" class="form-group">
          <label class="form-label">背景颜色</label>
          <div class="color-picker-row">
            <input type="color" v-model="localConfig.backgroundColor" class="color-input" />
            <input type="text" v-model="localConfig.backgroundColor" class="form-input color-text" />
          </div>
        </div>

        <div v-if="localConfig.backgroundStyle === 'gradient'" class="form-group">
          <label class="form-label">渐变起始色</label>
          <div class="color-picker-row">
            <input type="color" v-model="localConfig.gradientStart" class="color-input" />
            <input type="text" v-model="localConfig.gradientStart" class="form-input color-text" />
          </div>
        </div>

        <div v-if="localConfig.backgroundStyle === 'gradient'" class="form-group">
          <label class="form-label">渐变结束色</label>
          <div class="color-picker-row">
            <input type="color" v-model="localConfig.gradientEnd" class="color-input" />
            <input type="text" v-model="localConfig.gradientEnd" class="form-input color-text" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.cover-editor {
  background: white;
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  overflow: hidden;
}

.editor-tabs {
  display: flex;
  border-bottom: 1px solid var(--color-border);
  background: #F8FAFC;
}

.tab-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 12px 8px;
  border: none;
  background: transparent;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: all 0.2s;
  border-bottom: 2px solid transparent;
}

.tab-btn:hover {
  color: var(--color-text);
  background: #F1F5F9;
}

.tab-btn.active {
  color: var(--color-primary);
  border-bottom-color: var(--color-primary);
  background: white;
}

.editor-body {
  padding: 20px;
}

.section-hint {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: 16px;
}

.theme-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.theme-card {
  border: 2px solid var(--color-border);
  border-radius: var(--radius);
  padding: 8px;
  cursor: pointer;
  transition: all 0.2s;
  background: white;
  text-align: center;
}

.theme-card:hover {
  border-color: var(--color-primary);
  box-shadow: var(--shadow-md);
}

.theme-card.active {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 1px var(--color-primary);
}

.theme-preview {
  height: 60px;
  border-radius: 4px;
  margin-bottom: 6px;
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.theme-preview-bar {
  width: 40%;
  height: 6px;
  border-radius: 3px;
}

.theme-preview-lines {
  display: flex;
  flex-direction: column;
  gap: 3px;
  align-items: center;
}

.preview-line {
  height: 3px;
  border-radius: 2px;
}

.preview-line.long {
  width: 50%;
}

.preview-line.short {
  width: 30%;
}

.theme-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text);
}

.layout-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.layout-card {
  border: 2px solid var(--color-border);
  border-radius: var(--radius);
  padding: 12px;
  cursor: pointer;
  transition: all 0.2s;
  background: white;
  text-align: left;
}

.layout-card:hover {
  border-color: var(--color-primary);
}

.layout-card.active {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 1px var(--color-primary);
}

.layout-icon {
  margin-bottom: 8px;
}

.layout-visual {
  height: 50px;
  background: var(--color-bg);
  border-radius: 4px;
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 8px;
}

.split-visual {
  flex-direction: row;
  gap: 8px;
}

.split-visual .lv-left {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
}

.split-visual .lv-right {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.hero-visual {
  gap: 3px;
}

.hero-visual .lv-hero-placeholder {
  width: 80%;
  height: 16px;
  background: var(--color-border);
  border-radius: 2px;
}

.lv-bar {
  height: 6px;
  width: 50%;
  background: var(--color-primary);
  border-radius: 3px;
}

.lv-bar.center {
  align-self: center;
}

.lv-bar.left {
  align-self: flex-start;
}

.lv-line {
  height: 3px;
  width: 30%;
  background: var(--color-border);
  border-radius: 2px;
}

.lv-line.center {
  align-self: center;
}

.lv-line.left {
  align-self: flex-start;
}

.lv-line.short {
  width: 20%;
}

.lv-line.shorter {
  width: 12%;
}

.lv-placeholder {
  width: 100%;
  height: 30px;
  background: var(--color-border);
  border-radius: 2px;
}

.layout-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.layout-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
}

.layout-desc {
  font-size: 11px;
  color: var(--color-text-secondary);
}

.position-options {
  display: flex;
  gap: 8px;
}

.pos-btn {
  flex: 1;
  padding: 8px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: white;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.pos-btn:hover {
  border-color: var(--color-primary);
}

.pos-btn.active {
  background: var(--color-primary);
  color: white;
  border-color: var(--color-primary);
}

.image-presets {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-bottom: 12px;
}

.img-preset-btn {
  border: 2px solid var(--color-border);
  border-radius: 6px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.2s;
  background: white;
  padding: 0;
}

.img-preset-btn:hover {
  border-color: var(--color-primary);
}

.img-preset-btn.active {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 1px var(--color-primary);
}

.img-preset-thumb {
  width: 100%;
  height: 50px;
  object-fit: cover;
  display: block;
}

.img-preset-label {
  display: block;
  font-size: 11px;
  padding: 4px;
  text-align: center;
  color: var(--color-text-secondary);
}

.upload-area {
  display: flex;
  gap: 8px;
  align-items: center;
}

.upload-btn {
  display: inline-flex;
  align-items: center;
  padding: 8px 16px;
  border: 1px dashed var(--color-border);
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  color: var(--color-text-secondary);
  transition: all 0.2s;
}

.upload-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.hidden-input {
  display: none;
}

.remove-img-btn {
  padding: 8px 12px;
  font-size: 13px;
}

.bg-style-options {
  display: flex;
  gap: 8px;
}

.bg-style-btn {
  flex: 1;
  padding: 8px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: white;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.bg-style-btn:hover {
  border-color: var(--color-primary);
}

.bg-style-btn.active {
  background: var(--color-primary);
  color: white;
  border-color: var(--color-primary);
}

.color-picker-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.color-input {
  width: 40px;
  height: 36px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  cursor: pointer;
  padding: 2px;
}

.color-text {
  flex: 1;
}

@media (max-width: 768px) {
  .theme-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .layout-grid {
    grid-template-columns: 1fr;
  }

  .image-presets {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
