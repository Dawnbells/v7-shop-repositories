/**
 * 订单结果中间件
 * 仅在 /order-result 路由生效
 * 获取订单信息并脱敏后放入 pageContext
 */

import { updatePageContext } from "../utils/page-context";
import { findOrderById } from "../repositories/orderRepository";
import { logger } from "../utils/logger";

/**
 * 手机号脱敏: 138****1234
 */
function maskPhone(phone: string): string {
  if (phone.length <= 4) return "****";
  return phone.slice(0, 3) + "****" + phone.slice(-4);
}

/**
 * 邮箱脱敏: a****@example.com
 */
function maskEmail(email: string | null): string | null {
  if (!email) return null;
  const [local, domain] = email.split("@");
  if (!local || !domain) return "****";
  return local[0] + "****@" + domain;
}

export default defineEventHandler(async (event) => {
  const path = event.path;

  // 仅处理订单结果页面
  if (!path.startsWith("/order-result")) {
    return;
  }

  // 跳过 API 路由
  if (path.startsWith("/api/")) {
    return;
  }

  try {
    // 获取 orderId 参数
    const query = getQuery(event);
    const orderId = Number(query.orderId);

    if (!orderId || isNaN(orderId)) {
      logger.debug("[13-order] No valid orderId in query");
      return;
    }

    // 查询订单
    const order = await findOrderById(orderId);

    if (!order) {
      logger.debug("[13-order] Order not found:", orderId);
      return;
    }

    // 拼接完整地址：详细地址 + 区 + 市 + 省（从小到大）
    const fullAddress = [
      order.address,
      order.district,
      order.city,
      order.province,
    ]
      .filter(Boolean)
      .join(" ");

    // 脱敏并更新 pageContext
    updatePageContext(event, {
      orderResult: {
        id: order.id,
        totalAmount: order.totalAmount,
        currencySymbol: order.currencySymbol,
        currencyCode: order.currencyCode,
        firstName: order.firstName,
        phone: maskPhone(order.phone),
        email: maskEmail(order.email),
        address: fullAddress || null,
        paymentMethod: order.paymentMethod,
        paymentStatus: order.paymentStatus,
        orderTime: order.orderTime.toISOString(),
      },
    });

    logger.debug("[13-order] Order result loaded:", {
      orderId: order.id,
      paymentStatus: order.paymentStatus,
    });
  } catch (error) {
    logger.error("[13-order] Error loading order:", error);
  }
});
