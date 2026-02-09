/**
 * 响应式样式计算
 */

import type { CSSProperties } from "vue";
import type { ResponsiveStyle, DeviceType, GlobalStyle } from "~/types/builder";
import { isGlobalStyleRef, isVariableStyleRef } from "~/types/schema";

export function useResponsive() {
  // 根据设备类型计算最终样式
  function resolveStyle(
    style: ResponsiveStyle,
    device: DeviceType,
    globalStyle?: GlobalStyle,
    variableValues?: Record<string, any>
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

    // 解析样式引用（全局皮肤 + 自定义变量）
    if (globalStyle) {
      return resolveStyleRefs(mergedStyle, globalStyle, variableValues);
    }

    return mergedStyle;
  }

  // 解析样式引用（全局皮肤 + 自定义变量）
  function resolveStyleRefs(
    style: CSSProperties,
    globalStyle: GlobalStyle,
    variableValues?: Record<string, any>
  ): CSSProperties {
    const resolved: CSSProperties = {};

    for (const [key, value] of Object.entries(style)) {
      if (isGlobalStyleRef(value)) {
        const globalKey = value.key as keyof GlobalStyle;
        resolved[key as keyof CSSProperties] = globalStyle[globalKey] as any;
      } else if (isVariableStyleRef(value)) {
        resolved[key as keyof CSSProperties] = (variableValues?.[value.key] ?? '') as any;
      } else {
        resolved[key as keyof CSSProperties] = value;
      }
    }

    return resolved;
  }

  // 解析全局样式引用（兼容旧接口）
  function resolveGlobalStyleRefs(
    style: CSSProperties,
    globalStyle: GlobalStyle
  ): CSSProperties {
    return resolveStyleRefs(style, globalStyle);
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
    globalStyle?: GlobalStyle,
    variableValues?: Record<string, any>
  ): string {
    const cssRules: string[] = [];
    const gs = globalStyle || ({} as GlobalStyle);

    // 基础样式
    const baseResolved = resolveStyleRefs(style.base, gs, variableValues);
    cssRules.push(`.${className} { ${styleToString(baseResolved)} }`);

    // PC 样式
    if (style.pc) {
      const pcResolved = resolveStyleRefs(style.pc, gs, variableValues);
      cssRules.push(
        `@media (min-width: 1024px) { .${className} { ${styleToString(pcResolved)} } }`
      );
    }

    // 平板样式
    if (style.tablet) {
      const tabletResolved = resolveStyleRefs(style.tablet, gs, variableValues);
      cssRules.push(
        `@media (min-width: 768px) and (max-width: 1023px) { .${className} { ${styleToString(tabletResolved)} } }`
      );
    }

    // 手机样式
    if (style.mobile) {
      const mobileResolved = resolveStyleRefs(style.mobile, gs, variableValues);
      cssRules.push(
        `@media (max-width: 767px) { .${className} { ${styleToString(mobileResolved)} } }`
      );
    }

    return cssRules.join("\n");
  }

  // 解析完整 ResponsiveStyle 的所有引用
  function resolveResponsiveStyleRefs(
    style: ResponsiveStyle,
    globalStyle: GlobalStyle,
    variableValues?: Record<string, any>
  ): ResponsiveStyle {
    return {
      base: resolveStyleRefs(style.base, globalStyle, variableValues),
      pc: style.pc ? resolveStyleRefs(style.pc, globalStyle, variableValues) : undefined,
      tablet: style.tablet ? resolveStyleRefs(style.tablet, globalStyle, variableValues) : undefined,
      mobile: style.mobile ? resolveStyleRefs(style.mobile, globalStyle, variableValues) : undefined,
    };
  }

  return {
    resolveStyle,
    resolveStyleRefs,
    resolveGlobalStyleRefs,
    resolveResponsiveStyleRefs,
    styleToString,
    generateResponsiveCSS,
  };
}
