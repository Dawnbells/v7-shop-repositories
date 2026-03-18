/**
 * 产品相关类型定义
 */

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
  imagePath?: string | null;
}

export interface ProductSpecification {
  id: number;
  sid: number | null;
  skuId: number;
  sellPrice: number;
  originPrice: number | null;
  costPrice: number | null;
  barcode: string | null;
  stockQuantity: number;
  linkStock: boolean;
  specificationImageId: number | null;
  specImagePath?: string | null;
  attributes: ProductSpecificationAttribute[];
}

export interface IntroductionItem {
  type: "image" | "html";
  id?: number;
  src?: string;
  width?: number;
  height?: number;
  aspectRatio?: number | null;
  content?: string;
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
  images: ProductImage[];
  specifications: ProductSpecification[];
  introductionData?: IntroductionItem[];
}
