/**
 * 获取语言列表 API
 * 从 t_languages 表查询所有有效语言
 */

import { findAllLanguages, type LanguageItem } from '../../repositories/languageRepository'

export default defineEventHandler(async (): Promise<LanguageItem[]> => {
  return findAllLanguages()
})
