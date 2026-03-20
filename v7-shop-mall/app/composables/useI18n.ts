/**
 * 国际化 Composable
 *
 * 根据 PageContext 中的 currentLanguage.code 返回对应语言的翻译
 * 支持中文 (zh) 和英文 (en)，默认中文
 */

import zh from "~/locales/zh";
import en from "~/locales/en";

const messages: Record<string, typeof zh> = { zh, en };

export function useI18n() {
  const { currentLanguage } = usePageContext();

  // 获取当前语言代码，默认中文
  const locale = computed(() => {
    const code = currentLanguage.value?.code?.toLowerCase();
    return code && messages[code] ? code : "zh";
  });

  // 翻译函数
  function t(key: string): string {
    const keys = key.split(".");
    let result: any = messages[locale.value];
    for (const k of keys) {
      result = result?.[k];
      if (result === undefined) break;
    }
    return result ?? key;
  }

  return { t, locale };
}
