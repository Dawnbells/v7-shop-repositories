/**
 * 域名解析中间件
 * 从请求中提取 host，查询数据库获取域名和公司信息
 * 设置 PageContext 中的域名相关实体
 *
 * 本地开发时可通过 NUXT_DEV_DOMAIN 环境变量指定模拟域名
 * 未配置域名或域名不存在时显示"店铺不存在"
 */

import { findDomainByFullName } from "../repositories/domainRepository";
import { getPageContext, updatePageContext } from "../utils/page-context";
import { showSafePage, SafePageType } from "../utils/safe-page";
import { logger } from "../utils/logger";

export default defineEventHandler(async (event) => {
  const path = event.path;

  // 跳过不需要域名解析的路由
  if (
    path.startsWith("/api/builder/") ||
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
    showSafePage(event, SafePageType.SHOP_NOT_FOUND);
    return;
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
      showSafePage(event, SafePageType.SHOP_NOT_FOUND);
      return;
    }
  }

  try {
    const result = await findDomainByFullName(queryDomain);
    logger.log("[01-domain] result:", result, queryDomain, host, isLocalDev);
    if (result) {
      // 本地开发时替换 fullName 为实际 host
      if (isLocalDev) {
        result.subDomain.fullName = host;
      }

      // 将所有实体存入 PageContext
      updatePageContext(event, {
        subDomain: result.subDomain,
        topLevelDomain: result.topLevelDomain,
        country: result.country,
        currency: result.currency,
        company: result.company,
        salesUser: result.salesUser,
      });
    } else {
      logger.warn(`[01-domain] Domain not found: ${queryDomain}`);
      showSafePage(event, SafePageType.SHOP_NOT_FOUND);
    }
  } catch (error) {
    logger.error("[01-domain] Error querying domain:", error);
    showSafePage(event, SafePageType.SHOP_NOT_FOUND);
  }
});
