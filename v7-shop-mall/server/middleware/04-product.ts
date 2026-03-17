/**
 * 商品信息加载中间件
 * 仅在商品详情页 (/product/[id]) 执行
 * 根据 landingSpuId + countryId 获取商品详细信息
 * 解析 introduction 字段中的图片，查询图片宽高信息
 * 设置 PageContext.productInfo
 */

import {
  findProductDetailBySpuAndCountry,
  findMultimediaFilesByIds,
  type IntroductionImage,
  type IntroductionItem,
} from "../repositories/productRepository";
import { getPageContext, updatePageContext } from "../utils/page-context";
import { logger } from "../utils/logger";

/** 匹配 /multimedia/{id} 格式的图片路径 */
const MULTIMEDIA_REGEX = /\/multimedia\/(\d+)/g;

/** 匹配 img 标签中 src 为 /multimedia/{id} 的完整标签 */
const IMG_TAG_REGEX = /<img[^>]*src="\/multimedia\/(\d+)"[^>]*>/g;

/**
 * 解析 introduction HTML，提取图片信息并构建结构化数据
 *
 * 处理流程：
 * 1. 使用正则提取所有 /multimedia/{id} 格式的图片 ID
 * 2. 批量查询 t_multimedia_files 表获取图片的 width、height、relativePath
 * 3. 遍历 HTML，将内容拆分为 image 和 html 两种类型的结构化数据
 * 4. 对于图片，计算 aspectRatio（宽高比），用于前端自适应显示
 *
 * @param introduction - 商品详情 HTML 字符串，包含 img 标签和其他 HTML 内容
 * @returns 结构化的详情数据数组，每个元素为 image 或 html 类型
 */
async function parseIntroduction(
  introduction: string | null,
): Promise<IntroductionItem[]> {
  if (!introduction) {
    return [];
  }

  // 第一步：提取所有图片 ID（去重）
  const imageIds: number[] = [];
  const matches = introduction.matchAll(MULTIMEDIA_REGEX);
  for (const match of matches) {
    const idStr = match[1];
    if (idStr) {
      const id = parseInt(idStr, 10);
      if (!isNaN(id) && !imageIds.includes(id)) {
        imageIds.push(id);
      }
    }
  }

  // 第二步：批量查询图片信息，构建 id -> imageInfo 映射
  let imageMap = new Map<number, IntroductionImage>();
  if (imageIds.length > 0) {
    const images = await findMultimediaFilesByIds(imageIds);
    imageMap = new Map(images.map((img) => [img.id, img]));
  }

  // 第三步：遍历 HTML，按 img 标签位置拆分内容
  const result: IntroductionItem[] = [];
  let lastIndex = 0;
  let imgMatch;

  while ((imgMatch = IMG_TAG_REGEX.exec(introduction)) !== null) {
    // 添加 img 标签之前的 HTML 内容
    if (imgMatch.index > lastIndex) {
      const htmlContent = introduction.slice(lastIndex, imgMatch.index).trim();
      if (htmlContent) {
        result.push({
          type: "html",
          content: htmlContent,
        });
      }
    }

    // 处理图片
    const imageIdStr = imgMatch[1];
    if (!imageIdStr) continue;
    const imageId = parseInt(imageIdStr, 10);
    const imageInfo = imageMap.get(imageId);

    if (imageInfo) {
      // 计算宽高比，若宽或高为 0 则返回 null
      const aspectRatio =
        imageInfo.width > 0 && imageInfo.height > 0
          ? imageInfo.width / imageInfo.height
          : null;

      result.push({
        type: "image",
        id: imageId,
        src: imageInfo.relativePath,
        width: imageInfo.width,
        height: imageInfo.height,
        aspectRatio,
      });
    } else {
      // 数据库中未找到图片信息，使用原始路径
      result.push({
        type: "image",
        id: imageId,
        src: `/multimedia/${imageId}`,
        aspectRatio: null,
      });
    }

    lastIndex = imgMatch.index + imgMatch[0].length;
  }

  // 添加最后一个 img 标签之后的 HTML 内容
  if (lastIndex < introduction.length) {
    const htmlContent = introduction.slice(lastIndex).trim();
    if (htmlContent) {
      result.push({
        type: "html",
        content: htmlContent,
      });
    }
  }

  return result;
}

const PRODUCT_ROUTE = /^\/product\/[\w-]+(\?.*)?$/;

function isProductRoute(path: string): boolean {
  return PRODUCT_ROUTE.test(path);
}

export default defineEventHandler(async (event) => {
  const path = event.path;

  // 跳过非商品详情页路由
  if (!isProductRoute(path)) {
    return;
  }

  // 跳过 API 路由和编辑器路由
  if (
    path.startsWith("/api/") ||
    path.startsWith("/builder") ||
    path.startsWith("/_nuxt") ||
    path.startsWith("/__nuxt")
  ) {
    return;
  }

  const pageContext = getPageContext(event);

  const landingSpuId = pageContext.landingPage?.landingSpuId;
  const countryId = pageContext.country?.id;

  if (!landingSpuId) {
    logger.warn("[04-product] No landingSpuId found in pageContext");
    return;
  }

  if (!countryId) {
    logger.warn("[04-product] No countryId found in pageContext");
    return;
  }

  try {
    const productInfo = await findProductDetailBySpuAndCountry(landingSpuId, countryId);

    if (!productInfo) {
      logger.warn(`[04-product] Product not found for landingSpuId=${landingSpuId}, countryId=${countryId}`);
      return;
    }

    const introductionData = await parseIntroduction(productInfo.introduction);

    updatePageContext(event, {
      productInfo: {
        ...productInfo,
        introductionData,
      },
    });
  } catch (error) {
    logger.error("[04-product] Error loading product info:", error);
  }
});
