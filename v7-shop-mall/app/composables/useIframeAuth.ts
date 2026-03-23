/**
 * useIframeAuth - iframe 通信和鉴权状态管理
 *
 * 当 builder 嵌入在 admin iframe 中时，通过 postMessage 接收：
 * - token：用于 API 鉴权
 * - imageBaseUrl：图片基础 URL
 * - apiBaseUrl：API 基础 URL
 * - mode：编辑模式（TEMPLATE 或 LANDING）
 * - query 参数（根据模式不同有不同字段）
 */

export type BuilderMode = "TEMPLATE" | "LANDING";

/**
 * iframe 父窗口传入的认证与上下文（BUILDER_INIT）
 *
 * **落地页编辑**（站点配置进入）：`mode: "LANDING"`，并传 `query.subDomainId`、`query.spuId` 等；
 * 主题在 Nuxt `/api/builder/load|save` 中按子域+SPU 读写。
 *
 * **主题模板编辑**（主题模板菜单进入）：`mode: "TEMPLATE"`，并传 `templateId`、`contextName`（模板名称）、
 * `apiBaseUrl`（管理后台 API 根路径，如 `https://host/api`）。编辑器将请求
 * `GET /theme-templates/{id}` 与 `POST /theme-templates/updateConfig`（需携带 token）。
 */
export interface IframeAuthPayload {
  token: string;
  imageBaseUrl?: string;
  /** 管理后台 API 根地址；模板模式必填，用于 theme-templates 接口 */
  apiBaseUrl?: string;
  mode?: BuilderMode;
  /** 主题模板 ID（字符串）；模板模式与 mode=TEMPLATE 一起传入 */
  templateId?: string;
  /** 模板显示名称或落地页辅助说明 */
  contextName?: string;
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
  mode: "LANDING",
  templateId: null,
  contextName: null,
  query: null,
  origin: null,
});

// 允许的 origin 白名单
const getAllowedOrigins = (): string[] => {
  const config = useRuntimeConfig();
  const origins = config.public?.allowedOrigins as string | undefined;
  if (origins) {
    return origins.split(",").map((o) => o.trim());
  }
  // 默认允许同源和常见开发地址
  return [
    typeof window !== "undefined" ? window.location.origin : "",
    "http://localhost:3000",
    "http://localhost:3001",
    "http://localhost:5173",
    "http://localhost:5200",
    "http://localhost:9999",
    "http://127.0.0.1:3000",
    "http://127.0.0.1:3001",
    "http://127.0.0.1:5173",
    "http://127.0.0.1:5200",
    "http://127.0.0.1:9999",
    "http://127.0.0.1:5500",
  ].filter(Boolean);
};

// 验证消息来源
const isAllowedOrigin = (origin: string): boolean => {
  const allowed = getAllowedOrigins();
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

  if (!isAllowedOrigin(event.origin)) {
    console.warn("[IframeAuth] 拒绝来自不受信任来源的消息:", event.origin);
    return;
  }

  const data = event.data;

  console.log("[IframeAuth] 消息数据:", data);

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

    // 启动重试机制
    scheduleRetry();
  } else if (import.meta.client) {
    console.log("[IframeAuth] 监听器已存在，跳过初始化");
  }

  const isReady = computed(() => authState.isReady);
  const token = computed(() => authState.token);
  const imageBaseUrl = computed(() => authState.imageBaseUrl);
  const apiBaseUrl = computed(() => authState.apiBaseUrl);
  const query = computed(() => authState.query);
  const mode = computed(() => authState.mode);
  const templateId = computed(() => authState.templateId);
  const contextName = computed(() => authState.contextName);
  const isTemplateMode = computed(() => authState.mode === "TEMPLATE");
  const isLandingMode = computed(() => authState.mode === "LANDING");

  const authHeaders = computed(() => {
    if (!authState.token) return {};
    return {
      Authorization: `Bearer ${authState.token}`,
    };
  });

  const buildImageUrl = (relativePath: string): string => {
    if (!relativePath) return "";
    if (
      relativePath.startsWith("http://") ||
      relativePath.startsWith("https://")
    ) {
      return relativePath;
    }
    const config = useRuntimeConfig();
    const baseUrl =
      authState.imageBaseUrl || (config.public.imageBaseUrl as string);
    if (!baseUrl) {
      return relativePath;
    }
    const cleanBaseUrl = baseUrl.endsWith("/") ? baseUrl.slice(0, -1) : baseUrl;
    const cleanPath = relativePath.startsWith("/")
      ? relativePath
      : `/${relativePath}`;
    return `${cleanBaseUrl}${cleanPath}`;
  };

  const authFetch = async <T = any>(
    url: string,
    options: RequestInit = {},
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
    buildImageUrl,
    authFetch,
    setAuth,
    clearAuth,
    stopReadyRetry,
  };
}
