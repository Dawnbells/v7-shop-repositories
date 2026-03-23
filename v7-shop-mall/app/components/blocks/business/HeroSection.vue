<script setup lang="ts">
/**
 * HeroSection Block - H5 风格首页海报组件
 * 支持全屏背景图、多层文字、按钮和多种预设样式
 */

interface Props {
  // 预设样式
  preset?: 'classic' | 'left' | 'bottom' | 'minimal' | 'dark'

  // 背景
  backgroundImage?: string
  backgroundColor?: string
  backgroundPosition?: string

  // 文字内容
  eyebrow?: string
  title?: string
  subtitle?: string

  // 按钮
  buttonText?: string
  buttonLinkType?: 'home' | 'landing' | 'custom'
  buttonLink?: string
  buttonStyle?: 'solid' | 'outline' | 'text'

  // 布局
  contentAlign?: 'left' | 'center' | 'right'
  contentPosition?: 'top' | 'center' | 'bottom'

  // 遮罩
  overlayType?: 'none' | 'solid' | 'gradient'
  overlayColor?: string
  overlayOpacity?: number
}

const props = withDefaults(defineProps<Props>(), {
  preset: 'classic',
  backgroundPosition: 'center',
  buttonLinkType: 'home',
  buttonStyle: 'solid',
  contentAlign: 'center',
  contentPosition: 'center',
  overlayType: 'gradient',
  overlayColor: '#000000',
  overlayOpacity: 30,
})

const { t } = useI18n()

// 检查是否在编辑器中
const isInEditor = inject<Ref<boolean>>('isInEditor', ref(false))

// 获取 cookie 用于落地页跳转
const fromUrlCookie = useCookie('_from_url')
const spuIdCookie = useCookie<string>('_spuId')

// 预设样式配置
const presetConfigs = {
  classic: {
    contentAlign: 'center',
    contentPosition: 'center',
    overlayType: 'gradient',
    overlayOpacity: 30,
  },
  left: {
    contentAlign: 'left',
    contentPosition: 'center',
    overlayType: 'gradient',
    overlayOpacity: 40,
  },
  bottom: {
    contentAlign: 'center',
    contentPosition: 'bottom',
    overlayType: 'gradient',
    overlayOpacity: 50,
  },
  minimal: {
    contentAlign: 'center',
    contentPosition: 'center',
    overlayType: 'none',
    overlayOpacity: 0,
  },
  dark: {
    contentAlign: 'center',
    contentPosition: 'center',
    overlayType: 'solid',
    overlayOpacity: 60,
  },
}

// 预设样式直接应用
// 切换预设时，相关属性会自动跟随预设变化
const effectiveConfig = computed(() => {
  const presetConfig = presetConfigs[props.preset] || presetConfigs.classic
  return {
    contentAlign: presetConfig.contentAlign as 'left' | 'center' | 'right',
    contentPosition: presetConfig.contentPosition as 'top' | 'center' | 'bottom',
    overlayType: presetConfig.overlayType as 'none' | 'solid' | 'gradient',
    overlayOpacity: presetConfig.overlayOpacity,
  }
})

// 背景样式
const backgroundStyle = computed(() => {
  const style: Record<string, string> = {}

  if (props.backgroundImage) {
    const imageUrl = buildImageUrl(props.backgroundImage)
    style.backgroundImage = `url(${imageUrl})`
    style.backgroundSize = 'cover'
    style.backgroundPosition = props.backgroundPosition || 'center'
    style.backgroundRepeat = 'no-repeat'
  } else if (props.backgroundColor) {
    style.backgroundColor = props.backgroundColor
  }

  return style
})

// 遮罩样式
const overlayStyle = computed(() => {
  const config = effectiveConfig.value
  if (config.overlayType === 'none') return {}

  const opacity = config.overlayOpacity / 100
  const color = props.overlayColor || '#000000'

  if (config.overlayType === 'gradient') {
    return {
      background: `linear-gradient(180deg, rgba(0,0,0,0) 0%, rgba(0,0,0,${opacity * 0.3}) 50%, rgba(0,0,0,${opacity}) 100%)`,
    }
  }

  // solid
  return {
    backgroundColor: color,
    opacity: opacity,
  }
})

// 内容容器类名
const contentClasses = computed(() => {
  const config = effectiveConfig.value
  return [
    'hero-content',
    `align-${config.contentAlign}`,
    `position-${config.contentPosition}`,
  ]
})

// 按钮类名
const buttonClasses = computed(() => {
  return ['hero-button', `button-${props.buttonStyle}`]
})

// 是否显示内容
const hasContent = computed(() => {
  return props.eyebrow || props.title || props.subtitle || props.buttonText
})

// 处理按钮点击（使用完整页面跳转，确保 SSR 渲染）
function handleButtonClick() {
  if (isInEditor.value) return

  switch (props.buttonLinkType) {
    case 'home':
      window.location.href = '/'
      break
    case 'landing': {
      // 落地页模式：优先跳转 from_url，否则跳转商品详情页
      const targetUrl = fromUrlCookie.value || (spuIdCookie.value ? `/product/${spuIdCookie.value}` : '/')
      window.location.href = targetUrl
      break
    }
    case 'custom':
      if (!props.buttonLink) return
      if (props.buttonLink.startsWith('http://') || props.buttonLink.startsWith('https://')) {
        window.open(props.buttonLink, '_blank')
      } else {
        window.location.href = props.buttonLink
      }
      break
  }
}

// 处理图片加载失败
function handleBackgroundError() {
  // 可以在这里处理背景图加载失败的情况
}
</script>

<template>
  <div class="block-hero-section" :style="backgroundStyle">
    <!-- 遮罩层 -->
    <div
      v-if="effectiveConfig.overlayType !== 'none'"
      class="hero-overlay"
      :style="overlayStyle"
    />

    <!-- 内容区域 -->
    <div :class="contentClasses">
      <div class="hero-inner">
        <!-- 小标题/眉题 -->
        <p v-if="eyebrow" class="hero-eyebrow">{{ eyebrow }}</p>

        <!-- 主标题 -->
        <h1 v-if="title" class="hero-title">{{ title }}</h1>

        <!-- 副标题 -->
        <p v-if="subtitle" class="hero-subtitle">{{ subtitle }}</p>

        <!-- 按钮 -->
        <button
          v-if="buttonText"
          :class="buttonClasses"
          @click="handleButtonClick"
        >
          {{ buttonText }}
        </button>
      </div>
    </div>

    <!-- 空状态占位 -->
    <div v-if="!backgroundImage && !hasContent" class="hero-placeholder">
      <i class="i-carbon-image" />
      <span>{{ t('common.addHeroImage', '添加海报图片') }}</span>
    </div>
  </div>
</template>

<style scoped>
.block-hero-section {
  position: relative;
  width: 100%;
  min-height: var(--hero-min-height, 400px);
  height: var(--hero-height, 500px);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background-color: var(--hero-bg-color, #1a1a1a);
}

/* 遮罩层 */
.hero-overlay {
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
}

/* 内容区域 */
.hero-content {
  position: relative;
  z-index: 2;
  width: 100%;
  max-width: var(--hero-content-max-width, 1200px);
  padding: var(--hero-content-padding, 40px 24px);
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
}

.hero-inner {
  display: flex;
  flex-direction: column;
  gap: var(--hero-content-gap, 16px);
}

/* 水平对齐 */
.hero-content.align-left {
  align-items: flex-start;
}

.hero-content.align-left .hero-inner {
  align-items: flex-start;
  text-align: left;
}

.hero-content.align-center {
  align-items: center;
}

.hero-content.align-center .hero-inner {
  align-items: center;
  text-align: center;
}

.hero-content.align-right {
  align-items: flex-end;
}

.hero-content.align-right .hero-inner {
  align-items: flex-end;
  text-align: right;
}

/* 垂直位置 */
.block-hero-section:has(.hero-content.position-top) {
  align-items: flex-start;
}

.block-hero-section:has(.hero-content.position-center) {
  align-items: center;
}

.block-hero-section:has(.hero-content.position-bottom) {
  align-items: flex-end;
}

.hero-content.position-top {
  padding-top: var(--hero-padding-top, 60px);
}

.hero-content.position-bottom {
  padding-bottom: var(--hero-padding-bottom, 60px);
}

/* 小标题/眉题 */
.hero-eyebrow {
  margin: 0;
  font-size: var(--hero-eyebrow-size, 14px);
  font-weight: var(--hero-eyebrow-weight, 400);
  color: var(--hero-eyebrow-color, rgba(255, 255, 255, 0.85));
  letter-spacing: var(--hero-eyebrow-spacing, 0.5px);
  text-transform: var(--hero-eyebrow-transform, none);
}

/* 主标题 */
.hero-title {
  margin: 0;
  font-size: var(--hero-title-size, 42px);
  font-weight: var(--hero-title-weight, 600);
  color: var(--hero-title-color, #ffffff);
  line-height: var(--hero-title-line-height, 1.2);
  letter-spacing: var(--hero-title-spacing, 2px);
  text-transform: var(--hero-title-transform, uppercase);
}

/* 副标题 */
.hero-subtitle {
  margin: 0;
  font-size: var(--hero-subtitle-size, 16px);
  font-weight: var(--hero-subtitle-weight, 400);
  color: var(--hero-subtitle-color, rgba(255, 255, 255, 0.8));
  line-height: var(--hero-subtitle-line-height, 1.6);
  max-width: var(--hero-subtitle-max-width, 600px);
}

/* 按钮基础样式 */
.hero-button {
  margin-top: var(--hero-button-margin-top, 8px);
  padding: var(--hero-button-padding, 12px 32px);
  font-size: var(--hero-button-size, 14px);
  font-weight: var(--hero-button-weight, 500);
  letter-spacing: var(--hero-button-spacing, 0.5px);
  border-radius: var(--hero-button-radius, 4px);
  cursor: pointer;
  transition: all 0.2s ease;
  text-transform: var(--hero-button-transform, none);
}

/* 实心按钮 */
.hero-button.button-solid {
  background-color: var(--hero-button-bg, rgba(0, 0, 0, 0.7));
  color: var(--hero-button-color, #ffffff);
  border: 1px solid var(--hero-button-border-color, rgba(255, 255, 255, 0.3));
}

.hero-button.button-solid:hover {
  background-color: var(--hero-button-hover-bg, rgba(0, 0, 0, 0.9));
  border-color: var(--hero-button-hover-border, rgba(255, 255, 255, 0.5));
}

/* 描边按钮 */
.hero-button.button-outline {
  background-color: transparent;
  color: var(--hero-button-color, #ffffff);
  border: 2px solid var(--hero-button-border-color, #ffffff);
}

.hero-button.button-outline:hover {
  background-color: var(--hero-button-hover-bg, rgba(255, 255, 255, 0.1));
}

/* 文字按钮 */
.hero-button.button-text {
  background-color: transparent;
  color: var(--hero-button-color, #ffffff);
  border: none;
  text-decoration: underline;
  text-underline-offset: 4px;
}

.hero-button.button-text:hover {
  opacity: 0.8;
}

/* 空状态占位 */
.hero-placeholder {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: rgba(255, 255, 255, 0.5);
  font-size: 14px;
}

.hero-placeholder i {
  font-size: 48px;
}

/* 响应式 - 移动端 */
@container (max-width: 640px) {
  .block-hero-section {
    min-height: var(--hero-min-height-mobile, 300px);
    height: var(--hero-height-mobile, 400px);
  }

  .hero-content {
    padding: var(--hero-content-padding-mobile, 32px 16px);
  }

  .hero-eyebrow {
    font-size: var(--hero-eyebrow-size-mobile, 12px);
  }

  .hero-title {
    font-size: var(--hero-title-size-mobile, 28px);
    letter-spacing: var(--hero-title-spacing-mobile, 1px);
  }

  .hero-subtitle {
    font-size: var(--hero-subtitle-size-mobile, 14px);
  }

  .hero-button {
    padding: var(--hero-button-padding-mobile, 10px 24px);
    font-size: var(--hero-button-size-mobile, 13px);
  }

  .hero-content.position-top {
    padding-top: var(--hero-padding-top-mobile, 40px);
  }

  .hero-content.position-bottom {
    padding-bottom: var(--hero-padding-bottom-mobile, 40px);
  }
}
</style>
