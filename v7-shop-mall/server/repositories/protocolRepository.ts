/**
 * 协议 Repository
 * 封装协议组相关的数据库查询
 */

import { query } from '../utils/db'

export interface ProtocolArticle {
  id: number
  title: string
}

export interface ProtocolGroup {
  id: number
  name: string
  sort: number
  articles: ProtocolArticle[]
}

interface ProtocolGroupRow {
  group_id: number
  group_name: string
  group_sort: number
  article_id: number | null
  article_title: string | null
}

/**
 * 根据 protocolId 查询协议组及其文章列表
 * 通过 t_protocol -> t_protocol_translation -> t_protocol_groups -> t_protocol_group_articles -> t_articles 关联查询
 */
export async function findProtocolGroupsByProtocolId(protocolId: number): Promise<ProtocolGroup[]> {
  const sql = `
    SELECT 
      pg.id as group_id,
      pg.name as group_name,
      pg.sort as group_sort,
      a.id as article_id,
      a.title as article_title
    FROM t_protocol p
    INNER JOIN t_protocol_translation pt ON pt.protocol_id = p.id
    INNER JOIN t_protocol_groups pg ON pg.protocol_translation_id = pt.id
    LEFT JOIN t_protocol_group_articles pga ON pga.protocol_group_id = pg.id
    LEFT JOIN t_articles a ON a.id = pga.article_id AND a.status = 'PUBLISHED'
    WHERE p.id = ? AND p.status != 'DELETED' AND pg.status != 'DELETED'
    ORDER BY pg.sort DESC, pg.id ASC, a.id ASC
  `

  const rows = await query<ProtocolGroupRow>(sql, [protocolId])

  if (!rows || rows.length === 0) {
    return []
  }

  const groupMap = new Map<number, ProtocolGroup>()

  for (const row of rows) {
    if (!groupMap.has(row.group_id)) {
      groupMap.set(row.group_id, {
        id: row.group_id,
        name: row.group_name,
        sort: row.group_sort,
        articles: [],
      })
    }

    if (row.article_id && row.article_title) {
      const group = groupMap.get(row.group_id)!
      group.articles.push({
        id: row.article_id,
        title: row.article_title,
      })
    }
  }

  return Array.from(groupMap.values())
}
