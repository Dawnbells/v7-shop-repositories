/**
 * 域名缓存清理 API
 * DELETE /api/cache/domain?fullName=xxx
 */

import { clearDomainCache, clearAllDomainCache, getDomainCacheStats } from "../../cache/domain.cache";

export default defineEventHandler(async (event) => {
  const query = getQuery(event);
  const fullName = query.fullName as string | undefined;

  if (fullName) {
    // 清理指定域名缓存
    const existed = await clearDomainCache(fullName);
    return {
      success: true,
      message: existed
        ? `Cache cleared for domain: ${fullName}`
        : `Domain not found in cache: ${fullName}`,
      fullName,
      existed,
    };
  } else {
    // 清理所有缓存
    const clearedCount = await clearAllDomainCache();
    return {
      success: true,
      message: `All domain cache cleared`,
      clearedCount,
    };
  }
});
