<script setup lang="ts">
/**
 * 变量管理弹窗组件
 * 用于管理主题自定义变量（添加、编辑、删除）
 * 支持多种变量类型：string、number、boolean、color、image、richtext、enum、array、object
 */

import type {
  CustomVariable,
  VariableType,
  EnumOption,
  VariableFieldSchema,
} from '~/types/data-context'
import { BASIC_VARIABLE_TYPES, ALL_VARIABLE_TYPES } from '~/types/data-context'

defineProps<{
  visible: boolean
  variables: CustomVariable[]
}>()

const emit = defineEmits<{
  close: []
  save: [variable: CustomVariable]
  delete: [key: string]
}>()

// ============ 编辑弹窗状态 ============

const showEditModal = ref(false)
const editMode = ref<'add' | 'edit'>('add')
const editingKey = ref<string | null>(null)

const editForm = ref<Partial<CustomVariable>>({
  key: '',
  label: '',
  type: 'string',
  defaultValue: '',
  description: '',
  i18n: false,
  enumOptions: [],
  itemType: 'string',
  itemSchema: [],
  fields: [],
})

const arrayItemIsComplex = ref(false)
const showDefaultValueEditor = ref(false)

// 变量类型选项
const variableTypes: Array<{ value: VariableType; label: string; icon: string; description: string }> = [
  { value: 'string', label: '文本', icon: 'i-carbon-text-font', description: '单行文本' },
  { value: 'number', label: '数字', icon: 'i-carbon-hashtag', description: '数值' },
  { value: 'boolean', label: '开关', icon: 'i-carbon-toggle-off', description: '是/否' },
  { value: 'color', label: '颜色', icon: 'i-carbon-color-palette', description: '颜色值' },
  { value: 'image', label: '图片', icon: 'i-carbon-image', description: '图片 URL' },
  { value: 'richtext', label: '富文本', icon: 'i-carbon-text-align-left', description: '多行富文本' },
  { value: 'enum', label: '枚举', icon: 'i-carbon-list-checked', description: '固定选项' },
  { value: 'array', label: '数组', icon: 'i-carbon-list', description: '列表数据' },
  { value: 'object', label: '对象', icon: 'i-carbon-json', description: '结构化数据' },
]

function getTypeInfo(type: VariableType) {
  return variableTypes.find(t => t.value === type) ?? variableTypes[0]!
}

function getDefaultValueByType(type: VariableType): any {
  switch (type) {
    case 'string': return ''
    case 'number': return 0
    case 'boolean': return false
    case 'color': return '#3b82f6'
    case 'image': return ''
    case 'richtext': return ''
    case 'enum': return ''
    case 'array': return []
    case 'object': return {}
    default: return ''
  }
}

function getVariableExtraInfo(variable: CustomVariable): string {
  if (variable.type === 'enum' && variable.enumOptions) {
    return `${variable.enumOptions.length} 个选项`
  }
  if (variable.type === 'array') {
    if (variable.itemSchema && variable.itemSchema.length > 0) {
      return `对象数组 (${variable.itemSchema.length} 个字段)`
    }
    if (variable.itemType) {
      const typeInfo = getTypeInfo(variable.itemType)
      return `${typeInfo?.label || variable.itemType}数组`
    }
  }
  if (variable.type === 'object' && variable.fields) {
    return `${variable.fields.length} 个字段`
  }
  return ''
}

// ============ 打开编辑弹窗 ============

function openAddModal() {
  editMode.value = 'add'
  editingKey.value = null
  resetEditForm()
  showEditModal.value = true
}

function openEditModal(variable: CustomVariable) {
  editMode.value = 'edit'
  editingKey.value = variable.key
  
  editForm.value = {
    key: variable.key,
    label: variable.label,
    type: variable.type,
    defaultValue: variable.defaultValue,
    description: variable.description || '',
    i18n: variable.i18n || false,
    enumOptions: variable.enumOptions ? JSON.parse(JSON.stringify(variable.enumOptions)) : [],
    itemType: variable.itemType || 'string',
    itemSchema: variable.itemSchema ? JSON.parse(JSON.stringify(variable.itemSchema)) : [],
    fields: variable.fields ? JSON.parse(JSON.stringify(variable.fields)) : [],
  }
  
  arrayItemIsComplex.value = !!(variable.itemSchema && variable.itemSchema.length > 0)
  showEditModal.value = true
}

function closeEditModal() {
  showEditModal.value = false
  editMode.value = 'add'
  editingKey.value = null
  resetEditForm()
}

function resetEditForm() {
  editForm.value = {
    key: '',
    label: '',
    type: 'string',
    defaultValue: '',
    description: '',
    i18n: false,
    enumOptions: [],
    itemType: 'string',
    itemSchema: [],
    fields: [],
  }
  arrayItemIsComplex.value = false
}

// ============ 类型变更处理 ============

function handleTypeChange(type: VariableType) {
  const oldType = editForm.value.type
  editForm.value.type = type
  
  if (oldType !== type) {
    editForm.value.defaultValue = getDefaultValueByType(type)
    
    if (type === 'enum') {
      editForm.value.enumOptions = editForm.value.enumOptions?.length 
        ? editForm.value.enumOptions 
        : [{ value: '', label: '' }]
    }
    
    if (type === 'array') {
      arrayItemIsComplex.value = false
      editForm.value.itemType = 'string'
      editForm.value.itemSchema = []
    }
    
    if (type === 'object') {
      editForm.value.fields = editForm.value.fields?.length 
        ? editForm.value.fields 
        : []
    }
  }
}

// ============ 枚举选项管理 ============

function addEnumOption() {
  if (!editForm.value.enumOptions) {
    editForm.value.enumOptions = []
  }
  editForm.value.enumOptions.push({ value: '', label: '' })
}

function removeEnumOption(index: number) {
  editForm.value.enumOptions?.splice(index, 1)
}

// ============ 数组元素类型管理 ============

function handleArrayItemTypeChange(isComplex: boolean) {
  arrayItemIsComplex.value = isComplex
  if (isComplex) {
    editForm.value.itemType = undefined
    if (!editForm.value.itemSchema?.length) {
      editForm.value.itemSchema = []
    }
  } else {
    editForm.value.itemType = 'string'
    editForm.value.itemSchema = []
  }
}

function addArrayItemField() {
  if (!editForm.value.itemSchema) {
    editForm.value.itemSchema = []
  }
  editForm.value.itemSchema.push({
    key: '',
    label: '',
    type: 'string',
  })
}

function removeArrayItemField(index: number) {
  editForm.value.itemSchema?.splice(index, 1)
}

// ============ 对象字段管理 ============

function addObjectField() {
  if (!editForm.value.fields) {
    editForm.value.fields = []
  }
  editForm.value.fields.push({
    key: '',
    label: '',
    type: 'string',
  })
}

function removeObjectField(index: number) {
  editForm.value.fields?.splice(index, 1)
}

// ============ 表单验证 ============

function validateForm(): string | null {
  if (!editForm.value.key) {
    return '请填写变量键名'
  }
  
  if (!editForm.value.label) {
    return '请填写显示名称'
  }
  
  if (!/^[a-zA-Z][a-zA-Z0-9_]*$/.test(editForm.value.key)) {
    return '键名只能包含字母、数字、下划线，且必须以字母开头'
  }
  
  if (editForm.value.type === 'enum') {
    const options = editForm.value.enumOptions || []
    if (options.length === 0) {
      return '枚举类型至少需要一个选项'
    }
    for (const opt of options) {
      if (!opt.value || !opt.label) {
        return '枚举选项的值和标签都不能为空'
      }
    }
  }
  
  if (editForm.value.type === 'array' && arrayItemIsComplex.value) {
    const schema = editForm.value.itemSchema || []
    if (schema.length === 0) {
      return '数组元素结构至少需要一个字段'
    }
    for (const field of schema) {
      if (!field.key || !field.label) {
        return '数组元素字段的键名和标签都不能为空'
      }
    }
  }
  
  if (editForm.value.type === 'object') {
    const fields = editForm.value.fields || []
    if (fields.length === 0) {
      return '对象类型至少需要一个字段'
    }
    for (const field of fields) {
      if (!field.key || !field.label) {
        return '对象字段的键名和标签都不能为空'
      }
    }
  }
  
  return null
}

// ============ 保存变量 ============

function handleSave() {
  const error = validateForm()
  if (error) {
    alert(error)
    return
  }
  
  const variable: CustomVariable = {
    key: editForm.value.key!,
    label: editForm.value.label!,
    type: editForm.value.type || 'string',
    defaultValue: editForm.value.defaultValue,
    description: editForm.value.description || undefined,
    i18n: editForm.value.i18n || undefined,
  }
  
  if (variable.type === 'enum') {
    variable.enumOptions = editForm.value.enumOptions?.filter(
      opt => opt.value && opt.label
    )
    if (variable.enumOptions && variable.enumOptions.length > 0 && !variable.defaultValue) {
      variable.defaultValue = variable.enumOptions[0]?.value
    }
  }
  
  if (variable.type === 'array') {
    if (arrayItemIsComplex.value) {
      variable.itemSchema = editForm.value.itemSchema?.filter(
        f => f.key && f.label
      )
    } else {
      variable.itemType = editForm.value.itemType
    }
  }
  
  if (variable.type === 'object') {
    variable.fields = editForm.value.fields?.filter(f => f.key && f.label)
  }
  
  emit('save', variable)
  closeEditModal()
}

// ============ 删除变量 ============

function handleDeleteVariable(key: string, label: string) {
  if (confirm(`确定要删除变量「${label}」吗？`)) {
    emit('delete', key)
  }
}

// ============ 关闭管理弹窗 ============

function handleClose() {
  closeEditModal()
  emit('close')
}

// 默认值显示摘要
const defaultValueSummary = computed(() => {
  const type = editForm.value.type
  const value = editForm.value.defaultValue
  
  if (value === undefined || value === null || value === '') {
    return '未配置'
  }
  
  switch (type) {
    case 'string':
    case 'image':
    case 'richtext':
      return String(value).length > 20 ? String(value).slice(0, 20) + '...' : String(value)
    case 'number':
      return String(value)
    case 'boolean':
      return value ? '是' : '否'
    case 'color':
      return value
    case 'enum':
      const opt = editForm.value.enumOptions?.find(o => o.value === value)
      return opt ? opt.label : String(value)
    case 'array':
      return `${(value as any[])?.length || 0} 个元素`
    case 'object':
      return '已配置'
    default:
      return '已配置'
  }
})

// 处理默认值保存
function handleDefaultValueSave(value: any) {
  editForm.value.defaultValue = value
}

const editModalTitle = computed(() => {
  return editMode.value === 'add' ? '添加变量' : '编辑变量'
})
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="visible" class="modal-overlay" @click.self="handleClose">
        <div class="variable-manager">
          <!-- 头部 -->
          <div class="manager-header">
            <h2 class="manager-title">
              <span class="i-carbon-parameter"></span>
              变量管理
            </h2>
            <button class="close-btn" @click="handleClose">
              <span class="i-carbon-close"></span>
            </button>
          </div>

          <!-- 内容区 -->
          <div class="manager-content">
            <div class="variable-list">
              <div v-if="variables.length === 0" class="empty-state">
                <span class="i-carbon-parameter empty-icon"></span>
                <p class="empty-title">暂无自定义变量</p>
                <p class="empty-hint">点击下方按钮添加变量</p>
              </div>

              <div
                v-for="variable in variables"
                :key="variable.key"
                class="variable-item"
              >
                <div class="variable-info">
                  <div class="variable-type-icon">
                    <span :class="getTypeInfo(variable.type).icon"></span>
                  </div>
                  <div class="variable-details">
                    <div class="variable-name">
                      <span class="variable-label">{{ variable.label }}</span>
                      <span class="variable-key">site.{{ variable.key }}</span>
                    </div>
                    <div v-if="variable.description" class="variable-desc">
                      {{ variable.description }}
                    </div>
                    <div class="variable-meta">
                      <span class="meta-tag">{{ getTypeInfo(variable.type).label }}</span>
                      <span v-if="getVariableExtraInfo(variable)" class="meta-tag extra">
                        {{ getVariableExtraInfo(variable) }}
                      </span>
                      <span v-if="variable.i18n" class="meta-tag i18n">多语言</span>
                    </div>
                  </div>
                </div>
                <div class="variable-actions">
                  <button
                    class="action-btn"
                    title="编辑"
                    @click="openEditModal(variable)"
                  >
                    <span class="i-carbon-edit"></span>
                  </button>
                  <button
                    class="action-btn danger"
                    title="删除"
                    @click="handleDeleteVariable(variable.key, variable.label)"
                  >
                    <span class="i-carbon-trash-can"></span>
                  </button>
                </div>
              </div>
            </div>
          </div>

          <!-- 底部 -->
          <div class="manager-footer">
            <button class="btn btn-primary" @click="openAddModal">
              <span class="i-carbon-add"></span>
              添加变量
            </button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- 编辑弹窗 -->
    <Transition name="modal">
      <div v-if="showEditModal" class="modal-overlay edit-modal-overlay" @click.self="closeEditModal">
        <div class="edit-modal">
          <!-- 编辑弹窗头部 -->
          <div class="edit-modal-header">
            <h3 class="edit-modal-title">
              <span :class="editMode === 'add' ? 'i-carbon-add' : 'i-carbon-edit'"></span>
              {{ editModalTitle }}
            </h3>
            <button class="close-btn" @click="closeEditModal">
              <span class="i-carbon-close"></span>
            </button>
          </div>

          <!-- 编辑弹窗内容 -->
          <div class="edit-modal-content">
            <!-- 基本信息 -->
            <div class="form-section">
              <div class="section-title">基本信息</div>
              <div class="form-row">
                <div class="form-group">
                  <label>键名 <span class="required">*</span></label>
                  <div class="input-with-prefix">
                    <span class="input-prefix">site.</span>
                    <input
                      v-model="editForm.key"
                      type="text"
                      class="property-input"
                      placeholder="如：logo"
                      :disabled="editMode === 'edit'"
                    />
                  </div>
                </div>
                <div class="form-group">
                  <label>显示名称 <span class="required">*</span></label>
                  <input
                    v-model="editForm.label"
                    type="text"
                    class="property-input"
                    placeholder="如：网站 Logo"
                  />
                </div>
              </div>
            </div>

            <!-- 变量类型 -->
            <div class="form-section">
              <div class="section-title">变量类型</div>
              <div class="type-selector">
                <button
                  v-for="typeOption in variableTypes"
                  :key="typeOption.value"
                  class="type-btn"
                  :class="{ active: editForm.type === typeOption.value }"
                  :title="typeOption.description"
                  @click="handleTypeChange(typeOption.value)"
                >
                  <span :class="typeOption.icon"></span>
                  <span>{{ typeOption.label }}</span>
                </button>
              </div>
            </div>

            <!-- 类型配置区域 -->
            <div v-if="['enum', 'array', 'object'].includes(editForm.type || '')" class="form-section">
              <div class="section-title">
                {{ editForm.type === 'enum' ? '枚举选项' : editForm.type === 'array' ? '数组配置' : '对象字段' }}
              </div>

              <!-- 枚举类型配置 -->
              <div v-if="editForm.type === 'enum'" class="type-config">
                <div class="config-header">
                  <span class="config-hint">定义枚举的可选值</span>
                  <button class="add-item-btn" @click="addEnumOption">
                    <span class="i-carbon-add"></span>
                    添加选项
                  </button>
                </div>
                <div class="enum-options">
                  <div class="enum-header">
                    <span>值</span>
                    <span>显示标签</span>
                    <span></span>
                  </div>
                  <div
                    v-for="(opt, index) in editForm.enumOptions"
                    :key="index"
                    class="enum-option-row"
                  >
                    <input
                      v-model="opt.value"
                      type="text"
                      class="property-input"
                      placeholder="如：light"
                    />
                    <input
                      v-model="opt.label"
                      type="text"
                      class="property-input"
                      placeholder="如：浅色模式"
                    />
                    <button
                      class="remove-item-btn"
                      :disabled="(editForm.enumOptions?.length || 0) <= 1"
                      @click="removeEnumOption(index)"
                    >
                      <span class="i-carbon-close"></span>
                    </button>
                  </div>
                  <div v-if="!editForm.enumOptions?.length" class="empty-config">
                    点击"添加选项"定义枚举值
                  </div>
                </div>
              </div>

              <!-- 数组类型配置 -->
              <div v-if="editForm.type === 'array'" class="type-config">
                <div class="array-type-switch">
                  <button
                    class="type-switch-btn"
                    :class="{ active: !arrayItemIsComplex }"
                    @click="handleArrayItemTypeChange(false)"
                  >
                    <span class="i-carbon-string-text"></span>
                    简单类型
                  </button>
                  <button
                    class="type-switch-btn"
                    :class="{ active: arrayItemIsComplex }"
                    @click="handleArrayItemTypeChange(true)"
                  >
                    <span class="i-carbon-json"></span>
                    对象类型
                  </button>
                </div>

                <!-- 简单类型选择 -->
                <div v-if="!arrayItemIsComplex" class="simple-type-select">
                  <label>元素类型</label>
                  <select v-model="editForm.itemType" class="property-input">
                    <option v-for="t in BASIC_VARIABLE_TYPES" :key="t" :value="t">
                      {{ getTypeInfo(t).label }}
                    </option>
                  </select>
                </div>

                <!-- 对象类型字段定义 -->
                <div v-else class="object-fields-config">
                  <div class="fields-header">
                    <span class="config-hint">定义数组元素的字段结构</span>
                    <button class="add-item-btn" @click="addArrayItemField">
                      <span class="i-carbon-add"></span>
                      添加字段
                    </button>
                  </div>
                  <div class="fields-list">
                    <div class="fields-list-header">
                      <span>字段键名</span>
                      <span>显示标签</span>
                      <span>类型</span>
                      <span></span>
                    </div>
                    <div
                      v-for="(field, index) in editForm.itemSchema"
                      :key="index"
                      class="field-row"
                    >
                      <input
                        v-model="field.key"
                        type="text"
                        class="property-input"
                        placeholder="如：title"
                      />
                      <input
                        v-model="field.label"
                        type="text"
                        class="property-input"
                        placeholder="如：标题"
                      />
                      <select v-model="field.type" class="property-input field-type-select">
                        <option v-for="t in ALL_VARIABLE_TYPES" :key="t" :value="t">
                          {{ getTypeInfo(t).label }}
                        </option>
                      </select>
                      <button class="remove-item-btn" @click="removeArrayItemField(index)">
                        <span class="i-carbon-close"></span>
                      </button>
                    </div>
                    <div v-if="!editForm.itemSchema?.length" class="empty-config">
                      点击"添加字段"定义数组元素结构
                    </div>
                  </div>
                </div>
              </div>

              <!-- 对象类型配置 -->
              <div v-if="editForm.type === 'object'" class="type-config">
                <div class="config-header">
                  <span class="config-hint">定义对象的字段结构</span>
                  <button class="add-item-btn" @click="addObjectField">
                    <span class="i-carbon-add"></span>
                    添加字段
                  </button>
                </div>
                <div class="fields-list">
                  <div class="fields-list-header">
                    <span>字段键名</span>
                    <span>显示标签</span>
                    <span>类型</span>
                    <span></span>
                  </div>
                  <div
                    v-for="(field, index) in editForm.fields"
                    :key="index"
                    class="field-row"
                  >
                    <input
                      v-model="field.key"
                      type="text"
                      class="property-input"
                      placeholder="如：name"
                    />
                    <input
                      v-model="field.label"
                      type="text"
                      class="property-input"
                      placeholder="如：名称"
                    />
                    <select v-model="field.type" class="property-input field-type-select">
                      <option v-for="t in ALL_VARIABLE_TYPES" :key="t" :value="t">
                        {{ getTypeInfo(t).label }}
                      </option>
                    </select>
                    <button class="remove-item-btn" @click="removeObjectField(index)">
                      <span class="i-carbon-close"></span>
                    </button>
                  </div>
                  <div v-if="!editForm.fields?.length" class="empty-config">
                    点击"添加字段"定义对象结构
                  </div>
                </div>
              </div>
            </div>

            <!-- 默认值配置 -->
            <div class="form-section">
              <div class="section-title">默认值</div>
              <div class="default-value-trigger">
                <button
                  class="default-value-btn"
                  :class="{ configured: defaultValueSummary !== '未配置' }"
                  @click="showDefaultValueEditor = true"
                >
                  <span class="i-carbon-settings-adjust"></span>
                  <span class="default-value-summary">{{ defaultValueSummary }}</span>
                  <span class="i-carbon-chevron-right"></span>
                </button>
              </div>
            </div>

            <!-- 其他选项 -->
            <div class="form-section">
              <div class="section-title">其他选项</div>
              <div class="form-group">
                <label>描述</label>
                <input
                  v-model="editForm.description"
                  type="text"
                  class="property-input"
                  placeholder="变量用途说明（可选）"
                />
              </div>
              <div class="form-group inline">
                <label class="checkbox-label">
                  <input
                    v-model="editForm.i18n"
                    type="checkbox"
                    class="checkbox-input"
                  />
                  <span class="checkbox-custom"></span>
                  <span class="checkbox-text">支持多语言</span>
                </label>
              </div>
            </div>
          </div>

          <!-- 编辑弹窗底部 -->
          <div class="edit-modal-footer">
            <button class="btn btn-secondary" @click="closeEditModal">取消</button>
            <button class="btn btn-primary" @click="handleSave">
              <span class="i-carbon-checkmark"></span>
              保存
            </button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- 默认值配置弹窗 -->
    <BuilderDefaultValueEditor
      :visible="showDefaultValueEditor"
      :type="editForm.type || 'string'"
      :value="editForm.defaultValue"
      :enum-options="editForm.enumOptions"
      :item-type="editForm.itemType"
      :item-schema="editForm.itemSchema"
      :fields="editForm.fields"
      @close="showDefaultValueEditor = false"
      @save="handleDefaultValueSave"
    />
  </Teleport>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(2px);
  z-index: 1000;
}

.edit-modal-overlay {
  z-index: 1001;
}

/* 变量管理主弹窗 */
.variable-manager {
  display: flex;
  flex-direction: column;
  width: 600px;
  max-width: 90vw;
  max-height: 80vh;
  background: linear-gradient(180deg, #1e293b 0%, #0f172a 100%);
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 16px;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
  overflow: hidden;
}

.manager-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid rgba(71, 85, 105, 0.5);
  background: linear-gradient(180deg, rgba(30, 41, 59, 0.8) 0%, rgba(30, 41, 59, 0.4) 100%);
}

.manager-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 600;
  color: #f1f5f9;
  margin: 0;
}

.manager-title span {
  font-size: 20px;
  color: #3b82f6;
}

.close-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  color: #94a3b8;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.close-btn:hover {
  color: #f1f5f9;
  background: rgba(239, 68, 68, 0.1);
  border-color: rgba(239, 68, 68, 0.3);
}

.manager-content {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
}

.variable-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;
}

.empty-icon {
  font-size: 48px;
  color: #475569;
  margin-bottom: 16px;
}

.empty-title {
  font-size: 16px;
  font-weight: 500;
  color: #94a3b8;
  margin: 0 0 8px;
}

.empty-hint {
  font-size: 14px;
  color: #64748b;
  margin: 0;
}

.variable-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  background: linear-gradient(135deg, rgba(15, 23, 42, 0.8) 0%, rgba(15, 23, 42, 0.6) 100%);
  border: 1px solid rgba(71, 85, 105, 0.4);
  border-radius: 12px;
  transition: all 0.2s;
}

.variable-item:hover {
  border-color: rgba(59, 130, 246, 0.4);
  background: linear-gradient(135deg, rgba(15, 23, 42, 0.9) 0%, rgba(15, 23, 42, 0.7) 100%);
}

.variable-info {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  flex: 1;
  min-width: 0;
}

.variable-type-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  font-size: 20px;
  color: #3b82f6;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.15) 0%, rgba(59, 130, 246, 0.05) 100%);
  border: 1px solid rgba(59, 130, 246, 0.2);
  border-radius: 10px;
  flex-shrink: 0;
}

.variable-details {
  flex: 1;
  min-width: 0;
}

.variable-name {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
}

.variable-label {
  font-size: 15px;
  font-weight: 500;
  color: #f1f5f9;
}

.variable-key {
  font-size: 12px;
  font-family: 'Monaco', 'Menlo', monospace;
  color: #64748b;
  padding: 2px 6px;
  background: rgba(51, 65, 85, 0.5);
  border-radius: 4px;
}

.variable-desc {
  font-size: 13px;
  color: #64748b;
  margin-top: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.variable-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.meta-tag {
  padding: 3px 8px;
  font-size: 11px;
  font-weight: 500;
  color: #94a3b8;
  background: rgba(51, 65, 85, 0.6);
  border-radius: 4px;
}

.meta-tag.extra {
  color: #60a5fa;
  background: rgba(59, 130, 246, 0.15);
}

.meta-tag.i18n {
  color: #22c55e;
  background: rgba(34, 197, 94, 0.15);
}

.variable-actions {
  display: flex;
  gap: 6px;
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  font-size: 16px;
  color: #64748b;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.action-btn:hover {
  color: #3b82f6;
  background: rgba(59, 130, 246, 0.1);
  border-color: rgba(59, 130, 246, 0.3);
}

.action-btn.danger:hover {
  color: #ef4444;
  background: rgba(239, 68, 68, 0.1);
  border-color: rgba(239, 68, 68, 0.3);
}

.manager-footer {
  padding: 16px 24px;
  border-top: 1px solid rgba(71, 85, 105, 0.5);
  background: linear-gradient(180deg, rgba(30, 41, 59, 0.4) 0%, rgba(30, 41, 59, 0.8) 100%);
}

/* ============ 编辑弹窗 ============ */
.edit-modal {
  display: flex;
  flex-direction: column;
  width: 800px;
  max-width: 95vw;
  max-height: 90vh;
  background: linear-gradient(180deg, #1e293b 0%, #0f172a 100%);
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 16px;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
  overflow: hidden;
}

.edit-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid rgba(71, 85, 105, 0.5);
  background: linear-gradient(180deg, rgba(30, 41, 59, 0.8) 0%, rgba(30, 41, 59, 0.4) 100%);
}

.edit-modal-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 600;
  color: #f1f5f9;
  margin: 0;
}

.edit-modal-title span:first-child {
  font-size: 20px;
  color: #3b82f6;
}

.edit-modal-content {
  flex: 1;
  overflow-x: hidden;
  overflow-y: auto;
  padding: 24px;
}

.edit-modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid rgba(71, 85, 105, 0.5);
  background: linear-gradient(180deg, rgba(30, 41, 59, 0.4) 0%, rgba(30, 41, 59, 0.8) 100%);
}

/* 表单区块 */
.form-section {
  margin-bottom: 24px;
  overflow: hidden;
}

.form-section:last-child {
  margin-bottom: 0;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(71, 85, 105, 0.3);
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.form-group {
  margin-bottom: 16px;
}

.form-group:last-child {
  margin-bottom: 0;
}

.form-group.inline {
  display: flex;
  align-items: center;
}

.form-group label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: #94a3b8;
  margin-bottom: 8px;
}

.required {
  color: #ef4444;
}

.input-with-prefix {
  display: flex;
  align-items: center;
  width: 100%;
}

.input-prefix {
  padding: 10px 12px;
  font-size: 14px;
  font-family: 'Monaco', 'Menlo', monospace;
  color: #64748b;
  background: rgba(51, 65, 85, 0.5);
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-right: none;
  border-radius: 8px 0 0 8px;
  flex-shrink: 0;
}

.input-with-prefix .property-input {
  border-radius: 0 8px 8px 0;
  flex: 1;
  min-width: 0;
}

.property-input {
  width: 100%;
  min-width: 0;
  padding: 10px 14px;
  font-size: 14px;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 8px;
  color: #f1f5f9;
  outline: none;
  transition: all 0.2s;
  box-sizing: border-box;
}

.property-input:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
}

.property-input::placeholder {
  color: #64748b;
}

.property-input:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 类型选择器 */
.type-selector {
  display: flex;
  flex-wrap: nowrap;
  gap: 8px;
  overflow: hidden;
}

.type-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 500;
  color: #94a3b8;
  background: rgba(15, 23, 42, 0.5);
  border: 1px solid rgba(71, 85, 105, 0.4);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.type-btn:hover {
  color: #e2e8f0;
  border-color: rgba(71, 85, 105, 0.6);
}

.type-btn.active {
  color: #3b82f6;
  background: rgba(59, 130, 246, 0.1);
  border-color: rgba(59, 130, 246, 0.5);
}

/* 类型配置区域 */
.type-config {
  padding: 16px;
  background: rgba(15, 23, 42, 0.5);
  border: 1px solid rgba(71, 85, 105, 0.4);
  border-radius: 12px;
}

.config-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.config-hint {
  font-size: 13px;
  color: #64748b;
}

.add-item-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 500;
  color: #3b82f6;
  background: rgba(59, 130, 246, 0.1);
  border: 1px solid rgba(59, 130, 246, 0.3);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.add-item-btn:hover {
  background: rgba(59, 130, 246, 0.2);
  border-color: rgba(59, 130, 246, 0.5);
}

/* 枚举选项 */
.enum-options {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.enum-header {
  display: grid;
  grid-template-columns: 1fr 1fr 36px;
  gap: 12px;
  font-size: 12px;
  font-weight: 500;
  color: #64748b;
  padding: 0 4px;
}

.enum-option-row {
  display: grid;
  grid-template-columns: 1fr 1fr 36px;
  gap: 12px;
  align-items: center;
}

.remove-item-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  font-size: 14px;
  color: #64748b;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
}

.remove-item-btn:hover:not(:disabled) {
  color: #ef4444;
  background: rgba(239, 68, 68, 0.1);
  border-color: rgba(239, 68, 68, 0.3);
}

.remove-item-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.empty-config {
  padding: 24px;
  text-align: center;
  font-size: 13px;
  color: #64748b;
  background: rgba(30, 41, 59, 0.5);
  border: 1px dashed rgba(71, 85, 105, 0.4);
  border-radius: 8px;
}

/* 数组类型切换 */
.array-type-switch {
  display: flex;
  gap: 0;
  margin-bottom: 16px;
  border: 1px solid rgba(71, 85, 105, 0.4);
  border-radius: 8px;
  overflow: hidden;
}

.type-switch-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px 16px;
  font-size: 13px;
  font-weight: 500;
  color: #94a3b8;
  background: rgba(30, 41, 59, 0.5);
  border: none;
  cursor: pointer;
  transition: all 0.2s;
}

.type-switch-btn:not(:last-child) {
  border-right: 1px solid rgba(71, 85, 105, 0.4);
}

.type-switch-btn:hover {
  color: #e2e8f0;
}

.type-switch-btn.active {
  color: #3b82f6;
  background: rgba(59, 130, 246, 0.1);
}

.simple-type-select {
  margin-top: 12px;
}

.simple-type-select label {
  display: block;
  font-size: 13px;
  color: #94a3b8;
  margin-bottom: 8px;
}

/* 对象字段配置 */
.object-fields-config {
  margin-top: 12px;
}

.fields-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.fields-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.fields-list-header {
  display: grid;
  grid-template-columns: 1fr 1fr 110px 36px;
  gap: 12px;
  font-size: 12px;
  font-weight: 500;
  color: #64748b;
  padding: 0 4px;
}

.field-row {
  display: grid;
  grid-template-columns: 1fr 1fr 110px 36px;
  gap: 12px;
  align-items: center;
}

.field-type-select {
  padding: 10px 8px;
  min-width: 0;
}

/* 默认值配置 */
.default-value-trigger {
  width: 100%;
}

.default-value-btn {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 14px 18px;
  font-size: 14px;
  color: #94a3b8;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
  text-align: left;
}

.default-value-btn:hover {
  border-color: rgba(71, 85, 105, 0.8);
  background: rgba(15, 23, 42, 0.8);
}

.default-value-btn.configured {
  color: #e2e8f0;
  border-color: rgba(59, 130, 246, 0.4);
  background: rgba(59, 130, 246, 0.05);
}

.default-value-btn .i-carbon-settings-adjust {
  font-size: 20px;
  color: #3b82f6;
  flex-shrink: 0;
}

.default-value-summary {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.default-value-btn .i-carbon-chevron-right {
  font-size: 16px;
  color: #64748b;
  flex-shrink: 0;
}

/* 复选框 */
.checkbox-label {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}

.checkbox-input {
  display: none;
}

.checkbox-custom {
  width: 20px;
  height: 20px;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 4px;
  position: relative;
  transition: all 0.2s;
}

.checkbox-input:checked + .checkbox-custom {
  background: #3b82f6;
  border-color: #3b82f6;
}

.checkbox-input:checked + .checkbox-custom::after {
  content: '';
  position: absolute;
  left: 6px;
  top: 2px;
  width: 5px;
  height: 10px;
  border: solid #fff;
  border-width: 0 2px 2px 0;
  transform: rotate(45deg);
}

.checkbox-text {
  font-size: 14px;
  color: #e2e8f0;
}

/* 按钮 */
.btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px 20px;
  font-size: 14px;
  font-weight: 500;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-primary {
  color: #fff;
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.3);
}

.btn-primary:hover {
  background: linear-gradient(135deg, #60a5fa 0%, #3b82f6 100%);
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.4);
  transform: translateY(-1px);
}

.btn-secondary {
  color: #e2e8f0;
  background: rgba(51, 65, 85, 0.5);
  border: 1px solid rgba(71, 85, 105, 0.5);
}

.btn-secondary:hover {
  background: rgba(51, 65, 85, 0.8);
  border-color: rgba(71, 85, 105, 0.8);
}

/* 动画 */
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;
}

.modal-enter-active .variable-manager,
.modal-enter-active .edit-modal,
.modal-leave-active .variable-manager,
.modal-leave-active .edit-modal {
  transition: transform 0.2s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-from .variable-manager,
.modal-enter-from .edit-modal,
.modal-leave-to .variable-manager,
.modal-leave-to .edit-modal {
  transform: scale(0.95);
}
</style>
