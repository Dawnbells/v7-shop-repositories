/**
 * 日期格式化 Composable
 *
 * 依据站点当前语言自动本地化日期（Intl），或使用国际通用的固定格式
 * 供编辑器可配置的日期展示统一调用，避免各组件写死语言
 */

/** 日期格式预设 */
export type DateFormatPreset =
  | "auto" // 跟随语言 · 长格式：2026年8月12日 / August 12, 2026
  | "auto-medium" // 跟随语言 · 简写：2026年8月12日 / Aug 12, 2026
  | "auto-numeric" // 跟随语言 · 数字：2026/8/12 / 12.08.2026
  | "iso" // 国际标准 ISO 8601：2026-08-12
  | "dmy" // 日/月/年：12/08/2026
  | "mdy"; // 月/日/年：08/12/2026

/**
 * 站点语言代码 → BCP 47 语言标签
 * 只需映射非标准代码，其余语言代码本身即为合法标签
 */
const LOCALE_TAG_MAP: Record<string, string> = {
  zh: "zh-CN",
  zhtw: "zh-TW",
};

/**
 * Intl 选项映射（仅 auto 系列使用）
 * auto-numeric 不用 dateStyle: "short"，避免部分语言输出两位年份（8/12/26）
 */
const AUTO_INTL_OPTIONS: Record<string, Intl.DateTimeFormatOptions> = {
  auto: { dateStyle: "long" },
  "auto-medium": { dateStyle: "medium" },
  "auto-numeric": { year: "numeric", month: "numeric", day: "numeric" },
};

export function useDateFormat() {
  const { locale } = useI18n();

  /** 当前语言对应的 BCP 47 标签 */
  const localeTag = computed(() => LOCALE_TAG_MAP[locale.value] || locale.value);

  /** 归一化为 Date，非法值返回 null */
  function toDate(value: string | number | Date | null | undefined): Date | null {
    if (value === null || value === undefined || value === "") return null;
    const date = value instanceof Date ? value : new Date(value);
    return Number.isNaN(date.getTime()) ? null : date;
  }

  /** 固定格式（不依赖 ICU，各语言下输出一致） */
  function formatFixed(date: Date, preset: DateFormatPreset): string {
    const y = String(date.getFullYear());
    const m = String(date.getMonth() + 1).padStart(2, "0");
    const d = String(date.getDate()).padStart(2, "0");
    if (preset === "dmy") return `${d}/${m}/${y}`;
    if (preset === "mdy") return `${m}/${d}/${y}`;
    return `${y}-${m}-${d}`;
  }

  /**
   * 格式化日期
   * @param value 日期值（ISO 字符串 / 时间戳 / Date）
   * @param preset 格式预设，默认跟随站点语言
   */
  function formatDate(
    value: string | number | Date | null | undefined,
    preset: DateFormatPreset = "auto"
  ): string {
    const date = toDate(value);
    if (!date) return "";

    const intlOptions = AUTO_INTL_OPTIONS[preset];
    if (!intlOptions) return formatFixed(date, preset);

    try {
      return new Intl.DateTimeFormat(localeTag.value, intlOptions).format(date);
    } catch {
      // 语言标签不被运行时支持时回退到国际标准格式
      return formatFixed(date, "iso");
    }
  }

  return { locale, localeTag, formatDate };
}
