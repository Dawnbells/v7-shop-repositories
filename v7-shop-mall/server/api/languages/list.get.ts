/**
 * 获取语言列表 API
 * 从 t_languages 表查询所有有效语言
 */

import { query } from '../../utils/db'

interface LanguageItem {
  id: number
  code: string
  name: string
  cname: string
}

export default defineEventHandler(async (): Promise<LanguageItem[]> => {
  const sql = `
    SELECT id, code, name, cname 
    FROM t_languages 
    WHERE status = 'VALID'
    ORDER BY id ASC
  `

  const rows = await query<LanguageItem>(sql)
  return rows
})
