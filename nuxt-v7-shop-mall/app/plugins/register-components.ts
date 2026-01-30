/**
 * 组件自动注册插件
 *
 * 功能说明：
 * 该插件在 Nuxt 应用启动时自动扫描并注册 Vue 组件，使其可以在
 * ComponentRenderer 中通过组件类型名称动态渲染。
 *
 * 双注册机制：
 * 1. 组件实例注册 - 用于 ComponentRenderer 动态渲染
 * 2. 组件元数据注册 - 用于编辑器组件面板和属性面板
 *
 * 工作原理：
 * 1. 使用 Vite 的 import.meta.glob 在构建时静态分析目录结构
 * 2. 将 Vue 组件文件名从 PascalCase 转换为 kebab-case 作为类型标识
 * 3. 注册组件实例到实例注册表
 * 4. 如果组件导出了元数据（meta 或 __meta），同时注册到元数据注册表
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

export default defineNuxtPlugin(() => {
  // 获取组件注册表的注册方法
  const { registerComponentInstance, registerComponent } = useComponentRegistry();

  /**
   * 注册组件模块到全局注册表
   * 同时注册组件实例和元数据（如果有）
   *
   * @param modules - import.meta.glob 返回的模块映射
   * @param logPrefix - 日志前缀，用于区分不同类型的组件
   * @param isLayoutComponent - 是否为布局组件（用于设置 layoutOnly 默认值）
   *
   * 处理流程：
   * 1. 从路径中提取文件名（不含扩展名）
   * 2. 将 PascalCase 转换为 kebab-case 作为组件类型标识
   * 3. 注册组件实例到实例注册表
   * 4. 如果组件导出了元数据，注册到元数据注册表
   *
   * 元数据获取优先级：
   * 1. module.default.__meta - 组件默认导出中附加的元数据
   * 2. module.meta - 组件文件中单独导出的 meta 对象
   */
  function registerModules(
    modules: Record<string, { default: any; meta?: ComponentMeta }>,
    logPrefix: string,
    isLayoutComponent: boolean = false
  ) {
    for (const [path, module] of Object.entries(modules)) {
      // 从路径中提取文件名（不含扩展名）
      // 正则解释: \/([^/]+)\.vue$
      // - \/ 匹配最后一个斜杠
      // - ([^/]+) 捕获组：匹配文件名（不含斜杠的任意字符）
      // - \.vue$ 匹配 .vue 扩展名
      const match = path.match(/\/([^/]+)\.vue$/);

      if (match && match[1]) {
        const componentName = match[1];

        // PascalCase -> kebab-case
        // 转换步骤：
        // 1. replace(/([A-Z])/g, "-$1") - 在每个大写字母前加连字符
        // 2. toLowerCase() - 全部转小写
        // 3. replace(/^-/, "") - 移除开头的连字符
        const type = componentName
          .replace(/([A-Z])/g, "-$1")
          .toLowerCase()
          .replace(/^-/, "");

        // ============================================================
        // 1. 注册组件实例
        // ============================================================
        // module.default 是 Vue 组件的默认导出
        registerComponentInstance(type, module.default);

        // ============================================================
        // 2. 注册组件元数据（如果有）
        // ============================================================
        // 元数据可以从以下位置获取：
        // - module.default.__meta: 附加在组件默认导出上的元数据
        // - module.meta: 组件文件中单独导出的 meta 对象
        const componentMeta: ComponentMeta | undefined =
          module.default?.__meta || module.meta;

        if (componentMeta) {
          // 注册元数据，确保 type 与实例注册一致
          registerComponent({
            ...componentMeta,
            type, // 使用从文件名转换的 type，确保一致性
            // 布局组件默认设置 layoutOnly: true（除非显式指定）
            layoutOnly: componentMeta.layoutOnly ?? isLayoutComponent,
          });

          if (import.meta.dev) {
            console.log(`[${logPrefix}] ${type} (含元数据) <- ${path}`);
          }
        } else {
          // 无元数据的组件只能用于渲染，不会出现在编辑器组件面板中
          if (import.meta.dev) {
            console.log(`[${logPrefix}] ${type} (无元数据) <- ${path}`);
          }
        }
      }
    }
  }

  // ============================================================
  // 注册业务组件 (shop 目录)
  // ============================================================

  /**
   * import.meta.glob 是 Vite 提供的特殊语法，用于批量导入模块
   *
   * 参数说明：
   * - 路径模式: "@/components/shop/**\/*.vue"
   *   - @/ 是 Nuxt 的路径别名，指向 app/ 目录
   *   - **\/ 表示匹配任意深度的子目录
   *   - *.vue 表示匹配所有 Vue 文件
   *
   * - eager: true
   *   - 同步导入所有匹配的模块
   *   - 确保组件在渲染前已经加载完成
   *
   * 返回值格式：
   * {
   *   "/components/shop/NoticeBar.vue": { default: VueComponent, meta?: ComponentMeta },
   *   ...
   * }
   */
  const shopComponents = import.meta.glob("@/components/shop/**/*.vue", {
    eager: true,
  }) as Record<string, { default: any; meta?: ComponentMeta }>;

  // isLayoutComponent = false: 业务组件不是布局专用
  registerModules(shopComponents, "组件注册", false);

  // ============================================================
  // 注册布局组件 (shop-layout 目录)
  // ============================================================

  /**
   * 布局组件是用于构建页面整体结构的组件
   * 例如：页头 (Header)、页脚 (Footer)、导航栏 (Navigation)、页面插槽 (PageSlot) 等
   *
   * PageSlot 是一个特殊的布局组件，用于在布局中标记页面内容的插入位置
   * LayoutRenderer 会识别 PageSlot 并在该位置渲染实际的页面内容
   *
   * 布局组件默认设置 layoutOnly: true，只在编辑布局时显示在组件面板中
   */
  const layoutComponents = import.meta.glob("@/components/shop-layout/**/*.vue", {
    eager: true,
  }) as Record<string, { default: any; meta?: ComponentMeta }>;

  // isLayoutComponent = true: 布局组件默认只在布局编辑时可用
  registerModules(layoutComponents, "布局组件注册", true);
});
