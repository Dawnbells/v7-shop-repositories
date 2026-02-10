/**
 * 数据上下文 composable
 * 用于在组件树中传递和访问数据
 * 支持数据绑定、表达式解析等功能
 */

import type { InjectionKey } from "vue";
import type { ProductInfo } from "~/types/page-context";
import type { PropBinding, BindableField, DataFieldSchema } from "~/types/data-context";

// 文章信息类型
export interface ArticleInfo {
  id: number;
  name: string;
  title: string;
  content: string;
  description: string;
}

// 数据上下文类型
export interface DataContext {
  // 产品信息
  product?: ProductInfo;
  // 文章信息
  article?: ArticleInfo;
  // 用户信息（可扩展）
  user?: {
    id?: string;
    name?: string;
    email?: string;
  };
  // 订单信息（可扩展）
  order?: {
    id?: string;
    status?: string;
    total?: number;
  };
  // 自定义数据
  custom?: Record<string, any>;
}

// 注入键
export const DATA_CONTEXT_KEY: InjectionKey<Ref<DataContext>> = Symbol("dataContext");

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
 * 提供数据上下文
 * 在页面顶层调用，向下传递数据
 */
export function provideDataContext(context: DataContext) {
  const contextRef = ref<DataContext>(context);
  provide(DATA_CONTEXT_KEY, contextRef);
  return contextRef;
}

/**
 * 使用数据上下文
 * 在组件中调用，获取上层传递的数据
 */
export function useDataContext(): Ref<DataContext> {
  // 使用默认值避免警告，同时确保返回空上下文
  const context = inject(DATA_CONTEXT_KEY, ref<DataContext>({}));
  return context;
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
  context: DataContext
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
  context: DataContext
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
export function resolveExpression(expression: string, context: DataContext | Record<string, any>): any {
  if (!expression) return undefined;
  return getValueByPath(context, expression);
}

/**
 * 解析属性绑定，返回最终值
 * @param binding 属性绑定对象
 * @param context 数据上下文
 */
export function resolvePropBinding(binding: PropBinding, context: DataContext | Record<string, any>): any {
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
export function getExpressionPreview(expression: string, context: DataContext | Record<string, any>): string {
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

/**
 * 页面预设数据字段定义
 * 基于 PageContext 类型生成的可绑定字段列表
 */
export const PAGE_CONTEXT_FIELDS: BindableField[] = [
  // 域名信息
  { path: "pageContext.domain.fullName", label: "完整域名", type: "string", source: "preset" },
  { path: "pageContext.domain.name", label: "域名名称", type: "string", source: "preset" },
  
  // 国家信息
  { path: "pageContext.country.code", label: "国家代码", type: "string", source: "preset" },
  { path: "pageContext.country.name", label: "国家名称", type: "string", source: "preset" },
  { path: "pageContext.country.phonePrefix", label: "电话前缀", type: "string", source: "preset" },
  
  // 货币信息
  { path: "pageContext.currency.code", label: "货币代码", type: "string", source: "preset" },
  { path: "pageContext.currency.name", label: "货币名称", type: "string", source: "preset" },
  { path: "pageContext.currency.symbol", label: "货币符号", type: "string", source: "preset" },
  { path: "pageContext.currency.exchangeRate", label: "汇率", type: "number", source: "preset" },
  
  // 语言信息
  { path: "pageContext.languages[0].code", label: "当前语言代码", type: "string", source: "preset" },
  { path: "pageContext.languages[0].name", label: "当前语言名称", type: "string", source: "preset" },
  
  // 公司信息
  { path: "pageContext.company.name", label: "公司名称", type: "string", source: "preset" },
  { path: "pageContext.company.domain", label: "公司域名", type: "string", source: "preset" },
  
  // 产品信息（通过 dataContext.product 访问）
  { path: "product.id", label: "产品 ID", type: "number", source: "preset" },
  { path: "product.spuId", label: "产品 SPU ID", type: "number", source: "preset" },
  { path: "product.title", label: "产品标题", type: "string", source: "preset" },
  { path: "product.merchandise", label: "商品名称", type: "string", source: "preset" },
  { path: "product.introduction", label: "产品介绍", type: "string", source: "preset" },
  { path: "product.summary", label: "产品摘要", type: "string", source: "preset" },
  { path: "product.sellPrice", label: "销售价格", type: "number", source: "preset" },
  { path: "product.originPrice", label: "原价", type: "number", source: "preset" },
  { path: "product.isMultiSpecs", label: "是否多规格", type: "boolean", source: "preset" },
  { path: "product.images", label: "产品图片列表", type: "array", source: "preset" },
  { path: "product.images[0].relativePath", label: "首张图片路径", type: "string", source: "preset" },
  { path: "product.specifications", label: "产品规格列表", type: "array", source: "preset" },
];

/**
 * 生成页面预设数据的可绑定字段列表
 * @returns 页面预设数据字段列表
 */
export function generatePageContextFields(): BindableField[] {
  return PAGE_CONTEXT_FIELDS;
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
  }));
}

/**
 * 合并所有可绑定字段（页面预设 + 自定义变量）
 * @param customVariables 自定义变量列表
 * @returns 合并后的可绑定字段列表
 */
export function generateAllBindableFields(
  customVariables: Array<{ key: string; label: string; type: string }> = []
): BindableField[] {
  const pageContextFields = generatePageContextFields();
  const variableFields = generateVariableFields(customVariables);
  return [...pageContextFields, ...variableFields];
}
