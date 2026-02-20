<script setup lang="ts">
/**
 * 变量编辑弹窗组件
 * 用于添加或编辑自定义变量
 */

import type { CustomVariable, VariableType, EnumOption, VariableFieldSchema } from '~/types/data-context'

const props = defineProps<{
  visible: boolean
  variable?: CustomVariable | null
}>()

const emit = defineEmits<{
  close: []
  save: [variable: CustomVariable]
}>()

const isEdit = computed(() => !!props.variable)

const form = ref<CustomVariable>({
  key: '',
  label: '',
  type: 'string',
  description: '',
  defaultValue: undefined,
  i18n: false,
  enumOptions: [],
  itemType: 'string',
  itemSchema: [],
  fields: [],
})

const showDefaultValueEditor = ref(false)

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      if (props.variable) {
        form.value = JSON.parse(JSON.stringify(props.variable))
      } else {
        form.value = {
          key: '',
          label: '',
          type: 'string',
          description: '',
          defaultValue: undefined,
          i18n: false,
          enumOptions: [],
          itemType: 'string',
          itemSchema: [],
          fields: [],
        }
      }
    }
  },
  { immediate: true }
)

const keyError = ref('')
const labelError = ref('')

function validateKey(value: string): boolean {
  if (!value) {
    keyError.value = '键名不能为空'
    return false
  }
  if (!/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(value)) {
    keyError.value = '键名只能包含字母、数字和下划线，且不能以数字开头'
    return false
  }
  keyError.value = ''
  return true
}

function validateLabel(value: string): boolean {
  if (!value) {
    labelError.value = '显示名称不能为空'
    return false
  }
  labelError.value = ''
  return true
}

function handleTypeChange(type: VariableType) {
  form.value.type = type
  form.value.defaultValue = undefined
  
  if (type === 'enum') {
    form.value.enumOptions = form.value.enumOptions?.length ? form.value.enumOptions : []
  }
  if (type === 'array') {
    form.value.itemType = 'string'
    form.value.itemSchema = []
  }
  if (type === 'object') {
    form.value.fields = form.value.fields?.length ? form.value.fields : []
  }
}

function handleTypeConfigChange(config: {
  enumOptions?: EnumOption[]
  itemType?: VariableType
  itemSchema?: VariableFieldSchema[]
  fields?: VariableFieldSchema[]
}) {
  if (config.enumOptions !== undefined) {
    form.value.enumOptions = config.enumOptions
  }
  if (config.itemType !== undefined) {
    form.value.itemType = config.itemType
  }
  if (config.itemSchema !== undefined) {
    form.value.itemSchema = config.itemSchema
  }
  if (config.fields !== undefined) {
    form.value.fields = config.fields
  }
}

function handleDefaultValueChange(value: any) {
  form.value.defaultValue = value
}

function handleSave() {
  const keyValid = validateKey(form.value.key)
  const labelValid = validateLabel(form.value.label)
  
  if (!keyValid || !labelValid) {
    return
  }
  
  const result: CustomVariable = {
    key: form.value.key,
    label: form.value.label,
    type: form.value.type,
  }
  
  if (form.value.description) {
    result.description = form.value.description
  }
  
  if (form.value.defaultValue !== undefined) {
    result.defaultValue = form.value.defaultValue
  }
  
  if (form.value.i18n) {
    result.i18n = true
  }
  
  if (form.value.type === 'enum' && form.value.enumOptions?.length) {
    result.enumOptions = form.value.enumOptions
  }
  
  if (form.value.type === 'array') {
    if (form.value.itemSchema?.length) {
      result.itemSchema = form.value.itemSchema
    } else if (form.value.itemType) {
      result.itemType = form.value.itemType
    }
  }
  
  if (form.value.type === 'object' && form.value.fields?.length) {
    result.fields = form.value.fields
  }
  
  emit('save', result)
}

const needsTypeConfig = computed(() => {
  return ['enum', 'array', 'object'].includes(form.value.type)
})
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="visible" class="modal-overlay" @click.self="emit('close')">
        <div class="edit-modal">
          <div class="modal-header">
            <h2 class="modal-title">
              <span :class="isEdit ? 'i-carbon-edit' : 'i-carbon-add'"></span>
              {{ isEdit ? '编辑变量' : '添加变量' }}
            </h2>
            <button class="close-btn" @click="emit('close')">
              <span class="i-carbon-close"></span>
            </button>
          </div>

          <div class="modal-content">
            <div class="form-section">
              <h3 class="section-title">基本信息</h3>
              
              <div class="form-group">
                <label class="form-label">
                  键名 <span class="required">*</span>
                </label>
                <div class="input-wrapper">
                  <span class="input-prefix">site.</span>
                  <input
                    v-model="form.key"
                    type="text"
                    class="form-input with-prefix"
                    :class="{ error: keyError }"
                    placeholder="variableName"
                    :disabled="isEdit"
                    @blur="validateKey(form.key)"
                  />
                </div>
                <p v-if="keyError" class="form-error">{{ keyError }}</p>
                <p v-else class="form-hint">变量在模板中的引用名称</p>
              </div>

              <div class="form-group">
                <label class="form-label">
                  显示名称 <span class="required">*</span>
                </label>
                <input
                  v-model="form.label"
                  type="text"
                  class="form-input"
                  :class="{ error: labelError }"
                  placeholder="输入显示名称"
                  @blur="validateLabel(form.label)"
                />
                <p v-if="labelError" class="form-error">{{ labelError }}</p>
                <p v-else class="form-hint">在编辑器中显示的名称</p>
              </div>

              <div class="form-group">
                <label class="form-label">描述</label>
                <textarea
                  v-model="form.description"
                  class="form-textarea"
                  placeholder="输入变量描述（可选）"
                  rows="2"
                ></textarea>
              </div>

              <div class="form-group inline">
                <label class="checkbox-label">
                  <input
                    v-model="form.i18n"
                    type="checkbox"
                    class="checkbox-input"
                  />
                  <span class="checkbox-custom"></span>
                  <span class="checkbox-text">支持多语言</span>
                </label>
              </div>
            </div>

            <div class="form-section">
              <h3 class="section-title">变量类型</h3>
              <VariableTypeSelector
                :value="form.type"
                @change="handleTypeChange"
              />
            </div>

            <div v-if="needsTypeConfig" class="form-section">
              <h3 class="section-title">类型配置</h3>
              <VariableTypeConfig
                :type="form.type"
                :enum-options="form.enumOptions"
                :item-type="form.itemType"
                :item-schema="form.itemSchema"
                :fields="form.fields"
                @change="handleTypeConfigChange"
              />
            </div>

            <div class="form-section">
              <h3 class="section-title">默认值</h3>
              <div class="default-value-trigger">
                <button
                  class="btn btn-secondary"
                  @click="showDefaultValueEditor = true"
                >
                  <span class="i-carbon-edit"></span>
                  {{ form.defaultValue !== undefined ? '修改默认值' : '设置默认值' }}
                </button>
                <span v-if="form.defaultValue !== undefined" class="value-preview">
                  已设置
                </span>
              </div>
            </div>
          </div>

          <div class="modal-footer">
            <button class="btn btn-secondary" @click="emit('close')">
              取消
            </button>
            <button class="btn btn-primary" @click="handleSave">
              <span class="i-carbon-checkmark"></span>
              {{ isEdit ? '保存修改' : '添加变量' }}
            </button>
          </div>

          <DefaultValueEditor
            :visible="showDefaultValueEditor"
            :type="form.type"
            :value="form.defaultValue"
            :enum-options="form.enumOptions"
            :item-type="form.itemType"
            :item-schema="form.itemSchema"
            :fields="form.fields"
            @close="showDefaultValueEditor = false"
            @save="handleDefaultValueChange"
          />
        </div>
      </div>
    </Transition>
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
  z-index: 1001;
}

.edit-modal {
  display: flex;
  flex-direction: column;
  width: 560px;
  max-width: 90vw;
  max-height: 85vh;
  background: linear-gradient(180deg, #1e293b 0%, #0f172a 100%);
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 16px;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
  overflow: hidden;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid rgba(71, 85, 105, 0.5);
  background: linear-gradient(180deg, rgba(30, 41, 59, 0.8) 0%, rgba(30, 41, 59, 0.4) 100%);
}

.modal-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 600;
  color: #f1f5f9;
  margin: 0;
}

.modal-title span {
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

.modal-content {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
}

.form-section {
  margin-bottom: 24px;
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
  margin: 0 0 16px;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(71, 85, 105, 0.3);
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

.form-label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: #e2e8f0;
  margin-bottom: 8px;
}

.required {
  color: #ef4444;
}

.input-wrapper {
  display: flex;
  align-items: center;
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
}

.form-input {
  width: 100%;
  padding: 10px 14px;
  font-size: 14px;
  color: #f1f5f9;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 8px;
  outline: none;
  transition: all 0.2s;
}

.form-input.with-prefix {
  border-radius: 0 8px 8px 0;
  font-family: 'Monaco', 'Menlo', monospace;
}

.form-input:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
}

.form-input.error {
  border-color: #ef4444;
}

.form-input:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.form-textarea {
  width: 100%;
  padding: 10px 14px;
  font-size: 14px;
  color: #f1f5f9;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(71, 85, 105, 0.5);
  border-radius: 8px;
  outline: none;
  resize: vertical;
  min-height: 60px;
  transition: all 0.2s;
}

.form-textarea:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
}

.form-error {
  font-size: 12px;
  color: #ef4444;
  margin: 6px 0 0;
}

.form-hint {
  font-size: 12px;
  color: #64748b;
  margin: 6px 0 0;
}

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

.default-value-trigger {
  display: flex;
  align-items: center;
  gap: 12px;
}

.value-preview {
  font-size: 13px;
  color: #22c55e;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid rgba(71, 85, 105, 0.5);
  background: linear-gradient(180deg, rgba(30, 41, 59, 0.4) 0%, rgba(30, 41, 59, 0.8) 100%);
}

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

.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;
}

.modal-enter-active .edit-modal,
.modal-leave-active .edit-modal {
  transition: transform 0.2s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-from .edit-modal,
.modal-leave-to .edit-modal {
  transform: scale(0.95);
}
</style>
