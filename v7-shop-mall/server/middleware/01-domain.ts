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
import { shouldSkipMiddleware } from "../utils/route-patterns";
import { logger } from "../utils/logger";

// spuId Cookie 配置
const SPU_ID_COOKIE = "_spuId";
const SPU_ID_MAX_AGE = 365 * 24 * 60 * 60; // 30 天

// languageId Cookie 配置
const LANGUAGE_ID_COOKIE = "_languageId";
const LANGUAGE_ID_MAX_AGE = 365 * 24 * 60 * 60; // 1 年

// from_url Cookie 配置（用于 Header Logo 跳转）
const FROM_URL_COOKIE = "_from_url";
const FROM_URL_MAX_AGE = 365 * 24 * 60 * 60; // 30 天

export default defineEventHandler(async (event) => {
  const path = event.path;

  if (shouldSkipMiddleware(path, { allowApiCheckout: true })) {
    return;
  }

  // 获取请求的 host
  const host = getRequestHost(event, { xForwardedHost: true });

  if (!host) {
    logger.warn("[01-domain] No host found in request");
    return showSafePage(SafePageType.SHOP_NOT_FOUND);
  }

  // 判断是否为本地开发环境
  const isLocalDev =
    host.includes("localhost") ||
    host.includes("127.0.0.1") ||
    host.includes("192.168.0.26");

  // 确定要查询的域名
  let queryDomain = host;

  if (isLocalDev) {
    const config = useRuntimeConfig();
    const devDomain = config.devDomain as string;

    if (devDomain) {
      queryDomain = devDomain;
    } else {
      logger.warn("[01-domain] No devDomain configured for local development");
      return showSafePage(SafePageType.SHOP_NOT_FOUND);
    }
  }

  // 获取 spuId：产品页从 URL 解析并写入 Cookie，其他页面从 Cookie 读取
  let spuId: number | null = null;
  const pathParts = path.split("/").filter(Boolean);

  if (pathParts[0] === "product" && pathParts[1]) {
    // 产品页：从 URL 解析 spuId
    spuId = parseInt(pathParts[1], 10) || null;

    // 保存完整 URL 到 from_url cookie（用于 Header Logo 跳转）
    setCookie(event, FROM_URL_COOKIE, path, {
      maxAge: FROM_URL_MAX_AGE,
      path: "/",
    });

    // 写入或更新 spuId Cookie
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
    const _t0 = performance.now();
    const result = await findDomainByFullName(queryDomain);
    const _t1 = performance.now();
    logger.debug(
      `[01-domain timing] findDomainByFullName: ${(_t1 - _t0).toFixed(1)}ms (${path})`,
    );
    if (!result) {
      logger.warn(`[01-domain] Domain not found: ${queryDomain}`);
      showSafePage(SafePageType.SHOP_NOT_FOUND);
      return;
    }

    // 检查所有必需字段是否存在
    const { subDomain, topLevelDomain, country, currency, company, salesUser } =
      result;

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

    // 语言选择逻辑：URL参数 > Cookie > 列表第一个
    const languages = country.languages || [];
    let currentLanguageId: number | null = null;
    let currentLanguage = languages[0] || null;

    if (languages.length > 0) {
      const languageMap = new Map(languages.map((l) => [l.id, l]));

      // 1. 优先从 URL 参数获取
      const urlQuery = getQuery(event);
      const urlLanguageId = urlQuery.languageId
        ? parseInt(String(urlQuery.languageId), 10)
        : null;

      if (urlLanguageId && languageMap.has(urlLanguageId)) {
        currentLanguageId = urlLanguageId;
        currentLanguage = languageMap.get(urlLanguageId)!;
      } else {
        // 2. 其次从 Cookie 获取
        const cookieLanguageId = getCookie(event, LANGUAGE_ID_COOKIE);
        const parsedCookieLanguageId = cookieLanguageId
          ? parseInt(cookieLanguageId, 10)
          : null;

        if (parsedCookieLanguageId && languageMap.has(parsedCookieLanguageId)) {
          currentLanguageId = parsedCookieLanguageId;
          currentLanguage = languageMap.get(parsedCookieLanguageId)!;
        } else {
          // 3. 使用列表第一个作为默认值
          currentLanguageId = languages[0]!.id;
          currentLanguage = languages[0]!;
        }
      }

      // 将选中的语言ID写入 Cookie
      setCookie(event, LANGUAGE_ID_COOKIE, String(currentLanguageId), {
        maxAge: LANGUAGE_ID_MAX_AGE,
        path: "/",
      });
    } else {
      logger.warn(
        `[01-domain] No languages found for country: ${country.code}`,
      );
    }

    // 将所有实体存入 PageContext
    updatePageContext(event, {
      subDomain,
      topLevelDomain,
      country,
      currency,
      company,
      salesUser,
      currentLanguageId,
      currentLanguage,
      spuId,
    });
  } catch (error) {
    const requestUrl = getRequestURL(event, {
      xForwardedHost: true,
      xForwardedProto: true,
    });
    logger.error(
      `[01-domain] Error querying domain, url=${requestUrl.href}:`,
      error,
    );
    showSafePage(SafePageType.SHOP_NOT_FOUND);
  }
});
