<script setup lang="ts">
/**
 * 属性面板 - 右侧
 * 编辑选中组件的属性和样式
 * 支持 Tab 切换（属性/样式/事件）、组件操作（复制/删除）、样式设备切换
 * 支持属性绑定模式（静态值/数据绑定）
 */

import type { PropBinding, StyleDeviceType } from "~/types/builder";
import { isStyleRef, isGlobalStyleRef, isVariableStyleRef } from "~/types/schema";
import { GLOBAL_STYLE_OPTIONS } from "~/types/theme";
import { STYLE_BREAKPOINTS, STYLE_DEVICE_LIST } from "~/constants";
import {
  useEditorDataContext,
  resolveExpression,
  getExpressionPreview,
  generatePageContextFields,
  generateVariableFields,
} from "~/composables/useDataContext";

// Tab 类型
type PanelTabKey = "props" | "style" | "events";

// 当前页面状态
const {
  selectedComponent,
  selectedComponentId,
  currentDevice,
  updateComponentProps,
  updateComponentStyle,
  updateComponentDeviceStyle,
  removeComponent,
  copyComponent,
} = useCurrentPage();

// 组件注册表
const { getComponentMeta } = useComponentRegistry();

// 主题状态（获取自定义变量和变量值）
const { variableSchema, variableValues, siteConfig } = useThemeSchema();

// 编辑器数据上下文
const editorDataContext = useEditorDataContext();

// 选中组件的元数据
const componentMeta = computed(() => {
  if (!selectedComponent.value) return null;
  return getComponentMeta(selectedComponent.value.type);
});

// 当前面板 Tab（记住上次停留，不随选中组件切换而重置）
const activeTab = ref<PanelTabKey>("props");

// 当前编辑的样式设备（默认通用）
const styleDevice = ref<StyleDeviceType>("base");

// 属性绑定模式记录（key -> 'static' | 'binding'）
const propBindingModes = ref<Record<string, "static" | "binding">>({});

// 样式引用模式记录（key -> 'static' | 'ref'）
const styleRefModes = ref<Record<string, "static" | "ref">>({});

// 属性表单数据
const propsForm = computed(() => {
  return selectedComponent.value?.props || {};
});

// 样式表单数据（当前设备）
const styleForm = computed<Record<string, any>>(() => {
  if (!selectedComponent.value) return {};
  const style = selectedComponent.value.style;
  if (styleDevice.value === "base") {
    return { ...style.base };
  }
  return {
    ...style.base,
    ...style[styleDevice.value],
  };
});

// 页面预设数据字段
const pageContextFields = computed(() => generatePageContextFields());

// 自定义变量字段
const variableFields = computed(() => {
  const variables = variableSchema.value || [];
  return generateVariableFields(variables);
});

// 可绑定字段（合并页面预设和自定义变量）
const bindableFields = computed(() => {
  return [...pageContextFields.value, ...variableFields.value];
});

// 是否有可绑定字段
const hasBindableFields = computed(() => bindableFields.value.length > 0);

// 是否有页面预设字段
const hasPageContextFields = computed(() => pageContextFields.value.length > 0);

// 是否有自定义变量字段
const hasVariableFields = computed(() => variableFields.value.length > 0);

// 初始化绑定模式
watch(
  selectedComponent,
  (comp) => {
    if (!comp) {
      propBindingModes.value = {};
      styleRefModes.value = {};
      return;
    }
    // 根据属性值判断是否为绑定模式
    const modes: Record<string, "static" | "binding"> = {};
    for (const [key, value] of Object.entries(comp.props || {})) {
      if (isBindingObject(value)) {
        modes[key] = "binding";
      } else {
        modes[key] = "static";
      }
    }
    propBindingModes.value = modes;

    // 根据样式值判断是否为引用模式
    const sRefModes: Record<string, "static" | "ref"> = {};
    const allStyleEntries = {
      ...comp.style.base,
      ...comp.style.pc,
      ...comp.style.tablet,
      ...comp.style.mobile,
    };
    for (const [key, value] of Object.entries(allStyleEntries)) {
      if (isStyleRef(value)) {
        sRefModes[key] = "ref";
      }
    }
    styleRefModes.value = sRefModes;
  },
  { immediate: true }
);

// 判断是否为绑定对象
function isBindingObject(value: any): value is PropBinding {
  return (
    value &&
    typeof value === "object" &&
    "type" in value &&
    value.type === "binding"
  );
}

// 获取属性的绑定模式
function getBindingMode(key: string): "static" | "binding" {
  return propBindingModes.value[key] || "static";
}

// 判断属性是否可绑定
function isPropBindable(prop: any) {
  return (
    prop?.type === "text" ||
    prop?.type === "textarea" ||
    prop?.type === "number" ||
    prop?.type === "json"
  );
}

// 切换绑定模式
function toggleBindingMode(key: string) {
  const currentMode = getBindingMode(key);
  const newMode = currentMode === "static" ? "binding" : "static";
  propBindingModes.value[key] = newMode;

  if (!selectedComponentId.value) return;

  if (newMode === "binding") {
    // 切换到绑定模式，创建绑定对象
    const initialValue = propsForm.value[key];
    const binding: PropBinding = {
      type: "binding",
      value: initialValue,
      expression: "",
    };
    updateComponentProps(selectedComponentId.value, { [key]: binding });
  } else {
    // 切换到静态模式，提取值
    const currentValue = propsForm.value[key];
    let staticValue: any = "";
    if (isBindingObject(currentValue)) {
      // 如果有表达式，尝试解析出值
      if (currentValue.expression) {
        try {
          staticValue = resolveExpression(
            currentValue.expression,
            editorDataContext.value.mockData
          );
        } catch {
          staticValue = currentValue.value;
        }
      } else {
        staticValue = currentValue.value;
      }
    } else {
      staticValue = currentValue;
    }
    updateComponentProps(selectedComponentId.value, { [key]: staticValue });
  }
}

// 获取属性的实际值（用于显示）
function getPropValue(key: string): any {
  const value = propsForm.value[key];
  if (isBindingObject(value)) {
    return value.value;
  }
  return value;
}

// 获取属性的绑定表达式
function getPropExpression(key: string): string {
  const value = propsForm.value[key];
  if (isBindingObject(value)) {
    return value.expression || "";
  }
  return "";
}

// 获取表达式预览值
function getPreviewValue(expression: string): string {
  return getExpressionPreview(expression, editorDataContext.value.mockData);
}

// 更新属性
function handlePropChange(key: string, value: any) {
  if (!selectedComponentId.value) return;
  updateComponentProps(selectedComponentId.value, { [key]: value });
}

// 更新属性绑定表达式
function handleBindingChange(key: string, expression: string) {
  if (!selectedComponentId.value) return;
  let resolved: any;
  try {
    resolved = resolveExpression(expression, editorDataContext.value.mockData);
  } catch {
    const old = propsForm.value[key];
    resolved = isBindingObject(old) ? old.value : old;
  }
  const binding: PropBinding = {
    type: "binding",
    value: resolved,
    expression,
  };
  updateComponentProps(selectedComponentId.value, { [key]: binding });
}

// 处理数字输入（带范围限制）
function handleNumberInputChange(
  key: string,
  raw: string,
  schema: { min?: number; max?: number; step?: number }
) {
  if (!selectedComponentId.value) return;
  if (raw === "") return;

  let num = Number(raw);
  if (Number.isNaN(num)) return;

  if (typeof schema.min === "number") num = Math.max(schema.min, num);
  if (typeof schema.max === "number") num = Math.min(schema.max, num);

  updateComponentProps(selectedComponentId.value, { [key]: num });
}

// 更新样式（当前选中的设备）
function handleStyleChange(key: string, value: any) {
  if (!selectedComponentId.value) return;
  updateComponentDeviceStyle(selectedComponentId.value, { [key]: value }, styleDevice.value);
}

// 获取样式的引用模式
function getStyleRefMode(key: string): "static" | "ref" {
  return styleRefModes.value[key] || "static";
}

// 切换样式引用模式
function toggleStyleRefMode(key: string) {
  const currentMode = getStyleRefMode(key);
  const newMode = currentMode === "static" ? "ref" : "static";
  styleRefModes.value[key] = newMode;

  if (!selectedComponentId.value) return;

  if (newMode === "static") {
    // 切回静态模式，清空该样式值
    handleStyleChange(key, "");
  }
  // 切到引用模式时，等待用户选择引用值，不立即保存
}

// 处理样式引用选择变更
function handleStyleRefChange(key: string, refValue: string) {
  if (!selectedComponentId.value || !refValue) return;

  // 格式: "global:primaryColor" 或 "variable:myVar"
  const [type, refKey] = refValue.split(":");
  if (type === "global") {
    handleStyleChange(key, { type: "global", key: refKey });
  } else if (type === "variable") {
    handleStyleChange(key, { type: "variable", key: refKey });
  }
}

// 获取样式引用的当前选中值（用于下拉的 :value）
function getStyleRefSelectValue(key: string): string {
  const value = styleForm.value[key];
  if (isGlobalStyleRef(value)) {
    return `global:${value.key}`;
  }
  if (isVariableStyleRef(value)) {
    return `variable:${value.key}`;
  }
  return "";
}

// 获取样式引用的预览显示值
function getStyleRefPreview(key: string): string {
  const value = styleForm.value[key];
  if (isGlobalStyleRef(value)) {
    const gs = siteConfig.value?.globalStyle;
    return gs ? (gs[value.key as keyof typeof gs] as string) || "" : "";
  }
  if (isVariableStyleRef(value)) {
    return variableValues.value[value.key] ?? "";
  }
  return typeof value === "string" ? value : "";
}

// 获取样式引用的标签
function getStyleRefLabel(key: string): string {
  const value = styleForm.value[key];
  if (isGlobalStyleRef(value)) {
    for (const group of GLOBAL_STYLE_OPTIONS) {
      const item = group.items.find((i) => i.key === value.key);
      if (item) return `${group.group} - ${item.label}`;
    }
    return value.key;
  }
  if (isVariableStyleRef(value)) {
    const variable = variableSchema.value?.find((v: any) => v.key === value.key);
    return variable?.label || value.key;
  }
  return "";
}

// 过滤出与样式属性类型兼容的自定义变量
function getStyleCompatibleVariables(styleType: string) {
  if (!variableSchema.value) return [];
  // 颜色类型匹配 color 变量，其余匹配 string 变量
  const compatibleTypes = styleType === "color" ? ["color", "string"] : ["string", "number"];
  return variableSchema.value.filter((v: any) => compatibleTypes.includes(v.type));
}

// 删除组件
function handleDelete() {
  if (!selectedComponentId.value) return;
  if (confirm("确定要删除这个组件吗？")) {
    removeComponent(selectedComponentId.value);
  }
}

// 复制组件
function handleCopy() {
  if (!selectedComponentId.value) return;
  copyComponent(selectedComponentId.value);
}
</script>

<template>
  <div class="property-panel">
    <!-- 有选中组件 -->
    <template v-if="selectedComponent && componentMeta">
      <!-- 组件信息 -->
      <div class="panel-header">
        <div class="component-info">
          <span
            :class="componentMeta.icon || 'i-carbon-cube'"
            class="component-icon"
          ></span>
          <span class="component-type">{{
            componentMeta.name || selectedComponent.type
          }}</span>
        </div>
        <div class="header-actions">
          <button class="action-btn" title="复制" @click="handleCopy">
            <span class="i-carbon-copy"></span>
          </button>
          <button class="action-btn danger" title="删除" @click="handleDelete">
            <span class="i-carbon-trash-can"></span>
          </button>
        </div>
      </div>

      <!-- Tab 切换 -->
      <div class="panel-tabs">
        <button
          class="panel-tab-btn"
          :class="{ active: activeTab === 'props' }"
          @click="activeTab = 'props'"
        >
          属性
        </button>
        <button
          class="panel-tab-btn"
          :class="{ active: activeTab === 'style' }"
          @click="activeTab = 'style'"
        >
          样式
        </button>
        <button
          class="panel-tab-btn"
          :class="{ active: activeTab === 'events' }"
          @click="activeTab = 'events'"
        >
          事件
        </button>
      </div>

      <!-- 属性编辑区 -->
      <div class="panel-content">
        <!-- 属性分组 -->
        <div v-show="activeTab === 'props'" class="property-section">
          <h4 class="section-title">属性</h4>

          <template v-if="componentMeta.propsSchema?.length">
            <div
              v-for="prop in componentMeta.propsSchema"
              :key="prop.key"
              class="property-item"
            >
              <div class="property-header">
                <label class="property-label">{{ prop.label }}</label>
                <!-- 绑定切换按钮（仅在有可绑定字段且属性可绑定时显示） -->
                <button
                  v-if="hasBindableFields && isPropBindable(prop)"
                  class="binding-toggle"
                  :class="{ active: getBindingMode(prop.key) === 'binding' }"
                  :title="
                    getBindingMode(prop.key) === 'binding'
                      ? '切换为静态值'
                      : '切换为数据绑定'
                  "
                  @click="toggleBindingMode(prop.key)"
                >
                  <span class="i-carbon-data-base"></span>
                </button>
              </div>

              <!-- 绑定模式 -->
              <template
                v-if="
                  isPropBindable(prop) && getBindingMode(prop.key) === 'binding'
                "
              >
                <div class="binding-editor">
                  <!-- JSON 类型支持手动输入表达式 -->
                  <input
                    v-if="prop.type === 'json'"
                    type="text"
                    class="property-input"
                    :value="getPropExpression(prop.key)"
                    placeholder="输入表达式，如 product.images"
                    @input="
                      handleBindingChange(
                        prop.key,
                        ($event.target as HTMLInputElement).value
                      )
                    "
                  />
                  <select
                    class="property-input binding-select"
                    :value="getPropExpression(prop.key)"
                    @change="
                      handleBindingChange(
                        prop.key,
                        ($event.target as HTMLSelectElement).value
                      )
                    "
                  >
                    <option value="">-- 选择数据字段 --</option>
                    <!-- 页面预设数据 -->
                    <optgroup v-if="hasPageContextFields" label="页面预设数据">
                      <option
                        v-for="field in pageContextFields"
                        :key="field.path"
                        :value="field.path"
                      >
                        {{ field.label }} ({{ field.path }})
                      </option>
                    </optgroup>
                    <!-- 自定义变量 -->
                    <optgroup v-if="hasVariableFields" label="自定义变量">
                      <option
                        v-for="field in variableFields"
                        :key="field.path"
                        :value="field.path"
                      >
                        {{ field.label }} ({{ field.path }})
                      </option>
                    </optgroup>
                  </select>
                  <div
                    v-if="getPropExpression(prop.key)"
                    class="binding-preview"
                  >
                    <span class="preview-label">预览:</span>
                    <span class="preview-value">{{
                      getPreviewValue(getPropExpression(prop.key))
                    }}</span>
                  </div>
                </div>
              </template>

              <!-- 静态模式 -->
              <template v-else>
                <!-- 文本输入 -->
                <input
                  v-if="prop.type === 'text'"
                  type="text"
                  class="property-input"
                  :value="getPropValue(prop.key)"
                  :placeholder="prop.placeholder"
                  @input="
                    handlePropChange(
                      prop.key,
                      ($event.target as HTMLInputElement).value
                    )
                  "
                />

                <!-- 多行文本 -->
                <textarea
                  v-else-if="prop.type === 'textarea'"
                  class="property-input property-textarea"
                  :value="getPropValue(prop.key)"
                  :placeholder="prop.placeholder"
                  @input="
                    handlePropChange(
                      prop.key,
                      ($event.target as HTMLTextAreaElement).value
                    )
                  "
                ></textarea>

                <!-- 数字 -->
                <input
                  v-else-if="prop.type === 'number'"
                  type="number"
                  class="property-input"
                  :value="getPropValue(prop.key)"
                  :min="prop.min"
                  :max="prop.max"
                  :step="prop.step"
                  @input="
                    handleNumberInputChange(
                      prop.key,
                      ($event.target as HTMLInputElement).value,
                      prop
                    )
                  "
                />

                <!-- 开关 -->
                <label v-else-if="prop.type === 'switch'" class="switch">
                  <input
                    type="checkbox"
                    :checked="getPropValue(prop.key)"
                    @change="
                      handlePropChange(
                        prop.key,
                        ($event.target as HTMLInputElement).checked
                      )
                    "
                  />
                  <span class="switch-slider"></span>
                </label>

                <!-- 下拉选择 -->
                <select
                  v-else-if="prop.type === 'select'"
                  class="property-input"
                  :value="getPropValue(prop.key)"
                  @change="
                    handlePropChange(
                      prop.key,
                      ($event.target as HTMLSelectElement).value
                    )
                  "
                >
                  <option
                    v-for="opt in prop.options"
                    :key="opt.value"
                    :value="opt.value"
                  >
                    {{ opt.label }}
                  </option>
                </select>

                <!-- 颜色 -->
                <div v-else-if="prop.type === 'color'" class="color-input">
                  <input
                    type="color"
                    :value="getPropValue(prop.key) || '#000000'"
                    @input="
                      handlePropChange(
                        prop.key,
                        ($event.target as HTMLInputElement).value
                      )
                    "
                  />
                  <input
                    type="text"
                    class="property-input"
                    :value="getPropValue(prop.key)"
                    placeholder="#000000"
                    @input="
                      handlePropChange(
                        prop.key,
                        ($event.target as HTMLInputElement).value
                      )
                    "
                  />
                </div>

                <!-- JSON 编辑器（用于数组等复杂类型） -->
                <textarea
                  v-else-if="prop.type === 'json'"
                  class="property-input property-textarea property-json"
                  :value="JSON.stringify(getPropValue(prop.key), null, 2)"
                  :placeholder="prop.placeholder || '[]'"
                  @input="
                    (e) => {
                      try {
                        const value = JSON.parse(
                          (e.target as HTMLTextAreaElement).value
                        );
                        handlePropChange(prop.key, value);
                      } catch (err) {
                        // JSON 解析失败，暂时不更新
                      }
                    }
                  "
                ></textarea>

                <!-- 默认：文本输入 -->
                <input
                  v-else
                  type="text"
                  class="property-input"
                  :value="getPropValue(prop.key)"
                  @input="
                    handlePropChange(
                      prop.key,
                      ($event.target as HTMLInputElement).value
                    )
                  "
                />
              </template>
            </div>
          </template>

          <p v-else class="empty-hint">该组件暂无可编辑属性</p>
        </div>

        <!-- 样式分组 -->
        <div v-show="activeTab === 'style'" class="property-section">
          <div class="section-header">
            <h4 class="section-title">样式</h4>
            <!-- 设备切换（图标按钮组） -->
            <div class="style-device-tabs">
              <button
                v-for="device in STYLE_DEVICE_LIST"
                :key="device"
                class="device-tab"
                :class="{ active: styleDevice === device }"
                :title="STYLE_BREAKPOINTS[device].label"
                @click="styleDevice = device"
              >
                <span :class="STYLE_BREAKPOINTS[device].icon"></span>
              </button>
            </div>
          </div>

          <template v-if="componentMeta.styleSchema?.length">
            <div
              v-for="style in componentMeta.styleSchema"
              :key="style.key"
              class="property-item"
            >
              <div class="property-label-row">
                <label class="property-label">{{ style.label }}</label>
                <!-- 引用模式切换按钮 -->
                <button
                  class="style-ref-toggle"
                  :class="{ active: getStyleRefMode(style.key) === 'ref' }"
                  :title="getStyleRefMode(style.key) === 'ref' ? '切换为自定义值' : '引用全局皮肤/变量'"
                  @click="toggleStyleRefMode(style.key)"
                >
                  <span class="i-carbon-link"></span>
                </button>
              </div>

              <!-- 引用模式：下拉选择全局皮肤或自定义变量 -->
              <template v-if="getStyleRefMode(style.key) === 'ref'">
                <select
                  class="property-input style-ref-select"
                  :value="getStyleRefSelectValue(style.key)"
                  @change="handleStyleRefChange(style.key, ($event.target as HTMLSelectElement).value)"
                >
                  <option value="">-- 选择引用 --</option>
                  <optgroup v-for="group in GLOBAL_STYLE_OPTIONS" :key="group.group" :label="'全局 - ' + group.group">
                    <option
                      v-for="item in group.items"
                      :key="item.key"
                      :value="'global:' + item.key"
                    >
                      {{ item.label }}
                    </option>
                  </optgroup>
                  <optgroup v-if="getStyleCompatibleVariables(style.type).length" label="自定义变量">
                    <option
                      v-for="v in getStyleCompatibleVariables(style.type)"
                      :key="v.key"
                      :value="'variable:' + v.key"
                    >
                      {{ v.label || v.key }}
                    </option>
                  </optgroup>
                </select>
                <!-- 引用预览 -->
                <div v-if="getStyleRefSelectValue(style.key)" class="style-ref-preview">
                  <span class="preview-label">{{ getStyleRefLabel(style.key) }}</span>
                  <span
                    v-if="style.type === 'color'"
                    class="preview-color-swatch"
                    :style="{ backgroundColor: getStyleRefPreview(style.key) }"
                  ></span>
                  <span v-else class="preview-value">{{ getStyleRefPreview(style.key) }}</span>
                </div>
              </template>

              <!-- 静态模式：原有编辑器 -->
              <template v-else>
                <!-- 尺寸 -->
                <div v-if="style.type === 'size'" class="size-input">
                  <input
                    type="number"
                    class="property-input"
                    :value="parseInt(styleForm[style.key]) || ''"
                    @input="
                      handleStyleChange(
                        style.key,
                        ($event.target as HTMLInputElement).value +
                          (style.unit || 'px')
                      )
                    "
                  />
                  <span class="unit">{{ style.unit || "px" }}</span>
                </div>

                <!-- 颜色 -->
                <div v-else-if="style.type === 'color'" class="color-input">
                  <input
                    type="color"
                    :value="styleForm[style.key] || '#000000'"
                    @input="
                      handleStyleChange(
                        style.key,
                        ($event.target as HTMLInputElement).value
                      )
                    "
                  />
                  <input
                    type="text"
                    class="property-input"
                    :value="styleForm[style.key]"
                    placeholder="#000000"
                    @input="
                      handleStyleChange(
                        style.key,
                        ($event.target as HTMLInputElement).value
                      )
                    "
                  />
                </div>

                <!-- 下拉选择 -->
                <select
                  v-else-if="style.type === 'select'"
                  class="property-input"
                  :value="styleForm[style.key]"
                  @change="
                    handleStyleChange(
                      style.key,
                      ($event.target as HTMLSelectElement).value
                    )
                  "
                >
                  <option
                    v-for="opt in style.options"
                    :key="opt.value"
                    :value="opt.value"
                  >
                    {{ opt.label }}
                  </option>
                </select>

                <!-- 滑块 -->
                <div v-else-if="style.type === 'slider'" class="slider-input">
                  <input
                    type="range"
                    :min="style.min || 0"
                    :max="style.max || 100"
                    :step="style.step || 1"
                    :value="parseInt(styleForm[style.key]) || 0"
                    @input="
                      handleStyleChange(
                        style.key,
                        ($event.target as HTMLInputElement).value +
                          (style.unit || '')
                      )
                    "
                  />
                  <span class="slider-value">{{
                    styleForm[style.key] || 0
                  }}</span>
                </div>

                <!-- 默认：文本输入 -->
                <input
                  v-else
                  type="text"
                  class="property-input"
                  :value="styleForm[style.key]"
                  :placeholder="style.defaultValue"
                  @input="
                    handleStyleChange(
                      style.key,
                      ($event.target as HTMLInputElement).value
                    )
                  "
                />
              </template>
            </div>
          </template>

          <p v-else class="empty-hint">该组件暂无可编辑样式</p>
        </div>

        <!-- 事件分组 -->
        <div v-show="activeTab === 'events'" class="property-section">
          <h4 class="section-title">事件</h4>
          <p class="empty-hint">事件配置功能开发中...</p>
        </div>
      </div>
    </template>

    <!-- 无选中组件 -->
    <div v-else class="empty-panel">
      <span class="i-carbon-touch-1 text-5xl text-gray-600 mb-4"></span>
      <p class="text-gray-400">选择一个组件</p>
      <p class="text-sm text-gray-600 mt-1">点击画布中的组件进行编辑</p>
    </div>
  </div>
</template>

<style scoped>
.property-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow-x: hidden;
  overflow-y: auto;
  /* Firefox 滚动条样式 */
  scrollbar-width: thin;
  scrollbar-color: #475569 transparent;
}

/* 自定义滚动条样式 - 适配深色主题 */
.property-panel::-webkit-scrollbar {
  width: 8px;
}

.property-panel::-webkit-scrollbar-track {
  background: transparent;
}

.property-panel::-webkit-scrollbar-thumb {
  background-color: #475569;
  border-radius: 4px;
}

.property-panel::-webkit-scrollbar-thumb:hover {
  background-color: #64748b;
}

/* 确保所有元素使用 border-box */
.property-panel *,
.property-panel *::before,
.property-panel *::after {
  box-sizing: border-box;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid #334155;
}

.component-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.component-icon {
  font-size: 20px;
  color: #3b82f6;
}

.component-type {
  font-size: 14px;
  font-weight: 600;
  color: #e2e8f0;
}

.header-actions {
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
  color: #94a3b8;
  background: none;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.action-btn:hover {
  color: #e2e8f0;
  background-color: #334155;
}

.action-btn.danger:hover {
  color: #ef4444;
  background-color: #7f1d1d;
}

/* Tab 切换 */
.panel-tabs {
  display: flex;
  gap: 0;
  padding: 0 16px;
  border-bottom: 1px solid #334155;
}

.panel-tab-btn {
  padding: 12px 16px;
  font-size: 14px;
  font-weight: 500;
  color: #94a3b8;
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  cursor: pointer;
  transition: all 0.2s;
}

.panel-tab-btn:hover {
  color: #e2e8f0;
}

.panel-tab-btn.active {
  color: #3b82f6;
  border-bottom-color: #3b82f6;
}

.panel-content {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  /* Firefox 滚动条样式 */
  scrollbar-width: thin;
  scrollbar-color: #475569 transparent;
}

.panel-content::-webkit-scrollbar {
  width: 8px;
}

.panel-content::-webkit-scrollbar-track {
  background: transparent;
}

.panel-content::-webkit-scrollbar-thumb {
  background-color: #475569;
  border-radius: 4px;
}

.panel-content::-webkit-scrollbar-thumb:hover {
  background-color: #64748b;
}

.property-section {
  margin-bottom: 24px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.section-title {
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 12px;
}

.section-header .section-title {
  margin-bottom: 0;
}

/* 设备切换图标按钮组 */
.style-device-tabs {
  display: flex;
  gap: 2px;
  padding: 2px;
  background-color: #0f172a;
  border-radius: 6px;
}

.device-tab {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 24px;
  font-size: 14px;
  color: #64748b;
  background: none;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.device-tab:hover {
  color: #94a3b8;
}

.device-tab.active {
  color: #3b82f6;
  background-color: #1e293b;
}

.property-item {
  margin-bottom: 12px;
}

.property-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.property-label {
  font-size: 13px;
  color: #94a3b8;
}

/* 绑定切换按钮 */
.binding-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  font-size: 14px;
  color: #64748b;
  background: none;
  border: 1px solid transparent;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.binding-toggle:hover {
  color: #3b82f6;
  background-color: #1e293b;
}

.binding-toggle.active {
  color: #3b82f6;
  background-color: rgba(59, 130, 246, 0.1);
  border-color: #3b82f6;
}

/* 样式属性标签行 */
.property-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

/* 样式引用切换按钮 */
.style-ref-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: 1px solid transparent;
  background: none;
  color: #64748b;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
  font-size: 12px;
}

.style-ref-toggle:hover {
  color: #3b82f6;
  background-color: #1e293b;
}

.style-ref-toggle.active {
  color: #3b82f6;
  background-color: rgba(59, 130, 246, 0.1);
  border-color: #3b82f6;
}

/* 样式引用下拉 */
.style-ref-select {
  width: 100%;
  font-size: 12px;
}

/* 样式引用预览 */
.style-ref-preview {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
  padding: 4px 8px;
  background: #1e293b;
  border-radius: 4px;
  font-size: 12px;
  color: #94a3b8;
}

.style-ref-preview .preview-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.style-ref-preview .preview-color-swatch {
  width: 16px;
  height: 16px;
  border-radius: 3px;
  border: 1px solid #334155;
  flex-shrink: 0;
}

.style-ref-preview .preview-value {
  color: #e2e8f0;
  font-family: monospace;
}

/* 绑定编辑器 */
.binding-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.binding-select {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%2394a3b8' d='M2.5 4.5L6 8l3.5-3.5'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 12px center;
  padding-right: 32px;
  cursor: pointer;
}

.binding-preview {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  background-color: #1e293b;
  border-radius: 4px;
  font-size: 12px;
}

.preview-label {
  color: #64748b;
}

.preview-value {
  color: #60a5fa;
  font-family: "Monaco", "Menlo", monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.property-input {
  width: 100%;
  max-width: 100%;
  padding: 8px 12px;
  font-size: 14px;
  background-color: #0f172a;
  border: 1px solid #334155;
  border-radius: 6px;
  color: #e2e8f0;
  outline: none;
  transition: border-color 0.2s;
}

.property-input:focus {
  border-color: #3b82f6;
}

.property-textarea {
  min-height: 80px;
  resize: vertical;
}

.property-json {
  font-family: "Courier New", monospace;
  font-size: 12px;
  min-height: 120px;
}

/* 颜色输入 */
.color-input {
  display: flex;
  align-items: center;
  gap: 8px;
}

.color-input input[type="color"] {
  width: 36px;
  height: 36px;
  padding: 0;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  flex-shrink: 0;
}

.color-input .property-input {
  flex: 1;
}

/* 尺寸输入 */
.size-input {
  display: flex;
  align-items: center;
  gap: 8px;
}

.size-input .property-input {
  flex: 1;
}

.unit {
  font-size: 12px;
  color: #64748b;
}

/* 滑块 */
.slider-input {
  display: flex;
  align-items: center;
  gap: 12px;
}

.slider-input input[type="range"] {
  flex: 1;
  height: 4px;
  background-color: #334155;
  border-radius: 2px;
  cursor: pointer;
}

.slider-value {
  min-width: 40px;
  font-size: 12px;
  color: #94a3b8;
  text-align: right;
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

.empty-hint {
  font-size: 13px;
  color: #64748b;
  text-align: center;
  padding: 20px;
}

.empty-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  text-align: center;
}
</style>
