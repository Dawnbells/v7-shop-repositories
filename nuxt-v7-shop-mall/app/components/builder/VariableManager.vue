<script setup lang="ts">
/**
 * 变量管理弹窗组件
 * 用于管理主题自定义变量（添加、编辑、删除）
 * 支持多种变量类型：string、number、boolean、color、image、richtext、enum、array、object
 * 支持 enum 枚举选项定义
 * 支持 array/object 子类型嵌套定义
 */

import type {
  CustomVariable,
  VariableType,
  BasicVariableType,
  EnumOption,
  VariableFieldSchema,
  I18nDefaultValue,
} from "~/types/data-context";
import {
  BASIC_VARIABLE_TYPES,
  ALL_VARIABLE_TYPES,
} from "~/types/data-context";

// Props
defineProps<{
  visible: boolean;
}>();

// Emits
const emit = defineEmits<{
  (e: "close"): void;
}>();

// 主题状态
const { globalData, addGlobalVariable, updateGlobalVariable, removeGlobalVariable } = useThemeSchema();

// 变量列表
const variables = computed(() => globalData.value.variables || []);

// ============ 默认值配置弹窗状态 ============

const showDefaultValueModal = ref(false);

// 打开默认值配置弹窗
function openDefaultValueModal() {
  showDefaultValueModal.value = true;
}

// 处理默认值配置保存
function handleDefaultValueSave(data: {
  defaultValue: any;
  i18n: boolean;
  i18nLanguages: number[];
  i18nDefaults: I18nDefaultValue[];
}) {
  editForm.value.defaultValue = data.defaultValue;
  editForm.value.i18n = data.i18n;
  editForm.value.i18nLanguages = data.i18nLanguages;
  editForm.value.i18nDefaults = data.i18nDefaults;
}

// 默认值显示摘要
const defaultValueSummary = computed(() => {
  const type = editForm.value.type;
  const value = editForm.value.defaultValue;
  const i18n = editForm.value.i18n;
  const i18nCount = editForm.value.i18nLanguages?.length || 0;
  
  if (i18n && i18nCount > 0) {
    return `已配置 ${i18nCount} 种语言`;
  }
  
  if (value === undefined || value === null || value === "") {
    return "未配置";
  }
  
  switch (type) {
    case "string":
    case "image":
    case "richtext":
      return String(value).length > 20 ? String(value).slice(0, 20) + "..." : String(value);
    case "number":
      return String(value);
    case "boolean":
      return value ? "是" : "否";
    case "color":
      return value;
    case "enum":
      const opt = editForm.value.enumOptions?.find(o => o.value === value);
      return opt ? opt.label : String(value);
    case "array":
      return `${(value as any[])?.length || 0} 个元素`;
    case "object":
      return "已配置";
    default:
      return "已配置";
  }
});

// ============ 编辑弹窗状态 ============

// 是否显示编辑弹窗
const showEditModal = ref(false);

// 编辑模式：'add' | 'edit'
const editMode = ref<"add" | "edit">("add");

// 正在编辑的变量原始键名（用于更新时查找）
const editingKey = ref<string | null>(null);

// 编辑表单数据
const editForm = ref<Partial<CustomVariable>>({
  key: "",
  label: "",
  type: "string",
  defaultValue: "",
  description: "",
  i18n: false,
  i18nLanguages: [],
  i18nDefaults: [],
  enumOptions: [],
  itemType: "string",
  itemSchema: [],
  fields: [],
});

// 数组元素是否为复杂类型
const arrayItemIsComplex = ref(false);

// 变量类型选项
const variableTypes: Array<{ value: VariableType; label: string; icon: string; description: string }> = [
  { value: "string", label: "文本", icon: "i-carbon-text-font", description: "单行文本" },
  { value: "number", label: "数字", icon: "i-carbon-hashtag", description: "数值" },
  { value: "boolean", label: "开关", icon: "i-carbon-toggle-off", description: "是/否" },
  { value: "color", label: "颜色", icon: "i-carbon-color-palette", description: "颜色值" },
  { value: "image", label: "图片", icon: "i-carbon-image", description: "图片 URL" },
  { value: "richtext", label: "富文本", icon: "i-carbon-text-align-left", description: "多行富文本" },
  { value: "enum", label: "枚举", icon: "i-carbon-list-checked", description: "固定选项" },
  { value: "array", label: "数组", icon: "i-carbon-list", description: "列表数据" },
  { value: "object", label: "对象", icon: "i-carbon-json", description: "结构化数据" },
];

// 基本类型（用于孙类型选择）
const basicTypes: BasicVariableType[] = BASIC_VARIABLE_TYPES;

// 所有类型（用于子类型选择）
const allTypes: VariableType[] = ALL_VARIABLE_TYPES;

// 获取变量类型的显示信息
function getTypeInfo(type: VariableType) {
  return variableTypes.find((t) => t.value === type) || variableTypes[0];
}

// 根据类型获取默认值
function getDefaultValueByType(type: VariableType): any {
  switch (type) {
    case "string":
      return "";
    case "number":
      return 0;
    case "boolean":
      return false;
    case "color":
      return "#000000";
    case "image":
      return "";
    case "richtext":
      return "";
    case "enum":
      return "";
    case "array":
      return [];
    case "object":
      return {};
    default:
      return "";
  }
}

// ============ 打开编辑弹窗 ============

// 打开添加弹窗
function openAddModal() {
  editMode.value = "add";
  editingKey.value = null;
  resetEditForm();
  showEditModal.value = true;
}

// 打开编辑弹窗
function openEditModal(variable: CustomVariable) {
  editMode.value = "edit";
  editingKey.value = variable.key;
  
  // 深拷贝变量数据到表单
  editForm.value = {
    key: variable.key,
    label: variable.label,
    type: variable.type,
    defaultValue: variable.defaultValue,
    description: variable.description || "",
    i18n: variable.i18n || false,
    i18nLanguages: variable.i18nLanguages ? [...variable.i18nLanguages] : [],
    i18nDefaults: variable.i18nDefaults ? JSON.parse(JSON.stringify(variable.i18nDefaults)) : [],
    enumOptions: variable.enumOptions ? JSON.parse(JSON.stringify(variable.enumOptions)) : [],
    itemType: variable.itemType || "string",
    itemSchema: variable.itemSchema ? JSON.parse(JSON.stringify(variable.itemSchema)) : [],
    fields: variable.fields ? JSON.parse(JSON.stringify(variable.fields)) : [],
  };
  
  // 判断数组元素是否为复杂类型
  arrayItemIsComplex.value = !!(variable.itemSchema && variable.itemSchema.length > 0);
  
  showEditModal.value = true;
}

// 关闭编辑弹窗
function closeEditModal() {
  showEditModal.value = false;
  editMode.value = "add";
  editingKey.value = null;
  resetEditForm();
}

// 重置编辑表单
function resetEditForm() {
  editForm.value = {
    key: "",
    label: "",
    type: "string",
    defaultValue: "",
    description: "",
    i18n: false,
    i18nLanguages: [],
    i18nDefaults: [],
    enumOptions: [],
    itemType: "string",
    itemSchema: [],
    fields: [],
  };
  arrayItemIsComplex.value = false;
}

// ============ 类型变更处理 ============

function handleTypeChange(type: VariableType) {
  const oldType = editForm.value.type;
  editForm.value.type = type;
  
  // 仅在类型真正改变时重置相关字段
  if (oldType !== type) {
    editForm.value.defaultValue = getDefaultValueByType(type);
    
    if (type === "enum") {
      editForm.value.enumOptions = editForm.value.enumOptions?.length 
        ? editForm.value.enumOptions 
        : [{ value: "", label: "" }];
    }
    
    if (type === "array") {
      arrayItemIsComplex.value = false;
      editForm.value.itemType = "string";
      editForm.value.itemSchema = [];
    }
    
    if (type === "object") {
      editForm.value.fields = editForm.value.fields?.length 
        ? editForm.value.fields 
        : [];
    }
  }
}

// ============ 枚举选项管理 ============

function addEnumOption() {
  if (!editForm.value.enumOptions) {
    editForm.value.enumOptions = [];
  }
  editForm.value.enumOptions.push({ value: "", label: "" });
}

function removeEnumOption(index: number) {
  editForm.value.enumOptions?.splice(index, 1);
}

// ============ 数组元素类型管理 ============

function handleArrayItemTypeChange(isComplex: boolean) {
  arrayItemIsComplex.value = isComplex;
  if (isComplex) {
    editForm.value.itemType = undefined;
    if (!editForm.value.itemSchema?.length) {
      editForm.value.itemSchema = [];
    }
  } else {
    editForm.value.itemType = "string";
    editForm.value.itemSchema = [];
  }
}

function addArrayItemField() {
  if (!editForm.value.itemSchema) {
    editForm.value.itemSchema = [];
  }
  editForm.value.itemSchema.push({
    key: "",
    label: "",
    type: "string",
  });
}

function removeArrayItemField(index: number) {
  editForm.value.itemSchema?.splice(index, 1);
}

// ============ 对象字段管理 ============

function addObjectField() {
  if (!editForm.value.fields) {
    editForm.value.fields = [];
  }
  editForm.value.fields.push({
    key: "",
    label: "",
    type: "string",
  });
}

function removeObjectField(index: number) {
  editForm.value.fields?.splice(index, 1);
}


// ============ 表单验证 ============

function validateForm(): string | null {
  if (!editForm.value.key) {
    return "请填写变量键名";
  }
  
  if (!editForm.value.label) {
    return "请填写显示名称";
  }
  
  // 检查键名格式
  if (!/^[a-zA-Z][a-zA-Z0-9_]*$/.test(editForm.value.key)) {
    return "键名只能包含字母、数字、下划线，且必须以字母开头";
  }
  
  // 添加模式下检查键名是否已存在
  if (editMode.value === "add") {
    if (variables.value.some((v) => v.key === editForm.value.key)) {
      return "该键名已存在";
    }
  }
  
  // 编辑模式下，如果修改了键名，检查新键名是否已存在
  if (editMode.value === "edit" && editingKey.value !== editForm.value.key) {
    if (variables.value.some((v) => v.key === editForm.value.key)) {
      return "该键名已存在";
    }
  }
  
  // 验证枚举选项
  if (editForm.value.type === "enum") {
    const options = editForm.value.enumOptions || [];
    if (options.length === 0) {
      return "枚举类型至少需要一个选项";
    }
    for (const opt of options) {
      if (!opt.value || !opt.label) {
        return "枚举选项的值和标签都不能为空";
      }
    }
  }
  
  // 验证数组元素结构
  if (editForm.value.type === "array" && arrayItemIsComplex.value) {
    const schema = editForm.value.itemSchema || [];
    if (schema.length === 0) {
      return "数组元素结构至少需要一个字段";
    }
    for (const field of schema) {
      if (!field.key || !field.label) {
        return "数组元素字段的键名和标签都不能为空";
      }
    }
  }
  
  // 验证对象字段
  if (editForm.value.type === "object") {
    const fields = editForm.value.fields || [];
    if (fields.length === 0) {
      return "对象类型至少需要一个字段";
    }
    for (const field of fields) {
      if (!field.key || !field.label) {
        return "对象字段的键名和标签都不能为空";
      }
    }
  }
  
  return null;
}

// ============ 保存变量 ============

function handleSave() {
  const error = validateForm();
  if (error) {
    alert(error);
    return;
  }
  
  const variable: CustomVariable = {
    key: editForm.value.key!,
    label: editForm.value.label!,
    type: editForm.value.type || "string",
    defaultValue: editForm.value.defaultValue,
    description: editForm.value.description || undefined,
    i18n: editForm.value.i18n || undefined,
  };
  
  // 添加多语言配置
  if (variable.i18n) {
    const languages = editForm.value.i18nLanguages || [];
    const defaults = editForm.value.i18nDefaults || [];
    if (languages.length > 0) {
      variable.i18nLanguages = languages;
      variable.i18nDefaults = defaults.filter(d => languages.includes(d.languageId));
    }
  }
  
  // 添加类型特定字段
  if (variable.type === "enum") {
    variable.enumOptions = editForm.value.enumOptions?.filter(
      (opt) => opt.value && opt.label
    );
    if (variable.enumOptions && variable.enumOptions.length > 0 && !variable.defaultValue) {
      variable.defaultValue = variable.enumOptions[0].value;
    }
  }
  
  if (variable.type === "array") {
    if (arrayItemIsComplex.value) {
      variable.itemSchema = editForm.value.itemSchema?.filter(
        (f) => f.key && f.label
      );
    } else {
      variable.itemType = editForm.value.itemType;
    }
  }
  
  if (variable.type === "object") {
    variable.fields = editForm.value.fields?.filter((f) => f.key && f.label);
  }
  
  let success = false;
  
  if (editMode.value === "add") {
    success = addGlobalVariable(variable);
  } else {
    // 编辑模式
    if (editingKey.value === variable.key) {
      // 键名未变，直接更新
      success = updateGlobalVariable(editingKey.value, variable);
    } else {
      // 键名已变，需要删除旧的并添加新的
      removeGlobalVariable(editingKey.value!);
      success = addGlobalVariable(variable);
    }
  }
  
  if (success) {
    closeEditModal();
  } else {
    alert("保存失败");
  }
}

// ============ 删除变量 ============

function handleDeleteVariable(key: string, label: string) {
  if (confirm(`确定要删除变量「${label}」吗？`)) {
    removeGlobalVariable(key);
  }
}

// ============ 关闭管理弹窗 ============

function handleClose() {
  closeEditModal();
  emit("close");
}

// 获取变量的额外信息描述
function getVariableExtraInfo(variable: CustomVariable): string {
  if (variable.type === "enum" && variable.enumOptions) {
    return `${variable.enumOptions.length} 个选项`;
  }
  if (variable.type === "array") {
    if (variable.itemSchema && variable.itemSchema.length > 0) {
      return `对象数组 (${variable.itemSchema.length} 个字段)`;
    }
    if (variable.itemType) {
      return `${getTypeInfo(variable.itemType).label}数组`;
    }
  }
  if (variable.type === "object" && variable.fields) {
    return `${variable.fields.length} 个字段`;
  }
  return "";
}

// 弹窗标题
const editModalTitle = computed(() => {
  return editMode.value === "add" ? "添加变量" : "编辑变量";
});
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
            <!-- 变量列表 -->
            <div class="variable-list">
              <div v-if="variables.length === 0" class="empty-state">
                <span class="i-carbon-parameter text-4xl text-gray-600 mb-3"></span>
                <p class="text-gray-400">暂无自定义变量</p>
                <p class="text-sm text-gray-600">点击下方按钮添加变量</p>
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
              <span class="i-carbon-add mr-1"></span>
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
            <h3 class="edit-modal-title">{{ editModalTitle }}</h3>
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

              <!-- ============ 枚举类型配置 ============ -->
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

              <!-- ============ 数组类型配置 ============ -->
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
                    <option v-for="t in basicTypes" :key="t" :value="t">
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
                        <option v-for="t in allTypes" :key="t" :value="t">
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

              <!-- ============ 对象类型配置 ============ -->
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
                      <option v-for="t in allTypes" :key="t" :value="t">
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
                  @click="openDefaultValueModal"
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
            </div>
          </div>

          <!-- 编辑弹窗底部 -->
          <div class="edit-modal-footer">
            <button class="btn btn-secondary" @click="closeEditModal">取消</button>
            <button class="btn btn-primary" @click="handleSave">
              <span class="i-carbon-checkmark mr-1"></span>
              保存
            </button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- 默认值配置弹窗 -->
    <DefaultValueModal
      v-model="showDefaultValueModal"
      :variable-type="editForm.type || 'string'"
      :default-value="editForm.defaultValue"
      :i18n="editForm.i18n || false"
      :i18n-languages="editForm.i18nLanguages || []"
      :i18n-defaults="editForm.i18nDefaults || []"
      :enum-options="editForm.enumOptions"
      :item-type="editForm.itemType"
      :item-schema="editForm.itemSchema"
      :fields="editForm.fields"
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
  width: 560px;
  max-width: 90vw;
  max-height: 80vh;
  background-color: #1e293b;
  border-radius: 12px;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
  overflow: hidden;
}

.manager-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #334155;
}

.manager-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
  color: #e2e8f0;
}

.close-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  color: #94a3b8;
  background: none;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.close-btn:hover {
  color: #e2e8f0;
  background-color: #334155;
}

.manager-content {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
}

.variable-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  text-align: center;
}

.variable-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background-color: #0f172a;
  border: 1px solid #334155;
  border-radius: 8px;
  transition: border-color 0.2s;
}

.variable-item:hover {
  border-color: #475569;
}

.variable-info {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  flex: 1;
  min-width: 0;
}

.variable-type-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  font-size: 18px;
  color: #3b82f6;
  background-color: rgba(59, 130, 246, 0.1);
  border-radius: 8px;
  flex-shrink: 0;
}

.variable-details {
  flex: 1;
  min-width: 0;
}

.variable-name {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
}

.variable-label {
  font-size: 14px;
  font-weight: 500;
  color: #e2e8f0;
}

.variable-key {
  font-size: 12px;
  font-family: "Monaco", "Menlo", monospace;
  color: #64748b;
}

.variable-desc {
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.variable-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}

.meta-tag {
  padding: 2px 6px;
  font-size: 10px;
  color: #94a3b8;
  background-color: #334155;
  border-radius: 4px;
}

.meta-tag.extra {
  color: #60a5fa;
  background-color: rgba(96, 165, 250, 0.1);
}

.meta-tag.i18n {
  color: #22c55e;
  background-color: rgba(34, 197, 94, 0.1);
}

.variable-actions {
  display: flex;
  gap: 4px;
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  font-size: 16px;
  color: #64748b;
  background: none;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.action-btn:hover {
  color: #3b82f6;
  background-color: rgba(59, 130, 246, 0.1);
}

.action-btn.danger:hover {
  color: #ef4444;
  background-color: rgba(239, 68, 68, 0.1);
}

.manager-footer {
  padding: 16px 20px;
  border-top: 1px solid #334155;
}

/* ============ 编辑弹窗 ============ */
.edit-modal {
  display: flex;
  flex-direction: column;
  width: 800px;
  max-width: 95vw;
  max-height: 90vh;
  background-color: #1e293b;
  border-radius: 12px;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
  overflow: hidden;
}

.edit-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #334155;
}

.edit-modal-title {
  font-size: 16px;
  font-weight: 600;
  color: #e2e8f0;
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
  gap: 8px;
  padding: 16px 20px;
  border-top: 1px solid #334155;
}

/* 表单区块 */
.form-section {
  margin-bottom: 20px;
  overflow: hidden;
}

.form-section:last-child {
  margin-bottom: 0;
}

.section-title {
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 12px;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.form-group {
  margin-bottom: 12px;
}

.form-group:last-child {
  margin-bottom: 0;
}

.form-group label {
  display: block;
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 6px;
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
  padding: 8px 10px;
  font-size: 14px;
  font-family: "Monaco", "Menlo", monospace;
  color: #64748b;
  background-color: #334155;
  border: 1px solid #475569;
  border-right: none;
  border-radius: 6px 0 0 6px;
  flex-shrink: 0;
}

.input-with-prefix .property-input {
  border-radius: 0 6px 6px 0;
  flex: 1;
  min-width: 0;
}

.property-input {
  width: 100%;
  min-width: 0;
  padding: 8px 12px;
  font-size: 14px;
  background-color: #0f172a;
  border: 1px solid #475569;
  border-radius: 6px;
  color: #e2e8f0;
  outline: none;
  transition: border-color 0.2s;
  box-sizing: border-box;
}

.property-input:focus {
  border-color: #3b82f6;
}

.property-input::placeholder {
  color: #64748b;
}

.property-textarea {
  min-height: 80px;
  resize: vertical;
}

/* 类型选择器 */
.type-selector {
  display: flex;
  flex-wrap: nowrap;
  gap: 6px;
  overflow: hidden;
}

.type-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 8px 10px;
  font-size: 12px;
  color: #94a3b8;
  background-color: #0f172a;
  border: 1px solid #334155;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.type-btn:hover {
  color: #e2e8f0;
  border-color: #475569;
}

.type-btn.active {
  color: #3b82f6;
  background-color: rgba(59, 130, 246, 0.1);
  border-color: #3b82f6;
}

/* 类型配置区域 */
.type-config {
  padding: 16px;
  background-color: #0f172a;
  border: 1px solid #334155;
  border-radius: 8px;
}

.type-config *,
.type-config *::before,
.type-config *::after {
  box-sizing: border-box;
}

.config-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.config-hint {
  font-size: 12px;
  color: #64748b;
}

.add-item-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  font-size: 12px;
  color: #3b82f6;
  background: none;
  border: 1px solid #3b82f6;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.add-item-btn:hover {
  background-color: rgba(59, 130, 246, 0.1);
}

/* 枚举选项 */
.enum-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.enum-header {
  display: grid;
  grid-template-columns: 1fr 1fr 36px;
  gap: 12px;
  font-size: 11px;
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
  background: none;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
}

.remove-item-btn:hover:not(:disabled) {
  color: #ef4444;
  background-color: rgba(239, 68, 68, 0.1);
}

.remove-item-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.empty-config {
  padding: 20px;
  text-align: center;
  font-size: 12px;
  color: #64748b;
  background-color: #1e293b;
  border: 1px dashed #334155;
  border-radius: 6px;
}

/* 数组类型切换 */
.array-type-switch {
  display: flex;
  gap: 0;
  margin-bottom: 12px;
  border: 1px solid #334155;
  border-radius: 6px;
  overflow: hidden;
}

.type-switch-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 12px;
  font-size: 13px;
  color: #94a3b8;
  background-color: #1e293b;
  border: none;
  cursor: pointer;
  transition: all 0.2s;
}

.type-switch-btn:not(:last-child) {
  border-right: 1px solid #334155;
}

.type-switch-btn:hover {
  color: #e2e8f0;
}

.type-switch-btn.active {
  color: #3b82f6;
  background-color: rgba(59, 130, 246, 0.1);
}

.simple-type-select {
  margin-top: 8px;
}

.simple-type-select label {
  display: block;
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 6px;
}

/* 对象字段配置 */
.object-fields-config {
  margin-top: 8px;
}

.fields-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.fields-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.fields-list-header {
  display: grid;
  grid-template-columns: 1fr 1fr 110px 36px;
  gap: 12px;
  font-size: 11px;
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
  padding: 8px 6px;
  min-width: 0;
}

/* 颜色输入 */
.color-input {
  display: flex;
  align-items: center;
  gap: 8px;
}

.color-input input[type="color"] {
  width: 40px;
  height: 40px;
  padding: 0;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  flex-shrink: 0;
}

.color-input .property-input {
  flex: 1;
}

/* 开关 */
.switch {
  position: relative;
  display: inline-block;
  width: 44px;
  height: 24px;
}

.switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.switch-slider {
  position: absolute;
  cursor: pointer;
  inset: 0;
  background-color: #334155;
  border-radius: 24px;
  transition: 0.2s;
}

.switch-slider:before {
  position: absolute;
  content: "";
  height: 18px;
  width: 18px;
  left: 3px;
  bottom: 3px;
  background-color: white;
  border-radius: 50%;
  transition: 0.2s;
}

.switch input:checked + .switch-slider {
  background-color: #3b82f6;
}

.switch input:checked + .switch-slider:before {
  transform: translateX(20px);
}

/* 复选框 */
.checkbox-label {
  display: flex !important;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.checkbox-label input[type="checkbox"] {
  width: 16px;
  height: 16px;
  accent-color: #3b82f6;
}

/* 按钮 */
.btn {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 8px 16px;
  font-size: 14px;
  font-weight: 500;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-primary {
  background-color: #3b82f6;
  color: white;
}

.btn-primary:hover {
  background-color: #2563eb;
}

.btn-secondary {
  background-color: #334155;
  color: #e2e8f0;
}

.btn-secondary:hover {
  background-color: #475569;
}

/* ============ 默认值配置样式 ============ */

.default-value-trigger {
  width: 100%;
}

.default-value-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 12px 16px;
  font-size: 14px;
  color: #94a3b8;
  background-color: #0f172a;
  border: 1px solid #334155;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  text-align: left;
}

.default-value-btn:hover {
  border-color: #475569;
  background-color: #1e293b;
}

.default-value-btn.configured {
  color: #e2e8f0;
  border-color: #3b82f6;
  background-color: rgba(59, 130, 246, 0.05);
}

.default-value-btn .i-carbon-settings-adjust {
  font-size: 18px;
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
