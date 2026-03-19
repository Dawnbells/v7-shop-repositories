/**
 * 收银台页面 Composable
 *
 * 管理收银台页面状态：
 * - 获取待结算商品（从购物车或直接下单）
 * - 管理收货地址表单数据
 * - 管理支付方式选择
 * - 调用后端 API 计算订单金额
 * - 调用后端 API 提交订单
 */

import type { CartItem, DirectOrderItem } from "~/composables/useCart";

/**
 * 后端价格计算响应
 */
interface CalculateResponse {
  success: boolean;
  data: {
    items: Array<{
      productId: number;
      specId: number | null;
      productName: string;
      specName: string | null;
      price: string;
      originPrice: string | null;
      quantity: number;
      subtotal: string;
      image: string | null;
    }>;
    subtotal: string;
    shippingFee: string;
    discount: string;
    total: string;
  };
}

/**
 * 后端下单响应
 */
interface OrderResponse {
  success: boolean;
  data: {
    orderId: string;
    total: string;
  };
}

/**
 * 收货地址
 */
export interface ShippingAddress {
  fullName: string;
  phone: string;
  email?: string;
  province: string;
  city: string;
  district?: string;
  address: string;
  postalCode?: string;
  note?: string;
}

/**
 * 支付方式
 */
export type PaymentMethod = "cod" | "online";

/**
 * 支付方式选项
 */
export interface PaymentMethodOption {
  id: PaymentMethod;
  name: string;
  description: string;
  icon: string;
  enabled: boolean;
}

/**
 * 结算商品项（统一格式）
 */
export interface CheckoutItem {
  id: string;
  productId: number;
  productName: string;
  specId: number | null;
  specAttributes: Array<{ name: string; value: string }>;
  price: number;
  originPrice?: number | null;
  quantity: number;
  image?: string;
}

/**
 * 订单提交数据
 */
export interface OrderSubmitData {
  items: CheckoutItem[];
  shippingAddress: ShippingAddress;
  paymentMethod: PaymentMethod;
  subtotal: number;
  shippingFee: number;
  discount: number;
  total: number;
  note?: string;
}

/**
 * 表单验证错误
 */
export interface FormErrors {
  fullName?: string;
  phone?: string;
  email?: string;
  province?: string;
  city?: string;
  district?: string;
  address?: string;
  postalCode?: string;
}

export function useCheckoutPage() {
  const route = useRoute();
  const router = useRouter();
  const { cartItems, directOrderItem, clearCart, clearDirectOrderItem, loadFromStorage } = useCart();
  const { formatPrice: baseFmtPrice } = useCurrency();

  // 收银台专用的价格格式化函数（跳过汇率转换，因为后端已经转换过了）
  const formatPrice = (price: number | string | null | undefined) => baseFmtPrice(price, true);

  // 是否为直接下单模式
  const isDirectMode = computed(() => route.query.mode === "direct");

  // 结算商品列表
  const checkoutItems = useState<CheckoutItem[]>("checkoutItems", () => []);

  // 收货地址表单
  const shippingAddress = useState<ShippingAddress>("shippingAddress", () => ({
    fullName: "",
    phone: "",
    email: "",
    province: "",
    city: "",
    district: "",
    address: "",
    postalCode: "",
    note: "",
  }));

  // 支付方式
  const paymentMethod = useState<PaymentMethod>("paymentMethod", () => "cod");

  // 可用的支付方式列表
  const paymentMethods = computed<PaymentMethodOption[]>(() => [
    {
      id: "cod",
      name: "货到付款",
      description: "收到商品后再付款",
      icon: "i-carbon-delivery",
      enabled: true,
    },
    {
      id: "online",
      name: "在线支付",
      description: "使用信用卡或其他在线支付方式",
      icon: "i-carbon-wallet",
      enabled: false,
    },
  ]);

  // 表单验证错误
  const formErrors = useState<FormErrors>("checkoutFormErrors", () => ({}));

  // 是否正在提交
  const isSubmitting = useState<boolean>("checkoutSubmitting", () => false);

  // 提交结果
  const submitError = useState<string | null>("checkoutSubmitError", () => null);

  // 价格数据（从后端获取）
  const subtotal = useState<number>("checkoutSubtotal", () => 0);
  const shippingFee = useState<number>("checkoutShippingFee", () => 0);
  const discount = useState<number>("checkoutDiscount", () => 0);
  const total = useState<number>("checkoutTotal", () => 0);

  // 是否正在计算价格
  const isCalculating = useState<boolean>("checkoutCalculating", () => false);

  // 价格计算错误
  const calculateError = useState<string | null>("checkoutCalculateError", () => null);

  // 商品总数量
  const itemCount = computed(() => {
    return checkoutItems.value.reduce((sum, item) => sum + item.quantity, 0);
  });

  // 是否有商品
  const hasItems = computed(() => checkoutItems.value.length > 0);

  // 初始化结算商品
  async function initCheckoutItems() {
    if (import.meta.server) return;

    let items: CheckoutItem[] = [];

    if (isDirectMode.value && directOrderItem.value) {
      // 直接下单模式：使用 directOrderItem
      const item = directOrderItem.value;
      items = [
        {
          id: `${item.productId}-${item.specId ?? "default"}`,
          productId: item.productId,
          productName: item.productName,
          specId: item.specId,
          specAttributes: item.specAttributes,
          price: item.price,
          originPrice: item.originPrice,
          quantity: item.quantity,
          image: item.image,
        },
      ];
    } else {
      // 购物车模式：使用购物车商品
      loadFromStorage();
      items = cartItems.value.map((item) => ({
        id: item.id,
        productId: item.productId,
        productName: item.productName,
        specId: item.specId,
        specAttributes: item.specAttributes,
        price: item.price,
        originPrice: item.originPrice,
        quantity: item.quantity,
        image: item.image,
      }));
    }

    checkoutItems.value = items;

    // 初始化后立即计算价格
    if (items.length > 0) {
      await calculatePrice();
    }
  }

  // 调用后端 API 计算价格
  async function calculatePrice(): Promise<boolean> {
    if (checkoutItems.value.length === 0) {
      subtotal.value = 0;
      shippingFee.value = 0;
      discount.value = 0;
      total.value = 0;
      return true;
    }

    isCalculating.value = true;
    calculateError.value = null;

    try {
      const requestItems = checkoutItems.value.map(item => ({
        productId: item.productId,
        specId: item.specId,
        quantity: item.quantity,
      }));

      const response = await $fetch<CalculateResponse>('/api/checkout/calculate', {
        method: 'POST',
        body: { items: requestItems },
      });

      if (response.success && response.data) {
        // 更新商品信息（使用后端返回的实时价格）
        const priceMap = new Map(
          response.data.items.map(item => [
            `${item.productId}-${item.specId}`,
            item,
          ])
        );

        checkoutItems.value = checkoutItems.value.map(item => {
          const key = `${item.productId}-${item.specId}`;
          const priceInfo = priceMap.get(key);
          if (priceInfo) {
            return {
              ...item,
              productName: priceInfo.productName,
              price: parseFloat(priceInfo.price),
              originPrice: priceInfo.originPrice ? parseFloat(priceInfo.originPrice) : null,
              image: priceInfo.image || item.image,
            };
          }
          return item;
        });

        // 更新价格数据
        subtotal.value = parseFloat(response.data.subtotal);
        shippingFee.value = parseFloat(response.data.shippingFee);
        discount.value = parseFloat(response.data.discount);
        total.value = parseFloat(response.data.total);

        return true;
      }

      calculateError.value = '计算价格失败';
      return false;
    } catch (error: any) {
      console.error('[Checkout] Calculate price error:', error);
      calculateError.value = error.data?.message || error.message || '计算价格失败，请稍后重试';
      return false;
    } finally {
      isCalculating.value = false;
    }
  }

  // 从 pageContext 获取 addressFields 配置
  const { countryInfo } = usePageContext();

  const activeAddressFields = computed<string[]>(() => {
    const raw = countryInfo.value?.addressFields;
    if (!raw) return [];
    return raw.split(',').map(s => s.trim()).filter(Boolean);
  });

  // 验证表单（根据 addressFields 动态验证地址字段）
  function validateForm(): boolean {
    const errors: FormErrors = {};
    const addr = shippingAddress.value;
    const fields = activeAddressFields.value;

    if (!addr.fullName?.trim()) {
      errors.fullName = "请输入收货人姓名";
    }

    if (!addr.phone?.trim()) {
      errors.phone = "请输入联系电话";
    } else if (!/^[\d\s\-+()]{6,20}$/.test(addr.phone.trim())) {
      errors.phone = "请输入有效的电话号码";
    }

    if (addr.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(addr.email)) {
      errors.email = "请输入有效的邮箱地址";
    }

    if (fields.includes('province') && !addr.province?.trim()) {
      errors.province = "请选择省/州";
    }

    if (fields.includes('city') && !addr.city?.trim()) {
      errors.city = "请选择城市";
    }

    if (fields.includes('district') && !addr.district?.trim()) {
      errors.district = "请选择区/县";
    }

    if (!addr.address?.trim()) {
      errors.address = "请输入详细地址";
    }

    formErrors.value = errors;
    return Object.keys(errors).length === 0;
  }

  // 清除表单错误
  function clearFormErrors() {
    formErrors.value = {};
  }

  // 更新收货地址字段
  function updateAddress<K extends keyof ShippingAddress>(
    field: K,
    value: ShippingAddress[K]
  ) {
    shippingAddress.value = {
      ...shippingAddress.value,
      [field]: value,
    };
    // 清除该字段的错误
    if (formErrors.value[field as keyof FormErrors]) {
      formErrors.value = {
        ...formErrors.value,
        [field]: undefined,
      };
    }
  }

  // 设置支付方式
  function setPaymentMethod(method: PaymentMethod) {
    paymentMethod.value = method;
  }

  // 提交订单
  async function submitOrder(): Promise<boolean> {
    // 验证表单
    if (!validateForm()) {
      return false;
    }

    // 检查是否有商品
    if (!hasItems.value) {
      submitError.value = "购物车为空，无法提交订单";
      return false;
    }

    isSubmitting.value = true;
    submitError.value = null;

    try {
      // 构建请求数据
      const requestItems = checkoutItems.value.map(item => ({
        productId: item.productId,
        specId: item.specId,
        quantity: item.quantity,
      }));

      // 调用后端下单 API
      const response = await $fetch<OrderResponse>('/api/checkout/order', {
        method: 'POST',
        body: {
          items: requestItems,
          shippingAddress: {
            fullName: shippingAddress.value.fullName,
            phone: shippingAddress.value.phone,
            email: shippingAddress.value.email,
            province: shippingAddress.value.province,
            city: shippingAddress.value.city,
            district: shippingAddress.value.district,
            postalCode: shippingAddress.value.postalCode,
            address: shippingAddress.value.address,
            note: shippingAddress.value.note,
          },
          paymentMethod: paymentMethod.value,
        },
      });

      if (!response.success) {
        throw new Error('下单失败');
      }

      // 清理数据
      if (isDirectMode.value) {
        clearDirectOrderItem();
      } else {
        clearCart();
      }

      // 跳转到订单结果页
      router.push(`/order-result?orderId=${response.data.orderId}`);

      return true;
    } catch (error: any) {
      console.error('[Checkout] Submit order error:', error);
      submitError.value = error.data?.message || error.message || "提交订单失败，请稍后重试";
      return false;
    } finally {
      isSubmitting.value = false;
    }
  }

  // 格式化规格属性
  function formatSpecAttributes(
    attrs: Array<{ name: string; value: string }>
  ): string {
    if (!attrs || attrs.length === 0) return "";
    return attrs.map((a) => `${a.name}: ${a.value}`).join(", ");
  }

  return {
    // 状态
    checkoutItems,
    shippingAddress,
    paymentMethod,
    paymentMethods,
    formErrors,
    isSubmitting,
    submitError,
    isCalculating,
    calculateError,

    // 价格数据（从后端获取）
    subtotal,
    shippingFee,
    discount,
    total,

    // 计算属性
    isDirectMode,
    itemCount,
    hasItems,

    // 方法
    initCheckoutItems,
    calculatePrice,
    validateForm,
    clearFormErrors,
    updateAddress,
    setPaymentMethod,
    submitOrder,
    formatSpecAttributes,
    formatPrice,
  };
}
