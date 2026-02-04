/**
 * 组件元数据注册插件
 *
 * 功能说明：
 * 该插件在 Nuxt 应用启动时自动扫描并注册组件元数据，用于编辑器的组件面板和属性面板。
 * 组件实例的动态渲染由 Nuxt 全局组件 + Vue resolveComponent 处理，无需手动注册。
 *
 * 工作原理：
 * 1. 使用 Vite 的 import.meta.glob 在构建时静态分析目录结构
 * 2. 将 Vue 组件文件名从 PascalCase 转换为 kebab-case 作为类型标识
 * 3. 如果组件导出了元数据（meta 或 __meta），注册到元数据注册表
 *
 * 扫描目录：
 * - @/components/shop/**\/*.vue       - 业务组件（商品卡片、购物车等）
 * - @/components/shop-layout/**\/*.vue - 布局组件（页头、页脚、导航等）
 *
 * 命名转换示例：
 * - NoticeBar.vue      -> "notice-bar"
 * - ProductCard.vue    -> "product-card"
 * - PageSlot.vue       -> "page-slot"
 *
 * 组件元数据定义方式：
 * 在组件文件中使用 <script lang="ts"> 导出 meta 对象：
 *
 * @example
 * ```vue
 * <script lang="ts">
 * import type { ComponentMeta } from "~/types/component-meta";
 *
 * export const meta: ComponentMeta = {
 *   type: "notice-bar",
 *   name: "通知栏",
 *   icon: "i-carbon-notification",
 *   category: "marketing",
 *   // ... 其他配置
 * };
 *
 * export default { __meta: meta };
 * </script>
 *
 * <script setup lang="ts">
 * // 组件逻辑
 * </script>
 * ```
 */

import type { ComponentMeta } from "~/types/component-meta";

// 服务器端注册标记
// 在 SSR 环境下，模块级变量在服务器进程生命周期内保持
// 避免每次 SSR 请求都重复注册元数据
let serverRegistered = false;

export default defineNuxtPlugin(() => {
  // 服务器端：检查是否已注册，避免每次请求重复注册
  // 客户端：每次刷新都需要注册（因为 JS 重新加载，注册表为空）
  if (import.meta.server && serverRegistered) {
    return;
  }

  // 获取组件注册表的注册方法（仅元数据注册）
  const { registerComponent } = useComponentRegistry();

  /**
   * 注册组件元数据到注册表
   * 仅注册元数据，组件实例由 Nuxt 全局组件机制处理
   *
   * @param modules - import.meta.glob 返回的模块映射
   * @param logPrefix - 日志前缀，用于区分不同类型的组件
   * @param isLayoutComponent - 是否为布局组件（用于设置 layoutOnly 默认值）
   */
  function registerModules(
    modules: Record<string, { default: any; meta?: ComponentMeta }>,
    logPrefix: string,
    isLayoutComponent: boolean = false
  ) {
    for (const [path, module] of Object.entries(modules)) {
      // 从路径中提取文件名（不含扩展名）
      const match = path.match(/\/([^/]+)\.vue$/);

      if (match && match[1]) {
        const componentName = match[1];

        // PascalCase -> kebab-case
        const type = componentName
          .replace(/([A-Z])/g, "-$1")
          .toLowerCase()
          .replace(/^-/, "");

        // 注册组件元数据（如果有）
        // 元数据可以从以下位置获取：
        // - module.default.__meta: 附加在组件默认导出上的元数据
        // - module.meta: 组件文件中单独导出的 meta 对象
        const componentMeta: ComponentMeta | undefined =
          module.default?.__meta || module.meta;

        if (componentMeta) {
          // 注册元数据，确保 type 与文件名一致
          registerComponent({
            ...componentMeta,
            type,
            // 布局组件默认设置 layoutOnly: true（除非显式指定）
            layoutOnly: componentMeta.layoutOnly ?? isLayoutComponent,
          });

          if (import.meta.dev) {
            console.log(`[${logPrefix}] ${type} (含元数据) <- ${path}`);
          }
        }
      }
    }
  }

  // ============================================================
  // 注册业务组件元数据 (shop 目录)
  // ============================================================
  const shopComponents = import.meta.glob("@/components/shop/**/*.vue", {
    eager: true,
  }) as Record<string, { default: any; meta?: ComponentMeta }>;

  registerModules(shopComponents, "组件元数据注册", false);

  // ============================================================
  // 注册布局组件元数据 (shop-layout 目录)
  // ============================================================
  const layoutComponents = import.meta.glob(
    "@/components/shop-layout/**/*.vue",
    {
      eager: true,
    }
  ) as Record<string, { default: any; meta?: ComponentMeta }>;

  registerModules(layoutComponents, "布局组件元数据注册", true);

  // 服务器端：标记为已注册，后续 SSR 请求将跳过注册逻辑
  if (import.meta.server) {
    serverRegistered = true;
  }
});
