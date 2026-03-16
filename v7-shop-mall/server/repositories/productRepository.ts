/**
 * 产品 Repository
 * 封装产品相关的数据库查询
 */

import { query, queryOne } from "../utils/db";

export interface ProductImage {
  id: number;
  relativePath: string;
  name: string;
  width: number;
  height: number;
  suffix: string;
  fileSize: number;
  mediaType: string;
  mediaState: string;
}

export interface ProductSpecificationAttribute {
  name: string;
  value: string;
  imagePath: string | null;
}

export interface ProductSpecification {
  id: number;
  barcode: string | null;
  costPrice: number | null;
  linkStock: boolean;
  originPrice: number | null;
  sellPrice: number;
  stockQuantity: number;
  specImagePath: string | null;
  attributes: ProductSpecificationAttribute[];
}

export interface ProductInfo {
  id: number;
  spuId: number;
  skuId: number | null;
  countryId: number;
  languageId: number | null;
  title: string;
  summary: string | null;
  introduction: string | null;
  merchandise: string | null;
  waybillProductName: string | null;
  sellPrice: number;
  originPrice: number | null;
  costPrice: number | null;
  isTaxable: boolean;
  taxationMethod: string | null;
  fixedTaxAmount: number | null;
  taxAmountThreshold: number | null;
  taxQuantityThreshold: number;
  taxPerBase: number | null;
  barcode: string | null;
  stockQuantity: number;
  linkStock: boolean;
  isMultiSpecs: boolean;
  videoFileId: number | null;
  botShowSpuId: number | null;
  riskUserShowSpuId: number | null;
  blacklistedUserShowSpuId: number | null;
}

export interface ProductDetail extends ProductInfo {
  images: ProductImage[];
  specifications: ProductSpecification[];
}

/**
 * 将规格查询结果按 id 分组，聚合属性
 */
function groupSpecificationRows(rows: any[]): ProductSpecification[] {
  const specMap = new Map<number, ProductSpecification>();

  for (const row of rows) {
    if (!specMap.has(row.id)) {
      specMap.set(row.id, {
        id: row.id,
        barcode: row.barcode,
        costPrice: row.costPrice,
        linkStock: Boolean(row.linkStock),
        originPrice: row.originPrice,
        sellPrice: row.sellPrice,
        stockQuantity: row.stockQuantity,
        specImagePath: row.specImagePath || null,
        attributes: [],
      });
    }

    if (row.attrName && row.attrValue) {
      specMap.get(row.id)!.attributes.push({
        name: row.attrName,
        value: row.attrValue,
        imagePath: row.attrImagePath || null,
      });
    }
  }

  return Array.from(specMap.values());
}

/**
 * 根据 SPU ID 和国家 ID 查询产品完整详情
 * 一次主查询 + 并行查询图片和规格
 */
export async function findProductDetailBySpuAndCountry(
  spuId: number,
  countryId: number,
): Promise<ProductDetail | null> {
  // 1. 主查询：获取商品基本信息
  const productSql = `
    SELECT 
      p.id,
      p.spu_id as spuId,
      p.sku_id as skuId,
      p.country_id as countryId,
      p.language_id as languageId,
      p.title,
      p.summary,
      p.introduction,
      p.merchandise,
      p.waybill_product_name as waybillProductName,
      p.sell_price as sellPrice,
      p.origin_price as originPrice,
      p.cost_price as costPrice,
      p.is_taxable as isTaxable,
      p.taxation_method as taxationMethod,
      p.fixed_tax_amount as fixedTaxAmount,
      p.tax_amount_threshold as taxAmountThreshold,
      p.tax_quantity_threshold as taxQuantityThreshold,
      p.tax_per_base as taxPerBase,
      p.barcode,
      p.stock_quantity as stockQuantity,
      p.link_stock as linkStock,
      p.is_multi_specs as isMultiSpecs,
      p.video_file_id as videoFileId,
      p.bot_show_spu_id as botShowSpuId,
      p.risk_user_show_spu_id as riskUserShowSpuId,
      p.black_listed_user_show_spu_id as blacklistedUserShowSpuId
    FROM t_products p
    WHERE p.spu_id = ? AND p.country_id = ? AND p.status = 'VALID'
    LIMIT 1
  `;

  const productRow = await queryOne<any>(productSql, [spuId, countryId]);

  if (!productRow) {
    return null;
  }

  const product: ProductInfo = {
    ...productRow,
    isTaxable: Boolean(productRow.isTaxable),
    linkStock: Boolean(productRow.linkStock),
    isMultiSpecs: Boolean(productRow.isMultiSpecs),
  };

  // 2. 并行查询：图片和规格同时执行
  const imagesSql = `
    SELECT 
      mf.id,
      mf.name,
      mf.width,
      mf.height,
      mf.suffix,
      mf.file_size as fileSize,
      mf.relative_path as relativePath,
      mf.media_type as mediaType,
      mf.media_state as mediaState
    FROM t_product_images pi
    JOIN t_multimedia_files mf ON pi.image_file_id = mf.id
    WHERE pi.product_id = ?
  `;

  const specsSql = `
    SELECT 
      s.id,
      s.barcode,
      s.cost_price AS costPrice,
      s.link_stock AS linkStock,
      s.origin_price AS originPrice,
      s.sell_price AS sellPrice,
      s.stock_quantity AS stockQuantity,
      mf.relative_path AS specImagePath,
      a.name AS attrName,
      a.value AS attrValue,
      am.relative_path AS attrImagePath
    FROM t_product_specifications s
    LEFT JOIN t_product_specification_attributes a 
      ON a.product_specification_id = s.id
    LEFT JOIN t_multimedia_files am 
      ON am.id = a.multimedia_file_id
    LEFT JOIN t_multimedia_files mf 
      ON mf.id = s.specification_image_id
    WHERE s.product_id = ?
    ORDER BY s.id, a.id
  `;

  const [images, specRows] = await Promise.all([
    query<ProductImage>(imagesSql, [product.id]),
    product.isMultiSpecs
      ? query<any>(specsSql, [product.id])
      : Promise.resolve([]),
  ]);

  const specifications = groupSpecificationRows(specRows);

  return {
    ...product,
    images,
    specifications,
  };
}
