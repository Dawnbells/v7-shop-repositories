import mysql from "mysql2/promise";

let pool: mysql.Pool | null = null;

/**
 * 获取数据库连接池
 * 使用 runtimeConfig 中的配置
 */
export function getPool(): mysql.Pool {
  if (!pool) {
    const config = useRuntimeConfig();
    pool = mysql.createPool({
      host: config.db.host,
      port: config.db.port,
      user: config.db.user,
      password: config.db.password,
      database: config.db.database,
      waitForConnections: true,
      connectionLimit: 10,
    });
  }
  return pool;
}

/**
 * 执行 SQL 查询
 */
export async function query<T = any>(
  sql: string,
  params?: any[]
): Promise<T[]> {
  const pool = getPool();
  const [rows] = await pool.execute(sql, params);
  return rows as T[];
}

/**
 * 执行 SQL 查询并返回第一条记录
 */
export async function queryOne<T = any>(
  sql: string,
  params?: any[]
): Promise<T | null> {
  const rows = await query<T>(sql, params);
  return rows[0] || null;
}
