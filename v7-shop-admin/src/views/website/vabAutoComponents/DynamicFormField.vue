<template>
  <el-form-item
    :label="fieldSchema.label"
    :prop="fieldKey"
    :required="fieldSchema.required && fieldSchema.type !== 'color' && fieldSchema.type !== 'html'"
    :rules="fieldRules"
  >
    <template #label>
      <span
        v-if="fieldSchema.required && (fieldSchema.type === 'color' || fieldSchema.type === 'html')"
        style="color: red"
      >
        *
      </span>
      <span>{{ fieldSchema.label }}</span>
      <el-tooltip v-if="fieldSchema.description" :content="fieldSchema.description" placement="top">
        <el-icon style="margin-left: 4px; cursor: help">
          <question-filled />
        </el-icon>
      </el-tooltip>
    </template>

    <!-- String类型 -->
    <el-input
      v-if="fieldSchema.type === 'string' && !fieldSchema.enum"
      v-model="localValue"
      clearable
      :placeholder="`请输入${fieldSchema.label}`"
    />

    <!-- String类型带enum（下拉选择） -->
    <el-select
      v-else-if="fieldSchema.type === 'string' && fieldSchema.enum"
      v-model="localValue"
      clearable
      :placeholder="`请选择${fieldSchema.label}`"
      style="width: 100%"
    >
      <el-option v-for="option in fieldSchema.enum" :key="option" :label="option" :value="option" />
    </el-select>

    <!-- HTML类型（富文本） -->
    <div v-else-if="fieldSchema.type === 'html'" class="html-field">
      <product-wang-editor ref="htmlEditorRef" v-model="localValue" :is-product="false" />
      <div
        v-if="fieldSchema.placeholders && Object.keys(fieldSchema.placeholders).length > 0"
        class="placeholder-box"
      >
        <div class="placeholder-title">占位符（点击插入并复制）</div>
        <div class="placeholder-list">
          <el-tooltip
            v-for="(ph, phKey) in fieldSchema.placeholders"
            :key="phKey"
            :content="ph.description || ph.label || ''"
            placement="top"
          >
            <el-tag
              class="placeholder-tag"
              @mousedown.prevent="noop"
              @click="insertPlaceholder(ph.placeholder || String(phKey))"
            >
              {{ ph.label || phKey }}：{{ ph.placeholder || phKey }}
            </el-tag>
          </el-tooltip>
        </div>
      </div>
    </div>

    <!-- Boolean类型 -->
    <el-switch v-else-if="fieldSchema.type === 'boolean'" v-model="localValue" />

    <!-- Number类型 -->
    <el-input-number
      v-else-if="fieldSchema.type === 'number'"
      v-model="localValue"
      class="number-input"
      controls-position="right"
      style="width: 100%"
    />

    <!-- Image类型 -->
    <div v-else-if="fieldSchema.type === 'image'" class="image-field">
      <div class="image-input-container">
        <el-input
          v-model="imageUrlValue"
          clearable
          placeholder="请输入图片链接或选择图片"
          style="flex: 1; margin-right: 8px"
          @blur="handleImageUrlChange"
        />
        <el-button :icon="Plus" type="primary" @click="chooseImage">选择图片</el-button>
      </div>
      <div v-if="imageDisplayUrl" class="image-preview">
        <el-image
          fit="contain"
          :src="imageDisplayUrl"
          style="width: 120px; height: 120px; margin-top: 8px; border: 1px solid #d9d9d9"
        />
        <el-button
          circle
          :icon="Delete"
          size="small"
          style="position: absolute; top: 5px; right: 5px"
          type="danger"
          @click="clearImage"
        />
      </div>
    </div>

    <!-- Color类型 -->
    <div v-else-if="fieldSchema.type === 'color'" class="color-field">
      <el-input
        v-model="colorInputValue"
        clearable
        placeholder="请输入颜色值，如 #FFFFFF、#FF121212、rgb(255,0,0)、rgba(255,0,0,0.5)、argb(255,18,18,18) 或 18, 18, 18"
        style="flex: 1; margin-right: 8px"
        @blur="handleColorInputChange"
      />
      <el-color-picker v-model="colorPickerValue" show-alpha @change="handleColorPickerChange" />
    </div>

    <!-- Array类型 -->
    <div v-else-if="fieldSchema.type === 'array'" class="array-field">
      <div v-for="(item, index) in arrayValue" :key="index" class="array-item">
        <div v-if="fieldSchema.items?.type === 'object'" class="array-object-item">
          <template v-for="(propSchema, propKey) in fieldSchema.items.properties" :key="propKey">
            <el-form-item
              :prop="`${String(fieldKey)}.${String(index)}.${String(propKey)}`"
              style="margin-bottom: 10px"
            >
              <template #label>
                <span v-if="propSchema.required" style="margin-right: 4px; color: red">*</span>
                <span>{{ propSchema.label }}</span>
              </template>
              <!-- 嵌套字段：String -->
              <el-input
                v-if="propSchema.type === 'string' && !propSchema.enum"
                v-model="item[propKey]"
                clearable
                :placeholder="`请输入${propSchema.label}`"
              />

              <!-- 嵌套字段：String带enum -->
              <el-select
                v-else-if="propSchema.type === 'string' && propSchema.enum"
                v-model="item[propKey]"
                clearable
                :placeholder="`请选择${propSchema.label}`"
                style="width: 100%"
              >
                <el-option
                  v-for="option in propSchema.enum"
                  :key="option"
                  :label="option"
                  :value="option"
                />
              </el-select>

              <!-- 嵌套字段：Boolean -->
              <el-switch v-else-if="propSchema.type === 'boolean'" v-model="item[propKey]" />

              <!-- 嵌套字段：Number -->
              <el-input-number
                v-else-if="propSchema.type === 'number'"
                v-model="item[propKey]"
                class="number-input"
                controls-position="right"
                style="width: 100%"
              />

              <!-- 嵌套字段：Image -->
              <div v-else-if="propSchema.type === 'image'" class="image-field">
                <div class="image-input-container">
                  <el-input
                    clearable
                    :model-value="getArrayItemImageUrl(item[propKey])"
                    placeholder="请输入图片链接或选择图片"
                    style="flex: 1; margin-right: 8px"
                    @blur="onArrayItemImageUrlBlur(index, propKey, $event)"
                  />
                  <el-button
                    :icon="Plus"
                    type="primary"
                    @click="chooseArrayItemImage(index, propKey)"
                  >
                    选择图片
                  </el-button>
                </div>
                <div v-if="getArrayItemImageUrl(item[propKey])" class="image-preview">
                  <el-image
                    fit="contain"
                    :src="getArrayItemImageUrl(item[propKey])"
                    style="width: 120px; height: 120px; margin-top: 8px; border: 1px solid #d9d9d9"
                  />
                  <el-button
                    circle
                    :icon="Delete"
                    size="small"
                    style="position: absolute; top: 5px; right: 5px"
                    type="danger"
                    @click="clearArrayItemImage(index, propKey)"
                  />
                </div>
              </div>
            </el-form-item>
          </template>
        </div>

        <!-- 简单类型数组 -->
        <el-input-number
          v-else-if="fieldSchema.items?.type === 'number'"
          v-model="arrayValue[index]"
          class="number-input"
          controls-position="right"
          style="flex: 1; margin-right: 8px"
        />
        <el-switch
          v-else-if="fieldSchema.items?.type === 'boolean'"
          v-model="arrayValue[index]"
          style="margin-right: 8px"
        />
        <el-input
          v-else
          v-model="arrayValue[index]"
          clearable
          :placeholder="`请输入${fieldSchema.label}`"
          style="flex: 1; margin-right: 8px"
        />

        <el-button circle :icon="Delete" type="danger" @click="removeArrayItem(index)" />
      </div>
      <el-button :icon="Plus" type="primary" @click="addArrayItem">
        添加{{ fieldSchema.label }}
      </el-button>
    </div>

    <!-- Object类型 -->
    <div v-else-if="fieldSchema.type === 'object'" class="object-field">
      <template v-for="(propSchema, propKey) in fieldSchema.properties" :key="propKey">
        <dynamic-form-field
          :field-key="String(propKey)"
          :field-schema="propSchema"
          :model-value="objectValue[propKey]"
          @update:model-value="objectValue[propKey] = $event"
        />
      </template>
    </div>
  </el-form-item>
</template>

<script lang="ts" setup>
import { Delete, Plus, QuestionFilled } from '@element-plus/icons-vue'
import { useUserStore } from '/@/store/modules/user'
import ProductWangEditor from '/@/views/product/vabAutoComponents/ProductWangEditor.vue'

defineOptions({
  name: 'DynamicFormField',
})

const props = defineProps<{
  fieldKey: string
  fieldSchema: any
  modelValue: any
}>()

const emit = defineEmits(['update:modelValue'])

const fileChooserRef = inject<any>('fileChooserRef')
const userStore = useUserStore()
const imageBaseUrl = computed(() => userStore.getImageBaseUrl)

const localValue = computed({
  get: () => props.modelValue,
  set: (value: any) => emit('update:modelValue', value),
})

const htmlEditorRef = ref<any>(null)
const noop = () => {}

// 颜色格式解析和转换工具函数，返回用于颜色选择器的格式（HEX或rgba）
const parseColorToHex = (colorValue: any): string => {
  if (!colorValue) return ''

  const colorStr = String(colorValue).trim()

  // 如果是8位HEX格式（包含alpha通道）: #AARRGGBB
  if (/^#[\da-f]{8}$/i.test(colorStr)) {
    // 提取alpha和RGB值，转换为rgba格式用于颜色选择器
    const alphaHex = colorStr.substring(1, 3)
    const rHex = colorStr.substring(3, 5)
    const gHex = colorStr.substring(5, 7)
    const bHex = colorStr.substring(7, 9)
    const alpha = parseInt(alphaHex, 16) / 255 // 转换为0-1范围
    const r = parseInt(rHex, 16)
    const g = parseInt(gHex, 16)
    const b = parseInt(bHex, 16)
    return `rgba(${r}, ${g}, ${b}, ${alpha.toFixed(3)})`
  }

  // 如果已经是6位或3位HEX格式
  if (/^#([\da-f]{3}){1,2}$/i.test(colorStr)) {
    // 如果是3位HEX，转换为6位
    if (colorStr.length === 4) {
      const r = colorStr[1]
      const g = colorStr[2]
      const b = colorStr[3]
      return `#${r}${r}${g}${g}${b}${b}`
    }
    return colorStr
  }

  // 如果是ARGB格式: argb(255, 18, 18, 18)
  const argbMatch = colorStr.match(/argb\((\d+),\s*(\d+),\s*(\d+),\s*(\d+)\)/i)
  if (argbMatch) {
    const alpha = parseInt(argbMatch[1]) / 255 // 转换为0-1范围
    const r = parseInt(argbMatch[2])
    const g = parseInt(argbMatch[3])
    const b = parseInt(argbMatch[4])
    return `rgba(${r}, ${g}, ${b}, ${alpha.toFixed(3)})`
  }

  // 如果是RGB格式: rgb(18, 18, 18) 或 rgba(18, 18, 18, 0.5)
  const rgbMatch = colorStr.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([\d.]+))?\)/i)
  if (rgbMatch) {
    // 如果是rgba格式，直接返回
    if (rgbMatch[4] !== undefined) {
      return colorStr
    }
    // 如果是rgb格式，转换为HEX
    const r = parseInt(rgbMatch[1])
    const g = parseInt(rgbMatch[2])
    const b = parseInt(rgbMatch[3])
    return `#${[r, g, b].map((x) => x.toString(16).padStart(2, '0')).join('')}`
  }

  // 如果是数字格式: 18, 18, 18
  const numMatch = colorStr.match(/^(\d+),\s*(\d+),\s*(\d+)$/)
  if (numMatch) {
    const r = parseInt(numMatch[1])
    const g = parseInt(numMatch[2])
    const b = parseInt(numMatch[3])
    return `#${[r, g, b].map((x) => x.toString(16).padStart(2, '0')).join('')}`
  }

  // 如果无法解析，返回空字符串
  return ''
}

// 从颜色字符串中提取alpha值（0-255范围）
const extractAlphaFromColorString = (colorStr: string): number | null => {
  if (!colorStr) return null

  const str = String(colorStr).trim()

  // 8位HEX格式: #AARRGGBB
  if (/^#[\da-f]{8}$/i.test(str)) {
    const alphaHex = str.substring(1, 3)
    return parseInt(alphaHex, 16)
  }

  // ARGB格式: argb(255, 18, 18, 18)
  const argbMatch = str.match(/argb\((\d+),\s*(\d+),\s*(\d+),\s*(\d+)\)/i)
  if (argbMatch) {
    return parseInt(argbMatch[1])
  }

  // RGBA格式: rgba(18, 18, 18, 0.5)
  const rgbaMatch = str.match(/rgba\((\d+),\s*(\d+),\s*(\d+),\s*([\d.]+)\)/i)
  if (rgbaMatch) {
    const alpha = parseFloat(rgbaMatch[4])
    // 将0-1范围的alpha转换为0-255范围
    return Math.round(alpha * 255)
  }

  return null
}

const hexToColorString = (pickerValue: string, originalFormat?: string): string => {
  if (!pickerValue) return ''

  // 如果pickerValue是rgba格式，提取RGB和alpha
  let rgb: { r: number; g: number; b: number } | null = null
  let alpha: number | null = null

  if (pickerValue.startsWith('rgba')) {
    const rgba = parseRgba(pickerValue)
    if (rgba) {
      rgb = { r: rgba.r, g: rgba.g, b: rgba.b }
      alpha = rgba.alpha
    }
  } else {
    // 如果是HEX格式
    rgb = hexToRgb(pickerValue)
  }

  if (!rgb) {
    // 如果无法解析，返回原值
    return pickerValue
  }

  // 如果原格式存在且是特定格式，尝试保持原格式
  if (originalFormat) {
    const orig = originalFormat.trim()

    // 如果是8位HEX格式（ARGB）: #AARRGGBB
    if (/^#[\da-f]{8}$/i.test(orig)) {
      const origAlpha = extractAlphaFromColorString(orig)
      const finalAlpha =
        alpha === null ? (origAlpha === null ? 255 : origAlpha) : Math.round(alpha * 255)
      const alphaHex = finalAlpha.toString(16).padStart(2, '0')
      const hex = `#${[rgb.r, rgb.g, rgb.b].map((x) => x.toString(16).padStart(2, '0')).join('')}`
      return `#${alphaHex}${hex.substring(1)}`
    }

    // 如果是ARGB格式: argb(255, 18, 18, 18)
    if (/^argb\(/.test(orig)) {
      const argbMatch = orig.match(/argb\((\d+),\s*(\d+),\s*(\d+),\s*(\d+)\)/i)
      const finalAlpha =
        alpha === null ? (argbMatch ? parseInt(argbMatch[1]) : 255) : Math.round(alpha * 255)
      return `argb(${finalAlpha}, ${rgb.r}, ${rgb.g}, ${rgb.b})`
    }

    // 如果是数字格式: 18, 18, 18
    if (/^\d+,\s*\d+,\s*\d+$/.test(orig)) {
      return `${rgb.r}, ${rgb.g}, ${rgb.b}`
    }

    // 如果是RGB格式: rgb(18, 18, 18)
    if (/^rgb\(/.test(orig)) {
      return `rgb(${rgb.r}, ${rgb.g}, ${rgb.b})`
    }

    // 如果是RGBA格式: rgba(18, 18, 18, 0.5)
    if (/^rgba\(/.test(orig)) {
      const rgbaMatch = orig.match(/rgba\((\d+),\s*(\d+),\s*(\d+),\s*([\d.]+)\)/i)
      const finalAlpha = alpha === null ? (rgbaMatch ? parseFloat(rgbaMatch[4]) : 1) : alpha
      return `rgba(${rgb.r}, ${rgb.g}, ${rgb.b}, ${finalAlpha})`
    }
  }

  // 如果原始格式是ARGB相关格式但当前值没有指定，使用默认的ARGB格式
  if (alpha !== null) {
    // 如果原始格式是8位HEX或ARGB，保持ARGB格式
    if (
      originalFormat &&
      (/^#[\da-f]{8}$/i.test(originalFormat) || /^argb\(/.test(originalFormat))
    ) {
      const alpha255 = Math.round(alpha * 255)
      return `argb(${alpha255}, ${rgb.r}, ${rgb.g}, ${rgb.b})`
    }
    // 否则返回rgba格式
    return `rgba(${rgb.r}, ${rgb.g}, ${rgb.b}, ${alpha})`
  }

  // 默认返回HEX格式
  return `#${[rgb.r, rgb.g, rgb.b].map((x) => x.toString(16).padStart(2, '0')).join('')}`
}

const hexToRgb = (hex: string): { r: number; g: number; b: number } | null => {
  const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex)
  return result
    ? {
        r: parseInt(result[1], 16),
        g: parseInt(result[2], 16),
        b: parseInt(result[3], 16),
      }
    : null
}

// 从rgba字符串提取RGB和alpha信息
const parseRgba = (rgbaStr: string): { r: number; g: number; b: number; alpha: number } | null => {
  const match = rgbaStr.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([\d.]+))?\)/i)
  if (match) {
    return {
      r: parseInt(match[1]),
      g: parseInt(match[2]),
      b: parseInt(match[3]),
      alpha: match[4] ? parseFloat(match[4]) : 1,
    }
  }
  return null
}

const arrayValue = computed({
  get: () => {
    if (!props.modelValue || !Array.isArray(props.modelValue)) {
      return []
    }
    return props.modelValue
  },
  set: (value: any) => emit('update:modelValue', value),
})

const objectValue = computed({
  get: () => {
    if (!props.modelValue || typeof props.modelValue !== 'object') {
      return {}
    }
    return props.modelValue
  },
  set: (value: any) => emit('update:modelValue', value),
})

// 颜色格式验证函数
const validateColorFormat = (rule: any, value: any, callback: any) => {
  if (!value) {
    if (props.fieldSchema.required) {
      callback(new Error(`${props.fieldSchema.label || props.fieldKey}不能为空`))
    } else {
      callback()
    }
    return
  }

  const colorStr = String(value).trim()

  // 检查是否是有效的颜色格式
  const isValidColor =
    /^#([\da-f]{3}){1,2}$/i.test(colorStr) || // HEX格式（3位或6位）
    /^#[\da-f]{8}$/i.test(colorStr) || // 8位HEX格式（ARGB）
    /^rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([\d.]+))?\)$/i.test(colorStr) || // RGB/RGBA格式
    /^argb\((\d+),\s*(\d+),\s*(\d+),\s*(\d+)\)$/i.test(colorStr) || // ARGB格式
    /^(\d+),\s*(\d+),\s*(\d+)$/.test(colorStr) // 数字格式

  if (isValidColor) {
    callback()
  } else {
    callback(
      new Error(
        '请输入有效的颜色格式，如 #FFFFFF、#FF121212、rgb(255,0,0)、rgba(255,0,0,0.5)、argb(255,18,18,18) 或 18, 18, 18'
      )
    )
  }
}

// HTML 内容验证：检查去除 HTML 标签后是否有实际文本
const validateHtmlContent = (rule: any, value: any, callback: any) => {
  // 使用 props.modelValue 而不是 value 参数，因为 value 可能不是最新的
  const actualValue = props.modelValue

  if (!actualValue) {
    callback(new Error(`${props.fieldSchema.label || props.fieldKey}不能为空`))
    return
  }

  // 去除 HTML 标签，获取纯文本
  const div = document.createElement('div')
  div.innerHTML = actualValue
  const textContent = div.textContent || div.innerText || ''
  const trimmedText = textContent.trim()

  if (trimmedText === '') {
    callback(new Error(`${props.fieldSchema.label || props.fieldKey}不能为空`))
  } else {
    callback()
  }
}

const fieldRules = computed(() => {
  const rules: any[] = []

  if (props.fieldSchema.type === 'color') {
    // 对于color类型，使用自定义验证器同时验证必填和格式
    rules.push({
      required: false, // 不在rules中设置required，避免双重星号
      validator: validateColorFormat,
      trigger: 'blur',
    })
  } else if (props.fieldSchema.type === 'html') {
    // 对于html类型，使用自定义验证器检查内容是否有实际文本
    if (props.fieldSchema.required) {
      rules.push({
        required: false, // 不使用 required，改用自定义验证器
        validator: validateHtmlContent,
        trigger: ['change', 'blur'],
      })
    }
  } else {
    if (props.fieldSchema.required) {
      // 对于非color类型，使用标准的required验证
      rules.push({
        required: true,
        message: `${props.fieldSchema.label || props.fieldKey}不能为空`,
        trigger: 'blur',
      })
    }
  }

  return rules
})

// Image字段的URL值
const imageUrlValue = ref<string>('')

// 监听localValue变化，更新imageUrlValue
watch(
  () => localValue.value,
  (newVal: any) => {
    if (!newVal) {
      imageUrlValue.value = ''
      return
    }
    if (typeof newVal === 'string') {
      imageUrlValue.value = newVal
    } else if (newVal.absolutionPath) {
      imageUrlValue.value = newVal.absolutionPath
    } else {
      imageUrlValue.value = ''
    }
  },
  { immediate: true }
)

// Image字段的显示URL
const imageDisplayUrl = computed(() => {
  if (!localValue.value) return ''
  if (typeof localValue.value === 'string') {
    if (localValue.value.startsWith('__APP_IMAGE_URL__')) {
      const baseUrl = imageBaseUrl.value || ''
      return localValue.value.replace('__APP_IMAGE_URL__', baseUrl)
    }
    return localValue.value
  }
  if (localValue.value.absolutionPath) {
    return localValue.value.absolutionPath
  }
  return ''
})

const handleImageUrlChange = () => {
  if (!imageUrlValue.value || imageUrlValue.value.trim() === '') {
    localValue.value = null
    return
  }
  // 如果输入的是URL字符串，直接保存为字符串
  localValue.value = imageUrlValue.value.trim()
}

const chooseImage = async () => {
  if (!fileChooserRef?.value) {
    console.error('fileChooserRef not found')
    return
  }
  const images = await fileChooserRef.value.choose()
  if (images && images.length > 0) {
    const image = images[0]
    localValue.value = `__APP_IMAGE_URL__/${image.relativePath}`
  }
}

const clearImage = () => {
  localValue.value = null
}

const getArrayItemImageUrl = (value: any): string => {
  if (!value) return ''
  if (typeof value === 'string') {
    if (value.startsWith('__APP_IMAGE_URL__')) {
      const baseUrl = imageBaseUrl.value || ''
      return value.replace('__APP_IMAGE_URL__', baseUrl)
    }
    return value
  }
  if (value.absolutionPath) {
    return value.absolutionPath
  }
  return ''
}

const handleArrayItemImageUrlChange = (index: number, propKey: string | number, url: string) => {
  if (!url || url.trim() === '') {
    arrayValue.value[index][propKey] = null
    arrayValue.value = [...arrayValue.value]
    return
  }
  // 如果输入的是URL字符串，直接保存为字符串
  arrayValue.value[index][propKey] = url.trim()
  arrayValue.value = [...arrayValue.value]
}

const onArrayItemImageUrlBlur = (index: number, propKey: string | number, e: FocusEvent) => {
  const target = e?.target as HTMLInputElement | null
  handleArrayItemImageUrlChange(index, propKey, target?.value || '')
}

const chooseArrayItemImage = async (index: number, propKey: string | number) => {
  if (!fileChooserRef?.value) {
    console.error('fileChooserRef not found')
    return
  }
  const images = await fileChooserRef.value.choose()
  if (images && images.length > 0) {
    const image = images[0]
    arrayValue.value[index][propKey] = `__APP_IMAGE_URL__/${image.relativePath}`
    arrayValue.value = [...arrayValue.value]
  }
}

const clearArrayItemImage = (index: number, propKey: string | number) => {
  arrayValue.value[index][propKey] = null
  arrayValue.value = [...arrayValue.value]
}

const addArrayItem = () => {
  if (props.fieldSchema.items?.type === 'object') {
    // 创建对象数组项
    const newItem: any = {}
    if (props.fieldSchema.items.properties) {
      Object.keys(props.fieldSchema.items.properties).forEach((key) => {
        newItem[key] = ''
      })
    }
    arrayValue.value = [...arrayValue.value, newItem]
  } else {
    // 创建简单类型数组项
    arrayValue.value = [...arrayValue.value, '']
  }
}

const removeArrayItem = (index: number) => {
  arrayValue.value.splice(index, 1)
  arrayValue.value = [...arrayValue.value]
}

// Color字段相关
const colorInputValue = ref<string>('')
const colorPickerValue = ref<string>('')
const originalColorFormat = ref<string>('')
const isUpdatingFromPicker = ref<boolean>(false)
const isUpdatingFromInput = ref<boolean>(false)

// 监听localValue变化，更新color输入框和picker（仅当外部值变化时）
watch(
  () => localValue.value,
  (newVal: any) => {
    if (isUpdatingFromInput.value || isUpdatingFromPicker.value) {
      return
    }

    if (!newVal) {
      colorInputValue.value = ''
      colorPickerValue.value = ''
      originalColorFormat.value = ''
      return
    }

    const colorStr = String(newVal).trim()
    originalColorFormat.value = colorStr

    // 更新输入框显示原始格式
    colorInputValue.value = colorStr

    // 转换为HEX格式给picker使用
    const hex = parseColorToHex(colorStr)
    if (hex) {
      colorPickerValue.value = hex
    }
  },
  { immediate: true }
)

const handleColorInputChange = () => {
  if (!colorInputValue.value || colorInputValue.value.trim() === '') {
    isUpdatingFromInput.value = true
    localValue.value = null
    colorPickerValue.value = ''
    originalColorFormat.value = ''
    isUpdatingFromInput.value = false
    return
  }

  const colorStr = colorInputValue.value.trim()
  isUpdatingFromInput.value = true
  originalColorFormat.value = colorStr

  // 尝试解析颜色值
  const hex = parseColorToHex(colorStr)
  if (hex) {
    // 更新picker
    colorPickerValue.value = hex
    // 保存原始格式的值
    localValue.value = colorStr
  } else {
    // 如果无法解析，仍然保存输入的值
    localValue.value = colorStr
  }
  isUpdatingFromInput.value = false
}

// 监听colorPickerValue变化（通过watch和change事件）
watch(
  () => colorPickerValue.value,
  (newVal: string) => {
    if (isUpdatingFromInput.value) {
      return
    }

    if (!newVal) {
      isUpdatingFromPicker.value = true
      localValue.value = null
      colorInputValue.value = ''
      originalColorFormat.value = ''
      isUpdatingFromPicker.value = false
      return
    }

    // 根据原始格式决定如何显示
    isUpdatingFromPicker.value = true
    const displayValue = hexToColorString(newVal, originalColorFormat.value)
    colorInputValue.value = displayValue
    localValue.value = displayValue
    originalColorFormat.value = displayValue
    isUpdatingFromPicker.value = false
  }
)

const handleColorPickerChange = (value: string | null) => {
  // 这个函数在picker确认选择时触发（change事件）
  if (isUpdatingFromInput.value) {
    return
  }

  if (!value) {
    isUpdatingFromPicker.value = true
    localValue.value = null
    colorInputValue.value = ''
    originalColorFormat.value = ''
    isUpdatingFromPicker.value = false
    return
  }

  // 根据原始格式决定如何显示
  isUpdatingFromPicker.value = true
  const displayValue = hexToColorString(value, originalColorFormat.value)
  colorInputValue.value = displayValue
  localValue.value = displayValue
  originalColorFormat.value = displayValue
  isUpdatingFromPicker.value = false
}

const insertPlaceholder = async (text: string) => {
  const value = String(text || '')
  if (!value) return
  const ok = await htmlEditorRef.value?.insertPlaceholder?.(value)
  // 若富文本实例不存在/插入失败，则至少保证复制功能可用
  if (!ok) {
    await copyPlaceholder(value)
  }
}

const copyPlaceholder = async (text: string) => {
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
</script>

<style scoped>
.image-field {
  position: relative;
}

.image-preview {
  position: relative;
  display: inline-block;
}

.array-field {
  width: 100%;
}

.array-item {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 12px;
  margin-bottom: 16px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}

.array-object-item {
  flex: 1;
  width: 100%;
}

.object-field {
  padding: 16px;
  background-color: #f5f7fa;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}

.image-input-container {
  display: flex;
  gap: 8px;
  align-items: center;
}

.color-field {
  display: flex;
  gap: 8px;
  align-items: center;
}

.html-field {
  width: 100%;
}

.placeholder-box {
  margin-top: 10px;
}

.placeholder-title {
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.placeholder-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.placeholder-tag {
  cursor: pointer;
}

.number-input :deep(.el-input__inner) {
  text-align: left;
}
</style>
