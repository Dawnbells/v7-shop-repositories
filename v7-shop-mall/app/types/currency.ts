/**
 * 货币相关类型定义
 */

export interface Currency {
  id: number;
  code: string;
  name: string;
  symbol: string | null;
  exchangeRate: number | null;
  fractionDigits: number | null;
}
