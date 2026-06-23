type ImagePublicRuntimeConfig = {
  imageBaseUrl?: string
  imageFallbackUrl?: string
  [key: string]: unknown
}

type ImageRuntimeConfig = {
  public?: ImagePublicRuntimeConfig
}

type ImageUrlBuilders = {
  buildImageUrl: (path: string | undefined | null) => string
  buildFallbackImageUrl: (path: string | undefined | null) => string
}

function getPublicConfigValue(
  config: ImageRuntimeConfig,
  key: 'imageBaseUrl' | 'imageFallbackUrl',
): string {
  const value = config.public?.[key]
  return typeof value === 'string' ? value : ''
}

function isAbsoluteUrl(path: string): boolean {
  return path.startsWith('http://') || path.startsWith('https://')
}

function isLocalMockAsset(path: string): boolean {
  return path.startsWith('/mock/')
}

function joinImageBaseUrl(path: string | undefined | null, baseUrl: string): string {
  if (!path) return ''

  if (isAbsoluteUrl(path) || isLocalMockAsset(path) || !baseUrl) {
    return path
  }

  const cleanBaseUrl = baseUrl.endsWith('/') ? baseUrl.slice(0, -1) : baseUrl
  const cleanPath = path.startsWith('/') ? path : `/${path}`

  return `${cleanBaseUrl}${cleanPath}`
}

function joinFallbackImageBaseUrl(path: string | undefined | null, fallbackUrl: string): string {
  if (!path) return ''

  if (isAbsoluteUrl(path) || isLocalMockAsset(path) || !fallbackUrl) {
    return ''
  }

  const cleanBaseUrl = fallbackUrl.endsWith('/') ? fallbackUrl.slice(0, -1) : fallbackUrl
  const cleanPath = path.startsWith('/') ? path : `/${path}`

  return `${cleanBaseUrl}${cleanPath}`
}

function createImageUrlBuilders(config: ImageRuntimeConfig): ImageUrlBuilders {
  const imageBaseUrl = getPublicConfigValue(config, 'imageBaseUrl')
  const imageFallbackUrl = getPublicConfigValue(config, 'imageFallbackUrl')

  return {
    buildImageUrl: (path) => joinImageBaseUrl(path, imageBaseUrl),
    buildFallbackImageUrl: (path) => joinFallbackImageBaseUrl(path, imageFallbackUrl),
  }
}

/**
 * Builds a full image URL.
 */
export function buildImageUrl(path: string | undefined | null): string {
  const config = useRuntimeConfig() as unknown as ImageRuntimeConfig
  return joinImageBaseUrl(path, getPublicConfigValue(config, 'imageBaseUrl'))
}

/**
 * Builds a fallback image URL.
 */
export function buildFallbackImageUrl(path: string | undefined | null): string {
  const config = useRuntimeConfig() as unknown as ImageRuntimeConfig
  return joinFallbackImageBaseUrl(path, getPublicConfigValue(config, 'imageFallbackUrl'))
}

function useImageWithFallbackInternal(
  originalPath: MaybeRef<string | undefined | null>,
  builders: ImageUrlBuilders,
) {
  const path = toRef(originalPath)
  const isUsingFallback = ref(false)

  const currentUrl = computed(() => {
    const p = path.value
    if (!p) return ''

    if (isUsingFallback.value) {
      const fallback = builders.buildFallbackImageUrl(p)
      return fallback || builders.buildImageUrl(p)
    }

    return builders.buildImageUrl(p)
  })

  const handleError = (event: Event) => {
    const img = event.target as HTMLImageElement
    const fallbackUrl = builders.buildFallbackImageUrl(path.value)

    if (fallbackUrl && !isUsingFallback.value) {
      isUsingFallback.value = true
      img.src = fallbackUrl
    }
  }

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
 * Returns a reactive image URL with fallback handling.
 */
export function useImageWithFallback(originalPath: MaybeRef<string | undefined | null>) {
  const builders = createImageUrlBuilders(useRuntimeConfig() as unknown as ImageRuntimeConfig)
  return useImageWithFallbackInternal(originalPath, builders)
}

/**
 * Provides image URL helpers bound to the current Nuxt setup context.
 */
export function useImageUrl() {
  const builders = createImageUrlBuilders(useRuntimeConfig() as unknown as ImageRuntimeConfig)

  return {
    buildImageUrl: builders.buildImageUrl,
    buildFallbackImageUrl: builders.buildFallbackImageUrl,
    useImageWithFallback: (originalPath: MaybeRef<string | undefined | null>) =>
      useImageWithFallbackInternal(originalPath, builders),
  }
}
