/**
 * 响应式样式计算
 */

import type { CSSProperties } from "vue";
import type { ResponsiveStyle, DeviceType, GlobalStyle } from "~/types/builder";
import { isGlobalStyleRef } from "~/types/schema";

export function useResponsive() {
  // 根据设备类型计算最终样式
  function resolveStyle(
    style: ResponsiveStyle,
    device: DeviceType,
    globalStyle?: GlobalStyle
  ): CSSProperties {
    // 基础样式
    const baseStyle = { ...style.base };

    // 设备特定样式
    let deviceStyle: CSSProperties = {};
    switch (device) {
      case "mobile":
        deviceStyle = style.mobile || {};
        break;
      case "tablet":
        deviceStyle = style.tablet || style.mobile || {};
        break;
      case "pc":
        deviceStyle = style.pc || style.tablet || style.mobile || {};
        break;
    }

    // 合并样式
    const mergedStyle = { ...baseStyle, ...deviceStyle };

    // 解析全局样式引用
    if (globalStyle) {
      return resolveGlobalStyleRefs(mergedStyle, globalStyle);
    }

    return mergedStyle;
  }

  // 解析全局样式引用
  function resolveGlobalStyleRefs(
    style: CSSProperties,
    globalStyle: GlobalStyle
  ): CSSProperties {
    const resolved: CSSProperties = {};

    for (const [key, value] of Object.entries(style)) {
      if (isGlobalStyleRef(value)) {
        // 从全局样式中获取值
        const globalKey = value.key as keyof GlobalStyle;
        resolved[key as keyof CSSProperties] = globalStyle[globalKey] as any;
      } else {
        resolved[key as keyof CSSProperties] = value;
      }
    }

    return resolved;
  }

  // 将样式对象转换为 CSS 字符串
  function styleToString(style: CSSProperties): string {
    return Object.entries(style)
      .map(([key, value]) => {
        // 驼峰转短横线
        const cssKey = key.replace(/([A-Z])/g, "-$1").toLowerCase();
        return `${cssKey}: ${value}`;
      })
      .join("; ");
  }

  // 生成响应式 CSS 类
  function generateResponsiveCSS(
    style: ResponsiveStyle,
    className: string,
    globalStyle?: GlobalStyle
  ): string {
    const cssRules: string[] = [];

    // 基础样式
    const baseResolved = resolveGlobalStyleRefs(
      style.base,
      globalStyle || ({} as GlobalStyle)
    );
    cssRules.push(`.${className} { ${styleToString(baseResolved)} }`);

    // PC 样式
    if (style.pc) {
      const pcResolved = resolveGlobalStyleRefs(
        style.pc,
        globalStyle || ({} as GlobalStyle)
      );
      cssRules.push(
        `@media (min-width: 1024px) { .${className} { ${styleToString(pcResolved)} } }`
      );
    }

    // 平板样式
    if (style.tablet) {
      const tabletResolved = resolveGlobalStyleRefs(
        style.tablet,
        globalStyle || ({} as GlobalStyle)
      );
      cssRules.push(
        `@media (min-width: 768px) and (max-width: 1023px) { .${className} { ${styleToString(tabletResolved)} } }`
      );
    }

    // 手机样式
    if (style.mobile) {
      const mobileResolved = resolveGlobalStyleRefs(
        style.mobile,
        globalStyle || ({} as GlobalStyle)
      );
      cssRules.push(
        `@media (max-width: 767px) { .${className} { ${styleToString(mobileResolved)} } }`
      );
    }

    return cssRules.join("\n");
  }

  return {
    resolveStyle,
    resolveGlobalStyleRefs,
    styleToString,
    generateResponsiveCSS,
  };
}
