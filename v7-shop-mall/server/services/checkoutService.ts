/**
 * 收银台服务
 * 处理价格计算和订单创建逻辑
 */

import Decimal from "decimal.js";
import {
  findProductPrices,
  createTemporaryOrder,
  type ProductPriceInfo,
  type CreateOrderData,
  type OrderItemInfo,
  type OrderDeliveryInfo,
  type OrderFinancialInfo,
  type OrderPaymentInfo,
  type OrderContextInfo,
  type OrderRiskRecordInfo,
} from "../repositories/orderRepository";
import type { PageContext } from "../types/page-context";
import { CloakPage } from "../types/cloak";

// ============ 类型定义 ============

/**
 * 计算请求的商品项
 */
export interface CalculateRequestItem {
  productId: number;
  specId: number | null;
  quantity: number;
}

/**
 * 计算结果的商品项
 */
export interface CalculateResultItem {
  productId: number;
  specId: number | null;
  productName: string;
  specName: string | null;
  price: string;
  originPrice: string | null;
  quantity: number;
  subtotal: string;
  image: string | null;
}

/**
 * 价格计算结果
 */
export interface CalculateResult {
  items: CalculateResultItem[];
  subtotal: string;
  shippingFee: string;
  discount: string;
  tax: string;
  total: string;
}

/**
 * 收货地址信息（前端提交）
 */
export interface ShippingAddressInput {
  fullName: string;
  phone: string;
  email?: string;
  province?: string;
  city?: string;
  district?: string;
  postalCode?: string;
  address: string;
  note?: string;
  subscribeToUpdates?: boolean;
}

/**
 * 下单请求
 */
export interface CreateOrderRequest {
  items: CalculateRequestItem[];
  shippingAddress: ShippingAddressInput;
  paymentMethod: "cod" | "online";
}

/**
 * 下单结果
 */
export interface CreateOrderResult {
  orderId: number;
  total: string;
}

// ============ 价格计算 ============

/**
 * 价格转换：乘以汇率并设置小数位数（使用 HALF_UP 舍入）
 */
function convertPrice(
  price: number | string,
  exchangeRate: number | null,
  fractionDigits: number,
): string {
  const decimalPrice = new Decimal(price);
  const rate = new Decimal(exchangeRate || 1);
  return decimalPrice.mul(rate).toFixed(fractionDigits, Decimal.ROUND_HALF_UP);
}

/**
 * 计算订单价格
 *
 * @param items 商品列表
 * @param pageContext 页面上下文（包含国家、货币等信息）
 * @returns 价格计算结果
 */
export async function calculateOrderPrice(
  items: CalculateRequestItem[],
  pageContext: PageContext,
): Promise<CalculateResult> {
  if (!items || items.length === 0) {
    return {
      items: [],
      subtotal: "0.00",
      shippingFee: "0.00",
      discount: "0.00",
      tax: "0.00",
      total: "0.00",
    };
  }

  const countryId = pageContext.country.id;
  const exchangeRate = pageContext.currency.exchangeRate;
  const fractionDigits = pageContext.currency.fractionDigits ?? 2;

  // 从数据库查询商品价格
  const priceMap = await findProductPrices(items, countryId);

  // 计算每个商品的价格（转换为目标货币，前端直接显示不再转换）
  const resultItems: CalculateResultItem[] = [];
  let subtotalDecimal = new Decimal(0);

  for (const item of items) {
    const key = `${item.productId}-${item.specId}`;
    const priceInfo = priceMap.get(key);

    if (!priceInfo) {
      throw new Error(
        `商品不存在或已下架: productId=${item.productId}, specId=${item.specId}`,
      );
    }

    // 转换价格（乘以汇率转换为目标货币）
    const convertedPrice = convertPrice(
      priceInfo.sellPrice,
      exchangeRate,
      fractionDigits,
    );
    const convertedOriginPrice = priceInfo.originPrice
      ? convertPrice(priceInfo.originPrice, exchangeRate, fractionDigits)
      : null;

    // 计算小计
    const itemSubtotal = new Decimal(convertedPrice).mul(item.quantity);
    subtotalDecimal = subtotalDecimal.add(itemSubtotal);

    resultItems.push({
      productId: item.productId,
      specId: item.specId,
      productName: priceInfo.title,
      specName: priceInfo.specTitle,
      price: convertedPrice,
      originPrice: convertedOriginPrice,
      quantity: item.quantity,
      subtotal: itemSubtotal.toFixed(fractionDigits, Decimal.ROUND_HALF_UP),
      image: priceInfo.imagePath,
    });
  }

  // 运费计算（目前固定为 0，后续可扩展）
  const shippingFeeDecimal = new Decimal(0);

  // 优惠计算（目前固定为 0，后续营销活动可扩展）
  const discountDecimal = new Decimal(0);

  // 税费计算（目前固定为 0，后续可扩展）
  const taxDecimal = new Decimal(0);

  // 计算总计
  const totalDecimal = subtotalDecimal
    .add(shippingFeeDecimal)
    .sub(discountDecimal);
  const finalTotal = Decimal.max(totalDecimal, new Decimal(0));

  return {
    items: resultItems,
    subtotal: subtotalDecimal.toFixed(fractionDigits, Decimal.ROUND_HALF_UP),
    shippingFee: shippingFeeDecimal.toFixed(
      fractionDigits,
      Decimal.ROUND_HALF_UP,
    ),
    discount: discountDecimal.toFixed(fractionDigits, Decimal.ROUND_HALF_UP),
    tax: taxDecimal.toFixed(fractionDigits, Decimal.ROUND_HALF_UP),
    total: finalTotal.toFixed(fractionDigits, Decimal.ROUND_HALF_UP),
  };
}

// ============ 订单创建 ============

/**
 * 解析平台类型
 */
function normalizePlatform(userAgent: string | null): string {
  if (!userAgent) return "UNKNOWN";
  const ua = userAgent.toUpperCase();
  if (ua.includes("ANDROID")) return "ANDROID";
  if (ua.includes("IPAD")) return "IPAD";
  if (ua.includes("IPHONE") || ua.includes("IOS")) return "IOS";
  if (ua.includes("WIN")) return "WINDOWS";
  if (ua.includes("MAC")) return "MAC";
  if (ua.includes("LINUX")) return "LINUX";
  if (ua.includes("MOBILE")) return "MOBILE";
  return "DESKTOP";
}

/**
 * 创建订单
 *
 * @param request 下单请求
 * @param pageContext 页面上下文
 * @param riskData 风险数据（IP、UA 等）
 * @returns 订单创建结果
 */
export async function createOrder(
  request: CreateOrderRequest,
  pageContext: PageContext,
  riskData: {
    ip: string | null;
    realIp: string | null;
    userAgent: string | null;
    fingerprint: string | null;
    fromUrl: string | null;
    themeName: string | null;
  },
): Promise<CreateOrderResult> {
  // 1. 复用价格计算接口计算金额
  const priceResult = await calculateOrderPrice(request.items, pageContext);

  if (priceResult.items.length === 0) {
    throw new Error("购物车为空，无法下单");
  }

  const countryId = pageContext.country.id;
  const exchangeRate = pageContext.currency.exchangeRate;
  const fractionDigits = pageContext.currency.fractionDigits ?? 2;

  // 2. 获取完整的商品信息用于订单项
  const priceMap = await findProductPrices(request.items, countryId);

  // 3. 构建订单数据
  const now = new Date();
  const phone = request.shippingAddress.phone || "";

  // 收货信息
  const deliveryInfo: OrderDeliveryInfo = {
    firstName: request.shippingAddress.fullName,
    lastName: "",
    phone: phone,
    phoneLast8: phone.replace(/\D/g, "").slice(-8),
    email: request.shippingAddress.email || null,
    province: request.shippingAddress.province || null,
    city: request.shippingAddress.city || null,
    district: request.shippingAddress.district || null,
    postalCode: request.shippingAddress.postalCode || null,
    address: request.shippingAddress.address || null,
    receiveUpdates: request.shippingAddress.subscribeToUpdates || false,
    remoteArea: false,
    remark: request.shippingAddress.note || null,
  };

  // 金额信息
  const financialInfo: OrderFinancialInfo = {
    totalAmount: priceResult.total,
    shippingFee: priceResult.shippingFee,
    discountAmount: priceResult.discount,
    taxAmount: priceResult.tax,
  };

  // 支付信息
  const paymentInfo: OrderPaymentInfo = {
    paymentMethod: request.paymentMethod === "online" ? "ONLINE" : "COD",
    paymentStatus: "PENDING",
    paymentTime: null,
  };

  // 获取当前语言信息
  const currentLanguage = pageContext.currentLanguage || null;

  // 获取网站名称（从全局配置）
  const websiteName =
    pageContext.pageTheme?.siteConfig?.globalConfig?.siteName || null;

  // 上下文信息
  const contextInfo: OrderContextInfo = {
    companyId: pageContext.company.id,
    salesUid: pageContext.salesUser.id,
    salesPerson: pageContext.salesUser.name,
    departmentId: pageContext.salesUser.departmentId,
    department: pageContext.salesUser.departmentName,
    websiteId: pageContext.subDomain.websiteId,
    websiteName: websiteName,
    websiteUrl: pageContext.subDomain.fullName,
    countryId: pageContext.country.id,
    countryCode: pageContext.country.code,
    country: pageContext.country.name,
    currencyId: pageContext.currency.id,
    currencyCode: pageContext.currency.code,
    currencyName: pageContext.currency.name,
    currencySymbol: pageContext.currency.symbol,
    currencyExchangeRate: String(pageContext.currency.exchangeRate || 1),
    currencyFractionDigits: pageContext.currency.fractionDigits,
    languageId: pageContext.currentLanguageId,
    language: currentLanguage?.name || null,
    languageCode: currentLanguage?.code || null,
    phoneRule: pageContext.country.phoneRule,
    phonePrefix: pageContext.country.phonePrefix,
    addressRule: pageContext.country.addressRule,
  };

  // 风险记录
  const riskInfo: OrderRiskRecordInfo = {
    deviceId: riskData.fingerprint,
    remoteIp: riskData.ip,
    remoteIpInfo: null,
    realIp: riskData.realIp,
    realIpInfo: null,
    ua: riskData.userAgent,
    pdKey: pageContext.company?.accessKey || "",
    pdVal: pageContext.cloak?.pdVal || "",
    cloak: pageContext.cloak?.page === CloakPage.CLOAK,
    browserPlatform: normalizePlatform(riskData.userAgent),
  };

  // 订单商品项
  const orderItems: OrderItemInfo[] = request.items.map((item) => {
    const key = `${item.productId}-${item.specId}`;
    const priceInfo = priceMap.get(key)!;
    const convertedPrice = convertPrice(
      priceInfo.sellPrice,
      exchangeRate,
      fractionDigits,
    );
    const convertedOriginPrice = priceInfo.originPrice
      ? convertPrice(priceInfo.originPrice, exchangeRate, fractionDigits)
      : null;
    const convertedCostPrice = priceInfo.costPrice
      ? convertPrice(priceInfo.costPrice, exchangeRate, fractionDigits)
      : null;

    return {
      spuId: priceInfo.spuId,
      productId: item.productId,
      title: priceInfo.title,
      specTitle: priceInfo.specTitle || priceInfo.title,
      imageId: priceInfo.imageId,
      sellPrice: convertedPrice,
      originPrice: convertedOriginPrice,
      costPrice: convertedCostPrice,
      quantity: item.quantity,
      skuId: priceInfo.skuId || 0,
      skuCode: priceInfo.skuCode,
      skuName: priceInfo.skuName,
      skuIsVirtual: priceInfo.skuIsVirtual,
      merchandise: priceInfo.merchandise,
      waybillProductName: priceInfo.waybillProductName,
    };
  });

  // 4. 创建订单
  const orderData: CreateOrderData = {
    companyId: pageContext.company.id,
    from: riskData.themeName?.toUpperCase() || "V7_SHOP",
    fromUrl: riskData.fromUrl,
    platform: "V7_SHOP",
    orderTime: now,
    deliveryInfo,
    financialInfo,
    paymentInfo,
    contextInfo,
    riskInfo,
    items: orderItems,
  };

  const result = await createTemporaryOrder(orderData, pageContext.spuId || 0);

  return {
    orderId: result.orderId,
    total: priceResult.total,
  };
}
