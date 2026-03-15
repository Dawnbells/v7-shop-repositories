/**
 * 产品 Repository
 * 封装产品相关的数据库查询
 */

import { query, queryOne } from '../utils/db'

export interface ProductImage {
  id: number
  relativePath: string
  name: string
  width: number
  height: number
  suffix: string
  fileSize: number
  mediaType: string
  mediaState: string
}

export interface ProductSpecificationAttribute {
  name: string
  value: string
}

export interface ProductSpecification {
  id: number
  sid: number | null
  skuId: number
  sellPrice: number
  originPrice: number | null
  costPrice: number | null
  barcode: string | null
  stockQuantity: number
  linkStock: boolean
  specificationImageId: number | null
  attributes: ProductSpecificationAttribute[]
}

export interface ProductInfo {
  id: number
  spuId: number
  skuId: number | null
  countryId: number
  languageId: number | null
  title: string
  summary: string | null
  introduction: string | null
  merchandise: string | null
  waybillProductName: string | null
  sellPrice: number
  originPrice: number | null
  costPrice: number | null
  isTaxable: boolean
  taxationMethod: string | null
  fixedTaxAmount: number | null
  taxAmountThreshold: number | null
  taxQuantityThreshold: number
  taxPerBase: number | null
  barcode: string | null
  stockQuantity: number
  linkStock: boolean
  isMultiSpecs: boolean
  videoFileId: number | null
  botShowSpuId: number | null
  riskUserShowSpuId: number | null
  blacklistedUserShowSpuId: number | null
}

export interface ProductDetail extends ProductInfo {
  images: ProductImage[]
  specifications: ProductSpecification[]
}

/**
 * 根据 ID 查询产品基本信息
 */
export async function findProductById(id: string | number): Promise<ProductInfo | null> {
  const sql = `
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
    WHERE p.id = ? AND p.status = 'ACTIVE'
    LIMIT 1
  `

  const row = await queryOne<any>(sql, [id])

  if (!row) {
    return null
  }

  return {
    ...row,
    isTaxable: Boolean(row.isTaxable),
    linkStock: Boolean(row.linkStock),
    isMultiSpecs: Boolean(row.isMultiSpecs),
  }
}

/**
 * 查询产品图片列表
 */
export async function findProductImages(productId: string | number): Promise<ProductImage[]> {
  const sql = `
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
    ORDER BY pi.sort_order ASC
  `

  return query<ProductImage>(sql, [productId])
}

/**
 * 查询产品规格列表（含属性）
 */
export async function findProductSpecifications(productId: string | number): Promise<ProductSpecification[]> {
  const sql = `
    SELECT 
      ps.id,
      ps.sid,
      ps.sku_id as skuId,
      ps.sell_price as sellPrice,
      ps.origin_price as originPrice,
      ps.cost_price as costPrice,
      ps.barcode,
      ps.stock_quantity as stockQuantity,
      ps.link_stock as linkStock,
      ps.specification_image_id as specificationImageId,
      psa.name as attrName,
      psa.value as attrValue
    FROM t_product_specifications ps
    LEFT JOIN t_product_specification_attributes psa 
      ON psa.product_specification_id = ps.id AND psa.status <> 'DELETED'
    WHERE ps.product_id = ? AND ps.status <> 'DELETED'
    ORDER BY ps.sort_order ASC, psa.id ASC
  `

  const rows = await query<any>(sql, [productId])

  return groupSpecificationRows(rows)
}

/**
 * 将规格查询结果按 id 分组，聚合属性
 */
function groupSpecificationRows(rows: any[]): ProductSpecification[] {
  const specMap = new Map<number, ProductSpecification>()

  for (const row of rows) {
    if (!specMap.has(row.id)) {
      specMap.set(row.id, {
        id: row.id,
        sid: row.sid,
        skuId: row.skuId,
        sellPrice: row.sellPrice,
        originPrice: row.originPrice,
        costPrice: row.costPrice,
        barcode: row.barcode,
        stockQuantity: row.stockQuantity,
        linkStock: Boolean(row.linkStock),
        specificationImageId: row.specificationImageId,
        attributes: [],
      })
    }

    if (row.attrName && row.attrValue) {
      specMap.get(row.id)!.attributes.push({
        name: row.attrName,
        value: row.attrValue,
      })
    }
  }

  return Array.from(specMap.values())
}

/**
 * 查询产品完整详情（包含图片和规格）
 */
export async function findProductDetail(id: string | number): Promise<ProductDetail | null> {
  const product = await findProductById(id)

  if (!product) {
    return null
  }

  const images = await findProductImages(id)

  let specifications: ProductSpecification[] = []
  if (product.isMultiSpecs) {
    specifications = await findProductSpecifications(id)
  }

  return {
    ...product,
    images,
    specifications,
  }
}

/**
 * 根据 SPU ID 和国家 ID 查询产品 ID
 */
export async function findProductBySpuAndCountry(
  spuId: number,
  countryId: number
): Promise<number | null> {
  const sql = `
    SELECT id
    FROM t_products
    WHERE spu_id = ? AND country_id = ? AND status = 'ACTIVE'
    LIMIT 1
  `

  const row = await queryOne<{ id: number }>(sql, [spuId, countryId])

  return row?.id ?? null
}

/**
 * 根据 SPU ID 和国家 ID 查询产品完整详情
 * 一次主查询 + 并行查询图片和规格
 */
export async function findProductDetailBySpuAndCountry(
  spuId: number,
  countryId: number
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
    WHERE p.spu_id = ? AND p.country_id = ? AND p.status = 'ACTIVE'
    LIMIT 1
  `

  const productRow = await queryOne<any>(productSql, [spuId, countryId])

  if (!productRow) {
    return null
  }

  const product: ProductInfo = {
    ...productRow,
    isTaxable: Boolean(productRow.isTaxable),
    linkStock: Boolean(productRow.linkStock),
    isMultiSpecs: Boolean(productRow.isMultiSpecs),
  }

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
    ORDER BY pi.sort_order ASC
  `

  const specsSql = `
    SELECT 
      ps.id,
      ps.sid,
      ps.sku_id as skuId,
      ps.sell_price as sellPrice,
      ps.origin_price as originPrice,
      ps.cost_price as costPrice,
      ps.barcode,
      ps.stock_quantity as stockQuantity,
      ps.link_stock as linkStock,
      ps.specification_image_id as specificationImageId,
      psa.name as attrName,
      psa.value as attrValue
    FROM t_product_specifications ps
    LEFT JOIN t_product_specification_attributes psa 
      ON psa.product_specification_id = ps.id AND psa.status <> 'DELETED'
    WHERE ps.product_id = ? AND ps.status <> 'DELETED'
    ORDER BY ps.sort_order ASC, psa.id ASC
  `

  const [images, specRows] = await Promise.all([
    query<ProductImage>(imagesSql, [product.id]),
    product.isMultiSpecs
      ? query<any>(specsSql, [product.id])
      : Promise.resolve([]),
  ])

  const specifications = groupSpecificationRows(specRows)

  return {
    ...product,
    images,
    specifications,
  }
}
