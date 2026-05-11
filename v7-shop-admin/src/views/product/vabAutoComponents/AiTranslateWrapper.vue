<template>
  <div
    class="ai-translate-wrapper"
    @mouseenter="hovered = true"
    @mouseleave="hovered = false"
  >
    <slot />
    <transition name="fade">
      <div v-if="hovered" class="ai-translate-btn-wrap">
        <el-tooltip
          :content="!languageId ? '请先选择目标语言' : 'AI 翻译'"
          placement="top"
        >
          <button
            class="ai-translate-btn"
            :disabled="!languageId"
            type="button"
            @click.stop="openDialog"
          >
            <svg
              fill="#fff"
              height="16"
              style="display: block; fill: #fff"
              viewBox="0 0 24 24"
              width="16"
            >
              <path
                d="M10 4 L11.8 11.2 L19 13 L11.8 14.8 L10 22 L8.2 14.8 L1 13 L8.2 11.2 Z"
                style="fill: #fff"
              />
              <path
                d="M18 2 L18.7 5.3 L22 6 L18.7 6.7 L18 10 L17.3 6.7 L14 6 L17.3 5.3 Z"
                style="fill: #fff"
              />
            </svg>
          </button>
        </el-tooltip>
      </div>
    </transition>
    <ai-translate-prompt-dialog
      ref="dialogRef"
      :language-id="languageId"
      :source="source"
      :type="type"
      @apply="onApply"
    />
  </div>
</template>

<script lang="ts" setup>
defineOptions({ name: 'AiTranslateWrapper' })

const props = defineProps<{
  type: 'text' | 'html' | 'image'
  source: string | { id?: string; multimediaFileId?: string; absolutionPath?: string; imageUrl?: string; imageDataBase64?: string }
  languageId: string
}>()

const emit = defineEmits<{
  (e: 'apply', value: any): void
}>()

const hovered = ref(false)
const dialogRef = ref<any>(null)

const openDialog = () => {
  if (!props.languageId) return
  dialogRef.value?.open()
}

const onApply = (value: any) => {
  emit('apply', value)
}
</script>

<style lang="scss" scoped>
.ai-translate-wrapper {
  position: relative;
  display: inline-block;
  width: 100%;
}

.ai-translate-btn {
  position: absolute;
  top: -2px;
  left: -2px;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  color: #fff;
  cursor: pointer;
  background: #409eff;
  border: none;
  border-radius: 50%;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.15);
  transition: transform 0.2s;

  &:hover:not(:disabled) {
    transform: scale(1.15);
  }

  &:disabled {
    cursor: not-allowed;
    background: #c0c4cc;
  }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
