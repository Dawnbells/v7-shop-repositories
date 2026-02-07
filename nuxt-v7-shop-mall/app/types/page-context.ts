import type { CloakCheckResponse } from "./cloak";
import { CloakPage } from "./cloak";

/**
 * 安全页面类型
 */
export enum SafePageType {
  /** 店铺已关闭 */
  SHOP_CLOSED = "SHOP_CLOSED",
  /** 店铺不存在 */
  SHOP_NOT_FOUND = "SHOP_NOT_FOUND",
  /** 产品不存在 */
  PRODUCT_NOT_FOUND = "PRODUCT_NOT_FOUND",
}

/**
 * 子域名类型
 */
export type SubDomainType = "COMPANY" | "RELAY" | "WEBSITE";

/**
 * 子域名状态
 */
export type SubDomainStatus = "VALID" | "INVALID" | "DELETED";

/**
 * 子域名信息（对应 t_sub_domains 表）
 */
export interface SubDomain {
  id: number;
  fullName: string;
  name: string;
  type: SubDomainType | null;
  status: SubDomainStatus;
  companyId: number;
  websiteId: number | null;
  themeId: number | null;
  countryId: number | null;
  currencyId: number | null;
  languageId: number | null;
  analyzeSuccess: boolean | null;
}

/**
 * 国家信息（对应 t_countries 表）
 */
export interface Country {
  id: number;
  code: string;
  name: string;
  continentCode: string;
  phonePrefix: string | null;
  phoneRule: string | null;
  addressFields: string | null;
  addressRule: string | null;
  requiredEmail: boolean;
  requiredPhone: boolean;
  useFullName: boolean | null;
  footerCopyrightInfo: string | null;
}

/**
 * 货币信息（对应 t_currencies 表）
 */
export interface Currency {
  id: number;
  code: string;
  name: string;
  symbol: string | null;
  exchangeRate: number | null;
  fractionDigits: number | null;
}

/**
 * 语言信息（对应 t_languages 表）
 */
export interface Language {
  id: number;
  code: string;
  name: string;
  cname: string;
}

/**
 * 公司信息（对应 t_companies 表）
 */
export interface Company {
  id: number;
  name: string;
  domain: string | null;
  accessKey: string | null;
  cloakFallback: CloakPage | null;
}

/**
 * 斗篷策略类型
 */
export type CloakStrategy = "DEFAULT" | "GOOGLE_LENIENT" | "GOOGLE_STRICT";

/**
 * 顶级域名信息（对应 t_top_level_domains 表）
 */
export interface TopLevelDomain {
  id: number;
  name: string;
  cloakStrategy: CloakStrategy | null;
  userId: number | null;
}

/**
 * 销售用户信息（对应 t_system_users 表）
 */
export interface SalesUser {
  id: number;
  name: string;
  departmentId: number | null;
}

/**
 * 域名完整信息（包含关联的国家、货币、语言、公司、顶级域名、销售用户）
 */
export interface DomainInfo {
  domain: SubDomain;
  country: Country | null;
  currency: Currency | null;
  languages: Language[];
  company: Company | null;
  topLevelDomain: TopLevelDomain | null;
  salesUser: SalesUser | null;
}

/**
 * 产品图片信息
 */
export interface ProductImage {
  id: number;
  relativePath: string;
  name: string;
}

/**
 * 规格属性
 */
export interface SpecificationAttribute {
  name: string;
  value: string;
}

/**
 * 产品规格信息
 */
export interface ProductSpecification {
  id: number;
  skuId: number | null;
  sellPrice: number;
  originPrice: number | null;
  stockQuantity: number;
  attributes: SpecificationAttribute[];
}

/**
 * 产品信息（对应 t_products 表及关联表）
 */
export interface ProductInfo {
  id: number;
  spuId: number;
  languageId: number;
  title: string;
  merchandise: string;
  introduction: string | null;
  summary: string | null;
  sellPrice: number;
  originPrice: number | null;
  isMultiSpecs: boolean;
  images: ProductImage[];
  specifications: ProductSpecification[];
  /** 主题配置（JSON 格式，包含页面布局、组件、样式） */
  themeConfig?: any;
  /** 站点配置值（全局固定配置，如网站名称、Logo、版权等） */
  siteConfig?: Record<string, any>;
  /** 变量实际值（用户自定义变量的值） */
  variableValues?: Record<string, any>;
}

export interface PageContext {
  /** 当前访问的域名信息 */
  domain?: SubDomain;
  /** 国家信息 */
  country?: Country;
  /** 货币信息 */
  currency?: Currency;
  /** 语言列表 */
  languages?: Language[];
  /** 公司信息 */
  company?: Company;
  /** 顶级域名信息 */
  topLevelDomain?: TopLevelDomain;
  /** 销售用户信息 */
  salesUser?: SalesUser;
  /** 当前或最近访问的产品 SPU ID */
  spuId?: number;
  /** 客户端指纹 */
  fingerprint?: string;
  /** 斗篷检查结果 */
  cloak?: CloakCheckResponse;
  /** 安全页面类型（当需要显示安全页面时使用） */
  safePageType?: SafePageType;
  /** 落地页产品 ID（直接指向 t_products.id，用于 CLOAK 类型；LAND 类型为 null） */
  landingProductId?: number | null;
  /** 主题配置（JSON 格式，包含页面布局、组件、样式） */
  themeConfig?: any;
  /** 站点配置值（全局固定配置，如网站名称、Logo、版权等） */
  siteConfig?: Record<string, any>;
  /** 变量实际值（用户自定义变量的值） */
  variableValues?: Record<string, any>;
}
