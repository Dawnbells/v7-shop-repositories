/**
 * 斗篷检查 Server Middleware
 * 在请求到达页面前进行风控检查，将结果注入到 PageContext
 */

import { ProxyAgent } from "undici";
import type { H3Event } from "h3";
import type { CloakCheckRequest, CloakCheckResponse } from "../types/cloak";
import { CloakPage } from "../types/cloak";
import { showSafePage, SafePageType } from "../utils/safe-page";
import { getPageContext, updatePageContext } from "../utils/page-context";
import { logger } from "../utils/logger";

// 缓存代理 Agent
let proxyAgent: ProxyAgent | null = null;

// Fingerprint Cookie 名称
const FINGERPRINT_COOKIE = "fp";
// Fingerprint Cookie 有效期（1年）
const FINGERPRINT_MAX_AGE = 365 * 24 * 60 * 60;

// Cloak Cookie 名称（伪装为 page visit）
const CLOAK_COOKIE = "_pv";
// Cloak Cookie 有效期（1年）
const CLOAK_MAX_AGE = 365 * 24 * 60 * 60;

// 需要进行斗篷检查的路由模式（产品详情页）
const PRODUCT_ROUTE = /^\/product\/[\w-]+(\?.*)?$/;

// CloakPage 枚举到数字的映射
const CLOAK_PAGE_TO_NUM: Record<CloakPage, number> = {
  [CloakPage.LAND]: 0,
  [CloakPage.CLOAK]: 1,
  [CloakPage.CRAWLER]: 2,
  [CloakPage.RISK]: 3,
  [CloakPage.BLACKLISTED]: 4,
};

const NUM_TO_CLOAK_PAGE: Record<number, CloakPage> = {
  0: CloakPage.LAND,
  1: CloakPage.CLOAK,
  2: CloakPage.CRAWLER,
  3: CloakPage.RISK,
  4: CloakPage.BLACKLISTED,
};

// 允许通过 preview 参数强制指定的 CloakPage 类型（不包含 LAND）
const PREVIEW_ALLOWED_PAGES: Set<string> = new Set([
  CloakPage.CLOAK,
  CloakPage.CRAWLER,
  CloakPage.RISK,
  CloakPage.BLACKLISTED,
]);

/**
 * 检查是否是产品路由
 */
function isProductRoute(path: string): boolean {
  return PRODUCT_ROUTE.test(path);
}

/**
 * 从 query 参数获取 preview 模式的 CloakPage
 * 只允许 CLOAK、CRAWLER、RISK、BLACKLISTED，不允许 LAND
 */
function getPreviewCloakPage(event: H3Event): CloakPage | null {
  const query = getQuery(event);
  const preview = query.preview as string | undefined;

  if (preview && PREVIEW_ALLOWED_PAGES.has(preview)) {
    return preview as CloakPage;
  }
  return null;
}

/**
 * 序列化 cloakResult 到 cookie 值
 * 格式: {remote}-{page}-{isAdmin}-{pdVal}
 */
function serializeCloakResult(result: CloakCheckResponse): string {
  const remote = result.remote ? 1 : 0;
  const page = CLOAK_PAGE_TO_NUM[result.page];
  const isAdmin = result.isAdmin ? 1 : 0;
  return `${remote}-${page}-${isAdmin}-${result.pdVal}`;
}

/**
 * 从 cookie 值反序列化 cloakResult
 */
function deserializeCloakResult(value: string): CloakCheckResponse | null {
  const parts = value.split("-");
  if (parts.length < 4) return null;
  const [remote, page, isAdmin, ...pdValParts] = parts;
  const pageNum = parseInt(page, 10);
  if (!(pageNum in NUM_TO_CLOAK_PAGE)) return null;
  return {
    remote: remote === "1",
    page: NUM_TO_CLOAK_PAGE[pageNum],
    isAdmin: isAdmin === "1",
    pdVal: pdValParts.join("-"), // pdVal 可能包含 "-"
  };
}

/**
 * 生成随机 UUID
 */
function generateUUID(): string {
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

/**
 * 获取或生成 fingerprint
 * 如果 Cookie 中没有，则生成新的并设置到响应 Cookie
 */
function getOrCreateFingerprint(event: H3Event): string {
  // 从 Cookie 获取
  const existingFp = getCookie(event, FINGERPRINT_COOKIE);
  if (existingFp) {
    return existingFp;
  }

  // 生成新的 fingerprint
  const newFp = generateUUID();

  // 设置到响应 Cookie
  setCookie(event, FINGERPRINT_COOKIE, newFp, {
    maxAge: FINGERPRINT_MAX_AGE,
    httpOnly: true,
    secure: true,
    sameSite: "lax",
    path: "/",
  });

  logger.log("[Cloak Middleware] Generated new fingerprint:", newFp);
  return newFp;
}

/**
 * 构建斗篷检查请求
 */
function buildCloakRequest(event: H3Event): CloakCheckRequest {
  const headers = getHeaders(event);
  const host = headers.host || "";
  const protocol = headers["x-forwarded-proto"] || "https";
  const req = event.node?.req;

  let fullPath = "";
  if (req && typeof req.url === "string") {
    // req.url 包含 path 和 query
    fullPath = req.url;
  } else if (event.path) {
    // event.path 通常只包含 path，不含 query
    fullPath = event.path;
    if (
      event.node?.req?.originalUrl &&
      typeof event.node.req.originalUrl === "string"
    ) {
      fullPath = event.node.req.originalUrl; // 某些 node 服务器有 originalUrl
    }
  }

  const url = `${protocol}://${host}${fullPath}`;

  // 获取客户端 IP
  const clientIp =
    (headers["x-forwarded-for"] as string)?.split(",")[0]?.trim() ||
    (headers["x-real-ip"] as string) ||
    event.node.req.socket?.remoteAddress ||
    "";

  // 构建请求头 Map
  const headerMap: Record<string, string> = {};
  for (const [key, value] of Object.entries(headers)) {
    if (typeof value === "string") {
      headerMap[key] = value;
    } else if (Array.isArray(value)) {
      headerMap[key] = (value as string[]).join(", ");
    }
  }

  // 获取或生成 fingerprint
  const fingerprint = getOrCreateFingerprint(event);

  // 从 pageContext 获取域名相关信息
  const pageContext = getPageContext(event);

  // 将 fingerprint 存入 pageContext
  updatePageContext(event, { fingerprint });

  return {
    clientIp,
    requestUrl: url,
    spuId: pageContext.spuId ?? undefined,
    headers: headerMap,
    fingerprint,
    cloakStrategy: pageContext.topLevelDomain?.cloakStrategy ?? "DEFAULT",
    accessKey: pageContext.company?.accessKey ?? undefined,
    continentCode: pageContext.country?.continentCode ?? undefined,
    countryCode: pageContext.country?.code ?? undefined,
    companyDomain: pageContext.company?.domain ?? undefined,
    userId: pageContext.salesUser?.id ?? undefined,
    deptId: pageContext.salesUser?.departmentId ?? undefined,
  };
}

/**
 * 获取代理 Agent（如果配置了代理）
 */
function getProxyAgent(proxyUrl: string): ProxyAgent {
  if (!proxyAgent) {
    proxyAgent = new ProxyAgent(proxyUrl);
  }
  return proxyAgent;
}

/**
 * 调用风控服务
 * @param request 风控请求
 * @param fallbackPage 降级策略页面（远程调用失败时使用）
 */
async function performCloakCheck(
  request: CloakCheckRequest,
  fallbackPage: CloakPage = CloakPage.LAND,
): Promise<CloakCheckResponse> {
  const config = useRuntimeConfig();
  try {
    const fetchOptions: any = {
      method: "POST",
      body: request,
      headers: {
        "Content-Type": "application/json",
      },
      timeout: 5000,
    };

    // 如果配置了代理，使用代理
    if (config.httpProxy) {
      fetchOptions.dispatcher = getProxyAgent(config.httpProxy);
    }

    const response = await $fetch<CloakCheckResponse>(
      `${config.riskServiceUrl}/cloak/initial`,
      fetchOptions,
    );
    return {
      ...response,
      remote: true,
    };
  } catch (error) {
    logger.error("[Cloak Middleware] Check failed:", error);
    // 降级策略：使用公司配置的 fallbackPage
    return {
      remote: false,
      page: fallbackPage,
      pdVal: "",
      isAdmin: false,
    };
  }
}

export default defineEventHandler(async (event) => {
  const path = event.path || "";

  // 跳过不需要斗篷检查的路由
  if (
    path.startsWith("/api/") ||
    path.startsWith("/builder") ||
    path.startsWith("/_nuxt") ||
    path.startsWith("/__nuxt")
  ) {
    return;
  }

  logger.log("[Cloak Middleware] path:", path);

  // 获取降级策略
  const pageContext = getPageContext(event);
  const cloakFallback = pageContext.company?.cloakFallback;
  const fallbackPage = (cloakFallback as CloakPage) ?? CloakPage.LAND;

  let cloakResult: CloakCheckResponse | null = null;

  // 检查是否有 preview 参数强制指定 cloak 类型
  const previewPage = getPreviewCloakPage(event);
  if (previewPage) {
    logger.log(
      "[Cloak Middleware] Preview mode, forcing cloak page:",
      previewPage,
    );
    cloakResult = {
      remote: false,
      page: previewPage,
      pdVal: "preview",
      isAdmin: false,
    };
  } else if (isProductRoute(path)) {
    // 产品路由：远程调用风控服务
    const request = buildCloakRequest(event);
    logger.log("[Cloak Middleware] request:", request);
    cloakResult = await performCloakCheck(request, fallbackPage);
    logger.log("[Cloak Middleware] result:", cloakResult);

    // 存入 cookie
    setCookie(event, CLOAK_COOKIE, serializeCloakResult(cloakResult), {
      maxAge: CLOAK_MAX_AGE,
      path: "/",
    });
    logger.log("[Cloak Middleware] Saved cloak result to cookie");
  } else {
    // 非产品路由：从 cookie 读取
    const cookieValue = getCookie(event, CLOAK_COOKIE);
    if (cookieValue) {
      cloakResult = deserializeCloakResult(cookieValue);
      logger.log(
        "[Cloak Middleware] Got cloak result from cookie:",
        cloakResult,
      );
    }
    if (!cloakResult) {
      // 没有 cookie，使用降级策略
      logger.log(
        "[Cloak Middleware] No cookie, using fallback:",
        fallbackPage,
      );
      cloakResult = {
        remote: false,
        page: fallbackPage,
        pdVal: "",
        isAdmin: false,
      };
    }
  }

  // BLACKLISTED 显示安全页面
  if (cloakResult.page === CloakPage.BLACKLISTED) {
    showSafePage(event, SafePageType.SHOP_NOT_FOUND, {
      trackingId: cloakResult.pdVal,
    });
    return;
  }

  // 正常访问，将 cloak 结果注入到 context
  updatePageContext(event, { cloak: cloakResult });
});
