/**
 * 文章相关类型定义
 */

export interface ArticleInfo {
  id: number;
  title: string;
  name: string;
  description: string | null;
  content: string | null;
  author: string | null;
  publishedAt: string | null;
  coverImage: string | null;
}
