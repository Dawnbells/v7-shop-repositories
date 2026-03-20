/**
 * 协议相关 Composable
 *
 * 提供协议组数据和占位符替换
 * 数据从 usePageContext 获取（由中间件注入）
 */

export function useProtocol() {
  const { landingPage, protocolGroups, currentLanguage } = usePageContext();

  const placeholderValues = computed(
    () => landingPage.value?.protocolPlaceholderValues || {},
  );

  const languageCode = computed(
    () => currentLanguage.value?.code?.toUpperCase() || "",
  );

  const groups = computed(() => protocolGroups.value || []);

  const hasProtocolGroups = computed(() => groups.value.length > 0);

  /**
   * 替换协议标题中的占位符
   * @param text 包含占位符的文本，如 "{{companyName}} 隐私政策"
   * 支持中文、英文、数字、下划线等字符作为占位符 key
   * 国际化占位符 {{i18n_xxx}} 会先转换为 {{(LANGUAGECODE)xxx}} 再替换
   */
  function replacePlaceholders(text: string): string {
    if (!text) return "";

    // 先处理国际化占位符：{{i18n_xxx}} -> {{(LANGUAGECODE)xxx}}
    let processed = text;
    if (languageCode.value) {
      processed = processed.replace(
        /\{\{i18n_([^{}]+)\}\}/g,
        `{{(${languageCode.value})$1}}`,
      );
    }

    // 再替换所有占位符
    return processed.replace(/\{\{([^{}]+)\}\}/g, (match, key) => {
      return placeholderValues.value[key] ?? match;
    });
  }

  return {
    protocolGroups: groups,
    placeholderValues,
    hasProtocolGroups,
    replacePlaceholders,
  };
}
