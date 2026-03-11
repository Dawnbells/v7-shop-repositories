/**
 * 语言 Repository
 * 封装语言相关的数据库查询
 */

import { query } from '../utils/db'

export interface LanguageItem {
  id: number
  code: string
  name: string
  cname: string
}

/**
 * 查询所有有效语言列表
 */
export async function findAllLanguages(): Promise<LanguageItem[]> {
  const sql = `
    SELECT id, code, name, cname 
    FROM t_languages 
    WHERE status = 'VALID'
    ORDER BY id ASC
  `

  return query<LanguageItem>(sql)
}
