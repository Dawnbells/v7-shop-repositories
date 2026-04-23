/**
 * 计算订单价格 API
 * POST /api/checkout/calculate
 *
 * 根据商品列表计算订单价格，包括小计、运费、优惠、总计
 * 价格从数据库实时查询，防止前端篡改
 */

import {
  calculateOrderPrice,
  type CalculateRequestItem,
} from "../../services/checkoutService";
import { getPageContext } from "../../utils/page-context";

interface CalculateRequest {
  items: CalculateRequestItem[];
}

export default defineEventHandler(async (event) => {
  const t0 = performance.now();

  // 1. 获取页面上下文
  const pageContext = getPageContext(event);
  const t1 = performance.now();
  console.log(`[Checkout Timing] getPageContext: ${(t1 - t0).toFixed(1)}ms`);

  // 检查必需的上下文数据
  if (!pageContext.country || !pageContext.currency) {
    throw createError({
      statusCode: 500,
      message: "页面上下文未初始化，请刷新页面重试",
    });
  }

  // 2. 解析请求体
  const body = await readBody<CalculateRequest>(event);
  const t2 = performance.now();
  console.log(`[Checkout Timing] readBody: ${(t2 - t1).toFixed(1)}ms`);

  if (!body?.items || !Array.isArray(body.items)) {
    throw createError({
      statusCode: 400,
      statusMessage: "请求参数错误：items 必须是数组",
    });
  }

  // 验证每个商品项
  for (let i = 0; i < body.items.length; i++) {
    const item = body.items[i]!;
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

  try {
    // 3. 计算价格
    const t3 = performance.now();
    const result = await calculateOrderPrice(body.items, pageContext);
    const t4 = performance.now();
    console.log(`[Checkout Timing] calculateOrderPrice: ${(t4 - t3).toFixed(1)}ms | total: ${(t4 - t0).toFixed(1)}ms`);

    return {
      success: true,
      data: result,
    };
  } catch (error: any) {
    console.error("[Checkout API] Calculate price error:", error.message);

    // 商品不存在等业务错误
    if (error.message?.includes("商品不存在")) {
      throw createError({
        statusCode: 400,
        statusMessage: error.message,
      });
    }

    throw createError({
      statusCode: 500,
      statusMessage: "计算价格失败，请稍后重试",
    });
  }
});
