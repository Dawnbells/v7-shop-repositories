// Composables 统一导出入口
// 按功能分类：基础 composables 和 页面 composables

// 基础 composables（系统级功能）
export { usePageContext } from "./base/usePageContext";
export { useThemeRender } from "./base/useThemeRender";
export {
  useDataContext,
  provideDataContext,
  useEditorDataContext,
  provideEditorDataContext,
  resolveBindingExpression,
  resolvePropsBindings,
  hasBindingExpression,
  resolveExpression,
  resolvePropBinding,
  isPropBindingValue,
  generateBindableFields,
  getExpressionPreview,
  generatePageContextFields,
  generateVariableFields,
  generateAllBindableFields,
} from "./base/useDataContext";
export { useDeviceDetect } from "./base/useDeviceDetect";
export { useResponsive } from "./base/useResponsive";
export { useCurrentPage } from "./base/useCurrentPage";
export { useThemeSchema } from "./base/useThemeSchema";
export { useComponentRegistry } from "./base/useComponentRegistry";
export { useIframeAuth } from "./base/useIframeAuth";
export { useDragDrop } from "./base/useDragDrop";

// 页面 composables（按页面类型）
export { useProductInfo } from "./pages/useProductInfo";
export { useArticleInfo } from "./pages/useArticleInfo";
