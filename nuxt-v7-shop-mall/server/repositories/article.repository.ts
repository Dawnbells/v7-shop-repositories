/**
 * 文章数据仓库
 * 封装文章相关的数据库操作
 */

import { queryOne } from "../utils/db";

/**
 * 文章信息
 */
export interface ArticleInfo {
  id: number;
  name: string;
  title: string;
  content: string;
  description: string;
}

/**
 * 根据文章 ID 查询文章信息
 */
export async function findArticleById(
  articleId: number
): Promise<ArticleInfo | null> {
  const sql = `
    SELECT a.id, a.name, a.title, a.content, a.description
    FROM t_articles a
    WHERE a.id = ? AND a.status <> 'DELETED'
    LIMIT 1
  `;
  return queryOne<ArticleInfo>(sql, [articleId]);
}

/**
 * 查询落地页的协议占位符值
 * @param subDomainId 子域名 ID
 * @param spuId SPU ID
 * @param landingPageType 落地页类型 (LAND / CLOAK / BLACKLISTED)
 * @returns 占位符值 Map，如果不存在则返回 null
 */
export async function findProtocolPlaceholderValues(
  subDomainId: number,
  spuId: number,
  landingPageType: string
): Promise<Record<string, string> | null> {
  const sql = `
    SELECT protocol_placeholder_values
    FROM t_sub_domain_spu_landing_pages
    WHERE sub_domain_id = ? AND spu_id = ? AND landing_page_type = ?
    LIMIT 1
  `;
  const row = await queryOne<{ protocol_placeholder_values: any }>(sql, [
    subDomainId,
    spuId,
    landingPageType,
  ]);

  if (!row || !row.protocol_placeholder_values) {
    return null;
  }

  // mysql2 对 JSON 字段可能返回字符串或对象
  const values = row.protocol_placeholder_values;
  if (typeof values === "string") {
    try {
      return JSON.parse(values);
    } catch {
      return null;
    }
  }
  return values as Record<string, string>;
}

/**
 * 替换文本中的占位符 {{key}} 为对应值
 * 未匹配到值的占位符保留原样
 *
 * @param text 待替换的文本
 * @param values 占位符值 Map
 * @returns 替换后的文本
 */
export function replacePlaceholders(
  text: string,
  values: Record<string, string>
): string {
  if (!text || !values) return text;
  return text.replace(/\{\{(\w+)\}\}/g, (match, key) => {
    return key in values ? values[key] : match;
  });
}
