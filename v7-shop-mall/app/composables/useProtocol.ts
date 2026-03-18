/**
 * 协议相关 Composable
 *
 * 提供协议组和落地页相关数据
 * 数据从 usePageContext 获取（由中间件注入）
 */

import type { ProtocolGroup } from "./usePageContext";

export function useProtocol() {
  const { landingPage, protocolGroups } = usePageContext();

  // 协议占位符值
  const placeholderValues = computed(
    () => landingPage.value?.protocolPlaceholderValues || {}
  );

  // 协议组列表
  const groups = computed(() => protocolGroups.value || []);

  // 是否有协议组
  const hasProtocolGroups = computed(() => groups.value.length > 0);

  /**
   * 替换协议标题中的占位符
   * @param text 包含占位符的文本，如 "{{companyName}} 隐私政策"
   * @returns 替换后的文本
   */
  function replacePlaceholders(text: string): string {
    if (!text) return "";
    return text.replace(/\{\{(\w+)\}\}/g, (_, key) => {
      return placeholderValues.value[key] ?? `{{${key}}}`;
    });
  }

  return {
    landingPage,
    protocolGroups: groups,
    placeholderValues,
    hasProtocolGroups,
    replacePlaceholders,
  };
}
