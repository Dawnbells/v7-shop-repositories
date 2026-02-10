/**
 * 协议数据仓库
 * 用于页脚等场景按协议分组获取文章链接
 */

import { query, queryOne } from "../utils/db";
import { replacePlaceholders } from "./article.repository";

export interface ProtocolGroupLink {
  text: string;
  url: string;
}

export interface ProtocolGroupForFooter {
  title: string;
  links: ProtocolGroupLink[];
}

/**
 * 根据协议 ID 和语言 ID 获取协议翻译 ID
 * @param protocolId 协议 ID
 * @param languageId 语言 ID，为空时取该协议下任意一条翻译
 */
export async function findProtocolTranslationId(
  protocolId: number,
  languageId?: number | null
): Promise<number | null> {
  if (languageId != null) {
    const row = await queryOne<{ id: number }>(
      `SELECT id FROM t_protocol_translation WHERE protocol_id = ? AND language_id = ? LIMIT 1`,
      [protocolId, languageId]
    );
    return row?.id ?? null;
  }
  const row = await queryOne<{ id: number }>(
    `SELECT id FROM t_protocol_translation WHERE protocol_id = ? LIMIT 1`,
    [protocolId]
  );
  return row?.id ?? null;
}

/**
 * 获取协议分组及文章链接，用于页脚展示
 * @param protocolId 协议 ID
 * @param languageId 语言 ID（可选，用于选择对应语言的协议翻译）
 * @param placeholderValues 占位符值，用于替换文章标题/名称中的 {{key}}
 */
export async function findProtocolGroupsForFooter(
  protocolId: number,
  languageId?: number | null,
  placeholderValues?: Record<string, string> | null
): Promise<ProtocolGroupForFooter[]> {
  const translationId = await findProtocolTranslationId(protocolId, languageId);
  if (!translationId) {
    return [];
  }

  type Row = {
    group_id: number;
    group_name: string;
    group_sort: number;
    article_id: number | null;
    article_title: string | null;
    article_name: string | null;
  };

  const rows = await query<Row>(
    `SELECT pg.id AS group_id, pg.name AS group_name, pg.sort AS group_sort,
            pga.article_id AS article_id, a.title AS article_title, a.name AS article_name
     FROM t_protocol_groups pg
     LEFT JOIN t_protocol_group_articles pga ON pga.protocol_group_id = pg.id
     LEFT JOIN t_articles a ON a.id = pga.article_id AND (a.status IS NULL OR a.status <> 'DELETED')
     WHERE pg.protocol_translation_id = ?
     ORDER BY pg.sort ASC, pg.id, pga.article_id`,
    [translationId]
  );

  const values = placeholderValues ?? {};
  const byGroup = new Map<
    number,
    { name: string; sort: number; links: ProtocolGroupLink[] }
  >();

  for (const r of rows) {
    if (!byGroup.has(r.group_id)) {
      byGroup.set(r.group_id, {
        name: r.group_name ?? "",
        sort: r.group_sort,
        links: [],
      });
    }
    const g = byGroup.get(r.group_id)!;
    if (r.article_id != null && (r.article_title != null || r.article_name != null)) {
      const rawText = (r.article_title ?? r.article_name ?? "").trim() || String(r.article_id);
      const text = replacePlaceholders(rawText, values);
      g.links.push({
        text,
        url: `/article/${r.article_id}`,
      });
    }
  }

  return Array.from(byGroup.values())
    .sort((a, b) => a.sort - b.sort)
    .map((g) => ({ title: g.name, links: g.links }));
}
