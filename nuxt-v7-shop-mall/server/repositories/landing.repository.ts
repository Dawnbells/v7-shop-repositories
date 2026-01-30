/**
 * 落地页数据仓库
 * 封装落地页产品相关的数据库操作
 */

import type { ProductInfo, ProductImage, ProductSpecification, SpecificationAttribute } from "~/types/page-context";
import { query, queryOne } from "../utils/db";

/**
 * 解析 JSON 字段（mysql2 对 JSON 类型字段可能返回字符串或对象）
 */
function parseJsonField(value: any): any {
  if (value === null || value === undefined) {
    return null;
  }
  // 如果已经是对象，直接返回
  if (typeof value === "object") {
    return value;
  }
  // 如果是字符串，尝试解析
  if (typeof value === "string") {
    try {
      return JSON.parse(value);
    } catch {
      console.warn("[Landing Repository] Failed to parse JSON field");
      return null;
    }
  }
  return null;
}

/**
 * 根据 SPU ID 和语言 ID 直接查询产品信息（用于 LAND 类型）
 * 
 * @param spuId SPU ID
 * @param languageId 语言 ID
 * @param subDomainId 子域名 ID（可选，用于获取渲染配置）
 */
export async function findProductBySpuId(
  spuId: number,
  languageId: number,
  subDomainId?: number
): Promise<ProductInfo | null> {
  const productSql = `
    SELECT 
      p.id, p.spu_id, p.language_id, p.title, p.merchandise, p.introduction, p.summary,
      p.sell_price, p.origin_price, p.is_multi_specs
    FROM t_products p
    WHERE p.spu_id = ? AND p.language_id = ? AND p.status = 'VALID'
    LIMIT 1
  `;

  const productRow = await queryOne(productSql, [spuId, languageId]);
  if (!productRow) {
    return null;
  }

  // 获取渲染配置（包含主题配置、站点配置、变量值）
  let renderConfig: RenderConfig | null = null;
  if (subDomainId) {
    renderConfig = await findRenderConfig(subDomainId, spuId, "LAND");
  }

  return await buildProductInfo(productRow, renderConfig);
}

/**
 * 根据子域名、SPU ID 和语言 ID 查询 CLOAK 类型的落地页产品信息
 * 连表查询 t_sub_domain_spu_landing_pages 和 t_products
 * 
 * @param subDomainId 子域名 ID
 * @param spuId 原始 SPU ID
 * @param languageId 语言 ID
 */
export async function findCloakLandingProduct(
  subDomainId: number,
  spuId: number,
  languageId: number
): Promise<ProductInfo | null> {
  // 连表查询：t_sub_domain_spu_landing_pages JOIN t_products
  // CLOAK、CRAWLER、RISK 都使用 CLOAK 配置
  // 同时获取渲染配置（theme_config、site_config、variable_values）
  const productSql = `
    SELECT 
      p.id, p.spu_id, p.language_id, p.title, p.merchandise, p.introduction, p.summary,
      p.sell_price, p.origin_price, p.is_multi_specs,
      lp.theme_config, lp.site_config, lp.variable_values
    FROM t_sub_domain_spu_landing_pages lp
    JOIN t_products p ON lp.landing_page_spu_id = p.spu_id 
      AND p.language_id = ? AND p.status = 'VALID'
    WHERE lp.sub_domain_id = ? AND lp.spu_id = ? AND lp.landing_page_type = 'CLOAK'
    LIMIT 1
  `;

  const productRow = await queryOne(productSql, [languageId, subDomainId, spuId]);
  if (!productRow) {
    return null;
  }

  // 构建渲染配置
  const renderConfig: RenderConfig = {
    themeConfig: parseJsonField(productRow.theme_config),
    siteConfig: parseJsonField(productRow.site_config) || {},
    variableValues: parseJsonField(productRow.variable_values) || {},
  };

  return await buildProductInfo(productRow, renderConfig);
}

/**
 * 渲染配置结果（包含主题配置、站点配置、变量值）
 */
interface RenderConfig {
  themeConfig: any;
  siteConfig: Record<string, any>;
  variableValues: Record<string, any>;
}

/**
 * 查询渲染配置（包含 theme_config、site_config、variable_values）
 * 前端渲染不需要 variable_schema
 */
async function findRenderConfig(
  subDomainId: number,
  spuId: number,
  landingType: string
): Promise<RenderConfig | null> {
  const sql = `
    SELECT theme_config, site_config, variable_values
    FROM t_sub_domain_spu_landing_pages
    WHERE sub_domain_id = ? AND spu_id = ? AND landing_page_type = ?
    LIMIT 1
  `;

  const row = await queryOne(sql, [subDomainId, spuId, landingType]);
  if (!row) {
    return null;
  }

  return {
    themeConfig: parseJsonField(row.theme_config),
    siteConfig: parseJsonField(row.site_config) || {},
    variableValues: parseJsonField(row.variable_values) || {},
  };
}

/**
 * 查询主题配置（兼容旧代码）
 * @deprecated 使用 findRenderConfig 替代
 */
async function findThemeConfig(
  subDomainId: number,
  spuId: number,
  landingType: string
): Promise<any | null> {
  const config = await findRenderConfig(subDomainId, spuId, landingType);
  return config?.themeConfig || null;
}

/**
 * 构建完整的产品信息（包含图片和规格）
 * @param productRow 产品数据行
 * @param renderConfig 渲染配置（包含主题配置、站点配置、变量值）
 */
async function buildProductInfo(
  productRow: any,
  renderConfig: RenderConfig | null = null
): Promise<ProductInfo> {
  const productId = productRow.id;

  // 查询产品图片
  const images = await findProductImages(productId);

  // 查询产品规格
  const specifications = await findProductSpecifications(productId);

  return {
    id: productRow.id,
    spuId: productRow.spu_id,
    languageId: productRow.language_id,
    title: productRow.title,
    merchandise: productRow.merchandise,
    introduction: productRow.introduction,
    summary: productRow.summary,
    sellPrice: parseFloat(productRow.sell_price),
    originPrice: productRow.origin_price ? parseFloat(productRow.origin_price) : null,
    isMultiSpecs: !!productRow.is_multi_specs,
    images,
    specifications,
    // 渲染配置
    themeConfig: renderConfig?.themeConfig || null,
    siteConfig: renderConfig?.siteConfig || {},
    variableValues: renderConfig?.variableValues || {},
  };
}

/**
 * 查询产品图片
 */
async function findProductImages(productId: number): Promise<ProductImage[]> {
  const sql = `
    SELECT mf.id, mf.relative_path, mf.name
    FROM t_product_images pi
    JOIN t_multimedia_files mf ON pi.image_file_id = mf.id AND mf.status = 'VALID'
    WHERE pi.product_id = ?
  `;

  const rows = await query(sql, [productId]);
  return rows.map((row: any) => ({
    id: row.id,
    relativePath: row.relative_path,
    name: row.name,
  }));
}

/**
 * 查询产品规格
 */
async function findProductSpecifications(productId: number): Promise<ProductSpecification[]> {
  const specSql = `
    SELECT id, sku_id, sell_price, origin_price, stock_quantity
    FROM t_product_specifications
    WHERE product_id = ? AND status = 'VALID'
  `;

  const specRows = await query(specSql, [productId]);
  
  const specifications: ProductSpecification[] = [];
  
  for (const row of specRows) {
    // 查询规格属性
    const attributes = await findSpecificationAttributes(row.id);
    
    specifications.push({
      id: row.id,
      skuId: row.sku_id,
      sellPrice: parseFloat(row.sell_price),
      originPrice: row.origin_price ? parseFloat(row.origin_price) : null,
      stockQuantity: row.stock_quantity,
      attributes,
    });
  }

  return specifications;
}

/**
 * 查询规格属性
 */
async function findSpecificationAttributes(specificationId: number): Promise<SpecificationAttribute[]> {
  const sql = `
    SELECT name, value
    FROM t_product_specification_attributes
    WHERE product_specification_id = ? AND status = 'VALID'
  `;

  const rows = await query(sql, [specificationId]);
  return rows.map((row: any) => ({
    name: row.name,
    value: row.value,
  }));
}
