import redisDriver from 'unstorage/drivers/redis'
import { createStorage, type Storage } from 'unstorage'

let _storage: Storage | null = null

export function getRedisStorage(): Storage {
  if (!_storage) {
    const config = useRuntimeConfig()
    _storage = createStorage({
      driver: redisDriver({
        host: config.redis.host as string,
        port: Number(config.redis.port),
        password: config.redis.password as string,
        db: Number(config.redis.db),
      }),
    })
  }
  return _storage
}
