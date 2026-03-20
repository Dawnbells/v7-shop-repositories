/**
 * 文章 Repository
 * 封装文章相关的数据库查询
 */

import { queryOne } from "../utils/db";

export interface ArticleInfo {
  title: string;
  description: string | null;
  content: string | null;
  updateTime: string | null;
}

/**
 * 根据 ID 查询已发布的文章
 */
export async function findArticleById(
  id: string | number,
): Promise<ArticleInfo | null> {
  const sql = `
    SELECT 
      title,
      description,
      content,
      update_time as updateTime
    FROM t_articles
    WHERE id = ? AND status = 'VALID'
    LIMIT 1
  `;

  return queryOne<ArticleInfo>(sql, [id]);
}
