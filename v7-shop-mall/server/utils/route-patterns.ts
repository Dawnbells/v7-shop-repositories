const STATIC_PREFIXES = ["/_nuxt", "/__nuxt", "/builder"];
const PUBLIC_ROUTES = new Set(["/health"]);

/**
 * 判断当前路径是否应跳过 server middleware 处理。
 * 默认跳过静态资源、builder、以及所有 /api/ 路由。
 * 可通过 allowApiCheckout 放行 /api/checkout/。
 */
export function shouldSkipMiddleware(
  path: string,
  options?: { allowApiCheckout?: boolean },
): boolean {
  const pathname = path.split("?", 1)[0];
  const normalizedPath =
    pathname.length > 1 && pathname.endsWith("/")
      ? pathname.slice(0, -1)
      : pathname;
  if (PUBLIC_ROUTES.has(normalizedPath)) return true;
  for (const prefix of STATIC_PREFIXES) {
    if (path.startsWith(prefix)) return true;
  }
  if (path.startsWith("/api/")) {
    return !(options?.allowApiCheckout && path.startsWith("/api/checkout/"));
  }
  return false;
}

export const PRODUCT_ROUTE = /^\/product\/[\w-]+(\?.*)?$/;

export function isProductRoute(path: string): boolean {
  return PRODUCT_ROUTE.test(path);
}
