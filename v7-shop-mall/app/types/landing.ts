/**
 * 落地页相关类型定义
 */

export interface LandingPageInfo {
  landingSpuId: number | null;
  protocolId: number | null;
  protocolPlaceholderValues: Record<string, any>;
  variableSchema: any[];
}
