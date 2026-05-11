// flow-api.js — Google Flow API core module
// Reverse-engineered from TurboFlow (mx-a3f8b2c1.js)
// All API calls execute inside the Flow tab via chrome.scripting.executeScript

const RECAPTCHA_SITE_KEY = '6LdsFiUsAAAAAIjVDZcuLhaHiDn5nnHVXVRQGeMV';
const API_BASE = 'https://aisandbox-pa.googleapis.com';
const MODEL_NARWHAL = 'NARWHAL';
const FLOW_URL_PATTERN = /labs\.google\/fx(\/[a-z]{2}(-[a-z]{2})?)?\/tools\/flow/;
const TOKEN_CACHE_MS = 5 * 60 * 1000;

// reCAPTCHA 风控恢复策略：先尝试 reload Flow 页面，仍失败则新建 project；总恢复次数和 reload 次数都有上限，避免死循环。
const MAX_TOTAL_RECOVERY = 3;
const MAX_PAGE_RELOAD = 2;
const PAGE_LOAD_TIMEOUT_MS = 30 * 1000;
const RECAPTCHA_SETTLE_MS = 5 * 1000;

let cachedToken = null;
let tokenTimestamp = 0;
let flowTabId = null;
let projectId = null;

let recoveryInProgress = false;
let recoveryPromise = null;
let totalRecoveryAttempts = 0;
let pageReloadAttempts = 0;

export function resetRecoveryState() {
  totalRecoveryAttempts = 0;
  pageReloadAttempts = 0;
}

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
  if (cachedToken && Date.now() - tokenTimestamp < TOKEN_CACHE_MS) {
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

export function clearTokenCache() {
  cachedToken = null;
  tokenTimestamp = 0;
}

// ── Project ID ─────────────────────────────────────────────────────

export async function getProjectId(tabId) {
  const results = await chrome.scripting.executeScript({
    target: { tabId },
    world: 'MAIN',
    func: () => {
      const m = window.location.href.match(/project\/([a-f0-9-]+)/);
      return m ? m[1] : null;
    },
  });
  projectId = results?.[0]?.result || null;
  return projectId;
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

// ── reCAPTCHA recovery (page reload → new project) ─────────────────

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
 * L1 恢复：在 Flow tab 内强制刷新页面，以重置 reCAPTCHA session。
 * 加 ?t=timestamp 避免缓存命中。等加载完成后 sleep 一段时间让 grecaptcha 重新就绪，再实际取 token 验证。
 */
async function reloadFlowPage(tabId) {
  clearTokenCache();
  projectId = null;
  try {
    await chrome.scripting.executeScript({
      target: { tabId },
      world: 'MAIN',
      func: () => {
        window.location.href = window.location.href.split('?')[0] + '?t=' + Date.now();
      },
    });
    await waitForTabComplete(tabId, PAGE_LOAD_TIMEOUT_MS);
    await sleep(RECAPTCHA_SETTLE_MS);
    const token = await getRecaptchaToken(tabId, 'IMAGE_GENERATION');
    return !!token;
  } catch {
    return false;
  }
}

/**
 * L2 恢复：通过 trpc 接口新建一个 Flow project，再把 tab 导航到新 project URL。
 * 比 reload 更激进，能重置部分服务端绑定的 session 上下文。
 */
async function createNewProject(tabId) {
  clearTokenCache();
  projectId = null;
  try {
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
      return false;
    }
    const targetUrl = 'https://labs.google/fx/tools/flow/project/' + result.projectId;
    await chrome.scripting.executeScript({
      target: { tabId },
      world: 'MAIN',
      func: (url) => { window.location.href = url; },
      args: [targetUrl],
    });
    await waitForTabComplete(tabId, PAGE_LOAD_TIMEOUT_MS);
    await sleep(RECAPTCHA_SETTLE_MS);
    const token = await getRecaptchaToken(tabId, 'IMAGE_GENERATION');
    return !!token;
  } catch {
    return false;
  }
}

/**
 * reCAPTCHA 恢复调度器。
 * - 并发请求只触发一次恢复：通过 recoveryPromise 让其他调用方等待结果
 * - 总恢复次数有上限（避免长时间死循环消耗 API/Tab 资源）
 * - 优先 reload Flow 页面（最多 MAX_PAGE_RELOAD 次），仍失败再 createNewProject
 * resetRecoveryState() 会清零计数，应在任务成功或新批次开始时调用。
 */
export async function recoverRecaptcha(tabId) {
  if (recoveryInProgress && recoveryPromise) {
    return await recoveryPromise;
  }
  if (totalRecoveryAttempts >= MAX_TOTAL_RECOVERY) {
    return false;
  }
  recoveryInProgress = true;
  totalRecoveryAttempts++;
  recoveryPromise = (async () => {
    try {
      if (pageReloadAttempts < MAX_PAGE_RELOAD) {
        pageReloadAttempts++;
        if (await reloadFlowPage(tabId)) {
          return true;
        }
      }
      return await createNewProject(tabId);
    } finally {
      recoveryInProgress = false;
    }
  })();
  try {
    return await recoveryPromise;
  } finally {
    recoveryPromise = null;
  }
}

// ── Low-level API call (runs inside the Flow tab) ──────────────────

function deepClone(obj) {
  return JSON.parse(JSON.stringify(obj));
}

async function callFlowApi(tabId, url, payload, token, action = 'IMAGE_GENERATION', retryCount = 0) {
  const recaptchaToken = await getRecaptchaToken(tabId, action);
  if (!recaptchaToken) {
    throw new Error('Failed to obtain reCAPTCHA token — try refreshing the Flow page');
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
    // - L1(retryCount=1) 且 reCAPTCHA 风控：进入 recoverRecaptcha（reload Flow / 新建 project）
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
      if (retryCount === 1 && isRecaptcha) {
        const recovered = await recoverRecaptcha(tabId);
        if (recovered) {
          const freshToken = await getSessionToken(tabId);
          return callFlowApi(tabId, url, payload, freshToken || token, action, 2);
        }
      }
      if (isRecaptcha) {
        throw new Error('reCAPTCHA blocked — close the Flow tab, reopen it, and try again');
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
    // On 429: rate limited, wait and retry once
    if (result.status === 429 && retryCount < 2) {
      const backoff = 3000 * Math.pow(2, retryCount) + Math.random() * 1000;
      await sleep(backoff);
      return callFlowApi(tabId, url, payload, token, action, retryCount + 1);
    }
    throw new Error(result.error);
  }

  return result.data;
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
          return { error: 'HTTP ' + res.status + ': ' + errText.substring(0, 300), status: res.status };
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
    throw new Error('Upload failed: ' + result.error);
  }

  const mediaId = result.data?.media?.name;
  if (!mediaId) throw new Error('No mediaId in upload response');
  return mediaId;
}

// ── Generate image with reference (Nano Banana 2) ──────────────────

export async function generateWithReference(tabId, { prompt, referenceMediaId, aspectRatio, pid, token, model }) {
  const batchId = uuid();
  const sessionId = ';' + Date.now() + Math.random();
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
      userPaygateTier: 'PAYGATE_TIER_NOT_PAID',
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
          userPaygateTier: 'PAYGATE_TIER_NOT_PAID',
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

async function fetchViaFetchApi(tabId, imageUrl, timeoutMs) {
  const results = await chrome.scripting.executeScript({
    target: { tabId },
    world: 'MAIN',
    func: async (url, tMs) => {
      try {
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), tMs);
        const res = await fetch(url, { signal: controller.signal });
        clearTimeout(timer);
        if (!res.ok) return { error: 'HTTP ' + res.status };
        const blob = await res.blob();
        return await new Promise((resolve) => {
          const reader = new FileReader();
          reader.onload = () => resolve({ success: true, dataUrl: reader.result, size: blob.size });
          reader.onerror = () => resolve({ error: 'FileReader failed' });
          reader.readAsDataURL(blob);
        });
      } catch (e) {
        return { error: e.name === 'AbortError' ? `fetch timed out (${tMs / 1000}s)` : e.message };
      }
    },
    args: [imageUrl, timeoutMs],
  });

  const result = results?.[0]?.result;
  if (!result || result.error) {
    throw new Error('fetch failed: ' + (result?.error || 'unknown'));
  }
  return result;
}

async function fetchViaImageElement(tabId, imageUrl, timeoutMs) {
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
    throw new Error('Image element failed: ' + (result?.error || 'unknown'));
  }
  return result;
}

export async function fetchImageAsBase64(tabId, imageUrl, timeoutMs = 60000) {
  try {
    return await fetchViaFetchApi(tabId, imageUrl, timeoutMs);
  } catch (fetchErr) {
    return await fetchViaImageElement(tabId, imageUrl, timeoutMs);
  }
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

  // 真的试调一次 grecaptcha.enterprise.execute：风控触发时即便对象存在也无法拿 token，
  // 提前在这里拦下，避免后续 executeTask 消耗任务名额。
  const recaptchaToken = await getRecaptchaToken(tabId, 'IMAGE_GENERATION');
  if (!recaptchaToken) {
    return { connected: false, reason: 'reCAPTCHA blocked or not loaded. Refresh the Flow page; disable VPN if active.' };
  }

  return { connected: true, tabId, projectId: pid };
}
