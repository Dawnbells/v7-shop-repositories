/**
 * 收银台页面 Composable
 *
 * 管理收银台页面状态：
 * - 获取待结算商品（从购物车或直接下单）
 * - 管理收货地址表单数据
 * - 管理支付方式选择
 * - 计算订单金额
 * - 提交订单逻辑
 */

import type { CartItem, DirectOrderItem } from "~/composables/useCart";

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
  const { formatPrice } = useCurrency();

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

  // 运费（可配置）
  const shippingFee = useState<number>("checkoutShippingFee", () => 0);

  // 折扣金额
  const discount = useState<number>("checkoutDiscount", () => 0);

  // 商品小计
  const subtotal = computed(() => {
    return checkoutItems.value.reduce(
      (sum, item) => sum + item.price * item.quantity,
      0
    );
  });

  // 订单总计
  const total = computed(() => {
    return Math.max(0, subtotal.value + shippingFee.value - discount.value);
  });

  // 商品总数量
  const itemCount = computed(() => {
    return checkoutItems.value.reduce((sum, item) => sum + item.quantity, 0);
  });

  // 是否有商品
  const hasItems = computed(() => checkoutItems.value.length > 0);

  // 初始化结算商品
  function initCheckoutItems() {
    if (import.meta.server) return;

    if (isDirectMode.value && directOrderItem.value) {
      // 直接下单模式：使用 directOrderItem
      const item = directOrderItem.value;
      checkoutItems.value = [
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
      checkoutItems.value = cartItems.value.map((item) => ({
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
      const orderData: OrderSubmitData = {
        items: checkoutItems.value,
        shippingAddress: shippingAddress.value,
        paymentMethod: paymentMethod.value,
        subtotal: subtotal.value,
        shippingFee: shippingFee.value,
        discount: discount.value,
        total: total.value,
        note: shippingAddress.value.note,
      };

      // TODO: 调用后端 API 提交订单
      // const response = await $fetch('/api/orders', {
      //   method: 'POST',
      //   body: orderData,
      // });

      // 模拟提交成功
      console.log("Order submitted:", orderData);

      // 清理数据
      if (isDirectMode.value) {
        clearDirectOrderItem();
      } else {
        clearCart();
      }

      // 跳转到订单结果页
      // router.push(`/order-result?orderId=${response.orderId}`);
      router.push("/order-result");

      return true;
    } catch (error: any) {
      submitError.value = error.message || "提交订单失败，请稍后重试";
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

  // 客户端初始化
  if (import.meta.client) {
    onMounted(() => {
      initCheckoutItems();
    });
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
    shippingFee,
    discount,

    // 计算属性
    isDirectMode,
    subtotal,
    total,
    itemCount,
    hasItems,

    // 方法
    initCheckoutItems,
    validateForm,
    clearFormErrors,
    updateAddress,
    setPaymentMethod,
    submitOrder,
    formatSpecAttributes,
    formatPrice,
  };
}
