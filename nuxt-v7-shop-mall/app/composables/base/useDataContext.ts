/**
 * 数据绑定 composable
 * 用于解析数据绑定表达式、生成可绑定字段列表
 */

import type { InjectionKey } from "vue";
import type { PropBinding, BindableField, DataFieldSchema } from "~/types/data-context";

// 编辑器数据上下文注入键（用于编辑器中的数据绑定）
export const EDITOR_DATA_CONTEXT_KEY: InjectionKey<Ref<EditorDataContext>> = Symbol("editorDataContext");

// 编辑器数据上下文类型
export interface EditorDataContext {
  // Mock 数据（用于预览）
  mockData: Record<string, any>;
  // 可绑定字段列表
  bindableFields: BindableField[];
}

/**
 * 提供编辑器数据上下文
 */
export function provideEditorDataContext(context: EditorDataContext) {
  const contextRef = ref<EditorDataContext>(context);
  provide(EDITOR_DATA_CONTEXT_KEY, contextRef);
  return contextRef;
}

/**
 * 使用编辑器数据上下文
 */
export function useEditorDataContext(): Ref<EditorDataContext> {
  const context = inject(EDITOR_DATA_CONTEXT_KEY);
  if (!context) {
    return ref<EditorDataContext>({
      mockData: {},
      bindableFields: [],
    });
  }
  return context;
}

/**
 * 解析绑定表达式
 * 支持 {{product.title}} 这样的表达式语法
 */
export function resolveBindingExpression(
  expression: string,
  context: Record<string, any>
): any {
  // 检查是否是绑定表达式
  const match = expression.match(/^\{\{(.+)\}\}$/);
  if (!match) {
    return expression;
  }

  const path = match[1].trim();
  return getValueByPath(context, path);
}

/**
 * 根据路径获取对象值
 * 例如: getValueByPath({ product: { title: 'Hello' } }, 'product.title') => 'Hello'
 */
export function getValueByPath(obj: any, path: string): any {
  if (!obj || !path) return undefined;

  const keys = path.split(".");
  let result = obj;

  for (const key of keys) {
    if (result === null || result === undefined) {
      return undefined;
    }
    // 支持数组索引，如 items[0]
    const arrayMatch = key.match(/^(\w+)\[(\d+)\]$/);
    if (arrayMatch) {
      result = result[arrayMatch[1]]?.[parseInt(arrayMatch[2])];
    } else {
      result = result[key];
    }
  }

  return result;
}

/**
 * 解析组件 props 中的所有绑定表达式
 */
export function resolvePropsBindings(
  props: Record<string, any>,
  context: Record<string, any>
): Record<string, any> {
  const resolved: Record<string, any> = {};

  for (const [key, value] of Object.entries(props)) {
    if (typeof value === "string") {
      resolved[key] = resolveBindingExpression(value, context);
    } else if (Array.isArray(value)) {
      // 递归处理数组
      resolved[key] = value.map((item) => {
        if (typeof item === "string") {
          return resolveBindingExpression(item, context);
        } else if (typeof item === "object" && item !== null) {
          return resolvePropsBindings(item, context);
        }
        return item;
      });
    } else if (typeof value === "object" && value !== null) {
      // 递归处理对象
      resolved[key] = resolvePropsBindings(value, context);
    } else {
      resolved[key] = value;
    }
  }

  return resolved;
}

/**
 * 检查值是否包含绑定表达式
 */
export function hasBindingExpression(value: any): boolean {
  if (typeof value === "string") {
    return /\{\{.+\}\}/.test(value);
  }
  if (Array.isArray(value)) {
    return value.some(hasBindingExpression);
  }
  if (typeof value === "object" && value !== null) {
    return Object.values(value).some(hasBindingExpression);
  }
  return false;
}

/**
 * 解析表达式（不带 {{ }} 的路径）
 * @param expression 表达式，如 "product.title"
 * @param context 数据上下文
 */
export function resolveExpression(expression: string, context: Record<string, any>): any {
  if (!expression) return undefined;
  return getValueByPath(context, expression);
}

/**
 * 解析属性绑定，返回最终值
 * @param binding 属性绑定对象
 * @param context 数据上下文
 */
export function resolvePropBinding(binding: PropBinding, context: Record<string, any>): any {
  if (binding.type === "static") {
    return binding.value;
  }

  if (binding.type === "binding" && binding.expression) {
    return resolveExpression(binding.expression, context);
  }

  return binding.value;
}

/**
 * 判断值是否为 PropBinding 对象
 */
export function isPropBindingValue(value: any): value is PropBinding {
  return (
    value &&
    typeof value === "object" &&
    "type" in value &&
    (value.type === "static" || value.type === "binding")
  );
}

/**
 * 从数据结构生成可绑定字段列表
 * @param schema 数据字段结构
 * @param prefix 路径前缀
 * @param source 数据来源
 */
export function generateBindableFields(
  schema: DataFieldSchema[],
  prefix: string = "",
  source: "preset" | "variable" | "global" = "preset"
): BindableField[] {
  const fields: BindableField[] = [];

  for (const field of schema) {
    const path = prefix ? `${prefix}.${field.key}` : field.key;

    fields.push({
      path,
      label: field.label,
      type: field.type,
      source,
    });

    // 递归添加子字段
    if (field.children && field.children.length > 0) {
      if (field.type === "array") {
        // 数组类型，使用 [0] 表示第一个元素
        fields.push(...generateBindableFields(field.children, `${path}[0]`, source));
      } else {
        fields.push(...generateBindableFields(field.children, path, source));
      }
    }
  }

  return fields;
}

/**
 * 获取绑定表达式的预览值
 * @param expression 表达式
 * @param context 数据上下文
 */
export function getExpressionPreview(expression: string, context: Record<string, any>): string {
  if (!expression) return "";

  let value: any;
  try {
    value = resolveExpression(expression, context);
  } catch {
    return "(解析失败)";
  }

  if (value === undefined) return "(未找到)";
  if (Array.isArray(value)) return `Array(${value.length})`;
  if (typeof value === "object") {
    try {
      return JSON.stringify(value);
    } catch {
      return "(对象不可序列化)";
    }
  }
  return String(value);
}

// ============ 预设数据集相关（从 constants 导入） ============

import {
  PRESET_DATASETS,
  getBindableFieldsForPageType,
  getMockDataForPageType,
  type BindableFieldExt,
} from "~/constants/preset-datasets";

/**
 * 生成页面预设数据的可绑定字段列表
 * @deprecated 建议使用 getBindableFieldsForPageType
 * @returns 页面预设数据字段列表
 */
export function generatePageContextFields(): BindableField[] {
  return PRESET_DATASETS.pageContext.fields;
}

/**
 * 从自定义变量生成可绑定字段列表
 * @param variables 自定义变量列表
 * @returns 可绑定字段列表
 */
export function generateVariableFields(
  variables: Array<{ key: string; label: string; type: string }>
): BindableField[] {
  return variables.map((v) => ({
    path: `site.${v.key}`,
    label: v.label,
    type: v.type as any,
    source: "variable" as const,
    category: "variable",
    categoryLabel: "自定义变量",
  }));
}

/**
 * 合并所有可绑定字段（页面预设 + 自定义变量）
 * @param customVariables 自定义变量列表
 * @param pageType 页面类型（可选，默认使用通用页面上下文）
 * @returns 合并后的可绑定字段列表
 */
export function generateAllBindableFields(
  customVariables: Array<{ key: string; label: string; type: string }> = [],
  pageType?: string
): BindableField[] {
  const presetFields = pageType
    ? getBindableFieldsForPageType(pageType)
    : PRESET_DATASETS.pageContext.fields;
  const variableFields = generateVariableFields(customVariables);
  return [...presetFields, ...variableFields];
}

/**
 * 生成产品页可绑定字段列表
 * @returns 产品页可绑定字段列表
 */
export function generateProductFields(): BindableField[] {
  return PRESET_DATASETS.product.fields;
}

/**
 * 生成文章页可绑定字段列表
 * @returns 文章页可绑定字段列表
 */
export function generateArticleFields(): BindableField[] {
  return PRESET_DATASETS.article.fields;
}

/**
 * 根据页面类型生成可绑定字段列表
 * @param pageType 页面类型
 * @param customVariables 自定义变量列表
 * @returns 可绑定字段列表
 */
export function generateBindableFieldsForPage(
  pageType: string,
  customVariables: Array<{ key: string; label: string; type: string }> = []
): BindableFieldExt[] {
  const presetFields = getBindableFieldsForPageType(pageType);
  const variableFields = generateVariableFields(customVariables) as BindableFieldExt[];
  return [...presetFields, ...variableFields];
}

/**
 * 根据页面类型获取 Mock 数据
 * @param pageType 页面类型
 * @returns Mock 数据
 */
export function getMockDataForPage(pageType: string): Record<string, any> {
  return getMockDataForPageType(pageType);
}
