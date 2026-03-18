/**
 * Builder 预览 Composable
 *
 * 封装编辑器预览所需的 mock 数据注入
 * 内部通过 usePageContext 设置预览数据，避免组件直接依赖 usePageContext
 */

import type { ProductInfo } from "~/types/product";
import type { Currency } from "~/types/currency";

export function useBuilderPreview() {
  const { setMockData } = usePageContext();

  function setPreviewData(mockData: {
    productInfo?: ProductInfo | null;
    currency?: Currency | null;
  }) {
    setMockData(mockData);
  }

  return {
    setPreviewData,
  };
}
