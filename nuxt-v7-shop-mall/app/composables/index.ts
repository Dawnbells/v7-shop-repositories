// Composables 统一导出入口

// 基础 composables（系统级功能）
export { usePageContext } from "./base/usePageContext";
export { useThemeRender } from "./base/useThemeRender";
export {
  useEditorDataContext,
  provideEditorDataContext,
  resolveBindingExpression,
  resolvePropsBindings,
  hasBindingExpression,
  resolveExpression,
  resolvePropBinding,
  isPropBindingValue,
  getExpressionPreview,
  generatePageContextFields,
  generateVariableFields,
  generateAllBindableFields,
  generateProductFields,
  generateArticleFields,
} from "./base/useDataContext";
export { useDeviceDetect } from "./base/useDeviceDetect";
export { useResponsive } from "./base/useResponsive";
export { useCurrentPage } from "./base/useCurrentPage";
export { useThemeSchema } from "./base/useThemeSchema";
export { useComponentRegistry } from "./base/useComponentRegistry";
export { useIframeAuth } from "./base/useIframeAuth";
export { useDragDrop } from "./base/useDragDrop";

// 注意：页面 composables (useProductPage, useArticlePage) 由 Nuxt 自动从 composables 目录导入
// 无需在此处手动导出，避免重复导入警告
