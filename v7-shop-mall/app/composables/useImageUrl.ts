/**
 * useImageUrl - 图片 URL 处理 Composable
 * 
 * 用于前端渲染时处理图片 URL：
 * - 自动添加 CDN 前缀（相对路径）
 * - 支持降级 URL（主链接失败时重试）
 */

/**
 * 构建完整的图片 URL
 * - 如果是 http:// 或 https:// 开头，直接返回
 * - 否则拼接 imageBaseUrl 前缀
 */
export function buildImageUrl(path: string | undefined | null): string {
  if (!path) return ''
  
  // 已经是完整 URL，直接返回
  if (path.startsWith('http://') || path.startsWith('https://')) {
    return path
  }
  
  // public/mock 目录下的本地资源，直接使用相对路径
  if (path.startsWith('/mock/')) {
    return path
  }
  
  const config = useRuntimeConfig()
  const baseUrl = config.public.imageBaseUrl as string
  
  if (!baseUrl) {
    return path
  }
  
  // 拼接 URL
  const cleanBaseUrl = baseUrl.endsWith('/') ? baseUrl.slice(0, -1) : baseUrl
  const cleanPath = path.startsWith('/') ? path : `/${path}`
  
  return `${cleanBaseUrl}${cleanPath}`
}

/**
 * 构建降级图片 URL
 * - 使用 imageFallbackUrl 替代 imageBaseUrl
 */
export function buildFallbackImageUrl(path: string | undefined | null): string {
  if (!path) return ''
  
  // 已经是完整 URL，无法降级
  if (path.startsWith('http://') || path.startsWith('https://')) {
    return ''
  }
  
  // public/mock 目录下的本地资源，不需要降级
  if (path.startsWith('/mock/')) {
    return ''
  }
  
  const config = useRuntimeConfig()
  const fallbackUrl = config.public.imageFallbackUrl as string
  
  if (!fallbackUrl) {
    return ''
  }
  
  // 拼接 URL
  const cleanBaseUrl = fallbackUrl.endsWith('/') ? fallbackUrl.slice(0, -1) : fallbackUrl
  const cleanPath = path.startsWith('/') ? path : `/${path}`
  
  return `${cleanBaseUrl}${cleanPath}`
}

/**
 * useImageWithFallback - 带降级的图片 URL
 * 
 * 返回响应式的图片 URL，当主链接加载失败时自动切换到降级链接
 * 
 * @param originalPath - 原始图片路径（可以是响应式的）
 * @returns { currentUrl, handleError, isUsingFallback }
 */
export function useImageWithFallback(originalPath: MaybeRef<string | undefined | null>) {
  const path = toRef(originalPath)
  const isUsingFallback = ref(false)
  
  // 当前使用的 URL
  const currentUrl = computed(() => {
    const p = path.value
    if (!p) return ''
    
    if (isUsingFallback.value) {
      const fallback = buildFallbackImageUrl(p)
      return fallback || buildImageUrl(p)
    }
    
    return buildImageUrl(p)
  })
  
  // 处理图片加载错误
  const handleError = (event: Event) => {
    const img = event.target as HTMLImageElement
    const fallbackUrl = buildFallbackImageUrl(path.value)
    
    // 如果有降级 URL 且当前不是降级状态，切换到降级
    if (fallbackUrl && !isUsingFallback.value) {
      isUsingFallback.value = true
      img.src = fallbackUrl
    }
  }
  
  // 当路径变化时重置降级状态
  watch(path, () => {
    isUsingFallback.value = false
  })
  
  return {
    currentUrl,
    handleError,
    isUsingFallback: readonly(isUsingFallback),
  }
}

/**
 * useImageUrl composable
 * 提供图片 URL 处理的工具方法
 */
export function useImageUrl() {
  return {
    buildImageUrl,
    buildFallbackImageUrl,
    useImageWithFallback,
  }
}
