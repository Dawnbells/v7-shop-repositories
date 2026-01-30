/**
 * 域名数据仓库
 * 封装域名相关的数据库操作，包含域名、国家、货币、语言、公司、顶级域名、销售用户信息查询
 */

import type { SubDomain, Country, Currency, Language, Company, TopLevelDomain, SalesUser, DomainInfo } from "~/types/page-context";
import { query, queryOne } from "../utils/db";

/**
 * 数据库行转换为 SubDomain 对象
 */
function rowToSubDomain(row: any): SubDomain {
  return {
    id: row.id,
    fullName: row.full_name,
    name: row.name,
    type: row.type,
    status: row.status,
    companyId: row.company_id,
    websiteId: row.website_id,
    themeId: row.theme_id,
    countryId: row.country_id,
    currencyId: row.currency_id,
    languageId: row.language_id,
    analyzeSuccess: row.analyze_success,
  };
}

/**
 * 数据库行转换为 Language 对象
 */
function rowToLanguage(row: any): Language {
  return {
    id: row.id,
    code: row.code,
    name: row.name,
    cname: row.cname,
  };
}

/**
 * 根据完整域名查询有效的子域名信息及关联的国家、货币、语言、公司、顶级域名、销售用户
 * @param fullName 完整域名（如 shop.example.com）
 */
export async function findByFullName(fullName: string): Promise<DomainInfo | null> {
  // 使用 JOIN 一次性查询域名、国家、货币、公司、顶级域名、销售用户信息
  const domainSql = `
    SELECT 
      d.id, d.full_name, d.name, d.type, d.status, d.company_id,
      d.website_id, d.theme_id, d.country_id, d.currency_id, d.language_id, d.analyze_success,
      d.parent_domain_id,
      c.id AS country_id_val, c.code AS country_code, c.name AS country_name, 
      c.continent_code, c.phone_prefix, c.phone_rule, c.address_fields, c.address_rule,
      c.required_email, c.required_phone, c.use_full_name, c.footer_copyright_info,
      cur.id AS currency_id_val, cur.code AS currency_code, cur.name AS currency_name,
      cur.symbol, cur.exchange_rate, cur.fraction_digits,
      comp.id AS company_id_val, comp.name AS company_name, comp.domain AS company_domain, comp.access_key, comp.cloak_fallback,
      tld.id AS tld_id, tld.name AS tld_name, tld.cloakStrategy AS cloak_strategy, tld.user_id AS tld_user_id,
      su.id AS sales_user_id, su.name AS sales_user_name, su.department_id AS sales_department_id
    FROM t_sub_domains d
    LEFT JOIN t_countries c ON d.country_id = c.id AND c.status = 'VALID'
    LEFT JOIN t_currencies cur ON c.currency_id = cur.id AND cur.status = 'VALID'
    LEFT JOIN t_companies comp ON d.company_id = comp.id AND comp.status = 'VALID'
    LEFT JOIN t_top_level_domains tld ON d.parent_domain_id = tld.id AND tld.status = 'VALID'
    LEFT JOIN t_system_users su ON tld.user_id = su.id AND su.status = 'VALID'
    WHERE d.full_name = ? AND d.status = 'VALID'
    LIMIT 1
  `;

  const row = await queryOne(domainSql, [fullName]);
  if (!row) {
    return null;
  }

  const domain = rowToSubDomain(row);

  // 解析国家信息
  let country: Country | null = null;
  if (row.country_id_val) {
    country = {
      id: row.country_id_val,
      code: row.country_code,
      name: row.country_name,
      continentCode: row.continent_code,
      phonePrefix: row.phone_prefix,
      phoneRule: row.phone_rule,
      addressFields: row.address_fields,
      addressRule: row.address_rule,
      requiredEmail: !!row.required_email,
      requiredPhone: !!row.required_phone,
      useFullName: row.use_full_name == null ? null : !!row.use_full_name,
      footerCopyrightInfo: row.footer_copyright_info,
    };
  }

  // 解析货币信息
  let currency: Currency | null = null;
  if (row.currency_id_val) {
    currency = {
      id: row.currency_id_val,
      code: row.currency_code,
      name: row.currency_name,
      symbol: row.symbol,
      exchangeRate: row.exchange_rate ? parseFloat(row.exchange_rate) : null,
      fractionDigits: row.fraction_digits,
    };
  }

  // 解析公司信息
  let company: Company | null = null;
  if (row.company_id_val) {
    company = {
      id: row.company_id_val,
      name: row.company_name,
      domain: row.company_domain,
      accessKey: row.access_key,
      cloakFallback: row.cloak_fallback ?? null,
    };
  }

  // 解析顶级域名信息
  let topLevelDomain: TopLevelDomain | null = null;
  if (row.tld_id) {
    topLevelDomain = {
      id: row.tld_id,
      name: row.tld_name,
      cloakStrategy: row.cloak_strategy,
      userId: row.tld_user_id,
    };
  }

  // 解析销售用户信息
  let salesUser: SalesUser | null = null;
  if (row.sales_user_id) {
    salesUser = {
      id: row.sales_user_id,
      name: row.sales_user_name,
      departmentId: row.sales_department_id,
    };
  }

  // 查询语言列表
  let languages: Language[] = [];
  if (domain.countryId) {
    const languagesSql = `
      SELECT l.id, l.code, l.name, l.cname
      FROM t_languages l
      INNER JOIN t_country_languages cl ON l.id = cl.language_id
      WHERE cl.country_id = ? AND l.status = 'VALID'
    `;
    const languageRows = await query(languagesSql, [domain.countryId]);
    languages = languageRows.map(rowToLanguage);
  }

  return {
    domain,
    country,
    currency,
    languages,
    company,
    topLevelDomain,
    salesUser,
  };
}
