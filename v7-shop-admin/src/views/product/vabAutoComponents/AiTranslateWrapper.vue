<template>
  <div
    class="ai-translate-wrapper"
    @mouseenter="hovered = true"
    @mouseleave="hovered = false"
  >
    <slot />
    <transition name="fade">
      <el-tooltip
        v-if="hovered"
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
            fill="none"
            height="14"
            stroke="currentColor"
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            viewBox="0 0 24 24"
            width="14"
          >
            <path d="M12 2L2 7l10 5 10-5-10-5z" />
            <path d="M2 17l10 5 10-5" />
            <path d="M2 12l10 5 10-5" />
          </svg>
        </button>
      </el-tooltip>
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
  source: string | { id?: string; multimediaFileId?: string; absolutionPath?: string }
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
  background: var(--el-color-primary);
  border: none;
  border-radius: 50%;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.15);
  opacity: 0.85;
  transition: opacity 0.2s, transform 0.2s;

  &:hover:not(:disabled) {
    opacity: 1;
    transform: scale(1.15);
  }

  &:disabled {
    cursor: not-allowed;
    background: var(--el-color-info-light-5);
    opacity: 0.5;
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
