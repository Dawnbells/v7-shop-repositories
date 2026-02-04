/**
 * 落地页产品信息缓存层
 * 使用 Redis 缓存优化落地页产品查询性能
 */

import type { ProductInfo } from "~/types/page-context";
import { 
  findProductBySpuId as findProductBySpuIdFromDb,
  findCloakLandingProduct as findCloakLandingProductFromDb,
  findLandingPageConfig as findLandingPageConfigFromDb,
  type LandingPageConfig
} from "../repositories/landing.repository";
import { getRedis } from "../utils/redis";

/** 产品缓存 key 前缀 */
const PRODUCT_CACHE_PREFIX = "product:";

/** CLOAK 落地页缓存 key 前缀 */
const CLOAK_CACHE_PREFIX = "cloak_landing:";

/** Landing Page 配置缓存 key 前缀 */
const LANDING_CONFIG_CACHE_PREFIX = "landing_config:";

/** 缓存 TTL（秒） */
const CACHE_TTL = 60 * 60; // 1 hour

/**
 * 生成产品缓存 key
 * 格式: product:{subDomainId}:{spuId}:{languageId}
 */
function getProductCacheKey(subDomainId: number, spuId: number, languageId: number): string {
  return `${PRODUCT_CACHE_PREFIX}${subDomainId}:${spuId}:${languageId}`;
}

/**
 * 生成 CLOAK 落地页缓存 key
 * 格式: cloak_landing:{subDomainId}:{spuId}:{languageId}
 */
function getCloakCacheKey(subDomainId: number, spuId: number, languageId: number): string {
  return `${CLOAK_CACHE_PREFIX}${subDomainId}:${spuId}:${languageId}`;
}

/**
 * 生成 Landing Page 配置缓存 key
 * 格式: landing_config:{subDomainId}:{spuId}:{landingPageType}
 */
function getLandingConfigCacheKey(subDomainId: number, spuId: number, landingPageType: string): string {
  return `${LANDING_CONFIG_CACHE_PREFIX}${subDomainId}:${spuId}:${landingPageType}`;
}

/**
 * 根据 SPU ID 和语言 ID 查询产品信息（带缓存，用于 LAND 类型）
 * 
 * @param subDomainId 子域名 ID
 * @param spuId SPU ID
 * @param languageId 语言 ID
 */
export async function findProductBySpuId(
  subDomainId: number,
  spuId: number,
  languageId: number
): Promise<ProductInfo | null> {
  const redis = getRedis();
  const cacheKey = getProductCacheKey(subDomainId, spuId, languageId);

  try {
    // 先从缓存获取
    const cached = await redis.get(cacheKey);
    if (cached) {
      console.log("[Landing Cache] Product cache hit:", cacheKey);
      // 续期（滑动过期）
      await redis.expire(cacheKey, CACHE_TTL);
      return JSON.parse(cached) as ProductInfo;
    }
  } catch (error) {
    console.error("[Landing Cache] Redis get error:", error);
  }

  console.log("[Landing Cache] Product cache miss, fetching from DB:", cacheKey);

  // 从数据库查询
  const productInfo = await findProductBySpuIdFromDb(spuId, languageId, subDomainId);

  // 存入缓存（只缓存有效结果）
  if (productInfo) {
    try {
      await redis.setex(cacheKey, CACHE_TTL, JSON.stringify(productInfo));
    } catch (error) {
      console.error("[Landing Cache] Redis set error:", error);
    }
  }

  return productInfo;
}

/**
 * 根据子域名、SPU ID 和语言 ID 查询 CLOAK 类型的落地页产品信息（带缓存）
 * 
 * @param subDomainId 子域名 ID
 * @param spuId 原始 SPU ID
 * @param languageId 语言 ID
 */
export async function findCloakLandingProduct(
  subDomainId: number,
  spuId: number,
  languageId: number
): Promise<ProductInfo | null> {
  const redis = getRedis();
  const cacheKey = getCloakCacheKey(subDomainId, spuId, languageId);

  try {
    // 先从缓存获取
    const cached = await redis.get(cacheKey);
    if (cached) {
      console.log("[Landing Cache] Cloak cache hit:", cacheKey);
      // 续期（滑动过期）
      await redis.expire(cacheKey, CACHE_TTL);
      return JSON.parse(cached) as ProductInfo;
    }
  } catch (error) {
    console.error("[Landing Cache] Redis get error:", error);
  }

  console.log("[Landing Cache] Cloak cache miss, fetching from DB:", cacheKey);

  // 从数据库查询
  const productInfo = await findCloakLandingProductFromDb(subDomainId, spuId, languageId);

  // 存入缓存（只缓存有效结果）
  if (productInfo) {
    try {
      await redis.setex(cacheKey, CACHE_TTL, JSON.stringify(productInfo));
    } catch (error) {
      console.error("[Landing Cache] Redis set error:", error);
    }
  }

  return productInfo;
}

/**
 * 清理指定产品的缓存
 */
export async function clearProductCache(subDomainId: number, spuId: number, languageId: number): Promise<boolean> {
  const redis = getRedis();
  const cacheKey = getProductCacheKey(subDomainId, spuId, languageId);

  try {
    const result = await redis.del(cacheKey);
    const existed = result > 0;
    console.log("[Landing Cache] Product cache cleared:", cacheKey, existed ? "(existed)" : "(not found)");
    return existed;
  } catch (error) {
    console.error("[Landing Cache] Redis del error:", error);
    return false;
  }
}

/**
 * 清理指定 CLOAK 落地页产品的缓存
 */
export async function clearCloakCache(
  subDomainId: number,
  spuId: number,
  languageId: number
): Promise<boolean> {
  const redis = getRedis();
  const cacheKey = getCloakCacheKey(subDomainId, spuId, languageId);

  try {
    const result = await redis.del(cacheKey);
    const existed = result > 0;
    console.log("[Landing Cache] Cloak cache cleared:", cacheKey, existed ? "(existed)" : "(not found)");
    return existed;
  } catch (error) {
    console.error("[Landing Cache] Redis del error:", error);
    return false;
  }
}

/**
 * 清理指定产品的所有语言缓存（使用模式匹配）
 * 格式: product:{subDomainId}:{spuId}:*
 */
export async function clearProductCacheAllLanguages(
  subDomainId: number,
  spuId: number
): Promise<number> {
  const redis = getRedis();
  const pattern = `${PRODUCT_CACHE_PREFIX}${subDomainId}:${spuId}:*`;

  try {
    let totalDeleted = 0;
    let cursor = "0";

    do {
      const [newCursor, keys] = await redis.scan(cursor, "MATCH", pattern, "COUNT", 100);
      cursor = newCursor;
      if (keys.length > 0) {
        const deleted = await redis.del(...keys);
        totalDeleted += deleted;
      }
    } while (cursor !== "0");

    console.log("[Landing Cache] Product cache cleared (all languages):", pattern, "count:", totalDeleted);
    return totalDeleted;
  } catch (error) {
    console.error("[Landing Cache] Redis scan/del error:", error);
    return 0;
  }
}

/**
 * 清理指定 CLOAK 落地页产品的所有语言缓存（使用模式匹配）
 * 格式: cloak_landing:{subDomainId}:{spuId}:*
 */
export async function clearCloakCacheAllLanguages(
  subDomainId: number,
  spuId: number
): Promise<number> {
  const redis = getRedis();
  const pattern = `${CLOAK_CACHE_PREFIX}${subDomainId}:${spuId}:*`;

  try {
    let totalDeleted = 0;
    let cursor = "0";

    do {
      const [newCursor, keys] = await redis.scan(cursor, "MATCH", pattern, "COUNT", 100);
      cursor = newCursor;
      if (keys.length > 0) {
        const deleted = await redis.del(...keys);
        totalDeleted += deleted;
      }
    } while (cursor !== "0");

    console.log("[Landing Cache] Cloak cache cleared (all languages):", pattern, "count:", totalDeleted);
    return totalDeleted;
  } catch (error) {
    console.error("[Landing Cache] Redis scan/del error:", error);
    return 0;
  }
}

/**
 * 清理所有落地页产品缓存
 */
export async function clearAllLandingCache(): Promise<number> {
  const redis = getRedis();

  try {
    let totalDeleted = 0;

    // 清理 product: 开头的 key
    let cursor = "0";
    do {
      const [newCursor, keys] = await redis.scan(cursor, "MATCH", `${PRODUCT_CACHE_PREFIX}*`, "COUNT", 100);
      cursor = newCursor;
      if (keys.length > 0) {
        const deleted = await redis.del(...keys);
        totalDeleted += deleted;
      }
    } while (cursor !== "0");

    // 清理 cloak_landing: 开头的 key
    cursor = "0";
    do {
      const [newCursor, keys] = await redis.scan(cursor, "MATCH", `${CLOAK_CACHE_PREFIX}*`, "COUNT", 100);
      cursor = newCursor;
      if (keys.length > 0) {
        const deleted = await redis.del(...keys);
        totalDeleted += deleted;
      }
    } while (cursor !== "0");

    console.log("[Landing Cache] All cache cleared, count:", totalDeleted);
    return totalDeleted;
  } catch (error) {
    console.error("[Landing Cache] Redis clear error:", error);
    return 0;
  }
}

/**
 * 查询 Landing Page 配置（带缓存，用于 middleware）
 * 
 * @param subDomainId 子域名 ID
 * @param spuId SPU ID
 * @param landingPageType 落地页类型（LAND/CLOAK）
 */
export async function findLandingPageConfig(
  subDomainId: number,
  spuId: number,
  landingPageType: string
): Promise<LandingPageConfig | null> {
  const redis = getRedis();
  const cacheKey = getLandingConfigCacheKey(subDomainId, spuId, landingPageType);

  try {
    // 先从缓存获取
    const cached = await redis.get(cacheKey);
    if (cached) {
      console.log("[Landing Cache] Landing config cache hit:", cacheKey);
      // 续期（滑动过期）
      await redis.expire(cacheKey, CACHE_TTL);
      return JSON.parse(cached) as LandingPageConfig;
    }
  } catch (error) {
    console.error("[Landing Cache] Redis get error:", error);
  }

  console.log("[Landing Cache] Landing config cache miss, fetching from DB:", cacheKey);

  // 从数据库查询
  const config = await findLandingPageConfigFromDb(subDomainId, spuId, landingPageType);

  // 存入缓存（只缓存有效结果）
  if (config) {
    try {
      await redis.setex(cacheKey, CACHE_TTL, JSON.stringify(config));
    } catch (error) {
      console.error("[Landing Cache] Redis set error:", error);
    }
  }

  return config;
}

/**
 * 清理指定 Landing Page 配置的缓存
 */
export async function clearLandingConfigCache(
  subDomainId: number,
  spuId: number,
  landingPageType: string
): Promise<boolean> {
  const redis = getRedis();
  const cacheKey = getLandingConfigCacheKey(subDomainId, spuId, landingPageType);

  try {
    const result = await redis.del(cacheKey);
    const existed = result > 0;
    console.log("[Landing Cache] Landing config cache cleared:", cacheKey, existed ? "(existed)" : "(not found)");
    return existed;
  } catch (error) {
    console.error("[Landing Cache] Redis del error:", error);
    return false;
  }
}
