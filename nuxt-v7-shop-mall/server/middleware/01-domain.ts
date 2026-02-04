/**
 * 域名查询 Server Middleware
 * 根据当前访问域名查询商城信息，将结果注入到 event.context
 */

import { SafePageType } from "~/types/page-context";
import { showSafePage } from "../utils/safe-page";
import { updatePageContext } from "../utils/page-context";
import { findByFullName } from "../cache/domain.cache";

// 产品路由正则（匹配 /product/{id} 或 /product/{id}?xxx=xxx）
const PRODUCT_ROUTE = /^\/product\/([\w-]+)/;
// SPU Cookie 名称
const SPU_COOKIE = "spu_id";
// SPU Cookie 有效期（1年）
const SPU_MAX_AGE = 365 * 24 * 60 * 60;

export default defineEventHandler(async (event) => {
  const path = event.path || "";

  // 跳过 API 和 builder 路由（不需要经过域名检查中间件）
  if (path.startsWith("/api/") || path.startsWith("/builder")) {
    return;
  }

  const config = useRuntimeConfig();
  const headers = getHeaders(event);
  const host = headers.host || "";

  // 移除端口号
  let fullName = host.split(":")[0];

  // 本地开发时使用配置的开发域名
  if (fullName === "localhost" || fullName === "127.0.0.1") {
    if (config.devDomain) {
      fullName = config.devDomain;
      console.log("[Domain Middleware] Using dev domain:", fullName);
    } else {
      // 未配置开发域名，跳过域名检查
      showSafePage(event, SafePageType.SHOP_NOT_FOUND);
      return;
    }
  }

  // 处理 spuId：从路径提取或从 cookie 读取
  let spuId: number | undefined;
  const productMatch = path.match(PRODUCT_ROUTE);

  if (productMatch) {
    // 从路径提取 spuId
    spuId = Number(productMatch[1]) || undefined;
    if (spuId) {
      setCookie(event, SPU_COOKIE, String(spuId), {
        maxAge: SPU_MAX_AGE,
        path: "/",
      });
      console.log("[Domain Middleware] Set spuId from path:", spuId);
    }
  } else {
    // 从 cookie 读取 spuId
    const cookieValue = getCookie(event, SPU_COOKIE);
    spuId = cookieValue ? Number(cookieValue) || undefined : undefined;
    if (spuId) {
      console.log("[Domain Middleware] Got spuId from cookie:", spuId);
    }
  }

  console.log("[Domain Middleware] Checking domain:", fullName);

  try {
    // 查询域名信息（包含国家、货币、语言、公司、顶级域名、销售用户，使用 Redis 缓存）
    const domainInfo = await findByFullName(fullName ?? "");

    if (!domainInfo) {
      console.log("[Domain Middleware] Domain not found:", fullName);
      showSafePage(event, SafePageType.SHOP_NOT_FOUND);
      return;
    }

    console.log(
      "[Domain Middleware] Domain found:",
      domainInfo.domain.id,
      domainInfo.domain.name
    );

    // 校验必须字段是否存在
    const {
      domain,
      country,
      currency,
      languages,
      company,
      topLevelDomain,
      salesUser,
    } = domainInfo;

    if (
      !country ||
      !currency ||
      !languages?.length ||
      !company ||
      !topLevelDomain ||
      !salesUser
    ) {
      console.log("[Domain Middleware] Domain data incomplete:", {
        hasCountry: !!country,
        hasCurrency: !!currency,
        hasLanguages: !!languages?.length,
        hasCompany: !!company,
        hasTopLevelDomain: !!topLevelDomain,
        hasSalesUser: !!salesUser,
      });
      showSafePage(event, SafePageType.SHOP_CLOSED);
      return;
    }

    // 更新 pageContext
    updatePageContext(event, {
      domain,
      country,
      currency,
      languages,
      company,
      topLevelDomain,
      salesUser,
      spuId,
    });
  } catch (error) {
    console.error("[Domain Middleware] Database error:", error);
    // 数据库错误时降级处理：允许继续访问
    // 可根据业务需求调整为显示错误页面
  }
});
