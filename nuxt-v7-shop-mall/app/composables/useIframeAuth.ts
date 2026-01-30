/**
 * useIframeAuth - iframe 通信和鉴权状态管理
 * 
 * 当 nuxt builder 嵌入在 admin iframe 中时，通过 postMessage 接收：
 * - 用户 token（用于 API 鉴权）
 * - imageBaseUrl（图片基础 URL）
 * - apiBaseUrl（API 基础 URL）
 * - query 参数（subDomainId, spuId, landingType）
 */

export interface IframeAuthPayload {
  token: string;
  imageBaseUrl: string;
  apiBaseUrl: string;
  query: {
    subDomainId: string;
    spuId: string;
    landingType: string;
  };
}

export interface IframeInitMessage {
  type: 'BUILDER_INIT';
  payload: IframeAuthPayload;
}

// 全局状态（跨组件共享）
const authState = reactive<{
  isReady: boolean;
  token: string | null;
  imageBaseUrl: string | null;
  apiBaseUrl: string | null;
  query: IframeAuthPayload['query'] | null;
  origin: string | null;
}>({
  isReady: false,
  token: null,
  imageBaseUrl: null,
  apiBaseUrl: null,
  query: null,
  origin: null,
});

// 允许的 origin 白名单（可通过环境变量配置）
const getAllowedOrigins = (): string[] => {
  const config = useRuntimeConfig();
  const origins = config.public?.allowedOrigins as string | undefined;
  if (origins) {
    return origins.split(',').map(o => o.trim());
  }
  // 默认允许同源和常见开发地址
  return [
    window.location.origin,
    'http://localhost:3000',
    'http://localhost:5173',
    'http://localhost:5200',
    'http://localhost:9999',
    'http://127.0.0.1:3000',
    'http://127.0.0.1:5173',
    'http://127.0.0.1:5200',
    'http://127.0.0.1:9999',
  ];
};

// 验证消息来源
const isAllowedOrigin = (origin: string): boolean => {
  const allowed = getAllowedOrigins();
  // 如果白名单包含 '*'，允许所有来源（仅用于开发）
  if (allowed.includes('*')) {
    return true;
  }
  return allowed.includes(origin);
};

// 消息处理函数
const handleMessage = (event: MessageEvent) => {
  // 验证来源
  if (!isAllowedOrigin(event.origin)) {
    console.warn('[IframeAuth] 拒绝来自不受信任来源的消息:', event.origin);
    return;
  }

  const data = event.data;

  // 验证消息类型
  if (!data || typeof data !== 'object' || data.type !== 'BUILDER_INIT') {
    return;
  }

  const message = data as IframeInitMessage;
  const payload = message.payload;

  if (!payload || !payload.token) {
    console.warn('[IframeAuth] 无效的消息负载');
    return;
  }

  // 更新状态
  authState.token = payload.token;
  authState.imageBaseUrl = payload.imageBaseUrl || null;
  authState.apiBaseUrl = payload.apiBaseUrl || null;
  authState.query = payload.query || null;
  authState.origin = event.origin;
  authState.isReady = true;

  console.log('[IframeAuth] 已接收认证信息', {
    hasToken: !!payload.token,
    imageBaseUrl: payload.imageBaseUrl,
    apiBaseUrl: payload.apiBaseUrl,
    query: payload.query,
  });
};

// 是否已初始化监听
let isListenerInitialized = false;

// BUILDER_READY 重试定时器
let readyRetryTimer: ReturnType<typeof setInterval> | null = null;

// 发送 BUILDER_READY 消息
const sendBuilderReady = () => {
  if (typeof window !== 'undefined' && window.parent !== window) {
    window.parent.postMessage({ type: 'BUILDER_READY' }, '*');
    console.log('[IframeAuth] 发送 BUILDER_READY');
  }
};

// 停止重试
const stopReadyRetry = () => {
  if (readyRetryTimer) {
    clearInterval(readyRetryTimer);
    readyRetryTimer = null;
  }
};

/**
 * useIframeAuth composable
 * 提供 iframe 通信的认证状态和工具方法
 */
export function useIframeAuth() {
  // 仅在客户端初始化监听器
  if (import.meta.client && !isListenerInitialized) {
    window.addEventListener('message', handleMessage);
    isListenerInitialized = true;

    // 通知父窗口 builder 已准备好接收消息
    sendBuilderReady();

    // 设置重试机制，每 2 秒重试一次，直到收到认证信息
    readyRetryTimer = setInterval(() => {
      if (!authState.isReady) {
        sendBuilderReady();
      } else {
        stopReadyRetry();
      }
    }, 2000);
  }

  // 计算属性：是否已就绪
  const isReady = computed(() => authState.isReady);

  // 计算属性：认证头
  const authHeaders = computed(() => {
    if (!authState.token) return {};
    return {
      Authorization: `Bearer ${authState.token}`,
    };
  });

  // 计算属性：图片基础 URL
  const imageBaseUrl = computed(() => authState.imageBaseUrl);

  // 计算属性：API 基础 URL
  const apiBaseUrl = computed(() => authState.apiBaseUrl);

  // 计算属性：query 参数
  const query = computed(() => authState.query);

  // 计算属性：原始 token
  const token = computed(() => authState.token);

  /**
   * 构建完整图片 URL
   */
  const buildImageUrl = (relativePath: string): string => {
    if (!relativePath) return '';
    
    // 如果已经是完整 URL，直接返回
    if (relativePath.startsWith('http://') || relativePath.startsWith('https://')) {
      return relativePath;
    }

    const baseUrl = authState.imageBaseUrl;
    if (!baseUrl) {
      return relativePath;
    }

    // 拼接 URL
    const cleanBaseUrl = baseUrl.endsWith('/') ? baseUrl.slice(0, -1) : baseUrl;
    const cleanPath = relativePath.startsWith('/') ? relativePath : `/${relativePath}`;
    return `${cleanBaseUrl}${cleanPath}`;
  };

  /**
   * 发起带认证的 fetch 请求
   */
  const authFetch = async <T = any>(
    url: string,
    options: RequestInit = {}
  ): Promise<T> => {
    const baseUrl = authState.apiBaseUrl || '';
    const fullUrl = url.startsWith('http') ? url : `${baseUrl}${url}`;

    const response = await fetch(fullUrl, {
      ...options,
      headers: {
        ...options.headers,
        ...authHeaders.value,
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    return response.json();
  };

  /**
   * 手动设置认证信息（用于非 iframe 场景或测试）
   */
  const setAuth = (payload: Partial<IframeAuthPayload>) => {
    if (payload.token) authState.token = payload.token;
    if (payload.imageBaseUrl) authState.imageBaseUrl = payload.imageBaseUrl;
    if (payload.apiBaseUrl) authState.apiBaseUrl = payload.apiBaseUrl;
    if (payload.query) authState.query = payload.query;
    authState.isReady = true;
  };

  /**
   * 清除认证信息
   */
  const clearAuth = () => {
    authState.isReady = false;
    authState.token = null;
    authState.imageBaseUrl = null;
    authState.apiBaseUrl = null;
    authState.query = null;
    authState.origin = null;
  };

  return {
    // 状态
    isReady,
    token,
    authHeaders,
    imageBaseUrl,
    apiBaseUrl,
    query,
    
    // 方法
    buildImageUrl,
    authFetch,
    setAuth,
    clearAuth,
    stopReadyRetry,
  };
}
