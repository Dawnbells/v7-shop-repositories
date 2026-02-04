import type { PageContext } from "~/types/page-context";

// ============ 类型工具 ============

/** 所有支持的字段路径 */
type PageContextPaths =
  // domain 相关
  | "domain"
  | "domain.id"
  | "domain.fullName"
  | "domain.name"
  | "domain.type"
  | "domain.status"
  | "domain.companyId"
  | "domain.websiteId"
  | "domain.themeId"
  | "domain.countryId"
  | "domain.currencyId"
  | "domain.languageId"
  | "domain.analyzeSuccess"
  // cloak 相关
  | "cloak"
  | "cloak.page"
  | "cloak.isAdmin"
  | "cloak.pdVal"
  | "cloak.remote"
  // 其他
  | "safePageType"
  | "landingSpuId"
  | "themeConfig"
  | "siteConfig"
  | "variableValues";

/** 根据路径获取值类型 */
type PathValue<T, P extends string> = P extends keyof T
  ? T[P]
  : P extends `${infer K}.${infer Rest}`
    ? K extends keyof T
      ? T[K] extends object
        ? PathValue<T[K], Rest>
        : never
      : never
    : never;

/** 根据路径设置嵌套对象的类型 */
type SetPath<P extends string, V> = P extends `${infer K}.${infer Rest}`
  ? { [Key in K]: SetPath<Rest, V> }
  : { [Key in P]: V };

/** 合并多个对象类型（深度合并） */
type DeepMerge<T, U> = T extends object
  ? U extends object
    ? {
        [K in keyof T | keyof U]: K extends keyof T & keyof U
          ? DeepMerge<T[K], U[K]>
          : K extends keyof T
            ? T[K]
            : K extends keyof U
              ? U[K]
              : never;
      }
    : U
  : U;

/** 将联合类型转换为交叉类型 */
type UnionToIntersection<U> = (U extends any ? (k: U) => void : never) extends (k: infer I) => void
  ? I
  : never;

/** 根据路径数组构建结果类型 */
type BuildResult<Paths extends PageContextPaths[]> = UnionToIntersection<
  {
    [K in keyof Paths]: Paths[K] extends PageContextPaths
      ? SetPath<Paths[K], PathValue<PageContext, Paths[K]>>
      : never;
  }[number]
>;

/** 简化类型显示 */
type Simplify<T> = T extends object ? { [K in keyof T]: Simplify<T[K]> } : T;

/** 最终的 PickedPageContext 类型 */
type PickedPageContext<Paths extends PageContextPaths[]> = Simplify<DeepMerge<{}, BuildResult<Paths>>>;

// ============ 运行时函数 ============

/**
 * 根据路径从对象中提取值
 */
function pickByPaths<T extends object>(obj: T, paths: string[]): any {
  const result: any = {};

  for (const path of paths) {
    const keys = path.split(".");
    let source: any = obj;
    let target: any = result;

    for (let i = 0; i < keys.length; i++) {
      const key = keys[i];
      if (source == null || key == null || !(key in source)) break;

      if (i === keys.length - 1) {
        target[key] = source[key];
      } else {
        target[key] = target[key] || {};
        target = target[key];
        source = source[key];
      }
    }
  }

  return result;
}

/** 将类型的所有属性变为可选（深度） */
type DeepPartial<T> = T extends object
  ? { [K in keyof T]?: DeepPartial<T[K]> }
  : T;

// ============ 函数重载 ============

/**
 * 获取页面上下文（由服务端中间件注入）
 * 使用 useState 确保 SSR 数据在客户端水合时保持一致
 * 永远返回对象，不会返回 undefined
 *
 * @param fields 需要传递到客户端的字段路径，不传则传递全部
 * @example
 * // 只传递 cloak.page 和 cloak.isAdmin，访问时无需 ?
 * const pageContext = usePageContext(['cloak.page', 'cloak.isAdmin']);
 * console.log(pageContext.value.cloak.page); // 无需 ?
 *
 * // 传递整个 cloak 对象
 * const pageContext = usePageContext(['cloak']);
 * console.log(pageContext.value.cloak.isAdmin); // 无需 ?
 *
 * // 传递全部数据（属性可能为空）
 * const pageContext = usePageContext();
 */
export function usePageContext(): Ref<DeepPartial<PageContext>>;
export function usePageContext<K extends PageContextPaths>(
  fields: K[]
): Ref<PickedPageContext<K[]>>;
export function usePageContext<K extends PageContextPaths>(
  fields?: K[]
): Ref<any> {
  const stateKey = fields ? `pageContext:${fields.sort().join(",")}` : "pageContext";

  return useState(stateKey, () => {
    const event = useRequestEvent();
    const fullContext = event?.context?.pageContext as PageContext | undefined;

    if (!fullContext) return {};
    if (!fields || fields.length === 0) return fullContext;

    return pickByPaths(fullContext, fields);
  });
}
