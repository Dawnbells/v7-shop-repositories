export interface CloakCheckRequest {
  clientIp: string;
  requestUrl: string;
  spuId?: number;
  headers: Record<string, string>;
  fingerprint?: string;
  cloakStrategy?: string;
  accessKey?: string;
  continentCode?: string;
  countryCode?: string;
  companyDomain?: string;
  userId?: number;
  deptId?: number;
}

export interface CloakCheckResponse {
  remote: boolean;
  page: CloakPage;
  pdVal: string;
  isAdmin: boolean;
}

export enum CloakPage {
  LAND = "LAND",
  CLOAK = "CLOAK",
  CRAWLER = "CRAWLER",
  RISK = "RISK",
  BLACKLISTED = "BLACKLISTED",
}
