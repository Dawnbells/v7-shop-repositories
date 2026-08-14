// flow-api.js — Google Flow API core module
// Reverse-engineered from TurboFlow (mx-a3f8b2c1.js)
// All API calls execute inside the Flow tab via chrome.scripting.executeScript

const RECAPTCHA_SITE_KEY = '6LdsFiUsAAAAAIjVDZcuLhaHiDn5nnHVXVRQGeMV';
const API_BASE = 'https://aisandbox-pa.googleapis.com';
const MODEL_NARWHAL = 'NARWHAL';
const FLOW_PROJECT_URL_BASE = 'https://labs.google/fx/tools/flow/project/';
const FLOW_URL_PATTERN = /labs\.google\/fx(\/[a-z]{2}(-[a-z]{2})?)?\/tools\/flow/;

// reCAPTCHA 恢复参数：reload/导航后等页面 complete 的超时，以及 grecaptcha 重新就绪的两段冷却。
// 双档恢复策略由 background.js 决策（L1=仅清 labs.google storage；L2=L1 + 清 google.com _GRECAPTCHA cookie），
// 本模块只暴露 runRecoveryChain(tabId, level) 给 background 调用，链上任一步抛错都直接向上抛。
const PAGE_LOAD_TIMEOUT_MS = 30 * 1000;
const RECAPTCHA_SETTLE_MS = 5 * 1000;
const RECAPTCHA_POST_RELOAD_SETTLE_MS = 3 * 1000;
const RECAPTCHA_POST_CREATE_SETTLE_MS = 5 * 1000;

let cachedToken = null;
let tokenTimestamp = 0;
let flowTabId = null;
let projectId = null;

function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}

function randomSeed() {
  return Math.floor(Math.random() * 300000);
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

// ── Tab discovery ──────────────────────────────────────────────────

export async function findFlowTab() {
  const tabs = await chrome.tabs.query({ url: 'https://labs.google/fx/*' });
  const matching = tabs.filter((t) => t.url && FLOW_URL_PATTERN.test(t.url));
  if (matching.length === 0) {
    flowTabId = null;
    return null;
  }
  const best = matching.sort((a, b) => {
    if (a.status === 'complete' && b.status !== 'complete') return -1;
    if (b.status === 'complete' && a.status !== 'complete') return 1;
    return (b.lastAccessed || 0) - (a.lastAccessed || 0);
  })[0];
  flowTabId = best.id;
  return flowTabId;
}

export function getFlowTabId() {
  return flowTabId;
}

// ── Session token ──────────────────────────────────────────────────

export async function getSessionToken(tabId) {
  if (cachedToken) {
    return cachedToken;
  }
  const results = await chrome.scripting.executeScript({
    target: { tabId },
    world: 'MAIN',
    func: async () => {
      try {
        const res = await fetch('/fx/api/auth/session', { credentials: 'include' });
        const json = await res.json();
        return json.access_token || null;
      } catch {
        return null;
      }
    },
  });
  const token = results?.[0]?.result || null;
  if (token) {
    cachedToken = token;
    tokenTimestamp = Date.now();
  }
  return token;
}

export function setSessionToken(token, capturedAt = Date.now()) {
  if (!token) return;
  cachedToken = token;
  tokenTimestamp = capturedAt;
}

export async function refreshSessionToken(tabId) {
  clearTokenCache();
  return await getSessionToken(tabId);
}

export function getSessionTokenStatus() {
  return {
    tokenPresent: !!cachedToken,
    tokenCapturedAt: tokenTimestamp || null,
    tokenAgeMs: cachedToken && tokenTimestamp ? Date.now() - tokenTimestamp : null,
  };
}

export function clearTokenCache() {
  cachedToken = null;
  tokenTimestamp = 0;
}

// ── Project ID ─────────────────────────────────────────────────────

// 对齐 nano-b `Ue()`：projectId 全局缓存，首次提取后复用；reload / 新建 project / tab 关闭时清空。
async function readProjectIdFromTab(tabId) {
  const results = await chrome.scripting.executeScript({
    target: { tabId },
    world: 'MAIN',
    func: () => {
      const m = window.location.href.match(/project\/([a-f0-9-]+)/);
      return m ? m[1] : null;
    },
  });
  return results?.[0]?.result || null;
}

export async function getProjectId(tabId) {
  if (projectId) return projectId;
  projectId = await readProjectIdFromTab(tabId);
  return projectId;
}

export function clearProjectIdCache() {
  projectId = null;
}

// ── reCAPTCHA ──────────────────────────────────────────────────────

export async function getRecaptchaToken(tabId, action = 'IMAGE_GENERATION') {
  const results = await chrome.scripting.executeScript({
    target: { tabId },
    world: 'MAIN',
    func: async (siteKey, act) => {
      try {
        if (typeof grecaptcha !== 'undefined' && grecaptcha.enterprise) {
          return await grecaptcha.enterprise.execute(siteKey, { action: act });
        }
        return null;
      } catch {
        return null;
      }
    },
    args: [RECAPTCHA_SITE_KEY, action],
  });
  return results?.[0]?.result || null;
}

// ── reCAPTCHA recovery (clear storage [+ cookie] → reload → settle → create new project) ─────
//
// 双档恢复链：
//   L1 = clearFlowStorage(labs.google localStorage + sessionStorage) → reload → settle → createFlowProject → settle
//   L2 = L1 全部 + 在 reload 之前先清 google.com 的 _GRECAPTCHA cookie（重置 reCAPTCHA Enterprise 风险评分）
// 链上任一步抛错都直接向上抛出；background 决策是否升级到下一档或终止 + 删 project。

/**
 * 等待目标 tab 进入 complete 状态，超时即拒绝。
 * 用于 reload / 导航后阻塞到页面真正可交互。
 */
function waitForTabComplete(tabId, timeoutMs) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      chrome.tabs.onUpdated.removeListener(listener);
      reject(new Error('Page load timed out'));
    }, timeoutMs);
    function listener(updatedTabId, changeInfo) {
      if (updatedTabId === tabId && changeInfo.status === 'complete') {
        chrome.tabs.onUpdated.removeListener(listener);
        clearTimeout(timer);
        resolve();
      }
    }
    chrome.tabs.onUpdated.addListener(listener);
  });
}

/**
 * 清 Flow tab 的 localStorage + sessionStorage（labs.google origin 内）。
 * 注意：reCAPTCHA Enterprise 的 iframe 跑在 google.com 域，这一步并不能清 reCAPTCHA 自身的客户端状态，
 * 真正撬动风控评分需要走 L2 清 _GRECAPTCHA cookie。这一步只清 Flow 自身的本地缓存（project 列表、UI 偏好等）。
 */
export async function clearFlowStorage(tabId) {
  await chrome.scripting.executeScript({
    target: { tabId },
    world: 'MAIN',
    func: () => {
      try { localStorage.clear(); } catch {}
      try { sessionStorage.clear(); } catch {}
    },
  });
}

/**
 * 列举并删除所有名为 _GRECAPTCHA 的 cookie（一般在 .google.com 域）。
 * _GRECAPTCHA 是 reCAPTCHA Enterprise 的设备风险评分凭据：删除后服务端按"陌生设备"重新评估，
 * 用于打破累计风险评分被持续加重的状态。不动 SID/HSID/SSID/NID 等 Google 账号 cookie，不会登出。
 */
export async function clearGrecaptchaCookie() {
  if (!chrome.cookies) return 0;
  const cookies = await chrome.cookies.getAll({ name: '_GRECAPTCHA' });
  let removed = 0;
  for (const cookie of cookies) {
    const protocol = cookie.secure ? 'https://' : 'http://';
    const domain = cookie.domain.replace(/^\./, '');
    const url = `${protocol}${domain}${cookie.path || '/'}`;
    try {
      await chrome.cookies.remove({ url, name: cookie.name });
      removed++;
    } catch {
      // 单条失败不影响整体——继续清下一条
    }
  }
  return removed;
}

/**
 * Reload Flow tab（带 cache busting 参数），等页面 complete 并等 grecaptcha settle。
 * 不含 token 验证（验证由 runRecoveryChain 末尾统一做）。
 */
async function reloadFlowPageRaw(tabId) {
  clearTokenCache();
  projectId = null;
  await chrome.scripting.executeScript({
    target: { tabId },
    world: 'MAIN',
    func: () => {
      window.location.href = window.location.href.split('?')[0] + '?t=' + Date.now();
    },
  });
  await waitForTabComplete(tabId, PAGE_LOAD_TIMEOUT_MS);
  await sleep(RECAPTCHA_SETTLE_MS);
}

/**
 * 新建一个 Flow project（trpc project.createProject），把 tab 导航到新 project URL，等加载完。
 * 返回新 projectId；失败抛错。
 */
async function createFlowProjectAndNavigate(tabId) {
  projectId = null;
  const createRes = await chrome.scripting.executeScript({
    target: { tabId },
    world: 'MAIN',
    func: async () => {
      try {
        const now = new Date();
        const title = now.toLocaleDateString('en-US', { day: 'numeric', month: 'short' })
          + ', ' + now.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false });
        const res = await fetch('/fx/api/trpc/project.createProject', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'include',
          body: JSON.stringify({ json: { projectTitle: title, toolName: 'PINHOLE' } }),
        });
        if (!res.ok) return { error: 'HTTP ' + res.status };
        const json = await res.json();
        const pid = json?.result?.data?.json?.result?.projectId;
        return pid ? { success: true, projectId: pid } : { error: 'No projectId in response' };
      } catch (e) {
        return { error: e.message };
      }
    },
  });
  const result = createRes?.[0]?.result;
  if (!result?.success) {
    throw new Error(result?.error || 'Failed to create Flow project');
  }
  const targetUrl = FLOW_PROJECT_URL_BASE + result.projectId;
  await chrome.scripting.executeScript({
    target: { tabId },
    world: 'MAIN',
    func: (url) => { window.location.href = url; },
    args: [targetUrl],
  });
  await waitForTabComplete(tabId, PAGE_LOAD_TIMEOUT_MS);
  projectId = result.projectId;
  return projectId;
}

export async function ensureFlowProjectOpen(tabId) {
  const currentProjectId = await readProjectIdFromTab(tabId);
  if (currentProjectId) {
    projectId = currentProjectId;
    return currentProjectId;
  }
  return await createFlowProjectAndNavigate(tabId);
}

/**
 * 执行 reCAPTCHA 恢复链。
 *   level='L1' → 清 labs.google storage → reload → settle → 创建新 project → settle
 *   level='L2' → 在 reload 之前先清 _GRECAPTCHA cookie，其余同 L1
 * 任一步抛错都直接向上抛出；成功返回新建的 projectId。
 * 末尾会取一次 grecaptcha token 做就绪验证：拿不到 token 即视为恢复失败抛错。
 */
export async function runRecoveryChain(tabId, level) {
  await clearFlowStorage(tabId);
  if (level === 'L2') {
    await clearGrecaptchaCookie();
  }
  await reloadFlowPageRaw(tabId);
  const newProjectId = await createFlowProjectAndNavigate(tabId);
  const postSettle = level === 'L2' ? RECAPTCHA_POST_CREATE_SETTLE_MS : RECAPTCHA_POST_RELOAD_SETTLE_MS;
  await sleep(postSettle);
  const token = await getRecaptchaToken(tabId, 'IMAGE_GENERATION');
  if (!token) {
    throw new Error('grecaptcha settle failed: no token after recovery');
  }
  return newProjectId;
}

// ── Low-level API call (runs inside the Flow tab) ──────────────────

function deepClone(obj) {
  return JSON.parse(JSON.stringify(obj));
}

async function callFlowApi(tabId, url, payload, token, action = 'IMAGE_GENERATION', retryCount = 0) {
  // 风控恢复在 background 层处理：当 callFlowApi 抛 'reCAPTCHA blocked' 后，background 会停 poll、
  // 同步跑 runRecoveryChain（L1 或 L2）、成功后再恢复 poll。期间不再发起新 callFlowApi，所以这里不需要门闩。
  const recaptchaToken = await getRecaptchaToken(tabId, action);
  if (!recaptchaToken) {
    throw new Error('No reCAPTCHA token — try refreshing the Flow page');
  }

  // Deep-clone to avoid mutating the caller's payload on retries
  const payloadCopy = deepClone(payload);

  if (payloadCopy.clientContext?.recaptchaContext) {
    payloadCopy.clientContext.recaptchaContext.token = recaptchaToken;
  }
  if (payloadCopy.requests) {
    for (const req of payloadCopy.requests) {
      if (req.clientContext?.recaptchaContext) {
        req.clientContext.recaptchaContext.token = recaptchaToken;
      }
    }
  }

  const bodyStr = JSON.stringify(payloadCopy);

  const results = await chrome.scripting.executeScript({
    target: { tabId },
    world: 'MAIN',
    func: async (apiUrl, body, bearerToken) => {
      try {
        const res = await fetch(apiUrl, {
          method: 'POST',
          headers: {
            'Content-Type': 'text/plain;charset=UTF-8',
            Authorization: 'Bearer ' + bearerToken,
          },
          body,
        });
        const text = await res.text();
        if (!res.ok) {
          return {
            error: 'HTTP ' + res.status + ': ' + text.substring(0, 500),
            errText: text.substring(0, 1000),
            status: res.status,
          };
        }
        let data;
        try {
          data = JSON.parse(text);
        } catch {
          data = text;
        }
        return { success: true, data };
      } catch (e) {
        return { error: e.message };
      }
    },
    args: [url, bodyStr, token],
  });

  const result = results?.[0]?.result;
  if (!result) throw new Error('Script execution failed');

  if (result.error) {
    // 403：先按风控关键字识别 reCAPTCHA / 权限两类。
    // - L0(retryCount=0)：刷 session token 重试，覆盖临时 token 失效场景
    // - reCAPTCHA 风控：直接抛 'reCAPTCHA blocked'，由 background 决策 L1/L2 + runRecoveryChain
    // - 其它：抛出带语义的错误，由 background.js 映射为 errorCode 上报
    if (result.status === 403) {
      const errText = (result.errText || result.error || '').toLowerCase();
      const isRecaptcha = errText.includes('recaptcha')
        || errText.includes('captcha')
        || errText.includes('bot')
        || errText.includes('unusual_activity');
      const isPermission = errText.includes('permission')
        || errText.includes('forbidden')
        || errText.includes('auth');

      if (retryCount === 0) {
        await sleep(1500);
        clearTokenCache();
        const freshToken = await getSessionToken(tabId);
        return callFlowApi(tabId, url, payload, freshToken || token, action, 1);
      }
      if (isRecaptcha) {
        throw new Error('reCAPTCHA blocked — auto recovery required');
      }
      if (isPermission) {
        throw new Error('Access denied (403) — your Flow session may have expired. Refresh the Flow page and try again');
      }
      throw new Error('Blocked by Google (403) — refresh the Flow page, disable VPN if active, and try again');
    }
    // On 401: token expired, refresh and retry once
    if (result.status === 401 && retryCount === 0) {
      clearTokenCache();
      const freshToken = await getSessionToken(tabId);
      if (freshToken) {
        return callFlowApi(tabId, url, payload, freshToken, action, 1);
      }
    }
    // 刷过 token 仍 401（或压根拿不到新 token）：Flow 凭据确实失效了，必须等用户重新登录。
    // 抛结构化 code，而不是让 background 去匹配文案 —— Google 的 401 响应体措辞不稳定，
    // 而且这里的 errText 会被截断到 500 字，status 字段可能压根不在截断范围内。
    if (result.status === 401) {
      const error = new Error('Flow API unauthorized: ' + result.error);
      error.name = 'FlowAuthenticationError';
      error.code = 'FLOW_AUTHENTICATION_FAILED';
      error.httpStatus = 401;
      throw error;
    }
    // On 429: 先判 daily quota（账号级硬性限制，重试只会加重风控），其余走限速重试。
    // 对齐 nano-b je 的 429 + DAILY_QUOTA_REACHED/RESOURCE_EXHAUSTED 早停语义。
    if (result.status === 429) {
      const errText = result.errText || result.error || '';
      if (errText.includes('DAILY_QUOTA_REACHED') || errText.includes('RESOURCE_EXHAUSTED')) {
        throw new Error('DAILY_QUOTA_REACHED — daily generation limit reached. Try again in a few hours.');
      }
      // 重试次数对齐 nano-b lt 调度器：最多 3 次（retryCount < 3）
      if (retryCount < 3) {
        const backoff = 3000 * Math.pow(2, retryCount) + Math.random() * 1000;
        await sleep(backoff);
        return callFlowApi(tabId, url, payload, token, action, retryCount + 1);
      }
    }
    throw new Error(result.error);
  }

  return result.data;
}

export function buildPolicyFallbackCompletion({
  bridgeId,
  assignmentId,
  imageHash,
  apiStatus,
  reason,
  elapsedMs,
}) {
  const completion = {
    bridgeId,
    assignmentId,
    policyFallback: true,
    policyFallbackStatus: apiStatus,
    policyFallbackReason: reason || apiStatus,
    elapsedMs,
  };
  if (imageHash) completion.imageHash = imageHash;
  return completion;
}

// ── Upload image to Flow ───────────────────────────────────────────

export async function uploadImageToFlow(tabId, { base64, fileName, mimeType, pid, token }, retryCount = 0) {
  const results = await chrome.scripting.executeScript({
    target: { tabId },
    world: 'MAIN',
    func: async (imgBase64, name, mime, projId, bearerToken) => {
      try {
        const res = await fetch('https://aisandbox-pa.googleapis.com/v1/flow/uploadImage', {
          method: 'POST',
          headers: {
            'Content-Type': 'text/plain;charset=UTF-8',
            Authorization: 'Bearer ' + bearerToken,
          },
          body: JSON.stringify({
            clientContext: { projectId: projId, tool: 'PINHOLE' },
            fileName: name,
            imageBytes: imgBase64,
            isHidden: false,
            isUserUploaded: true,
            mimeType: mime,
          }),
        });
        if (!res.ok) {
          const errText = await res.text();
          let apiStatus = null;
          let reason = null;
          try {
            const errorBody = JSON.parse(errText);
            apiStatus = errorBody?.error?.status || null;
            const details = Array.isArray(errorBody?.error?.details) ? errorBody.error.details : [];
            const errorInfo = details.find((detail) =>
              detail?.['@type'] === 'type.googleapis.com/google.rpc.ErrorInfo'
              && typeof detail.reason === 'string');
            reason = errorInfo?.reason || null;
          } catch {
            // 非 JSON 错误仍沿用原有 HTTP 错误处理。
          }
          return {
            error: 'HTTP ' + res.status + ': ' + errText.substring(0, 300),
            status: res.status,
            apiStatus,
            reason,
          };
        }
        const json = await res.json();
        return { success: true, data: json };
      } catch (e) {
        return { error: e.message, isNetworkError: true };
      }
    },
    args: [base64, fileName, mimeType, pid, token],
  });

  const result = results?.[0]?.result;
  if (!result) {
    if (retryCount < 2) {
      await sleep(1000 * Math.pow(2, retryCount));
      return uploadImageToFlow(tabId, { base64, fileName, mimeType, pid, token }, retryCount + 1);
    }
    throw new Error('Upload script execution failed after retries');
  }

  if (result.error) {
    if (result.apiStatus === 'INVALID_ARGUMENT') {
      const error = new Error('Upload rejected by content policy: ' + result.error);
      error.name = 'FlowUploadPolicyError';
      error.code = 'FLOW_UPLOAD_POLICY_REJECTED';
      error.apiStatus = result.apiStatus;
      error.reason = result.reason || result.apiStatus;
      error.httpStatus = result.status || null;
      throw error;
    }
    const isRetryable = result.isNetworkError || result.status === 429 || result.status === 500 || result.status === 502 || result.status === 503;
    if (isRetryable && retryCount < 2) {
      const backoff = result.status === 429
        ? 3000 * Math.pow(2, retryCount)
        : 1000 * Math.pow(1.5, retryCount) + Math.random() * 500;
      await sleep(backoff);
      if (result.status === 401 || result.status === 403) {
        clearTokenCache();
        const freshToken = await getSessionToken(tabId);
        return uploadImageToFlow(tabId, { base64, fileName, mimeType, pid, token: freshToken || token }, retryCount + 1);
      }
      return uploadImageToFlow(tabId, { base64, fileName, mimeType, pid, token }, retryCount + 1);
    }
    if ((result.status === 401 || result.status === 403) && retryCount === 0) {
      clearTokenCache();
      const freshToken = await getSessionToken(tabId);
      if (freshToken) {
        return uploadImageToFlow(tabId, { base64, fileName, mimeType, pid, token: freshToken }, 1);
      }
    }
    if (result.status === 401 || result.apiStatus === 'UNAUTHENTICATED') {
      const error = new Error('Upload failed: ' + result.error);
      error.name = 'FlowAuthenticationError';
      error.code = 'FLOW_AUTHENTICATION_FAILED';
      error.apiStatus = result.apiStatus || null;
      error.httpStatus = result.status || null;
      throw error;
    }
    throw new Error('Upload failed: ' + result.error);
  }

  const mediaId = result.data?.media?.name;
  if (!mediaId) throw new Error('No mediaId in upload response');
  return mediaId;
}

// ── Generate image with reference (Nano Banana 2) ──────────────────

export async function generateWithReference(tabId, { prompt, referenceMediaId, aspectRatio, pid, token, model }) {
  const batchId = uuid();
  // 对齐 nano-b mt 中的 sessionId 生成方式 `";"+Date.now()+r`（r 是任务索引），单任务流场景下 r=0 即可
  const sessionId = ';' + Date.now() + '0';
  const seed = randomSeed();
  const url = `${API_BASE}/v1/projects/${pid}/flowMedia:batchGenerateImages`;

  const SUPPORTED_RATIOS = [
    'IMAGE_ASPECT_RATIO_SQUARE',
    'IMAGE_ASPECT_RATIO_PORTRAIT_THREE_FOUR',
    'IMAGE_ASPECT_RATIO_LANDSCAPE_FOUR_THREE',
    'IMAGE_ASPECT_RATIO_PORTRAIT',
    'IMAGE_ASPECT_RATIO_LANDSCAPE',
  ];
  let apiAspectRatio = aspectRatio || 'IMAGE_ASPECT_RATIO_LANDSCAPE';
  if (!SUPPORTED_RATIOS.includes(apiAspectRatio)) {
    apiAspectRatio = 'IMAGE_ASPECT_RATIO_LANDSCAPE';
  }
  const apiModel = model || MODEL_NARWHAL;

  const payload = {
    clientContext: {
      recaptchaContext: {
        applicationType: 'RECAPTCHA_APPLICATION_TYPE_WEB',
        token: 'PLACEHOLDER',
      },
      projectId: pid,
      tool: 'PINHOLE',
      sessionId,
    },
    mediaGenerationContext: { batchId },
    useNewMedia: true,
    requests: [
      {
        clientContext: {
          recaptchaContext: {
            applicationType: 'RECAPTCHA_APPLICATION_TYPE_WEB',
            token: 'PLACEHOLDER',
          },
          projectId: pid,
          tool: 'PINHOLE',
          sessionId,
        },
        imageAspectRatio: apiAspectRatio,
        imageInputs: [
          {
            imageInputType: 'IMAGE_INPUT_TYPE_REFERENCE',
            name: referenceMediaId,
          },
        ],
        imageModelName: apiModel,
        seed,
        structuredPrompt: {
          parts: [{ text: prompt }],
        },
      },
    ],
  };

  const data = await callFlowApi(tabId, url, payload, token, 'IMAGE_GENERATION');

  let fifeUrl = null;
  let mediaId = null;

  if (data?.media && Array.isArray(data.media)) {
    for (const m of data.media) {
      const url = m?.image?.generatedImage?.fifeUrl;
      if (url) {
        fifeUrl = url;
        break;
      }
    }
  }

  if (data?.workflows) {
    for (const w of data.workflows) {
      const mid = w?.metadata?.primaryMediaId;
      if (mid) {
        mediaId = mid;
        break;
      }
    }
  }

  return { fifeUrl, mediaId, batchId, seed, raw: data };
}

// ── Get downloadable URL for a mediaId ─────────────────────────────

export function getMediaRedirectUrl(mediaId) {
  return `https://labs.google/fx/api/trpc/media.getMediaUrlRedirect?name=${mediaId}`;
}

// ── Fetch image as base64 from the Flow tab context ────────────────
//
// 对齐 nano-b：图片字节通过 Flow tab 内 Image+canvas+toBlob('image/png') 取出。
// Image.crossOrigin='anonymous' 绕开 Google CDN 的 CORS 限制，比直接 fetch() 更稳。
// 与 nano-b 的差异：nano-b 返回 blob URL 由 background 再 fetch 下载到磁盘；
// bridge 需要把图片 base64 上报到自己的后端，所以这里通过 readAsDataURL 直接拿到 dataUrl。

export async function fetchImageAsBase64(tabId, imageUrl, timeoutMs = 60000) {
  const results = await chrome.scripting.executeScript({
    target: { tabId },
    world: 'MAIN',
    func: async (url, tMs) => {
      try {
        return await new Promise((resolve) => {
          const timeout = setTimeout(() => resolve({ error: `Image load timed out (${tMs / 1000}s)` }), tMs);
          const img = new Image();
          img.crossOrigin = 'anonymous';
          img.onload = () => {
            clearTimeout(timeout);
            try {
              const canvas = document.createElement('canvas');
              canvas.width = img.naturalWidth;
              canvas.height = img.naturalHeight;
              canvas.getContext('2d').drawImage(img, 0, 0);
              canvas.toBlob(
                (blob) => {
                  if (!blob) return resolve({ error: 'toBlob failed' });
                  const reader = new FileReader();
                  reader.onload = () => resolve({ success: true, dataUrl: reader.result, size: blob.size });
                  reader.readAsDataURL(blob);
                },
                'image/png'
              );
            } catch (e) {
              resolve({ error: 'Canvas error: ' + e.message });
            }
          };
          img.onerror = () => {
            clearTimeout(timeout);
            resolve({ error: 'Image load failed' });
          };
          img.src = url;
        });
      } catch (e) {
        return { error: e.message };
      }
    },
    args: [imageUrl, timeoutMs],
  });

  const result = results?.[0]?.result;
  if (!result || result.error) {
    // [DOWNLOAD_FAILED] 前缀让 background classifyErrorCode 能把"下载结果图失败"和其它错误区分开，
    // 仅这一类计入 consecutiveDownloadFails（连续 3 张触发 L1 恢复），避免被 FLOW_DISCONNECTED 等污染。
    throw new Error('[DOWNLOAD_FAILED] Failed to fetch image: ' + (result?.error || 'unknown'));
  }
  return result;
}

// ── Full connection check ──────────────────────────────────────────

export async function checkConnection() {
  const tabId = await findFlowTab();
  if (!tabId) {
    return { connected: false, reason: 'No Google Flow tab found. Open Google Flow first.' };
  }

  try {
    const tab = await chrome.tabs.get(tabId);
    if (tab.status !== 'complete') {
      return { connected: false, reason: 'Flow page is still loading...' };
    }
  } catch {
    flowTabId = null;
    return { connected: false, reason: 'Flow tab was closed.' };
  }

  const token = await getSessionToken(tabId);
  if (!token) {
    return { connected: false, reason: 'Could not get session token. Make sure you are logged into Google Flow.' };
  }

  const pid = await getProjectId(tabId);
  if (!pid) {
    return { connected: false, reason: 'No project open. Create or open a project in Flow.' };
  }

  // 不再在此处主动调 grecaptcha.enterprise.execute 做预检——4 并发场景下短时累计调用过多会触发风控。
  // 对齐 nano-b：每个任务只在 callFlowApi 内消耗 1 次 reCAPTCHA token。
  // 风控真正触发时由 callFlowApi 抛 'reCAPTCHA blocked'，background 决策 L1/L2 走 runRecoveryChain 兜底。
  return { connected: true, tabId, projectId: pid };
}

// ── Project list / delete (trpc) ───────────────────────────────────
//
// 用于"任务停止时清空账号下所有 project"。trpc 接口在 Flow tab context 里调（带 cookie 凭据）。
//   list:   GET  /fx/api/trpc/project.searchUserProjects?input=<URL-encoded JSON>
//   delete: POST /fx/api/trpc/project.deleteProject  body: {"json":{"projectToDeleteId":"..."}}

async function searchUserProjectsPage(tabId, cursor) {
  // trpc SuperJSON 透传：cursor=null 时需要 meta.values.cursor=["undefined"]，告诉服务端把 null 反序列化为 undefined
  const input = cursor === null
    ? { json: { pageSize: 20, toolName: 'PINHOLE', cursor: null }, meta: { values: { cursor: ['undefined'] } } }
    : { json: { pageSize: 20, toolName: 'PINHOLE', cursor } };
  const path = '/fx/api/trpc/project.searchUserProjects?input=' + encodeURIComponent(JSON.stringify(input));
  const results = await chrome.scripting.executeScript({
    target: { tabId },
    world: 'MAIN',
    func: async (p) => {
      try {
        const res = await fetch(p, { credentials: 'include' });
        if (!res.ok) return { error: 'HTTP ' + res.status };
        return { success: true, data: await res.json() };
      } catch (e) {
        return { error: e.message };
      }
    },
    args: [path],
  });
  const result = results?.[0]?.result;
  if (!result?.success) throw new Error(result?.error || 'searchUserProjects failed');
  return result.data;
}

/**
 * 分页列举当前账号下所有 project。
 * 实测响应结构：
 *   data.result.data.json.result.projects[]   // 每条 {projectId, projectInfo:{projectTitle, thumbnailMediaKey}, creationTime, ...}
 *   data.result.data.json.result.nextPageToken // 字符串；下一页请求时作为 cursor 传回
 * 返回 [{projectId, title}, ...]；防御性上限 1000 页（最多 20000 个 project），防止异常 token 导致死循环。
 */
export async function listAllUserProjects(tabId) {
  const all = [];
  let cursor = null;
  let safety = 0;
  while (safety++ < 1000) {
    const data = await searchUserProjectsPage(tabId, cursor);
    const inner = data?.result?.data?.json?.result || data?.result?.data?.json || {};
    const items = inner.projects || inner.userProjects || inner.results || inner.items || [];
    for (const p of items) {
      const pid = p?.projectId || p?.id;
      if (pid) {
        all.push({
          projectId: pid,
          title: p?.projectInfo?.projectTitle || p?.projectTitle || p?.title || '',
        });
      }
    }
    cursor = inner.nextPageToken || inner.nextCursor || inner.cursor || null;
    if (!cursor) break;
  }
  return all;
}

export async function deleteFlowProject(tabId, projectIdToDelete) {
  const results = await chrome.scripting.executeScript({
    target: { tabId },
    world: 'MAIN',
    func: async (pid) => {
      try {
        const res = await fetch('/fx/api/trpc/project.deleteProject', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'include',
          body: JSON.stringify({ json: { projectToDeleteId: pid } }),
        });
        if (!res.ok) return { error: 'HTTP ' + res.status };
        return { success: true };
      } catch (e) {
        return { error: e.message };
      }
    },
    args: [projectIdToDelete],
  });
  const result = results?.[0]?.result;
  if (!result?.success) throw new Error(result?.error || 'deleteProject failed');
}

/**
 * 列举并删除账号下所有 project。
 * 串行 200ms 间隔，单个失败继续不阻塞整批；进度通过 onProgress 回调上报给 background → sidepanel。
 * onProgress 阶段：
 *   { phase: 'list-failed', error }            列举失败，无法继续
 *   { phase: 'start', total }                   开始删除前
 *   { phase: 'progress', current, total, deleted, failed }  每删一个
 *   { phase: 'done', total, deleted, failed }   全部完成
 */
export async function deleteAllUserProjects(tabId, onProgress) {
  let projects;
  try {
    projects = await listAllUserProjects(tabId);
  } catch (e) {
    if (typeof onProgress === 'function') onProgress({ phase: 'list-failed', error: e.message });
    return { listed: 0, deleted: 0, failed: 0 };
  }
  const total = projects.length;
  let deleted = 0;
  let failed = 0;
  if (typeof onProgress === 'function') onProgress({ phase: 'start', total });
  for (let i = 0; i < projects.length; i++) {
    try {
      await deleteFlowProject(tabId, projects[i].projectId);
      deleted++;
    } catch {
      failed++;
    }
    if (typeof onProgress === 'function') {
      onProgress({ phase: 'progress', current: i + 1, total, deleted, failed });
    }
    await sleep(200);
  }
  if (typeof onProgress === 'function') onProgress({ phase: 'done', total, deleted, failed });
  return { listed: total, deleted, failed };
}
