/**
 * 创建订单 API
 * POST /api/checkout/order
 *
 * 提交订单，包括商品列表和收货信息
 * 复用价格计算接口进行金额计算，确保一致性
 */

import {
  createOrder,
  type CalculateRequestItem,
  type ShippingAddressInput,
} from "../../services/checkoutService";
import { getPageContext } from "../../utils/page-context";

interface OrderRequest {
  items: CalculateRequestItem[];
  shippingAddress: ShippingAddressInput;
  paymentMethod: "cod" | "online";
}

/**
 * 获取客户端 IP
 */
function getClientIP(event: any): string | null {
  const headers = event.node.req.headers;

  // 优先使用代理头
  const forwardedFor = headers["x-forwarded-for"];
  if (forwardedFor) {
    const ips = String(forwardedFor).split(",");
    return ips[0]?.trim() || null;
  }

  const realIp = headers["x-real-ip"];
  if (realIp) {
    return String(realIp);
  }

  const cfConnectingIp = headers["cf-connecting-ip"];
  if (cfConnectingIp) {
    return String(cfConnectingIp);
  }

  // 回退到 socket 地址
  return event.node.req.socket?.remoteAddress || null;
}

export default defineEventHandler(async (event) => {
  // 1. 获取页面上下文
  const pageContext = getPageContext(event);

  // 检查必需的上下文数据
  if (
    !pageContext.country ||
    !pageContext.currency ||
    !pageContext.company ||
    !pageContext.salesUser
  ) {
    throw createError({
      statusCode: 500,
      message: "页面上下文未初始化，请刷新页面重试",
    });
  }

  // 2. 解析请求体
  const body = await readBody<OrderRequest>(event);

  // 3. 验证请求参数
  if (!body?.items || !Array.isArray(body.items) || body.items.length === 0) {
    throw createError({
      statusCode: 400,
      statusMessage: "购物车为空",
    });
  }

  if (!body.shippingAddress) {
    throw createError({
      statusCode: 400,
      statusMessage: "缺少收货信息",
    });
  }

  // 验证必填字段
  const { shippingAddress } = body;
  if (!shippingAddress.fullName?.trim()) {
    throw createError({
      statusCode: 400,
      statusMessage: "请填写收货人姓名",
    });
  }

  if (!shippingAddress.phone?.trim()) {
    throw createError({
      statusCode: 400,
      statusMessage: "请填写联系电话",
    });
  }

  if (!shippingAddress.address?.trim()) {
    throw createError({
      statusCode: 400,
      statusMessage: "请填写详细地址",
    });
  }

  // 验证商品项
  for (let i = 0; i < body.items.length; i++) {
    const item = body.items[i];
    if (!item) continue;
    if (typeof item.productId !== "number" || item.productId <= 0) {
      throw createError({
        statusCode: 400,
        statusMessage: `商品 #${i + 1} 的 productId 无效`,
      });
    }
    if (typeof item.quantity !== "number" || item.quantity <= 0) {
      throw createError({
        statusCode: 400,
        statusMessage: `商品 #${i + 1} 的 quantity 必须大于 0`,
      });
    }
  }

  // 验证支付方式
  if (!["cod", "online"].includes(body.paymentMethod)) {
    throw createError({
      statusCode: 400,
      statusMessage: "无效的支付方式",
    });
  }

  try {
    // 4. 获取风险数据
    const headers = event.node.req.headers;
    const cookies = parseCookies(event);

    const riskData = {
      ip: getClientIP(event),
      realIp: (headers["v7-real-ip"] as string) || null,
      userAgent: (headers["user-agent"] as string) || null,
      fingerprint: pageContext.fingerprint || null,
      fromUrl: cookies["from_url"] || null,
      themeName: cookies["themeName"] || null,
    };

    // 5. 创建订单
    const result = await createOrder(
      {
        items: body.items,
        shippingAddress: body.shippingAddress,
        paymentMethod: body.paymentMethod,
      },
      pageContext,
      riskData,
    );

    return {
      success: true,
      data: {
        orderId: String(result.orderId),
        total: result.total,
      },
    };
  } catch (error: any) {
    console.error("[Checkout API] Create order error:", error);

    // 业务错误
    if (
      error.message?.includes("商品不存在") ||
      error.message?.includes("购物车为空")
    ) {
      throw createError({
        statusCode: 400,
        statusMessage: error.message,
      });
    }

    throw createError({
      statusCode: 500,
      statusMessage: "下单失败，请稍后重试",
    });
  }
});
