import {
  checkConnection,
  uploadImageToFlow,
  generateWithReference,
  getMediaRedirectUrl,
  fetchImageAsBase64,
  clearTokenCache,
  resetRecoveryState,
} from './flow-api.js';

const VERSION = '1.1.0';
const FLOW_URL = 'https://labs.google/fx/zh/tools/flow/';
const SUCCESS_DELAY_MS = 10000;
const FAILURE_DELAY_MS = 60000;
const IDLE_DELAY_MS = 10000;
const MAX_TASK_HISTORY = 50;
const MAX_LOG_HISTORY = 500;
const DEFAULT_CONCURRENCY = 1;
const MAX_CONCURRENCY = 100;
// 单次翻译总超时（含上传/生成/下载）。Flow 正常约 30~60 秒；网速慢时下载重试可达 120 秒，总预算 180 秒。
const TRANSLATE_TIMEOUT_MS = 180 * 1000;
// fail 上报失败的重试次数与基础间隔（指数退避）。尽量保证 server 端能及时收到失败信号，避免等到 lease 过期。
const FAIL_REPORT_MAX_RETRIES = 3;
const FAIL_REPORT_RETRY_BASE_MS = 1000;

/**
 * 根据 translateImage 抛出的错误信息映射到上报用的 errorCode。
 * 与 flow-api.js 中 callFlowApi 抛出的特定文案保持同步。
 */
function classifyErrorCode(message) {
  const text = (message || '').toLowerCase();
  if (text.includes('recaptcha blocked')) return 'RECAPTCHA_BLOCKED';
  if (text.includes('blocked by google (403)') || text.includes('access denied (403)')) return 'GOOGLE_BLOCKED';
  if (text.includes('timeout') || text.includes('timed out')) return 'TIMEOUT';
  // Flow tab 被关闭/导航走时，in-flight 的 fetch/executeScript 会抛 'Failed to fetch'、'Script execution failed' 等
  if (text.includes('failed to fetch')
    || text.includes('flow is not connected')
    || text.includes('no flow tab')
    || text.includes('flow tab')
    || text.includes('script execution failed')) return 'FLOW_DISCONNECTED';
  return 'FLOW_EXECUTION_FAILED';
}

/**
 * 把错误码翻译成更友好的描述（写日志用），原始 message 仍随 reportFail 上报给服务端。
 */
function friendlyErrorMessage(errorCode, rawMessage) {
  if (errorCode === 'FLOW_DISCONNECTED') return 'Flow tab unavailable (closed / navigated away)';
  return rawMessage;
}

let bridgeId = null;
let running = false;
let currentTasks = [];
let timerId = null;
let serviceCursor = 0;
let lastStatus = { connected: false, message: 'Not checked' };
let nextPollAt = 0;
let taskHistory = [];
let logHistory = [];

// reCAPTCHA 恢复全部失败时进入暂停：停止主动 poll，需用户在 sidepanel 点 Run Now 显式恢复。
let pollPaused = false;
let pauseReason = null;

// Flow 标签页可用性看门狗：每秒探测，标签关闭 → 立即阻断 poll；重新打开 → 自动恢复
const FLOW_URL_RE = /labs\.google\/fx(\/[a-z]{2}(-[a-z]{2})?)?\/tools\/flow/;
const WATCHDOG_INTERVAL_MS = 1000;
let flowTabAvailable = false;
let watchdogTimer = null;

/**
 * 轻量级 Flow tab 存活探测：仅查 chrome.tabs，不调 grecaptcha（避免高频消耗 reCAPTCHA token）。
 * grecaptcha 风控由 runLoop 内的 checkConnection 二次校验。
 */
async function probeFlowTabAvailable() {
  try {
    const tabs = await chrome.tabs.query({ url: 'https://labs.google/fx/*' });
    return tabs.some((t) => t.url && FLOW_URL_RE.test(t.url) && t.status === 'complete');
  } catch {
    return false;
  }
}

/**
 * 启动每秒一次的 Flow tab 看门狗。
 * - 关闭 → 终止 pending timer + 广播 disconnected + scheduleLoop 在此后被短路
 * - 重新打开 → 自动 scheduleLoop(100) 触发完整 checkConnection 流程
 */
function startConnectionWatchdog() {
  if (watchdogTimer) return;
  const tick = async () => {
    const ok = await probeFlowTabAvailable();
    if (ok === flowTabAvailable) return;
    flowTabAvailable = ok;
    if (ok) {
      addLog('info', '✅ Flow tab detected');
      broadcast({ type: 'CONNECTION_CHANGED', connected: false, message: 'Verifying Flow connection...', projectId: null });
      scheduleLoop(100);
    } else {
      const reason = 'Flow tab closed — open Google Flow to resume';
      addLog('warn', '⚠️ ' + reason);
      lastStatus = { connected: false, message: reason };
      if (timerId) {
        clearTimeout(timerId);
        timerId = null;
      }
      nextPollAt = 0;
      broadcast({ type: 'CONNECTION_CHANGED', connected: false, message: reason, projectId: null });
      broadcast({ type: 'COUNTDOWN_UPDATE', nextPollAt: 0 });
    }
  };
  tick();
  watchdogTimer = setInterval(tick, WATCHDOG_INTERVAL_MS);
}

/**
 * 安全调用 chrome.action.* API。
 * MV3 中 chrome.action 仅在 manifest 声明 "action" 字段时存在；某些 chromium 衍生浏览器即便声明了也可能 undefined。
 * 这里统一兜底，确保任何 badge/title 调用不会因 chrome.action 缺失抛 TypeError。
 */
function safeAction(fn) {
  try {
    if (!chrome.action) return;
    const result = fn(chrome.action);
    if (result && typeof result.catch === 'function') {
      result.catch(() => {});
    }
  } catch {
    // ignore
  }
}

/**
 * 进入暂停态：清掉待执行 timer、设置 action badge 强提示、广播状态给 sidepanel。
 * 触发条件：reCAPTCHA 恢复机制穷尽后仍失败（errorCode === RECAPTCHA_BLOCKED）。
 * 幂等：并发场景下多个 in-flight 任务可能同时调用，重复调用时仅刷新 reason，不重复广播日志。
 */
function pausePoll(reason) {
  const alreadyPaused = pollPaused;
  pollPaused = true;
  pauseReason = reason;
  nextPollAt = 0;
  if (timerId) {
    clearTimeout(timerId);
    timerId = null;
  }
  if (alreadyPaused) {
    return;
  }
  safeAction((action) => action.setBadgeText({ text: '!' }));
  safeAction((action) => action.setBadgeBackgroundColor({ color: '#d32f2f' }));
  lastStatus = { connected: false, message: reason };
  broadcast({ type: 'BRIDGE_PAUSED', reason });
  broadcast({ type: 'CONNECTION_CHANGED', connected: false, message: reason, projectId: null });
  broadcast({ type: 'COUNTDOWN_UPDATE', nextPollAt: 0 });
  addLog('error', '⛔ Poll paused: ' + reason);
}

/**
 * 用户显式恢复：清掉暂停状态、清 badge、重置 reCAPTCHA 恢复计数。
 * 由 RUN_NOW 等消息触发。
 */
function resumePoll() {
  if (!pollPaused) return false;
  pollPaused = false;
  pauseReason = null;
  resetRecoveryState();
  safeAction((action) => action.setBadgeText({ text: '' }));
  broadcast({ type: 'BRIDGE_RESUMED' });
  addLog('info', '✅ Poll resumed by user');
  return true;
}

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
  startConnectionWatchdog();
  loadPersistedState().then(() => scheduleLoop(1000));
});

chrome.runtime.onStartup.addListener(() => {
  ensureBridgeId();
  startConnectionWatchdog();
  loadPersistedState().then(() => scheduleLoop(1000));
});

// service worker 唤醒（含模块顶层执行）时也启动一次，覆盖 onInstalled/onStartup 都未触发的场景
startConnectionWatchdog();

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
      const stored = await chrome.storage.local.get(['services', 'concurrency']);
      const count = Array.isArray(stored.services) ? stored.services.length : 0;
      addLog('info', `Config saved (${count} services, concurrency ${stored.concurrency || DEFAULT_CONCURRENCY})`);
      scheduleLoop(1000);
      sendResponse({ ok: true });
    });
    return true;
  }

  if (msg.type === 'GET_STATUS') {
    sendResponse({
      running,
      currentTask: currentTasks[0] || null,
      currentTasks: getCurrentTasks(),
      lastStatus,
      nextPollAt,
      paused: pollPaused,
      pauseReason,
    });
    return false;
  }

  if (msg.type === 'RUN_NOW') {
    // 用户主动触发 → 总是先尝试退出暂停态，再 schedule 一次立即 poll
    resumePoll();
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
  const stored = await chrome.storage.local.get(['services', 'concurrency']);
  return {
    bridgeId,
    services: Array.isArray(stored.services) ? stored.services : [],
    concurrency: sanitizeConcurrency(stored.concurrency),
  };
}

async function saveConfig(config) {
  const concurrency = sanitizeConcurrency(config.concurrency);
  const services = Array.isArray(config.services)
    ? config.services.map((s) => ({
        baseUrl: (s.baseUrl || '').trim(),
        token: (s.token || '').trim(),
        enabled: s.enabled !== false,
      })).filter((s) => s.baseUrl && s.token)
    : [];
  await chrome.storage.local.set({ services, concurrency });
}

function sanitizeConcurrency(value) {
  const n = Number.parseInt(value, 10);
  if (!Number.isFinite(n)) return DEFAULT_CONCURRENCY;
  return Math.min(Math.max(n, 1), MAX_CONCURRENCY);
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
  // 暂停态下不再触发任何 poll，必须等用户点 Run Now 显式恢复
  if (pollPaused) {
    return;
  }
  // Flow tab 不可用时不发起 poll，等 watchdog 检测到重新打开再自动 schedule
  if (!flowTabAvailable) {
    return;
  }
  const newPollAt = Date.now() + delayMs;
  // 如果已有更早的 timer 排队，不重置——避免并发任务陆续完成时反复推迟 poll
  if (timerId && nextPollAt > 0 && nextPollAt <= newPollAt) {
    return;
  }
  if (timerId) clearTimeout(timerId);
  nextPollAt = newPollAt;
  broadcast({ type: 'COUNTDOWN_UPDATE', nextPollAt });
  timerId = setTimeout(() => {
    timerId = null;
    runLoop().catch((e) => {
      addLog('error', e.message);
      scheduleLoop(FAILURE_DELAY_MS);
    });
  }, delayMs);
}

async function runLoop() {
  if (running) return;
  running = true;
  try {
    const config = await loadConfig();
    const concurrency = config.concurrency;
    if (currentTasks.length >= concurrency) {
      nextPollAt = 0;
      broadcast({ type: 'COUNTDOWN_UPDATE', nextPollAt });
      return;
    }

    const services = config.services.filter((s) => s.enabled !== false && s.baseUrl && s.token);
    const conn = await checkConnection().catch((e) => ({ connected: false, reason: e.message }));
    lastStatus = { connected: conn.connected, message: conn.reason || 'Connected', projectId: conn.projectId };
    broadcast({ type: 'CONNECTION_CHANGED', ...lastStatus });
    addLog(conn.connected ? 'info' : 'warn', `Flow: ${conn.connected ? 'connected' : conn.reason || 'disconnected'}`);

    // bridge 自行决定能力：未连接（Flow tab 未就绪 / grecaptcha 不可用 等）就不发起 poll，
    // 由 watchdog 或下一次 scheduleLoop 重新检查；服务端不再做这层筛选，按 FIFO 派发
    if (!conn.connected) {
      scheduleLoop(IDLE_DELAY_MS);
      return;
    }

    const orderedServices = rotateServices(services);
    if (orderedServices.length === 0) {
      addLog('warn', 'No enabled services, idle');
      scheduleLoop(IDLE_DELAY_MS);
      return;
    }

    let received = 0;
    while (currentTasks.length < concurrency) {
      let found = false;
      for (let i = 0; i < orderedServices.length && currentTasks.length < concurrency; i++) {
        const service = orderedServices[i];
        const task = await pollTask(service, conn, concurrency);
        if (task?.hasTask) {
          found = true;
          received++;
          addLog('info', `Task received: ${task.subTaskId} from ${service.baseUrl}`);
          serviceCursor = (serviceCursor + i + 1) % orderedServices.length;
          executeTask(service, task).catch((e) => addLog('error', `Task runner error: ${e.message}`));
        }
      }
      if (!found) break;
    }

    if (received === 0) {
      addLog('info', `Poll done, no tasks (${orderedServices.length} services)`);
    } else {
      addLog('info', `Poll done, received ${received}, running ${currentTasks.length}/${concurrency}`);
    }
    if (currentTasks.length < concurrency) {
      scheduleLoop(IDLE_DELAY_MS);
    } else {
      nextPollAt = 0;
      broadcast({ type: 'COUNTDOWN_UPDATE', nextPollAt });
    }
  } finally {
    running = false;
  }
}

async function pollTask(service, conn, concurrency) {
  return postJson(service, '/turboflow-bridge/tasks/poll', {
    bridgeId,
    version: VERSION,
    flowConnected: !!conn.connected,
    projectId: conn.projectId || null,
    currentUrl: null,
    busy: currentTasks.length >= concurrency,
  }).catch((e) => {
    addLog('warn', `Poll failed: ${service.baseUrl} ${e.message}`);
    return null;
  });
}

async function executeTask(service, task) {
  const prompt = buildPrompt(task);
  const targetLang = task.targetLanguage || task.targetLanguageCode || 'Simplified Chinese';
  const sourceImage = ensureDataUrl(task.imageBase64);
  const taskState = {
    service: service.baseUrl,
    taskId: task.taskId,
    subTaskId: task.subTaskId,
    assignmentId: task.assignmentId,
    startedAt: Date.now(),
    sourceThumb: null,
    sourceImage,
    targetLang,
    prompt,
  };
  currentTasks.push(taskState);
  broadcastTasksChanged();
  const sourceThumb = await createThumbnail(task.imageBase64, 64);
  taskState.sourceThumb = sourceThumb;
  broadcastTasksChanged();

  const startedAt = Date.now();
  try {
    const result = await runWithTimeout(translateImage(task), TRANSLATE_TIMEOUT_MS, 'translate timeout (180s)');
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
    removeCurrentTask(task.assignmentId);
    // 任务成功完成 → 清零 reCAPTCHA 恢复计数，避免上一次偶发风控影响后续任务
    resetRecoveryState();
    scheduleLoop(SUCCESS_DELAY_MS);
  } catch (e) {
    const elapsed = Date.now() - startedAt;
    const errorCode = classifyErrorCode(e.message);
    // retryable 保持 true：服务端 failTask 当前不读 errorCode，沿用原有重试语义；
    // 实际"风控冷却"在插件端通过 scheduleLoop 的差异化延迟实现。
    await reportFailWithRetry(service, {
      bridgeId,
      assignmentId: task.assignmentId,
      errorCode,
      message: e.message,
      stack: e.stack || null,
      retryable: true,
      elapsedMs: elapsed,
    });
    addLog('error', `Task failed [${errorCode}]: ${friendlyErrorMessage(errorCode, e.message)}`);
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
    removeCurrentTask(task.assignmentId);
    // RECAPTCHA_BLOCKED / GOOGLE_BLOCKED 都是 Google 风控，自动重试也会失败 → 暂停 poll 等用户介入；
    // 其它错误（FLOW_DISCONNECTED 由 watchdog 兜底，TIMEOUT 等可能自动恢复）走常规 60s 后重试。
    if (errorCode === 'RECAPTCHA_BLOCKED' || errorCode === 'GOOGLE_BLOCKED') {
      pausePoll('⚠️ Google 风控触发 — 关闭并重开 Flow 标签页后点 Run Now');
    } else {
      scheduleLoop(FAILURE_DELAY_MS);
    }
  }
}

/**
 * 在指定时间内 race 一个 Promise；超时抛 timeoutMessage 错误。
 * 防止 Flow API 异步链路挂起（fetch 默认无 timeout），导致 currentTasks 永不释放。
 */
function runWithTimeout(promise, timeoutMs, timeoutMessage) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(timeoutMessage)), timeoutMs);
    promise.then(
      (value) => { clearTimeout(timer); resolve(value); },
      (err) => { clearTimeout(timer); reject(err); }
    );
  });
}

/**
 * fail 上报带指数退避重试。
 * server 端依赖此通知尽快释放 inFlight 槽位并重新排队，否则要等到 lease 过期才回收。
 */
async function reportFailWithRetry(service, payload) {
  for (let attempt = 0; attempt <= FAIL_REPORT_MAX_RETRIES; attempt++) {
    try {
      await postJson(service, '/turboflow-bridge/tasks/fail', payload);
      if (attempt > 0) {
        addLog('info', `Fail reported on retry ${attempt}: ${payload.assignmentId}`);
      }
      return;
    } catch (err) {
      if (attempt === FAIL_REPORT_MAX_RETRIES) {
        addLog('warn', `Fail report giving up after ${attempt + 1} attempts: ${err.message}`);
        return;
      }
      const backoff = FAIL_REPORT_RETRY_BASE_MS * Math.pow(2, attempt);
      addLog('warn', `Fail report attempt ${attempt + 1} failed (${err.message}), retrying in ${backoff}ms`);
      await sleep(backoff);
    }
  }
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function getCurrentTasks() {
  return currentTasks.map((task) => ({ ...task }));
}

function removeCurrentTask(assignmentId) {
  currentTasks = currentTasks.filter((task) => task.assignmentId !== assignmentId);
  broadcastTasksChanged();
}

function broadcastTasksChanged() {
  broadcast({
    type: 'TASK_CHANGED',
    currentTask: currentTasks[0] || null,
    currentTasks: getCurrentTasks(),
  });
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

  let image;
  let lastErr;
  for (const timeout of [60000, 120000]) {
    try {
      image = await fetchImageAsBase64(conn.tabId, resultUrl, timeout);
      break;
    } catch (e) {
      lastErr = e;
    }
  }
  if (!image) throw lastErr;
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
