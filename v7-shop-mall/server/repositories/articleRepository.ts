/**
 * 文章 Repository
 * 封装文章相关的数据库查询
 */

import { queryOne } from '../utils/db'

export interface ArticleInfo {
  id: number
  title: string
  name: string
  description: string | null
  content: string | null
  author: string | null
  publishedAt: string | null
  coverImage: string | null
}

/**
 * 根据 ID 查询已发布的文章
 */
export async function findArticleById(id: string | number): Promise<ArticleInfo | null> {
  const sql = `
    SELECT 
      id,
      title,
      name,
      description,
      content,
      author,
      published_at as publishedAt,
      cover_image as coverImage
    FROM t_articles
    WHERE id = ? AND status = 'PUBLISHED'
    LIMIT 1
  `

  return queryOne<ArticleInfo>(sql, [id])
}
