<template>
  <div :class="['wang-editor-container']" @mouseleave="hideImgAiBtn">
    <toolbar :editor="editorRef" style="border-bottom: 1px solid var(--el-border-color)" />
    <editor
      v-model="html"
      class="wang-editor-content"
      :default-config="editorConfig"
      @on-created="handleCreated"
      @on-change="handleChange"
      @on-focus="handleFocus"
      @on-blur="handleBlur"
    />
    <div
      v-if="imgAiBtnVisible"
      class="img-ai-btn"
      :style="imgAiBtnStyle"
      @click.stop="openImgTranslateDialog"
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
    </div>
    <ai-translate-prompt-dialog
      ref="imgDialogRef"
      :language-id="languageId"
      :source="currentImgSource"
      type="image"
      @apply="onImgTranslated"
    />
    <file-chooser ref="fileChooserRef" />
  </div>
</template>

<script lang="ts" setup>
import { DomEditor, type IDomEditor, type IEditorConfig } from '@wangeditor/editor'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import '@wangeditor/editor/dist/css/style.css'
import type { ShallowRef } from 'vue'
import { onBeforeUnmount, reactive, shallowRef } from 'vue'
import { useUserStore } from '/@/store/modules/user'
import { getEnv } from '/@/utils/env'

defineOptions({
  name: 'ProductWangEditor',
})
type InsertFnType = (url: string, alt: string, href: string) => void

const editorRef: ShallowRef<IDomEditor | undefined> = shallowRef<IDomEditor | undefined>()
const html = defineModel<string>()
const props = defineProps({
  isProduct: {
    type: Boolean,
    default: true,
  },
  languageId: {
    type: String,
    default: '',
  },
})
const userStore = useUserStore()
const { token } = userStore
const fileChooserRef = ref<any>(null)

const editorConfig = reactive<Partial<IEditorConfig>>({
  placeholder: props.isProduct ? '请输入商品详情...' : '请输入文章内容',
  MENU_CONF: {
    lineHeight: {
      lineHeightList: ['0.25', '0.5', '1', '1.5', '2', '2.5', '3', '3.5', '4'],
    },
    uploadImage: {
      server: `${getEnv('VITE_API_BASE_URL', window.location.origin)}/multimedia-file/uploadFiles/root`, // 您的服务器地址
      fieldName: 'vab-file-name',
      allowedFileTypes: ['image/*'],
      maxFileSize: 20 * 1024 * 1024, // 20M
      headers: {
        Authorization: `Bearer ${token}`,
      },
      async customInsert(res: any, insertFn: InsertFnType) {
        console.log(res)
        if (res.code === '0') {
          for (const multimedia of res.data.list) {
            await insertFn(multimedia.absolutionPath, 'Product details image', '')
          }
        }
      },
      async customBrowseAndUpload(insertFn: InsertFnType) {
        const images = await fileChooserRef.value.choose()
        if (!images || images.length < 0) {
          console.warn(`choose form sku images error: ${JSON.stringify(images)}`)
          return
        }
        // 最后插入图片
        images.forEach((element: any) => {
          insertFn(element.absolutionPath, 'Product details image', '')
        })
      },
    },
  },
})

const handleCreated = (editor: IDomEditor) => {
  editorRef.value = editor
  const toolbar = DomEditor.getToolbar(editor)

  const curToolbarConfig = toolbar?.getConfig()
  console.log(toolbar) // 当前菜单排序和分组
  console.log(curToolbarConfig) // 当前菜单排序和分组
}

const saveSelectionSafe = () => {
  const editor = editorRef.value as any
  if (!editor) return
  try {
    editor.saveSelection?.()
  } catch {
    // ignore
  }
}

const handleFocus = () => {
  saveSelectionSafe()
}

const handleBlur = () => {
  saveSelectionSafe()
}

const handleChange = () => {
  saveSelectionSafe()
}

const copyToClipboard = async (text: string) => {
  const value = String(text || '')
  if (!value) return
  try {
    await navigator.clipboard.writeText(value)
  } catch {
    // fallback
    const textarea = document.createElement('textarea')
    textarea.value = value
    textarea.style.position = 'fixed'
    textarea.style.left = '-9999px'
    document.body.append(textarea)
    textarea.select()
    document.execCommand('copy')
    textarea.remove()
  }
}

const insertPlaceholder = async (text: string): Promise<boolean> => {
  const value = String(text || '')
  if (!value) return false

  const editor = editorRef.value as any
  if (!editor) {
    html.value = `${html.value || ''}${value}`
    await copyToClipboard(value)
    return true
  }

  try {
    editor.focus?.()
    // 尝试恢复上次光标位置
    editor.restoreSelection?.()
    // 插入文本（优先使用 editor API）
    if (typeof editor.insertText === 'function') {
      editor.insertText(value)
    } else if (typeof editor.dangerouslyInsertHtml === 'function') {
      editor.dangerouslyInsertHtml(value)
    } else {
      html.value = `${html.value || ''}${value}`
    }
    // 插入后保存一下 selection，便于连续插入
    saveSelectionSafe()
  } catch {
    html.value = `${html.value || ''}${value}`
  }

  await copyToClipboard(value)
  return true
}

// ========== 富文本内嵌图片 AI 翻译 ==========
const imgAiBtnVisible = ref(false)
const imgAiBtnStyle = ref<Record<string, string>>({})
const imgDialogRef = ref<any>(null)
const currentImgSource = ref<any>({ id: '', absolutionPath: '' })
const pendingReplaceSrc = ref('')
let currentHoveredImg: HTMLImageElement | null = null
let imgLeaveTimer: ReturnType<typeof setTimeout> | null = null

const buildImgSource = (img: HTMLImageElement) => {
  const src = img.src || ''
  const m = src.match(/\/(\d+)(?:\?[^/]*)?$/)
  return {
    multimediaFileId: m ? m[1] : '',
    imageUrl: src.startsWith('data:') ? '' : src,
    imageDataBase64: src.startsWith('data:') ? (src.includes(',') ? src.split(',')[1] : '') : '',
    absolutionPath: src,
  }
}

const onEditorMouseOver = (e: MouseEvent) => {
  const target = e.target as HTMLElement
  if (target.tagName === 'IMG') {
    if (imgLeaveTimer) {
      clearTimeout(imgLeaveTimer)
      imgLeaveTimer = null
    }
    const img = target as HTMLImageElement
    currentHoveredImg = img
    const containerEl = img.closest('.wang-editor-container')
    if (!containerEl) return
    const containerRect = containerEl.getBoundingClientRect()
    const imgRect = img.getBoundingClientRect()
    imgAiBtnStyle.value = {
      top: `${imgRect.top - containerRect.top + 4}px`,
      left: `${imgRect.left - containerRect.left + 4}px`,
    }
    currentImgSource.value = buildImgSource(img)
    imgAiBtnVisible.value = true
  }
}

const hideImgAiBtn = () => {
  imgLeaveTimer = setTimeout(() => {
    imgAiBtnVisible.value = false
    currentHoveredImg = null
  }, 300)
}

const openImgTranslateDialog = () => {
  if (!props.languageId) return
  pendingReplaceSrc.value = currentHoveredImg?.src || ''
  imgDialogRef.value?.open()
}

const onImgTranslated = (result: any) => {
  if (!result || !result.absolutionPath || !pendingReplaceSrc.value) return
  const editor = editorRef.value as any
  if (!editor) return

  const currentHtml = editor.getHtml?.() || html.value || ''
  const doc = new DOMParser().parseFromString(currentHtml, 'text/html')
  let replaced = false
  doc.querySelectorAll('img').forEach((img) => {
    if (img.getAttribute('src') === pendingReplaceSrc.value) {
      img.setAttribute('src', result.absolutionPath)
      replaced = true
    }
  })
  if (replaced) {
    const newHtml = doc.body.innerHTML
    editor.setHtml?.(newHtml)
    html.value = newHtml
  }
  pendingReplaceSrc.value = ''
  imgAiBtnVisible.value = false
}

const setupEditorMouseListeners = () => {
  nextTick(() => {
    const container = document.querySelector('.wang-editor-content')
    if (container) {
      container.addEventListener('mouseover', onEditorMouseOver as any)
    }
  })
}

watch(editorRef, (editor) => {
  if (editor) setupEditorMouseListeners()
})

defineExpose({
  insertPlaceholder,
})

onBeforeUnmount(() => {
  const editor = editorRef.value
  if (editor) editor.destroy()
  const container = document.querySelector('.wang-editor-content')
  if (container) {
    container.removeEventListener('mouseover', onEditorMouseOver as any)
  }
})
</script>

<style lang="scss">
.img-ai-btn {
  position: absolute;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  color: #fff;
  cursor: pointer;
  background: #409eff;
  border-radius: 50%;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.2);
  transition: transform 0.2s;

  &:hover {
    transform: scale(1.15);
  }
}

.wang-editor-container {
  position: relative;
  width: 100% !important;
  height: 100% !important;
  padding: 0px !important;
  background-color: white;
  border: var(--el-border) !important;

  &.w-e-full-screen-container {
    z-index: 9999 !important;
    .wang-editor-content {
      max-height: none !important;
      overflow: auto !important;
    }
  }
  .wang-editor-content {
    min-height: 300px !important;
    max-height: 500px !important;
    padding: 0px 0 !important;
    margin-top: 10px;
    margin-bottom: 10px;
    overflow: auto !important;
  }

  .w-e-text-placeholder {
    position: absolute !important;
    top: 0px !important;
    left: 50% !important;
    width: 780px !important;
    transform: translateX(-50%) !important;
  }
  .w-e-scroll {
    min-height: 300px !important;
    p {
      width: 780px !important;
      margin: 0 auto !important;
    }
    .w-e-image-container {
      img {
        display: block !important;
        width: 780px !important;
      }
    }
  }
}
//   max-height: 600px;
//   padding: 0 !important;
//   overflow: hidden !important;
//   border: var(--el-border) !important;
//   transition: max-height 0.3s ease-in-out;

//   &.full-screen {
//     max-height: none;
//   }

//   .w-e-bar-divider {
//     display: none;
//   }

//   .w-e-toolbar-init {
//     border-bottom: 1px solid var(--el-border-color) !important;
//   }

//   .wang-editor-content {
//     box-sizing: border-box; // Include padding and border in the element's total width and height
//     width: 100%;
//     min-height: 300px !important;
//     max-height: 600px;
//     padding: 10px; // Ensure padding is consistent
//     overflow: auto !important;
//     background-color: var(--el-color-white);
//     border: 0;

//     &::placeholder {
//       color: #ccc; // Customize placeholder text color
//     }
//   }

//   #w-e-textarea-1 {
//     margin: var(--el-margin) !important;
//   }

//   .wang-editor-footer {
//     width: 100%;
//     margin: auto;
//   }

//   @media (max-width: 768px) {
//     .wang-editor-title,
//     .wang-editor-content,
//     .wang-editor-footer {
//       width: 100%;
//     }
//   }
// }

// .wang-editor-dialog {
//   img {
//     max-width: 100%;
//   }
// }
</style>
