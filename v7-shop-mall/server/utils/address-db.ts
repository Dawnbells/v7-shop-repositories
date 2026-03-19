/**
 * 地址库数据库连接
 * 独立于主数据库，使用 runtimeConfig.addressDb 配置
 */

import mysql from 'mysql2/promise'

let addressPool: mysql.Pool | null = null

/**
 * 获取地址库数据库连接池
 */
export function getAddressPool(): mysql.Pool {
  if (!addressPool) {
    const config = useRuntimeConfig()
    addressPool = mysql.createPool({
      host: config.addressDb.host,
      port: config.addressDb.port,
      user: config.addressDb.user,
      password: config.addressDb.password,
      database: config.addressDb.database,
      waitForConnections: true,
      connectionLimit: 5,
    })
  }
  return addressPool
}

/**
 * 执行地址库 SQL 查询
 */
export async function addressQuery<T = any>(
  sql: string,
  params?: any[]
): Promise<T[]> {
  const pool = getAddressPool()
  const [rows] = await pool.execute(sql, params)
  return rows as T[]
}
