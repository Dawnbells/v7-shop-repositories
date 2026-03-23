/**
 * 订单 Repository
 * 封装订单相关的数据库查询和写入
 */

import { query, queryOne, getPool } from "../utils/db";
import type { PoolConnection } from "mysql2/promise";

// ============ 类型定义 ============

export type OrderStatus = "VALID" | "INVALID";
export type PaymentMethod = "COD" | "ONLINE";
export type PaymentStatus = "PENDING" | "PAID" | "REFUNDED";

/**
 * 订单收货信息
 */
export interface OrderDeliveryInfo {
  firstName: string;
  lastName: string | null;
  phone: string;
  phoneLast8: string;
  email: string | null;
  province: string | null;
  city: string | null;
  district: string | null;
  postalCode: string | null;
  address: string | null;
  receiveUpdates: boolean;
  remoteArea: boolean;
  remark: string | null;
}

/**
 * 订单金额信息
 */
export interface OrderFinancialInfo {
  totalAmount: string;
  shippingFee: string;
  discountAmount: string;
  taxAmount: string;
}

/**
 * 订单支付信息
 */
export interface OrderPaymentInfo {
  paymentMethod: PaymentMethod;
  paymentStatus: PaymentStatus;
  paymentTime: Date | null;
}

/**
 * 订单上下文信息
 */
export interface OrderContextInfo {
  companyId: number;
  salesUid: number | null;
  salesPerson: string | null;
  departmentId: number | null;
  department: string | null;
  websiteId: number | null;
  websiteName: string | null;
  websiteUrl: string | null;
  countryId: number | null;
  countryCode: string | null;
  country: string | null;
  currencyId: number | null;
  currencyCode: string | null;
  currencyName: string | null;
  currencySymbol: string | null;
  currencyExchangeRate: string | null;
  currencyFractionDigits: number | null;
  languageId: number | null;
  language: string | null;
  languageCode: string | null;
  phoneRule: string | null;
  phonePrefix: string | null;
  addressRule: string | null;
}

/**
 * 订单风险记录信息
 */
export interface OrderRiskRecordInfo {
  deviceId: string | null;
  remoteIp: string | null;
  remoteIpInfo: string | null;
  realIp: string | null;
  realIpInfo: string | null;
  ua: string | null;
  pdKey: string | null;
  pdVal: string | null;
  cloak: boolean;
  browserPlatform: string | null;
}

/**
 * 订单商品项
 */
export interface OrderItemInfo {
  spuId: number;
  productId: number;
  title: string;
  specTitle: string;
  imageId: number | null;
  sellPrice: string;
  originPrice: string | null;
  costPrice: string | null;
  quantity: number;
  skuId: number;
  skuCode: string | null;
  skuName: string | null;
  skuIsVirtual: boolean;
  merchandise: string | null;
  waybillProductName: string | null;
}

/**
 * 创建订单的完整数据
 */
export interface CreateOrderData {
  companyId: number;
  from: string;
  fromUrl: string | null;
  platform: string;
  orderTime: Date;
  deliveryInfo: OrderDeliveryInfo;
  financialInfo: OrderFinancialInfo;
  paymentInfo: OrderPaymentInfo;
  contextInfo: OrderContextInfo;
  riskInfo: OrderRiskRecordInfo;
  items: OrderItemInfo[];
}

/**
 * 商品价格查询结果
 */
export interface ProductPriceInfo {
  companyId: number;
  productId: number;
  specId: number | null;
  spuId: number;
  title: string;
  specTitle: string | null;
  sellPrice: number;
  originPrice: number | null;
  costPrice: number | null;
  isTaxable: boolean;
  taxationMethod: string | null;
  fixedTaxAmount: number | null;
  taxAmountThreshold: number | null;
  taxQuantityThreshold: number;
  taxPerBase: number | null;
  skuId: number | null;
  skuCode: string | null;
  skuName: string | null;
  skuIsVirtual: boolean;
  merchandise: string | null;
  waybillProductName: string | null;
  imageId: number | null;
  imagePath: string | null;
  isMultiSpecs: boolean;
}

// ============ 价格查询 ============

/**
 * 根据 productId 和 specId 批量查询商品价格信息
 */
export async function findProductPrices(
  items: Array<{ productId: number; specId: number | null }>,
  countryId: number,
): Promise<Map<string, ProductPriceInfo>> {
  if (items.length === 0) {
    return new Map();
  }

  const result = new Map<string, ProductPriceInfo>();

  // 分离有规格和无规格的商品
  const withSpec = items.filter((i) => i.specId !== null);
  const withoutSpec = items.filter((i) => i.specId === null);

  // 查询无规格商品
  if (withoutSpec.length > 0) {
    const productIds = withoutSpec.map((i) => i.productId);
    const placeholders = productIds.map(() => "?").join(",");

    const sql = `
      SELECT
        p.company_id AS companyId,
        p.spu_id AS spuId,
        p.id AS productId,
        p.title AS title,
        p.title AS specTitle,
        MIN(pi.image_file_id) AS imageId,
        MIN(mf.relative_path) AS imagePath,
        p.sell_price AS sellPrice,
        p.origin_price AS originPrice,
        p.cost_price AS costPrice,
        p.is_taxable AS isTaxable,
        p.taxation_method AS taxationMethod,
        p.fixed_tax_amount AS fixedTaxAmount,
        p.tax_amount_threshold AS taxAmountThreshold,
        p.tax_quantity_threshold AS taxQuantityThreshold,
        p.tax_per_base AS taxPerBase,
        p.sku_id AS skuId,
        pk.name AS skuName,
        pk.sku_code AS skuCode,
        pk.is_virtual AS skuIsVirtual,
        p.merchandise AS merchandise,
        p.waybill_product_name AS waybillProductName,
        p.is_multi_specs AS isMultiSpecs
      FROM t_products p
      LEFT JOIN t_product_images pi ON pi.product_id = p.id
      LEFT JOIN t_multimedia_files mf ON mf.id = pi.image_file_id
      LEFT JOIN t_product_skus pk ON pk.id = p.sku_id
      WHERE p.id IN (${placeholders})
        AND p.country_id = ?
        AND p.status = 'VALID'
      GROUP BY p.id
    `;
    const rows = await query<any>(sql, [...productIds, countryId]);
    for (const row of rows) {
      const key = `${row.productId}-null`;
      result.set(key, {
        companyId: row.companyId,
        productId: row.productId,
        specId: null,
        spuId: row.spuId,
        title: row.title,
        specTitle: row.specTitle,
        sellPrice: Number(row.sellPrice),
        originPrice: row.originPrice ? Number(row.originPrice) : null,
        costPrice: row.costPrice ? Number(row.costPrice) : null,
        isTaxable: Boolean(row.isTaxable),
        taxationMethod: row.taxationMethod,
        fixedTaxAmount: row.fixedTaxAmount ? Number(row.fixedTaxAmount) : null,
        taxAmountThreshold: row.taxAmountThreshold
          ? Number(row.taxAmountThreshold)
          : null,
        taxQuantityThreshold: row.taxQuantityThreshold ?? 0,
        taxPerBase: row.taxPerBase ? Number(row.taxPerBase) : null,
        skuId: row.skuId,
        skuCode: row.skuCode,
        skuName: row.skuName,
        skuIsVirtual: Boolean(row.skuIsVirtual),
        merchandise: row.merchandise,
        waybillProductName: row.waybillProductName,
        imageId: row.imageId,
        imagePath: row.imagePath,
        isMultiSpecs: Boolean(row.isMultiSpecs),
      });
    }
  }

  // 查询有规格商品
  if (withSpec.length > 0) {
    const specIds = withSpec.map((i) => i.specId);
    const placeholders = specIds.map(() => "?").join(",");

    const sql = `
      SELECT
        p.company_id AS companyId,
        p.spu_id AS spuId,
        p.id AS productId,
        s.id AS specId,
        p.title AS title,
        sa_agg.specTitle AS specTitle,
        COALESCE(s.specification_image_id, pi_agg.imageId) AS imageId,
        COALESCE(smf.relative_path, pi_agg.imagePath) AS imagePath,
        s.sell_price AS sellPrice,
        s.origin_price AS originPrice,
        s.cost_price AS costPrice,
        p.is_taxable AS isTaxable,
        p.taxation_method AS taxationMethod,
        p.fixed_tax_amount AS fixedTaxAmount,
        p.tax_amount_threshold AS taxAmountThreshold,
        p.tax_quantity_threshold AS taxQuantityThreshold,
        p.tax_per_base AS taxPerBase,
        s.sku_id AS skuId,
        pk.name AS skuName,
        pk.sku_code AS skuCode,
        pk.is_virtual AS skuIsVirtual,
        p.merchandise AS merchandise,
        p.waybill_product_name AS waybillProductName,
        p.is_multi_specs AS isMultiSpecs
      FROM t_product_specifications s
      JOIN t_products p ON s.product_id = p.id
      LEFT JOIN (
        SELECT product_specification_id,
               GROUP_CONCAT(DISTINCT value ORDER BY id SEPARATOR ' · ') AS specTitle
        FROM t_product_specification_attributes
        GROUP BY product_specification_id
      ) sa_agg ON sa_agg.product_specification_id = s.id
      LEFT JOIN (
        SELECT pi.product_id,
               MIN(pi.image_file_id) AS imageId,
               MIN(mf.relative_path) AS imagePath
        FROM t_product_images pi
        LEFT JOIN t_multimedia_files mf ON mf.id = pi.image_file_id
        GROUP BY pi.product_id
      ) pi_agg ON pi_agg.product_id = p.id
      LEFT JOIN t_multimedia_files smf ON smf.id = s.specification_image_id
      LEFT JOIN t_product_skus pk ON pk.id = s.sku_id
      WHERE s.id IN (${placeholders})
        AND p.country_id = ?
        AND p.status = 'VALID'
    `;

    const rows = await query<any>(sql, [...specIds, countryId]);
    for (const row of rows) {
      const key = `${row.productId}-${row.specId}`;
      result.set(key, {
        companyId: row.companyId,
        productId: row.productId,
        specId: row.specId,
        spuId: row.spuId,
        title: row.title,
        specTitle: row.specTitle,
        sellPrice: Number(row.sellPrice),
        originPrice: row.originPrice ? Number(row.originPrice) : null,
        costPrice: row.costPrice ? Number(row.costPrice) : null,
        isTaxable: Boolean(row.isTaxable),
        taxationMethod: row.taxationMethod,
        fixedTaxAmount: row.fixedTaxAmount ? Number(row.fixedTaxAmount) : null,
        taxAmountThreshold: row.taxAmountThreshold
          ? Number(row.taxAmountThreshold)
          : null,
        taxQuantityThreshold: row.taxQuantityThreshold ?? 0,
        taxPerBase: row.taxPerBase ? Number(row.taxPerBase) : null,
        skuId: row.skuId,
        skuCode: row.skuCode,
        skuName: row.skuName,
        skuIsVirtual: Boolean(row.skuIsVirtual),
        merchandise: row.merchandise,
        waybillProductName: row.waybillProductName,
        imageId: row.imageId,
        imagePath: row.imagePath,
        isMultiSpecs: Boolean(row.isMultiSpecs),
      });
    }
  }

  return result;
}

// ============ 订单创建 ============

/**
 * 执行事务
 */
export async function transaction<T>(
  callback: (conn: PoolConnection) => Promise<T>,
): Promise<T> {
  const pool = getPool();
  const conn = await pool.getConnection();

  try {
    await conn.beginTransaction();
    const result = await callback(conn);
    await conn.commit();
    return result;
  } catch (error) {
    await conn.rollback();
    throw error;
  } finally {
    conn.release();
  }
}

/**
 * 插入订单上下文信息
 */
async function insertOrderContextInfo(
  conn: PoolConnection,
  info: OrderContextInfo,
  now: Date,
): Promise<number> {
  const sql = `
    INSERT INTO t_temporary_order_context_infos (
      create_time, status, update_time, company_id,
      address_rule, country, country_code, country_id,
      currency_code, currency_exchange_rate, currency_fraction_digits,
      currency_id, currency_name, currency_symbol,
      department, department_id, language, language_code, language_id,
      phone_rule, phone_prefix, sales_person, sales_uid,
      website_id, website_name, website_url
    ) VALUES (?, 'VALID', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
  `;

  const params = [
    now,
    now,
    info.companyId,
    info.addressRule,
    info.country,
    info.countryCode,
    info.countryId,
    info.currencyCode,
    info.currencyExchangeRate,
    info.currencyFractionDigits,
    info.currencyId,
    info.currencyName,
    info.currencySymbol,
    info.department,
    info.departmentId,
    info.language,
    info.languageCode,
    info.languageId,
    info.phoneRule,
    info.phonePrefix,
    info.salesPerson,
    info.salesUid,
    info.websiteId,
    info.websiteName,
    info.websiteUrl,
  ];

  const [result] = await conn.execute(sql, params);
  return (result as any).insertId;
}

/**
 * 插入风险记录信息
 */
async function insertRiskRecordInfo(
  conn: PoolConnection,
  info: OrderRiskRecordInfo,
  companyId: number,
  now: Date,
): Promise<number> {
  const sql = `
    INSERT INTO t_temporary_risk_record_infos (
      create_time, status, update_time, company_id,
      browser_platform, device_id, pd_key, pd_val,
      real_ip, real_ip_info, remote_ip, remote_ip_info, ua, cloak
    ) VALUES (?, 'VALID', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
  `;

  const params = [
    now,
    now,
    companyId,
    info.browserPlatform,
    info.deviceId,
    info.pdKey,
    info.pdVal,
    info.realIp,
    info.realIpInfo,
    info.remoteIp,
    info.remoteIpInfo,
    info.ua,
    info.cloak ? 1 : 0,
  ];

  const [result] = await conn.execute(sql, params);
  return (result as any).insertId;
}

/**
 * 插入临时订单主表
 */
async function insertTemporaryOrder(
  conn: PoolConnection,
  data: CreateOrderData,
  contextId: number,
  riskId: number,
  now: Date,
): Promise<number> {
  const sql = `
    INSERT INTO t_temporary_orders (
      id, origin_order_id,
      create_time, status, update_time, company_id,
      address, city, district, email, first_name, last_name,
      phone, postal_code, province, receive_updates, remark, is_remote_area,
      discount_amount, shipping_fee, tax_amount, total_amount,
      order_from, from_url, order_time,
      payment_method, payment_status, payment_time,
      platform, user_id, context_info_id, risk_info_id, phone_last_8
    ) VALUES (
      LAST_INSERT_ID(), LAST_INSERT_ID(),
      ?, 'VALID', ?, ?,
      ?, ?, ?, ?, ?, ?,
      ?, ?, ?, ?, ?, ?,
      ?, ?, ?, ?,
      ?, ?, ?,
      ?, ?, ?,
      ?, ?, ?, ?, ?
    )
  `;

  const params = [
    now,
    now,
    data.companyId,
    data.deliveryInfo.address,
    data.deliveryInfo.city,
    data.deliveryInfo.district,
    data.deliveryInfo.email,
    data.deliveryInfo.firstName,
    data.deliveryInfo.lastName,
    data.deliveryInfo.phone,
    data.deliveryInfo.postalCode,
    data.deliveryInfo.province,
    data.deliveryInfo.receiveUpdates ? 1 : 0,
    data.deliveryInfo.remark,
    data.deliveryInfo.remoteArea ? 1 : 0,
    data.financialInfo.discountAmount,
    data.financialInfo.shippingFee,
    data.financialInfo.taxAmount,
    data.financialInfo.totalAmount,
    data.from,
    data.fromUrl,
    data.orderTime,
    data.paymentInfo.paymentMethod,
    data.paymentInfo.paymentStatus,
    data.paymentInfo.paymentTime,
    data.platform,
    data.contextInfo.salesUid,
    contextId,
    riskId,
    data.deliveryInfo.phoneLast8,
  ];

  const [result] = await conn.execute(sql, params);
  return (result as any).insertId;
}

/**
 * 批量插入订单商品项
 */
async function insertOrderItems(
  conn: PoolConnection,
  items: OrderItemInfo[],
  orderId: number,
  companyId: number,
  salesUid: number | null,
  spuId: number,
  now: Date,
): Promise<void> {
  if (items.length === 0) return;

  const sql = `
    INSERT INTO t_temporary_order_items (
      create_time, status, update_time, company_id,
      barcode, cost_price, merchandise, origin_price,
      product_id, quantity, sell_price,
      sku_code, sku_id, is_virtual, sku_name,
      spec_title, spu_id, tax, title,
      waybill_product_name, user_id, image_id, order_id
    ) VALUES ?
  `;

  const values = items.map((item) => [
    now,
    "VALID",
    now,
    companyId,
    null,
    item.costPrice,
    item.merchandise,
    item.originPrice,
    item.productId,
    item.quantity,
    item.sellPrice,
    item.skuCode,
    item.skuId,
    item.skuIsVirtual ? 1 : 0,
    item.skuName,
    item.specTitle,
    spuId,
    null,
    item.title,
    item.waybillProductName,
    salesUid,
    item.imageId,
    orderId,
  ]);

  await conn.query(sql, [values]);
}

/**
 * 创建临时订单
 */
export async function createTemporaryOrder(
  data: CreateOrderData,
  spuId: number,
): Promise<{ orderId: number }> {
  return transaction(async (conn) => {
    const now = new Date();

    // 1. 插入上下文信息
    const contextId = await insertOrderContextInfo(conn, data.contextInfo, now);

    // 2. 插入风险记录
    const riskId = await insertRiskRecordInfo(
      conn,
      data.riskInfo,
      data.companyId,
      now,
    );

    // 3. 插入订单主表
    const orderId = await insertTemporaryOrder(
      conn,
      data,
      contextId,
      riskId,
      now,
    );

    // 4. 插入订单商品项
    await insertOrderItems(
      conn,
      data.items,
      orderId,
      data.companyId,
      data.contextInfo.salesUid,
      spuId,
      now,
    );

    return { orderId };
  });
}

/**
 * 根据订单ID查询订单信息
 */
export async function findOrderById(orderId: number): Promise<{
  id: number;
  totalAmount: string;
  currencySymbol: string | null;
  currencyCode: string | null;
} | null> {
  const sql = `
    SELECT 
      o.id,
      o.total_amount as totalAmount,
      c.currency_symbol as currencySymbol,
      c.currency_code as currencyCode
    FROM t_temporary_orders o
    LEFT JOIN t_temporary_order_context_infos c ON o.context_info_id = c.id
    WHERE o.id = ? AND o.status = 'VALID'
    LIMIT 1
  `;

  return queryOne(sql, [orderId]);
}
