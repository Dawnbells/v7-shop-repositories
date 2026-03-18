/**
 * 产品页 Composable
 *
 * 提供产品相关数据和工具方法
 * 产品数据从 usePageContext 获取（由中间件注入）
 * 价格格式化委托给 useCurrency
 */

import type { ProductSpecification } from "~/types/product";

export function useProductPage() {
  const { productInfo } = usePageContext();
  const { currency, formatPrice } = useCurrency();

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

  function increaseQuantity() {
    quantity.value++;
  }

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
