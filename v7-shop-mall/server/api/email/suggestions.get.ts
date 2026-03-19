/**
 * 邮箱联想 API
 * 根据用户输入的邮箱前缀和国家代码，返回匹配的邮箱建议
 */

import emailMentions from '~/utils/email-mentions'

interface QueryParams {
  prefix?: string
  country?: string
  limit?: string
}

export default defineEventHandler((event): string[] => {
  const query = getQuery<QueryParams>(event)
  
  const prefix = query.prefix || ''
  const country = query.country || 'default'
  const limit = Math.min(parseInt(query.limit || '10', 10), 20)
  
  if (!prefix.includes('@')) {
    return []
  }
  
  const atIndex = prefix.indexOf('@')
  const username = prefix.substring(0, atIndex)
  const domainPrefix = prefix.substring(atIndex + 1).toLowerCase()
  
  if (!username) {
    return []
  }
  
  let suffixes = emailMentions.get(country)
  if (!suffixes) {
    suffixes = emailMentions.get('default') || []
  }
  
  const matchedSuffixes = suffixes
    .filter(suffix => {
      const suffixDomain = suffix.substring(1).toLowerCase()
      return suffixDomain.startsWith(domainPrefix)
    })
    .slice(0, limit)
  
  return matchedSuffixes.map(suffix => `${username}${suffix}`)
})
