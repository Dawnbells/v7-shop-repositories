/**
 * 域名解析中间件
 * 从请求中提取 host，查询数据库获取域名和公司信息
 * 解析 URL 路径中的 spuId（产品页写入 Cookie，其他页面从 Cookie 读取）
 * 设置 PageContext 中的域名相关实体
 *
 * 本地开发时可通过 NUXT_DEV_DOMAIN 环境变量指定模拟域名
 * 未配置域名或域名不存在时显示"店铺不存在"
 */

import { findDomainByFullName } from "../repositories/domainRepository";
import { updatePageContext } from "../utils/page-context";
import { showSafePage, SafePageType } from "../utils/safe-page";
import { logger } from "../utils/logger";

// spuId Cookie 配置
const SPU_ID_COOKIE = "_spuId";
const SPU_ID_MAX_AGE = 30 * 24 * 60 * 60; // 30 天

export default defineEventHandler(async (event) => {
  const path = event.path;

  // 跳过不需要域名解析的路由
  if (
    path.startsWith("/api/") ||
    path.startsWith("/builder") ||
    path.startsWith("/_nuxt") ||
    path.startsWith("/__nuxt")
  ) {
    return;
  }

  // 获取请求的 host
  const host = getRequestHost(event, { xForwardedHost: true });

  if (!host) {
    logger.warn("[01-domain] No host found in request");
    showSafePage(SafePageType.SHOP_NOT_FOUND);
  }

  // 判断是否为本地开发环境
  const isLocalDev = host.includes("localhost") || host.includes("127.0.0.1");

  // 确定要查询的域名
  let queryDomain = host;

  if (isLocalDev) {
    const config = useRuntimeConfig();
    const devDomain = config.devDomain as string;

    if (devDomain) {
      queryDomain = devDomain;
    } else {
      // 未配置开发域名，无法确定店铺
      logger.warn("[01-domain] No devDomain configured for local development");
      showSafePage(SafePageType.SHOP_NOT_FOUND);
    }
  }

  // 获取 spuId：产品页从 URL 解析并写入 Cookie，其他页面从 Cookie 读取
  let spuId: number | null = null;
  const pathParts = path.split("/").filter(Boolean);

  if (pathParts[0] === "product" && pathParts[1]) {
    // 产品页：从 URL 解析 spuId
    spuId = parseInt(pathParts[1], 10) || null;

    // 写入或更新 Cookie
    if (spuId) {
      setCookie(event, SPU_ID_COOKIE, String(spuId), {
        maxAge: SPU_ID_MAX_AGE,
        path: "/",
      });
    }
  } else {
    // 其他页面：从 Cookie 读取 spuId
    const cookieValue = getCookie(event, SPU_ID_COOKIE);
    if (cookieValue) {
      spuId = parseInt(cookieValue, 10) || null;
    }
  }

  try {
    const result = await findDomainByFullName(queryDomain);
    logger.log("[01-domain] result:", result, queryDomain, host, isLocalDev);
    if (!result) {
      logger.warn(`[01-domain] Domain not found: ${queryDomain}`);
      showSafePage(SafePageType.SHOP_NOT_FOUND);
      return;
    }

    // 检查所有必需字段是否存在
    const { subDomain, topLevelDomain, country, currency, company, salesUser } = result;
    
    if (!subDomain) {
      logger.warn(`[01-domain] SubDomain is null for: ${queryDomain}`);
      showSafePage(SafePageType.SHOP_NOT_FOUND);
      return;
    }
    if (!topLevelDomain) {
      logger.warn(`[01-domain] TopLevelDomain is null for: ${queryDomain}`);
      showSafePage(SafePageType.SHOP_NOT_FOUND);
      return;
    }
    if (!country) {
      logger.warn(`[01-domain] Country is null for: ${queryDomain}`);
      showSafePage(SafePageType.SHOP_NOT_FOUND);
      return;
    }
    if (!currency) {
      logger.warn(`[01-domain] Currency is null for: ${queryDomain}`);
      showSafePage(SafePageType.SHOP_NOT_FOUND);
      return;
    }
    if (!company) {
      logger.warn(`[01-domain] Company is null for: ${queryDomain}`);
      showSafePage(SafePageType.SHOP_NOT_FOUND);
      return;
    }
    if (!salesUser) {
      logger.warn(`[01-domain] SalesUser is null for: ${queryDomain}`);
      showSafePage(SafePageType.SHOP_NOT_FOUND);
      return;
    }

    // 本地开发时替换 fullName 为实际 host
    if (isLocalDev) {
      subDomain.fullName = host;
    }

    // 将所有实体存入 PageContext
    updatePageContext(event, {
      subDomain,
      topLevelDomain,
      country,
      currency,
      company,
      salesUser,
      spuId,
    });
  } catch (error) {
    logger.error("[01-domain] Error querying domain:", error);
    showSafePage(SafePageType.SHOP_NOT_FOUND);
  }
});
