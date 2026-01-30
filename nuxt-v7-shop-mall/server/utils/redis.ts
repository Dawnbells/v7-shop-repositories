/**
 * Redis 工具类
 * 使用 runtimeConfig 中的配置
 */

import Redis from "ioredis";

let redis: Redis | null = null;

/**
 * 获取 Redis 客户端实例
 * 使用单例模式管理连接
 */
export function getRedis(): Redis {
  if (!redis) {
    const config = useRuntimeConfig();
    redis = new Redis({
      host: config.redis.host,
      port: config.redis.port,
      password: config.redis.password || undefined,
      db: config.redis.db,
      lazyConnect: true,
      retryStrategy(times) {
        const delay = Math.min(times * 50, 2000);
        return delay;
      },
    });

    redis.on("error", (err) => {
      console.error("[Redis] Connection error:", err.message);
    });

    redis.on("connect", () => {
      console.log("[Redis] Connected successfully");
    });
  }
  return redis;
}

/**
 * 关闭 Redis 连接
 */
export async function closeRedis(): Promise<void> {
  if (redis) {
    await redis.quit();
    redis = null;
    console.log("[Redis] Connection closed");
  }
}
