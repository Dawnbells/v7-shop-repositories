/**
 * 产品 Repository
 * 封装产品相关的数据库查询
 */

import { query, queryOne } from '../utils/db'

export interface ProductImage {
  id: number
  relativePath: string
  name: string
}

export interface ProductSpecification {
  id: number
  skuId: number
  sellPrice: number
  originPrice: number
  stockQuantity: number
  attributes: Array<{ name: string; value: string }>
}

export interface ProductInfo {
  id: number
  spuId: number
  title: string
  merchandise: string | null
  introduction: string | null
  summary: string | null
  sellPrice: number
  originPrice: number | null
  isMultiSpecs: boolean
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
      p.title,
      p.merchandise,
      p.introduction,
      p.summary,
      p.sell_price as sellPrice,
      p.origin_price as originPrice,
      p.is_multi_specs as isMultiSpecs
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
    isMultiSpecs: Boolean(row.isMultiSpecs),
  }
}

/**
 * 查询产品图片列表
 */
export async function findProductImages(productId: string | number): Promise<ProductImage[]> {
  const sql = `
    SELECT 
      id,
      relative_path as relativePath,
      name
    FROM t_product_images
    WHERE product_id = ?
    ORDER BY sort_order ASC
  `

  return query<ProductImage>(sql, [productId])
}

/**
 * 查询产品规格列表
 */
export async function findProductSpecifications(productId: string | number): Promise<ProductSpecification[]> {
  const sql = `
    SELECT 
      s.id,
      s.sku_id as skuId,
      s.sell_price as sellPrice,
      s.origin_price as originPrice,
      s.stock_quantity as stockQuantity,
      s.attributes
    FROM t_product_specifications s
    WHERE s.product_id = ?
    ORDER BY s.sort_order ASC
  `

  const rows = await query<any>(sql, [productId])

  return rows.map(row => ({
    ...row,
    attributes: typeof row.attributes === 'string'
      ? JSON.parse(row.attributes)
      : (row.attributes || []),
  }))
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
