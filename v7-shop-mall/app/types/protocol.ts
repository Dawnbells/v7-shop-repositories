/**
 * 协议相关类型定义
 */

export interface ProtocolArticle {
  id: number;
  title: string;
  description: string | null;
}

export interface ProtocolGroup {
  id: number;
  name: string;
  sort: number;
  articles: ProtocolArticle[];
}
