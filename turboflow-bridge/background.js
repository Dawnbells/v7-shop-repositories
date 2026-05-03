import {
  checkConnection,
  uploadImageToFlow,
  generateWithReference,
  getMediaRedirectUrl,
  fetchImageAsBase64,
  clearTokenCache,
} from './flow-api.js';

const VERSION = '1.1.0';
const FLOW_URL = 'https://labs.google/fx/zh/tools/flow/';
const SUCCESS_DELAY_MS = 10000;
const FAILURE_DELAY_MS = 60000;
const IDLE_DELAY_MS = 10000;
const MAX_TASK_HISTORY = 50;
const MAX_LOG_HISTORY = 500;

let bridgeId = null;
let running = false;
let currentTask = null;
let timerId = null;
let serviceCursor = 0;
let lastStatus = { connected: false, message: 'Not checked' };
let nextPollAt = 0;
let taskHistory = [];
let logHistory = [];

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
  loadPersistedState().then(() => scheduleLoop(1000));
});

chrome.runtime.onStartup.addListener(() => {
  ensureBridgeId();
  loadPersistedState().then(() => scheduleLoop(1000));
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
    saveConfig(msg.config || {}).then(async () => {
      const stored = await chrome.storage.local.get(['services']);
      const count = Array.isArray(stored.services) ? stored.services.length : 0;
      addLog('info', `Config saved (${count} services)`);
      scheduleLoop(1000);
      sendResponse({ ok: true });
    });
    return true;
  }

  if (msg.type === 'GET_STATUS') {
    sendResponse({ running, currentTask, lastStatus, nextPollAt });
    return false;
  }

  if (msg.type === 'RUN_NOW') {
    scheduleLoop(100);
    sendResponse({ ok: true });
    return false;
  }

  if (msg.type === 'GET_TASK_HISTORY') {
    const page = msg.page || 0;
    const pageSize = msg.pageSize || 20;
    const start = page * pageSize;
    const items = taskHistory.slice(start, start + pageSize);
    sendResponse({ items, total: taskHistory.length, page, pageSize });
    return false;
  }

  if (msg.type === 'GET_TODAY_STATS') {
    const todayStart = new Date();
    todayStart.setHours(0, 0, 0, 0);
    const ts = todayStart.getTime();
    let success = 0, failed = 0;
    for (const t of taskHistory) {
      if (t.time < ts) break;
      if (t.status === 'completed') success++;
      else failed++;
    }
    sendResponse({ total: success + failed, success, failed });
    return false;
  }

  if (msg.type === 'GET_LOGS') {
    sendResponse({ logs: logHistory });
    return false;
  }

  if (msg.type === 'CLEAR_LOGS') {
    logHistory = [];
    persistLogs();
    sendResponse({ ok: true });
    return false;
  }

  if (msg.type === 'LOG') {
    addLog(msg.level || 'info', msg.message || '');
    sendResponse({ ok: true });
    return false;
  }

  if (msg.type === 'TEST_TRANSLATE') {
    (async () => {
      try {
        const conn = await checkConnection();
        if (!conn.connected) throw new Error(conn.reason || 'Flow is not connected');
        const token = await getSessionTokenFromFlow(conn.tabId);
        const base64 = msg.imageBase64.startsWith('data:')
          ? msg.imageBase64.substring(msg.imageBase64.indexOf(',') + 1)
          : msg.imageBase64;
        const mediaId = await uploadImageToFlow(conn.tabId, {
          base64,
          fileName: msg.fileName || 'test.png',
          mimeType: msg.mimeType || 'image/png',
          pid: conn.projectId,
          token,
        });
        const prompt = msg.prompt || buildPrompt({
          targetLanguage: msg.targetLanguage || 'Simplified Chinese',
        });
        const aspectRatio = msg.aspectRatio === 'auto'
          ? aspectRatioFor(msg.width, msg.height)
          : msg.aspectRatio;
        const gen = await generateWithReference(conn.tabId, {
          prompt,
          referenceMediaId: mediaId,
          aspectRatio,
          pid: conn.projectId,
          token,
          model: msg.model || undefined,
        });
        const resultUrl = gen.fifeUrl || (gen.mediaId ? getMediaRedirectUrl(gen.mediaId) : null);
        if (!resultUrl) throw new Error('Flow returned no image url');
        const image = await fetchImageAsBase64(conn.tabId, resultUrl);
        sendResponse({ ok: true, resultDataUrl: image.dataUrl });
      } catch (e) {
        sendResponse({ ok: false, error: e.message });
      }
    })();
    return true;
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

async function loadPersistedState() {
  const stored = await chrome.storage.local.get(['taskHistory', 'logHistory']);
  taskHistory = Array.isArray(stored.taskHistory) ? stored.taskHistory : [];
  logHistory = Array.isArray(stored.logHistory) ? stored.logHistory : [];
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

function addTaskHistory(entry) {
  taskHistory.unshift(entry);
  if (taskHistory.length > MAX_TASK_HISTORY) taskHistory.length = MAX_TASK_HISTORY;
  chrome.storage.local.set({ taskHistory }).catch(() => {});
}

function addLog(level, message) {
  const entry = { level, message, time: Date.now() };
  logHistory.unshift(entry);
  if (logHistory.length > MAX_LOG_HISTORY) logHistory.length = MAX_LOG_HISTORY;
  broadcast({ type: 'BRIDGE_LOG', ...entry });
  persistLogs();
}

function persistLogs() {
  chrome.storage.local.set({ logHistory }).catch(() => {});
}

function scheduleLoop(delayMs) {
  if (timerId) clearTimeout(timerId);
  nextPollAt = Date.now() + delayMs;
  broadcast({ type: 'COUNTDOWN_UPDATE', nextPollAt });
  timerId = setTimeout(() => runLoop().catch((e) => {
    addLog('error', e.message);
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
    addLog(conn.connected ? 'info' : 'warn', `Flow: ${conn.connected ? 'connected' : conn.reason || 'disconnected'}`);

    const orderedServices = rotateServices(services);
    if (orderedServices.length === 0) {
      addLog('warn', 'No enabled services, idle');
      scheduleLoop(IDLE_DELAY_MS);
      return;
    }

    for (let i = 0; i < orderedServices.length; i++) {
      const service = orderedServices[i];
      const task = await pollTask(service, conn);
      if (task?.hasTask) {
        addLog('info', `Task received: ${task.subTaskId} from ${service.baseUrl}`);
        serviceCursor = (serviceCursor + i + 1) % orderedServices.length;
        await executeTask(service, task);
        return;
      }
    }

    addLog('info', `Poll done, no tasks (${orderedServices.length} services)`);
    scheduleLoop(IDLE_DELAY_MS);
  } finally {
    running = false;
  }
}

async function pollTask(service, conn) {
  return postJson(service, '/turboflow-bridge/tasks/poll', {
    bridgeId,
    version: VERSION,
    flowConnected: !!conn.connected,
    projectId: conn.projectId || null,
    currentUrl: null,
    busy: !!currentTask,
  }).catch((e) => {
    addLog('warn', `Poll failed: ${service.baseUrl} ${e.message}`);
    return null;
  });
}

async function executeTask(service, task) {
  const prompt = buildPrompt(task);
  const targetLang = task.targetLanguage || task.targetLanguageCode || 'Simplified Chinese';
  const sourceImage = ensureDataUrl(task.imageBase64);
  const sourceThumb = await createThumbnail(task.imageBase64, 64);
  currentTask = {
    service: service.baseUrl,
    taskId: task.taskId,
    subTaskId: task.subTaskId,
    assignmentId: task.assignmentId,
    startedAt: Date.now(),
    sourceThumb,
    sourceImage,
    targetLang,
    prompt,
  };
  broadcast({ type: 'TASK_CHANGED', currentTask });

  const startedAt = Date.now();
  try {
    const result = await translateImage(task);
    await postJson(service, '/turboflow-bridge/tasks/complete', {
      bridgeId,
      assignmentId: task.assignmentId,
      resultImageBase64: result.resultDataUrl,
      resultMimeType: 'image/png',
      resultUrl: result.resultUrl || null,
      elapsedMs: Date.now() - startedAt,
    });
    const elapsed = Date.now() - startedAt;
    const resultImage = ensureDataUrl(result.resultDataUrl);
    const resultThumb = await createThumbnail(result.resultDataUrl, 64);
    addLog('info', `Task completed: ${task.subTaskId}`);
    addTaskHistory({
      taskId: task.taskId,
      subTaskId: task.subTaskId,
      service: service.baseUrl,
      status: 'completed',
      elapsedMs: elapsed,
      time: Date.now(),
      sourceThumb,
      sourceImage,
      resultThumb,
      resultImage,
      targetLang,
    });
    currentTask = null;
    broadcast({ type: 'TASK_CHANGED', currentTask: null });
    scheduleLoop(SUCCESS_DELAY_MS);
  } catch (e) {
    await postJson(service, '/turboflow-bridge/tasks/fail', {
      bridgeId,
      assignmentId: task.assignmentId,
      errorCode: 'FLOW_EXECUTION_FAILED',
      message: e.message,
      stack: e.stack || null,
      retryable: true,
      elapsedMs: Date.now() - startedAt,
    }).catch(() => {});
    const elapsed = Date.now() - startedAt;
    addLog('error', `Task failed: ${e.message}`);
    addTaskHistory({
      taskId: task.taskId,
      subTaskId: task.subTaskId,
      service: service.baseUrl,
      status: 'failed',
      error: e.message,
      elapsedMs: elapsed,
      time: Date.now(),
      sourceThumb,
      sourceImage,
      targetLang,
    });
    currentTask = null;
    broadcast({ type: 'TASK_CHANGED', currentTask: null });
    scheduleLoop(FAILURE_DELAY_MS);
  }
}

async function createThumbnail(base64OrDataUrl, maxSize) {
  try {
    const dataUrl = base64OrDataUrl.startsWith('data:')
      ? base64OrDataUrl
      : 'data:image/png;base64,' + base64OrDataUrl;
    const res = await fetch(dataUrl);
    const blob = await res.blob();
    const bmp = await createImageBitmap(blob);
    const scale = Math.min(maxSize / bmp.width, maxSize / bmp.height, 1);
    const w = Math.round(bmp.width * scale);
    const h = Math.round(bmp.height * scale);
    const canvas = new OffscreenCanvas(w, h);
    const ctx = canvas.getContext('2d');
    ctx.drawImage(bmp, 0, 0, w, h);
    bmp.close();
    const outBlob = await canvas.convertToBlob({ type: 'image/jpeg', quality: 0.6 });
    const buf = await outBlob.arrayBuffer();
    const bytes = new Uint8Array(buf);
    let binary = '';
    for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
    return 'data:image/jpeg;base64,' + btoa(binary);
  } catch {
    return null;
  }
}

async function translateImage(task) {
  const conn = await checkConnection();
  if (!conn.connected) throw new Error(conn.reason || 'Flow is not connected');

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
    model: sanitizeModel(task.model),
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
always translate it as overlay text.`;
}

const SUPPORTED_MODELS = ['GEM_PIX_2', 'NARWHAL', 'IMAGEN_3_5'];

function sanitizeModel(model) {
  if (model && SUPPORTED_MODELS.includes(model)) return model;
  return 'NARWHAL';
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

function ensureDataUrl(value) {
  if (!value) return null;
  return value.startsWith('data:') ? value : 'data:image/png;base64,' + value;
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

ensureBridgeId().then(() => loadPersistedState()).then(() => scheduleLoop(1000));
