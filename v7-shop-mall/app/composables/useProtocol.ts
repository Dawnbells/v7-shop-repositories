/**
 * 协议相关 Composable
 *
 * 提供协议组数据和占位符替换
 * 数据从 usePageContext 获取（由中间件注入）
 */

export function useProtocol() {
  const { landingPage, protocolGroups } = usePageContext();

  const placeholderValues = computed(
    () => landingPage.value?.protocolPlaceholderValues || {}
  );

  const groups = computed(() => protocolGroups.value || []);

  const hasProtocolGroups = computed(() => groups.value.length > 0);

  /**
   * 替换协议标题中的占位符
   * @param text 包含占位符的文本，如 "{{companyName}} 隐私政策"
   */
  function replacePlaceholders(text: string): string {
    if (!text) return "";
    return text.replace(/\{\{(\w+)\}\}/g, (_, key) => {
      return placeholderValues.value[key] ?? `{{${key}}}`;
    });
  }

  return {
    protocolGroups: groups,
    placeholderValues,
    hasProtocolGroups,
    replacePlaceholders,
  };
}
