/**
 * 国际化 Composable
 *
 * 根据 PageContext 中的 currentLanguage.code 返回对应语言的翻译
 * 支持多种语言，默认中文
 */

import zh from "~/locales/zh";
import en from "~/locales/en";
import pl from "~/locales/pl";
import ro from "~/locales/ro";
import el from "~/locales/el";
import cs from "~/locales/cs";
import bg from "~/locales/bg";
import de from "~/locales/de";
import hu from "~/locales/hu";
import sl from "~/locales/sl";
import sk from "~/locales/sk";
import hr from "~/locales/hr";
import es from "~/locales/es";
import pt from "~/locales/pt";
import it from "~/locales/it";
import lv from "~/locales/lv";
import lt from "~/locales/lt";
import et from "~/locales/et";
import ja from "~/locales/ja";
import ar from "~/locales/ar";
import th from "~/locales/th";
import ms from "~/locales/ms";
import zhtw from "~/locales/zhtw";
import id from "~/locales/id";

const messages: Record<string, typeof zh> = {
  zh,
  en,
  pl,
  ro,
  el,
  cs,
  bg,
  de,
  hu,
  sl,
  sk,
  hr,
  es,
  pt,
  it,
  lv,
  lt,
  et,
  ja,
  ar,
  th,
  ms,
  zhtw,
  id,
};

export function useI18n() {
  const { currentLanguage } = usePageContext();

  // 获取当前语言代码，默认中文
  const locale = computed(() => {
    const code = currentLanguage.value?.code?.toLowerCase();
    return code && messages[code] ? code : "zh";
  });

  // 翻译函数
  function t(key: string, fallback?: string): string {
    const keys = key.split(".");
    let result: any = messages[locale.value];
    for (const k of keys) {
      result = result?.[k];
      if (result === undefined) break;
    }
    return result ?? fallback ?? key;
  }

  return { t, locale };
}
