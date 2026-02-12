/**
 * useIframeAuth - iframe 通信和鉴权状态管理
 *
 * 当 nuxt builder 嵌入在 admin iframe 中时，通过 postMessage 接收：
 * - 用户 token（用于 API 鉴权）
 * - imageBaseUrl（图片基础 URL）
 * - apiBaseUrl（API 基础 URL）
 * - mode（编辑模式：TEMPLATE 或 LANDING）
 * - query 参数（根据模式不同有不同字段）
 */

export type BuilderMode = "TEMPLATE" | "LANDING";

export interface IframeAuthPayload {
  token: string;
  imageBaseUrl: string;
  apiBaseUrl: string;
  mode?: BuilderMode; // 编辑模式
  // TEMPLATE 模式使用
  templateId?: string;
  contextName?: string;
  // LANDING 模式使用（向后兼容）
  query?: {
    subDomainId?: string;
    spuId?: string;
    landingType?: string;
    subDomainName?: string;
    spuName?: string;
  };
}

export interface IframeInitMessage {
  type: "BUILDER_INIT";
  payload: IframeAuthPayload;
}

// 全局状态（跨组件共享）
const authState = reactive<{
  isReady: boolean;
  token: string | null;
  imageBaseUrl: string | null;
  apiBaseUrl: string | null;
  mode: BuilderMode;
  templateId: string | null;
  contextName: string | null;
  query: IframeAuthPayload["query"] | null;
  origin: string | null;
}>({
  isReady: false,
  token: null,
  imageBaseUrl: null,
  apiBaseUrl: null,
  mode: "LANDING", // 默认为 LANDING 模式（向后兼容）
  templateId: null,
  contextName: null,
  query: null,
  origin: null,
});

// 允许的 origin 白名单（可通过环境变量配置）
const getAllowedOrigins = (): string[] => {
  const config = useRuntimeConfig();
  const origins = config.public?.allowedOrigins as string | undefined;
  if (origins) {
    return origins.split(",").map((o) => o.trim());
  }
  // 默认允许同源和常见开发地址
  return [
    window.location.origin,
    "http://localhost:3000",
    "http://localhost:5173",
    "http://localhost:5200",
    "http://localhost:9999",
    "http://127.0.0.1:3000",
    "http://127.0.0.1:5173",
    "http://127.0.0.1:5200",
    "http://127.0.0.1:9999",
  ];
};

// 验证消息来源
const isAllowedOrigin = (origin: string): boolean => {
  const allowed = getAllowedOrigins();
  // 如果白名单包含 '*'，允许所有来源（仅用于开发）
  if (allowed.includes("*")) {
    return true;
  }
  return allowed.includes(origin);
};

// 消息处理函数
const handleMessage = (event: MessageEvent) => {
  console.log("[IframeAuth] 收到消息事件:", {
    origin: event.origin,
    allowedOrigins: getAllowedOrigins(),
    isAllowed: isAllowedOrigin(event.origin),
  });

  // 验证来源
  if (!isAllowedOrigin(event.origin)) {
    console.warn("[IframeAuth] 拒绝来自不受信任来源的消息:", event.origin);
    return;
  }

  const data = event.data;

  console.log("[IframeAuth] 消息数据:", data);

  // 验证消息类型
  if (!data || typeof data !== "object" || data.type !== "BUILDER_INIT") {
    console.warn("[IframeAuth] 消息类型不匹配，期望 BUILDER_INIT:", data?.type);
    return;
  }

  const message = data as IframeInitMessage;
  const payload = message.payload;

  if (!payload || !payload.token) {
    console.warn("[IframeAuth] 无效的消息负载");
    return;
  }

  // 更新状态
  authState.token = payload.token;
  authState.imageBaseUrl = payload.imageBaseUrl || null;
  authState.apiBaseUrl = payload.apiBaseUrl || null;
  authState.mode = payload.mode || "LANDING";
  authState.templateId = payload.templateId || null;
  authState.contextName = payload.contextName || null;
  authState.query = payload.query || null;
  authState.origin = event.origin;
  authState.isReady = true;

  console.log("[IframeAuth] ✅ 认证成功，isReady:", authState.isReady);
};

// 是否已初始化监听
let isListenerInitialized = false;

// BUILDER_READY 重试定时器
let readyRetryTimer: ReturnType<typeof setTimeout> | null = null;
let retryCount = 0;

// 发送 BUILDER_READY 消息
const sendBuilderReady = () => {
  if (typeof window !== "undefined" && window.parent !== window) {
    window.parent.postMessage({ type: "BUILDER_READY" }, "*");
    console.log("[IframeAuth] 发送 BUILDER_READY");
  }
};

// 停止重试
const stopReadyRetry = () => {
  if (readyRetryTimer) {
    clearTimeout(readyRetryTimer);
    readyRetryTimer = null;
  }
  retryCount = 0;
};

// 递归重试发送 BUILDER_READY（前几次快速重试，之后慢速重试）
const scheduleRetry = () => {
  if (authState.isReady) {
    stopReadyRetry();
    return;
  }

  retryCount++;
  // 前 5 次快速重试（每 200ms），之后每 1 秒重试一次
  const delay = retryCount <= 5 ? 200 : 1000;

  readyRetryTimer = setTimeout(() => {
    if (!authState.isReady) {
      sendBuilderReady();
      scheduleRetry();
    }
  }, delay);
};

/**
 * useIframeAuth composable
 * 提供 iframe 通信的认证状态和工具方法
 */
export function useIframeAuth() {
  // 仅在客户端初始化监听器
  if (import.meta.client && !isListenerInitialized) {
    console.log("[IframeAuth] 初始化消息监听器...");
    window.addEventListener("message", handleMessage);
    isListenerInitialized = true;
    console.log("[IframeAuth] 监听器已注册，当前状态:", {
      isListenerInitialized,
      isReady: authState.isReady,
    });

    // 通知父窗口 builder 已准备好接收消息
    sendBuilderReady();

    // 启动重试机制（前几次快速重试，之后慢速重试）
    scheduleRetry();
  } else if (import.meta.client) {
    console.log("[IframeAuth] 监听器已存在，跳过初始化");
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

  // 计算属性：编辑模式
  const mode = computed(() => authState.mode);

  // 计算属性：模板 ID（TEMPLATE 模式）
  const templateId = computed(() => authState.templateId);

  // 计算属性：上下文名称（显示用）
  const contextName = computed(() => authState.contextName);

  // 计算属性：是否为模板模式
  const isTemplateMode = computed(() => authState.mode === "TEMPLATE");

  // 计算属性：是否为落地页模式
  const isLandingMode = computed(() => authState.mode === "LANDING");

  /**
   * 构建完整图片 URL
   * 优先使用 iframe 传入的 imageBaseUrl，否则回退到 runtimeConfig
   */
  const buildImageUrl = (relativePath: string): string => {
    if (!relativePath) return "";

    // 如果已经是完整 URL，直接返回
    if (
      relativePath.startsWith("http://") ||
      relativePath.startsWith("https://")
    ) {
      return relativePath;
    }

    // 优先使用 iframe 传入的 imageBaseUrl，否则使用 runtimeConfig
    const config = useRuntimeConfig();
    const baseUrl =
      authState.imageBaseUrl || (config.public.imageBaseUrl as string);
    if (!baseUrl) {
      return relativePath;
    }

    // 拼接 URL
    const cleanBaseUrl = baseUrl.endsWith("/") ? baseUrl.slice(0, -1) : baseUrl;
    const cleanPath = relativePath.startsWith("/")
      ? relativePath
      : `/${relativePath}`;
    return `${cleanBaseUrl}${cleanPath}`;
  };

  /**
   * 发起带认证的 fetch 请求
   */
  const authFetch = async <T = any>(
    url: string,
    options: RequestInit = {}
  ): Promise<T> => {
    const baseUrl = authState.apiBaseUrl || "";
    const fullUrl = url.startsWith("http") ? url : `${baseUrl}${url}`;

    const response = await fetch(fullUrl, {
      ...options,
      headers: {
        ...options.headers,
        ...(authHeaders.value as HeadersInit),
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
    if (payload.mode) authState.mode = payload.mode;
    if (payload.templateId) authState.templateId = payload.templateId;
    if (payload.contextName) authState.contextName = payload.contextName;
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
    authState.mode = "LANDING";
    authState.templateId = null;
    authState.contextName = null;
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
    mode,
    templateId,
    contextName,
    isTemplateMode,
    isLandingMode,

    // 方法
    buildImageUrl,
    authFetch,
    setAuth,
    clearAuth,
    stopReadyRetry,
  };
}
