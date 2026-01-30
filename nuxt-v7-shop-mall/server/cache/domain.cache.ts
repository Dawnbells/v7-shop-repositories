/**
 * 域名信息缓存层
 * 使用 Redis 缓存优化域名查询性能，支持 PM2 多进程共享
 */

import type { DomainInfo } from "~/types/page-context";
import { findByFullName as findByFullNameFromDb } from "../repositories/domain.repository";
import { getRedis } from "../utils/redis";

/** 缓存 key 前缀 */
const CACHE_PREFIX = "domain:";

/** 缓存 TTL（秒） */
const CACHE_TTL = 60 * 60; // 1 hour

/**
 * 生成缓存 key
 */
function getCacheKey(fullName: string): string {
  return `${CACHE_PREFIX}${fullName}`;
}

/**
 * 根据完整域名查询有效的子域名信息（带缓存）
 * @param fullName 完整域名（如 shop.example.com）
 */
export async function findByFullName(fullName: string): Promise<DomainInfo | null> {
  const redis = getRedis();
  const cacheKey = getCacheKey(fullName);

  try {
    // 先从缓存获取
    const cached = await redis.get(cacheKey);
    if (cached) {
      console.log("[Domain Cache] Cache hit:", fullName);
      // 续期（滑动过期）
      await redis.expire(cacheKey, CACHE_TTL);
      return JSON.parse(cached) as DomainInfo;
    }
  } catch (error) {
    console.error("[Domain Cache] Redis get error:", error);
    // Redis 错误时降级到数据库查询
  }

  console.log("[Domain Cache] Cache miss, fetching from DB:", fullName);

  // 从数据库查询
  const domainInfo = await findByFullNameFromDb(fullName);

  // 存入缓存（只缓存有效结果）
  if (domainInfo) {
    try {
      await redis.setex(cacheKey, CACHE_TTL, JSON.stringify(domainInfo));
    } catch (error) {
      console.error("[Domain Cache] Redis set error:", error);
    }
  }

  return domainInfo;
}

/**
 * 清理指定域名的缓存
 * @param fullName 完整域名
 * @returns 是否成功删除（true 表示缓存中存在并已删除）
 */
export async function clearDomainCache(fullName: string): Promise<boolean> {
  const redis = getRedis();
  const cacheKey = getCacheKey(fullName);

  try {
    const result = await redis.del(cacheKey);
    const existed = result > 0;
    console.log("[Domain Cache] Cache cleared:", fullName, existed ? "(existed)" : "(not found)");
    return existed;
  } catch (error) {
    console.error("[Domain Cache] Redis del error:", error);
    return false;
  }
}

/**
 * 清理所有域名缓存
 */
export async function clearAllDomainCache(): Promise<number> {
  const redis = getRedis();

  try {
    // 使用 SCAN 遍历并删除所有 domain: 开头的 key
    let cursor = "0";
    let totalDeleted = 0;

    do {
      const [newCursor, keys] = await redis.scan(cursor, "MATCH", `${CACHE_PREFIX}*`, "COUNT", 100);
      cursor = newCursor;

      if (keys.length > 0) {
        const deleted = await redis.del(...keys);
        totalDeleted += deleted;
      }
    } while (cursor !== "0");

    console.log("[Domain Cache] All cache cleared, count:", totalDeleted);
    return totalDeleted;
  } catch (error) {
    console.error("[Domain Cache] Redis clear error:", error);
    return 0;
  }
}

/**
 * 获取缓存统计信息
 */
export async function getDomainCacheStats(): Promise<{ size: number }> {
  const redis = getRedis();

  try {
    // 统计 domain: 开头的 key 数量
    let cursor = "0";
    let count = 0;

    do {
      const [newCursor, keys] = await redis.scan(cursor, "MATCH", `${CACHE_PREFIX}*`, "COUNT", 100);
      cursor = newCursor;
      count += keys.length;
    } while (cursor !== "0");

    return { size: count };
  } catch (error) {
    console.error("[Domain Cache] Redis stats error:", error);
    return { size: 0 };
  }
}
