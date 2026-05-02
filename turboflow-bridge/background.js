import {
  checkConnection,
  uploadImageToFlow,
  generateWithReference,
  getMediaRedirectUrl,
  fetchImageAsBase64,
  clearTokenCache,
} from './flow-api.js';

const VERSION = '1.0.0';
const FLOW_URL = 'https://labs.google/fx/zh/tools/flow/';
const SUCCESS_DELAY_MS = 10000;
const FAILURE_DELAY_MS = 60000;
const IDLE_DELAY_MS = 10000;

let bridgeId = null;
let running = false;
let currentTask = null;
let timerId = null;
let serviceCursor = 0;
let lastStatus = { connected: false, message: 'Not checked' };

chrome.sidePanel.setPanelBehavior({ openPanelOnActionClick: true }).catch(() => {});

chrome.tabs.onUpdated.addListener((tabId, _changeInfo, tab) => {
  if (tab.url && /labs\.google\/fx/.test(tab.url)) {
    chrome.sidePanel.setOptions({ tabId, path: 'sidepanel.html', enabled: true }).catch(() => {});
  }
});

chrome.tabs.onRemoved.addListener(() => {
  clearTokenCache();
});

chrome.runtime.onInstalled.addListener(() => {
  ensureBridgeId();
  scheduleLoop(1000);
});

chrome.runtime.onStartup.addListener(() => {
  ensureBridgeId();
  scheduleLoop(1000);
});

chrome.runtime.onMessage.addListener((msg, _sender, sendResponse) => {
  if (msg.type === 'CHECK_CONNECTION') {
    checkConnection().then((state) => {
      lastStatus = { connected: state.connected, message: state.reason || 'Connected', projectId: state.projectId };
      sendResponse(state);
    }).catch((e) => sendResponse({ connected: false, reason: e.message }));
    return true;
  }

  if (msg.type === 'OPEN_FLOW') {
    chrome.tabs.create({ url: FLOW_URL }).then(() => sendResponse({ ok: true }));
    return true;
  }

  if (msg.type === 'GET_CONFIG') {
    loadConfig().then(sendResponse);
    return true;
  }

  if (msg.type === 'SAVE_CONFIG') {
    saveConfig(msg.config || {}).then(() => {
      scheduleLoop(1000);
      sendResponse({ ok: true });
    });
    return true;
  }

  if (msg.type === 'GET_STATUS') {
    sendResponse({ running, currentTask, lastStatus });
    return false;
  }

  if (msg.type === 'RUN_NOW') {
    scheduleLoop(100);
    sendResponse({ ok: true });
    return false;
  }

  return false;
});

async function ensureBridgeId() {
  const stored = await chrome.storage.local.get(['bridgeId']);
  if (stored.bridgeId) {
    bridgeId = stored.bridgeId;
    return bridgeId;
  }
  bridgeId = crypto.randomUUID();
  await chrome.storage.local.set({ bridgeId });
  return bridgeId;
}

async function loadConfig() {
  await ensureBridgeId();
  const stored = await chrome.storage.local.get(['services']);
  return {
    bridgeId,
    services: Array.isArray(stored.services) ? stored.services : [],
  };
}

async function saveConfig(config) {
  const services = Array.isArray(config.services)
    ? config.services.map((s) => ({
        baseUrl: (s.baseUrl || '').trim(),
        token: (s.token || '').trim(),
        enabled: s.enabled !== false,
      })).filter((s) => s.baseUrl && s.token)
    : [];
  await chrome.storage.local.set({ services });
}

function scheduleLoop(delayMs) {
  if (timerId) clearTimeout(timerId);
  timerId = setTimeout(() => runLoop().catch((e) => {
    broadcast({ type: 'BRIDGE_LOG', level: 'error', message: e.message });
    scheduleLoop(FAILURE_DELAY_MS);
  }), delayMs);
}

async function runLoop() {
  if (running) return;
  running = true;
  try {
    const config = await loadConfig();
    const services = config.services.filter((s) => s.enabled !== false && s.baseUrl && s.token);
    const conn = await checkConnection().catch((e) => ({ connected: false, reason: e.message }));
    lastStatus = { connected: conn.connected, message: conn.reason || 'Connected', projectId: conn.projectId };
    broadcast({ type: 'CONNECTION_CHANGED', ...lastStatus });

    // 每轮先向所有服务心跳，让服务器知道这个插件是否已连上 Flow 项目。
    const orderedServices = rotateServices(services);

    for (const service of orderedServices) {
      await heartbeat(service, conn);
    }

    // 一个插件同一时间只执行一个任务；busy 或未连接 Flow 时只保持心跳。
    if (!conn.connected || currentTask || orderedServices.length === 0) {
      scheduleLoop(IDLE_DELAY_MS);
      return;
    }

    // 多个 service 轮询取任务，拿到任务后立即执行，完成/失败后再安排下一轮。
    for (let i = 0; i < orderedServices.length; i++) {
      const service = orderedServices[i];
      const task = await pollTask(service, conn);
      if (task?.hasTask) {
        serviceCursor = (serviceCursor + i + 1) % orderedServices.length;
        await executeTask(service, task);
        return;
      }
    }

    scheduleLoop(IDLE_DELAY_MS);
  } finally {
    running = false;
  }
}

async function heartbeat(service, conn) {
  await postJson(service, '/turboflow-bridge/heartbeat', {
    bridgeId,
    version: VERSION,
    flowConnected: !!conn.connected,
    projectId: conn.projectId || null,
    currentUrl: null,
    busy: !!currentTask,
  }).catch((e) => {
    broadcast({ type: 'BRIDGE_LOG', level: 'warn', message: `Heartbeat failed: ${service.baseUrl} ${e.message}` });
  });
}

async function pollTask(service, conn) {
  return postJson(service, '/turboflow-bridge/tasks/poll', {
    bridgeId,
    version: VERSION,
    flowConnected: true,
    projectId: conn.projectId || null,
  }).catch((e) => {
    broadcast({ type: 'BRIDGE_LOG', level: 'warn', message: `Poll failed: ${service.baseUrl} ${e.message}` });
    return null;
  });
}

async function executeTask(service, task) {
  // currentTask 是本插件本地锁，也会在心跳里上报 busy 状态。
  currentTask = {
    service: service.baseUrl,
    taskId: task.taskId,
    subTaskId: task.subTaskId,
    assignmentId: task.assignmentId,
    startedAt: Date.now(),
  };
  broadcast({ type: 'TASK_CHANGED', currentTask });

  const startedAt = Date.now();
  try {
    const result = await translateImage(task);
    // 成功回调会携带 assignmentId，服务端用它校验任务归属并释放并发槽。
    await postJson(service, '/turboflow-bridge/tasks/complete', {
      bridgeId,
      assignmentId: task.assignmentId,
      resultImageBase64: result.resultDataUrl,
      resultMimeType: 'image/png',
      resultUrl: result.resultUrl || null,
      elapsedMs: Date.now() - startedAt,
    });
    broadcast({ type: 'BRIDGE_LOG', level: 'info', message: `Task completed: ${task.subTaskId}` });
    currentTask = null;
    broadcast({ type: 'TASK_CHANGED', currentTask: null });
    scheduleLoop(SUCCESS_DELAY_MS);
  } catch (e) {
    // 失败回调默认 retryable=true；服务端会放入失败优先队列，优先交给下一个插件重试。
    await postJson(service, '/turboflow-bridge/tasks/fail', {
      bridgeId,
      assignmentId: task.assignmentId,
      errorCode: 'FLOW_EXECUTION_FAILED',
      message: e.message,
      stack: e.stack || null,
      retryable: true,
      elapsedMs: Date.now() - startedAt,
    }).catch(() => {});
    broadcast({ type: 'BRIDGE_LOG', level: 'error', message: `Task failed: ${e.message}` });
    currentTask = null;
    broadcast({ type: 'TASK_CHANGED', currentTask: null });
    scheduleLoop(FAILURE_DELAY_MS);
  }
}

async function translateImage(task) {
  const conn = await checkConnection();
  if (!conn.connected) throw new Error(conn.reason || 'Flow is not connected');

  // Flow 的浏览器会话 token 只在页面里可取，插件通过 MAIN world 注入脚本读取。
  const token = await getSessionTokenFromFlow(conn.tabId);
  const mediaId = await uploadImageToFlow(conn.tabId, {
    base64: stripDataUrl(task.imageBase64),
    fileName: task.fileName || 'source.png',
    mimeType: task.mimeType || 'image/png',
    pid: conn.projectId,
    token,
  });

  const gen = await generateWithReference(conn.tabId, {
    prompt: buildPrompt(task),
    referenceMediaId: mediaId,
    aspectRatio: aspectRatioFor(task.sourceWidth, task.sourceHeight),
    pid: conn.projectId,
    token,
  });

  const resultUrl = gen.fifeUrl || (gen.mediaId ? getMediaRedirectUrl(gen.mediaId) : null);
  if (!resultUrl) throw new Error('Flow returned no image url');

  const image = await fetchImageAsBase64(conn.tabId, resultUrl);
  return { resultUrl, resultDataUrl: image.dataUrl };
}

async function getSessionTokenFromFlow(tabId) {
  const result = await chrome.scripting.executeScript({
    target: { tabId },
    world: 'MAIN',
    func: async () => {
      const res = await fetch('/fx/api/auth/session', { credentials: 'include' });
      const json = await res.json();
      return json.access_token || null;
    },
  });
  const token = result?.[0]?.result;
  if (!token) throw new Error('Failed to get Flow session token');
  return token;
}

function buildPrompt(task) {
  const lang = task.targetLanguage || task.targetLanguageCode || 'Simplified Chinese';
  return `First, analyze whether the image contains any readable text.

Then classify detected text into two categories:

1. Translatable overlay text:
   text that is clearly added as part of the design or layout, such as titles,
descriptions, feature callouts, promotional text, labels, or other explanatory
text placed on top of the image.

2. Non-translatable embedded text:
   text that is physically part of the photographed product itself or its packaging,
such as printed text on the product, bottle, box, bag, label, tag, sticker, manual
shown in the photo, engraved text, embossed text, or any text naturally appearing
inside the original photographed object.

Rules:

- If the image contains translatable overlay text:
  Translate ONLY the translatable overlay text into ${lang}.

  This is a strict text-only edit on the image.
  Keep background, product, colors, and layout exactly unchanged.
  Do NOT translate or modify product/package text.
  Preserve original font style, size, alignment, and spacing as much as possible.
  Use concise, natural ${lang} suitable for e-commerce.
  Output ONLY the final translated image.

- If uncertain whether some text is overlay text or embedded product/package text,
leave it unchanged.`;
}

function aspectRatioFor(width, height) {
  if (!width || !height) return 'IMAGE_ASPECT_RATIO_LANDSCAPE';
  const ratio = width / height;
  const options = [
    { value: 16 / 9, key: 'IMAGE_ASPECT_RATIO_LANDSCAPE' },
    { value: 4 / 3, key: 'IMAGE_ASPECT_RATIO_LANDSCAPE_FOUR_THREE' },
    { value: 1, key: 'IMAGE_ASPECT_RATIO_SQUARE' },
    { value: 3 / 4, key: 'IMAGE_ASPECT_RATIO_PORTRAIT_THREE_FOUR' },
    { value: 9 / 16, key: 'IMAGE_ASPECT_RATIO_PORTRAIT' },
  ];
  return options.reduce((best, item) =>
    Math.abs(item.value - ratio) < Math.abs(best.value - ratio) ? item : best
  ).key;
}

function stripDataUrl(value) {
  if (!value) return '';
  const comma = value.indexOf(',');
  return comma >= 0 ? value.substring(comma + 1) : value;
}

async function postJson(service, path, body) {
  const res = await fetch(normalizeBaseUrl(service.baseUrl) + path, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${service.token}`,
    },
    body: JSON.stringify(body),
  });
  const text = await res.text();
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}: ${text.substring(0, 300)}`);
  }
  return text ? JSON.parse(text) : {};
}

function normalizeBaseUrl(baseUrl) {
  return baseUrl.replace(/\/+$/, '');
}

function rotateServices(services) {
  if (services.length === 0) return [];
  const start = serviceCursor % services.length;
  return services.slice(start).concat(services.slice(0, start));
}

function broadcast(message) {
  chrome.runtime.sendMessage(message).catch(() => {});
}

ensureBridgeId().then(() => scheduleLoop(1000));
