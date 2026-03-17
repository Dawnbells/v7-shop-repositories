/**
 * 产品页 Composable
 *
 * 提供产品相关数据和工具方法
 * 产品数据从 pageContext 获取（由中间件注入）
 */

import Decimal from "decimal.js";

/**
 * 规格类型定义（与 usePageContext 中的 ProductSpecification 一致）
 */
export interface ProductSpecification {
  id: number;
  sid: number | null;
  skuId: number;
  sellPrice: number;
  originPrice: number | null;
  costPrice: number | null;
  barcode: string | null;
  stockQuantity: number;
  linkStock: boolean;
  specificationImageId: number | null;
  specImagePath?: string | null;
  attributes: Array<{
    name: string;
    value: string;
    imagePath?: string | null;
  }>;
}

export function useProductPage() {
  const { productInfo, currency } = usePageContext();

  // 当前选中的规格
  const selectedSpec = useState<ProductSpecification | null>(
    "selectedSpec",
    () => null
  );

  // 临时预览图片（规格选中时显示，不影响原始主图）
  const previewImage = useState<string | null>("specPreviewImage", () => null);

  // 购买数量
  const quantity = useState<number>("purchaseQuantity", () => 1);

  // 选择规格
  function selectSpec(spec: ProductSpecification | null) {
    selectedSpec.value = spec;
  }

  // 设置临时预览图片
  function setPreviewImage(imagePath: string | null) {
    previewImage.value = imagePath;
  }

  // 设置购买数量
  function setQuantity(val: number) {
    quantity.value = Math.max(1, val);
  }

  // 增加购买数量
  function increaseQuantity() {
    quantity.value++;
  }

  // 减少购买数量
  function decreaseQuantity() {
    if (quantity.value > 1) {
      quantity.value--;
    }
  }

  // 初始化：多规格商品默认选中第一个规格
  watch(
    productInfo,
    (info) => {
      if (
        info?.isMultiSpecs &&
        info.specifications?.length > 0 &&
        !selectedSpec.value
      ) {
        selectedSpec.value = info.specifications[0] as ProductSpecification;
      }
    },
    { immediate: true }
  );

  function formatPrice(price: number | string | null | undefined): string {
    if (price == null) return "";
    const num = typeof price === "string" ? parseFloat(price) : price;
    if (isNaN(num)) return "";

    const curr = currency.value;
    if (curr?.code) {
      // 使用 decimal.js 进行高精度汇率转换
      let converted = new Decimal(num);
      if (curr.exchangeRate && curr.exchangeRate !== 1) {
        converted = converted.times(curr.exchangeRate);
      }

      return new Intl.NumberFormat(undefined, {
        style: "currency",
        currency: curr.code,
        minimumFractionDigits: curr.fractionDigits ?? 2,
        maximumFractionDigits: curr.fractionDigits ?? 2,
      }).format(converted.toNumber());
    }

    // 降级：使用默认格式（USD）
    return `$${num.toFixed(2)}`;
  }

  return {
    productInfo,
    currency,
    selectedSpec,
    selectSpec,
    formatPrice,
    previewImage,
    setPreviewImage,
    quantity,
    setQuantity,
    increaseQuantity,
    decreaseQuantity,
  };
}
