import {
  checkConnection,
  uploadImageToFlow,
  generateWithReference,
  getMediaRedirectUrl,
  fetchImageAsBase64,
  clearTokenCache,
  clearProjectIdCache,
  resetRecoveryState,
  getSessionToken,
} from './flow-api.js';

const VERSION = '1.1.0';
const FLOW_URL = 'https://labs.google/fx/zh/tools/flow/';
const POLL_INTERVAL_MS = 500;
const STAGGER_STEP_MS = 250;
const CONCURRENCY = 4;
const TRANSLATE_START_INTERVAL_MS = 20 * 1000;
const PAUSE_STATE_STORAGE_KEY = 'bridgePauseState';
const MAX_TASK_HISTORY = 50;
const MAX_LOG_HISTORY = 500;
// 单次翻译总超时（含上传/生成/下载）。Flow 正常约 30~60 秒；网速慢时下载重试可达 120 秒；
// 4 并发场景下任务内 sleep(250*i) 错峰最多 0.75 秒；总预算放宽到 300 秒。
const TRANSLATE_TIMEOUT_MS = 300 * 1000;
// fail 上报失败的重试次数与基础间隔（指数退避）。尽量保证 server 端能及时收到失败信号，避免等到 lease 过期。
const FAIL_REPORT_MAX_RETRIES = 3;
const FAIL_REPORT_RETRY_BASE_MS = 1000;

/**
 * 根据 translateImage 抛出的错误信息映射到上报用的 errorCode。
 * 与 flow-api.js 中 callFlowApi 抛出的特定文案保持同步。
 */
function classifyErrorCode(message) {
  const text = (message || '').toLowerCase();
  // 每日额度耗尽是账号级硬性限制，重试只会加重风控，需走 pausePoll 等账号自然恢复
  if (text.includes('daily_quota_reached') || text.includes('resource_exhausted')) return 'DAILY_QUOTA_REACHED';
  // reCAPTCHA 风控：包含 callFlowApi 抛出的 'reCAPTCHA blocked' 和 'No reCAPTCHA token' 两种
  if (text.includes('recaptcha blocked') || text.includes('no recaptcha token')) return 'RECAPTCHA_BLOCKED';
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
  if (errorCode === 'DAILY_QUOTA_REACHED') return '⚠️ Google 账号每日额度已用尽，需等几小时自然恢复';
  return rawMessage;
}

let bridgeId = null;
let running = false;
let currentTasks = [];
let timerId = null;
let serviceCursor = 0;
let lastStatus = { connected: false, message: 'Not checked' };
let nextPollAt = 0;
let lastTranslateStartAt = 0;
let taskHistory = [];
let logHistory = [];

// reCAPTCHA 恢复全部失败时进入暂停：停止主动 poll，需用户在 sidepanel 点 Run Now 显式恢复。
let pollPaused = false;
let pauseReason = null;
let pausedAt = 0;
let pauseUntilAt = 0;
let pauseReasonCode = null;
let autoReopenTimer = null;

// 1 小时强制冷静期：Google 账号级风控通常需要小时级冷却。冷静期内主动关闭 Flow tab 让账号完全离线，
// 倒计时结束后自动重开 Flow tab 触发 watchdog 自动恢复 poll。
const PAUSE_MIN_COOLDOWN_MS = 60 * 60 * 1000;
const DAILY_QUOTA_TIME_ZONE = 'America/Los_Angeles';
const DAILY_QUOTA_RESUME_BUFFER_MS = 5 * 60 * 1000;

// Flow 标签页可用性看门狗：每秒探测，标签关闭 → 立即阻断 poll；重新打开 → 自动恢复
const FLOW_URL_RE = /labs\.google\/fx(\/[a-z]{2}(-[a-z]{2})?)?\/tools\/flow/;
const WATCHDOG_INTERVAL_MS = 1000;
let flowTabAvailable = false;
let watchdogTimer = null;

/**
 * 轻量级 Flow tab 存活探测：仅查 chrome.tabs，不调 grecaptcha。
 * grecaptcha 风控由 callFlowApi 内部 1 次 token 申请 + 403 路径的三层恢复兜底，
 * 对齐 nano-b：每任务仅消耗 1 次 reCAPTCHA token。
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
 * - 重新打开 → 尝试 resumePoll（受冷却限制）+ scheduleLoop(100) 触发完整 checkConnection 流程
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
      // 暂停态下尝试自动恢复：30 分钟冷却内 resumePoll 会返回 false 并写 warn 日志，提醒用户继续等待
      if (pollPaused) {
        resumePoll(false);
      }
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
 * 关闭所有 Flow 标签页：冷静期内让 Google 完全看不到该用户的扩展活动。
 */
async function closeFlowTabs() {
  try {
    const tabs = await chrome.tabs.query({ url: 'https://labs.google/fx/*' });
    const targets = tabs.filter((t) => t.url && FLOW_URL_RE.test(t.url));
    for (const t of targets) {
      if (t.id) {
        await chrome.tabs.remove(t.id).catch(() => {});
      }
    }
    if (targets.length > 0) {
      addLog('warn', `🔌 已关闭 ${targets.length} 个 Flow 标签页（冷静期）`);
    }
  } catch (e) {
    addLog('warn', 'closeFlowTabs failed: ' + (e.message || e));
  }
}

/**
 * 计划在冷静期结束时主动重开 Flow 标签页。
 * watchdog 检测到新 tab 后会自动尝试 resumePoll（此时 cooldown 已过，可成功）。
 */
function getTimeZoneParts(date, timeZone) {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(date);
  const values = Object.fromEntries(parts.filter((p) => p.type !== 'literal').map((p) => [p.type, Number(p.value)]));
  return {
    year: values.year,
    month: values.month,
    day: values.day,
    hour: values.hour,
    minute: values.minute,
    second: values.second,
  };
}

function getTimeZoneOffsetMs(date, timeZone) {
  const p = getTimeZoneParts(date, timeZone);
  const localAsUtc = Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute, p.second);
  return localAsUtc - date.getTime();
}

function zonedTimeToUtcMs(year, month, day, hour, minute, second, timeZone) {
  const localAsUtc = Date.UTC(year, month - 1, day, hour, minute, second);
  let utc = localAsUtc;
  for (let i = 0; i < 3; i++) {
    utc = localAsUtc - getTimeZoneOffsetMs(new Date(utc), timeZone);
  }
  return utc;
}

function getNextDailyQuotaResumeAt(nowMs = Date.now()) {
  const nowPacific = getTimeZoneParts(new Date(nowMs), DAILY_QUOTA_TIME_ZONE);
  const nextPacificDate = new Date(Date.UTC(nowPacific.year, nowPacific.month - 1, nowPacific.day + 1));
  return zonedTimeToUtcMs(
    nextPacificDate.getUTCFullYear(),
    nextPacificDate.getUTCMonth() + 1,
    nextPacificDate.getUTCDate(),
    0,
    0,
    0,
    DAILY_QUOTA_TIME_ZONE
  ) + DAILY_QUOTA_RESUME_BUFFER_MS;
}

function getPauseUntilAt() {
  return pauseUntilAt || (pausedAt + PAUSE_MIN_COOLDOWN_MS);
}

function getPauseRemainingMs() {
  return pollPaused ? Math.max(0, getPauseUntilAt() - Date.now()) : 0;
}

function persistPauseState() {
  chrome.storage.local.set({
    [PAUSE_STATE_STORAGE_KEY]: {
      pollPaused,
      pauseReason,
      pausedAt,
      pauseUntilAt: getPauseUntilAt(),
      pauseReasonCode,
    },
  }).catch(() => {});
}

function clearPauseState() {
  chrome.storage.local.remove([PAUSE_STATE_STORAGE_KEY]).catch(() => {});
}

function scheduleAutoReopenFlow() {
  if (autoReopenTimer) {
    clearTimeout(autoReopenTimer);
    autoReopenTimer = null;
  }
  const remaining = getPauseRemainingMs();
  if (remaining <= 0) {
    autoReopenFlow();
    return;
  }
  autoReopenTimer = setTimeout(autoReopenFlow, remaining);
  const minutes = Math.ceil(remaining / 60000);
  addLog('info', `⏱️ ${minutes} 分钟后将自动重开 Flow 标签页并恢复 poll`);
}

async function autoReopenFlow() {
  autoReopenTimer = null;
  if (!pollPaused) return;
  addLog('info', '⏱️ 冷静期结束，主动打开 Flow 标签页');
  try {
    await chrome.tabs.create({ url: FLOW_URL });
  } catch (e) {
    addLog('warn', 'autoReopenFlow failed: ' + (e.message || e));
  }
}

/**
 * 进入暂停态：清掉待执行 timer、关闭 Flow tab、设置 action badge 强提示、广播状态给 sidepanel。
 * 触发条件：reCAPTCHA 恢复机制穷尽后仍失败（errorCode === RECAPTCHA_BLOCKED / GOOGLE_BLOCKED）。
 * 幂等：并发场景下多个 in-flight 任务可能同时调用，重复调用时仅刷新 reason，不重复广播日志。
 */
function pausePoll(reason, options = {}) {
  const alreadyPaused = pollPaused;
  const now = Date.now();
  const requestedPauseUntilAt = options.untilAt || (now + PAUSE_MIN_COOLDOWN_MS);
  pollPaused = true;
  pauseReason = reason;
  pauseReasonCode = options.code || pauseReasonCode || null;
  pauseUntilAt = Math.max(pauseUntilAt || 0, requestedPauseUntilAt);
  nextPollAt = 0;
  if (timerId) {
    clearTimeout(timerId);
    timerId = null;
  }
  if (alreadyPaused) {
    persistPauseState();
    scheduleAutoReopenFlow();
    return;
  }
  pausedAt = now;
  safeAction((action) => action.setBadgeText({ text: '!' }));
  safeAction((action) => action.setBadgeBackgroundColor({ color: '#d32f2f' }));
  lastStatus = { connected: false, message: reason };
  broadcast({ type: 'BRIDGE_PAUSED', reason });
  broadcast({ type: 'CONNECTION_CHANGED', connected: false, message: reason, projectId: null });
  broadcast({ type: 'COUNTDOWN_UPDATE', nextPollAt: 0 });
  addLog('error', '⛔ Poll paused: ' + reason);
  // 1 小时硬冷静期：关闭 Flow tab + 计划自动重开
  persistPauseState();
  closeFlowTabs();
  scheduleAutoReopenFlow();
}

/**
 * 恢复 poll。
 * - force=false：受 PAUSE_MIN_COOLDOWN_MS 限制，避免短时反复 Run Now 加重 Google 风控
 * - force=true：强制恢复（例如用户在 sidepanel 显式确认后）
 * 返回是否真的恢复成功。
 */
function resumePoll(force = false) {
  if (!pollPaused) return false;
  const remainingMs = getPauseRemainingMs();
  if (remainingMs > 0 && (pauseReasonCode === 'DAILY_QUOTA_REACHED' || !force)) {
    const remainingMin = Math.ceil(remainingMs / 60000);
    addLog('warn',
      `⏳ Google 风控冷静期中：还需 ${remainingMin} 分钟。短时强制恢复可能加重风控甚至触发临时封禁。`);
    return false;
  }
  pollPaused = false;
  pauseReason = null;
  pausedAt = 0;
  pauseUntilAt = 0;
  pauseReasonCode = null;
  if (autoReopenTimer) {
    clearTimeout(autoReopenTimer);
    autoReopenTimer = null;
  }
  clearPauseState();
  resetRecoveryState();
  safeAction((action) => action.setBadgeText({ text: '' }));
  broadcast({ type: 'BRIDGE_RESUMED' });
  addLog('info', force ? '✅ Poll force-resumed by user' : '✅ Poll auto-resumed after cooldown');
  // 冷静期内 Flow tab 已被 closeFlowTabs 关闭；恢复时主动打开，watchdog 会接管后续
  ensureFlowTabOpen();
  return true;
}

/**
 * 如果当前没有可用的 Flow tab（如冷静期内被关闭），主动打开一个。
 */
async function ensureFlowTabOpen() {
  try {
    const ok = await probeFlowTabAvailable();
    if (!ok) {
      await chrome.tabs.create({ url: FLOW_URL });
      addLog('info', '🔄 已打开 Flow 标签页');
    }
  } catch (e) {
    addLog('warn', 'ensureFlowTabOpen failed: ' + (e.message || e));
  }
}

chrome.sidePanel.setPanelBehavior({ openPanelOnActionClick: true }).catch(() => {});

chrome.tabs.onUpdated.addListener((tabId, _changeInfo, tab) => {
  if (tab.url && /labs\.google\/fx/.test(tab.url)) {
    chrome.sidePanel.setOptions({ tabId, path: 'sidepanel.html', enabled: true }).catch(() => {});
  }
});

chrome.tabs.onRemoved.addListener(() => {
  clearTokenCache();
  clearProjectIdCache();
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
      const stored = await chrome.storage.local.get(['services']);
      const count = Array.isArray(stored.services) ? stored.services.length : 0;
      addLog('info', `Config saved (${count} services)`);
      scheduleLoop(1000);
      sendResponse({ ok: true });
    });
    return true;
  }

  if (msg.type === 'GET_STATUS') {
    const cooldownRemainingMs = getPauseRemainingMs();
    sendResponse({
      running,
      currentTask: currentTasks[0] || null,
      currentTasks: getCurrentTasks(),
      lastStatus,
      nextPollAt,
      paused: pollPaused,
      pauseReason,
      pauseReasonCode,
      pausedAt: pollPaused ? pausedAt : 0,
      pauseUntilAt: pollPaused ? getPauseUntilAt() : 0,
      cooldownRemainingMs,
    });
    return false;
  }

  if (msg.type === 'RUN_NOW') {
    // 用户主动触发：尊重冷却时间，msg.force=true 时强制（即用户在 confirm 对话框中明确确认）
    const wasPaused = pollPaused;
    const resumed = resumePoll(msg.force === true);
    if (wasPaused && !resumed) {
      const remainingMs = getPauseRemainingMs();
      sendResponse({
        ok: false,
        requireConfirm: pauseReasonCode !== 'DAILY_QUOTA_REACHED',
        remainingMs,
        pauseReason,
        pauseReasonCode,
      });
      return false;
    }
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

  if (msg.action === 'executeInMainWorld') {
    const tabId = _sender.tab?.id || msg.tabId;
    if (!tabId) {
      sendResponse({ success: false, error: 'No tab ID' });
      return true;
    }
    chrome.scripting.executeScript({
      target: { tabId },
      world: 'MAIN',
      func: (funcBody, args) => (0, eval)(`(function(...args) { ${funcBody} })`)(...(args || [])),
      args: [msg.funcBody, msg.args || []],
    }).then((results) => {
      sendResponse({ success: true, result: results?.[0]?.result });
    }).catch((error) => {
      sendResponse({ success: false, error: error.message });
    });
    return true;
  }

  if (msg.type === 'TEST_TRANSLATE') {
    (async () => {
      try {
        const conn = await checkConnection();
        if (!conn.connected) throw new Error(conn.reason || 'Flow is not connected');
        const token = await getSessionToken(conn.tabId);
        if (!token) throw new Error('Failed to get Flow session token');
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

  if (msg.type === 'TEST_TRANSLATE_DOM') {
    (async () => {
      try {
        const conn = await checkConnection();
        if (!conn.connected) throw new Error(conn.reason || 'Flow is not connected');
        const prompt = msg.prompt || buildPrompt({
          targetLanguage: msg.targetLanguage || 'Simplified Chinese',
        });
        const aspectRatio = msg.aspectRatio === 'auto'
          ? aspectRatioFor(msg.width, msg.height)
          : msg.aspectRatio;

        await chrome.scripting.executeScript({
          target: { tabId: conn.tabId },
          files: ['flow-dom-method.js'],
          world: 'ISOLATED',
        });

        const result = await chrome.tabs.sendMessage(conn.tabId, {
          type: 'RUN_DOM_TRANSLATE_V3',
          task: {
            imageBase64: ensureDataUrl(msg.imageBase64),
            fileName: msg.fileName || 'test.png',
            mimeType: msg.mimeType || 'image/png',
            aspectRatio,
            model: sanitizeModel(msg.model),
            stealthMode: msg.stealthMode !== false,
            delayMin: Number(msg.delayMin || 0),
            delayMax: Number(msg.delayMax || 0),
            prompt,
          },
        });

        if (!result?.ok) throw new Error(result?.error || 'DOM translate failed');
        let resultDataUrl = result.resultDataUrl || null;
        if (!resultDataUrl && result.resultUrl) {
          const image = await fetchImageAsBase64(conn.tabId, result.resultUrl);
          resultDataUrl = image.dataUrl;
        }
        if (!resultDataUrl) throw new Error('DOM translate completed but no readable result image was returned');
        if (msg.autoClearCache) {
          await clearFlowPageCache(conn.tabId);
        }
        sendResponse({ ok: true, resultDataUrl, resultUrl: result.resultUrl || null });
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
  const stored = await chrome.storage.local.get(['taskHistory', 'logHistory', PAUSE_STATE_STORAGE_KEY]);
  taskHistory = Array.isArray(stored.taskHistory) ? stored.taskHistory : [];
  logHistory = Array.isArray(stored.logHistory) ? stored.logHistory : [];
  const pauseState = stored[PAUSE_STATE_STORAGE_KEY];
  if (pauseState?.pollPaused && pauseState.pauseUntilAt > Date.now()) {
    pollPaused = true;
    pauseReason = pauseState.pauseReason || 'Poll paused';
    pausedAt = pauseState.pausedAt || Date.now();
    pauseUntilAt = pauseState.pauseUntilAt;
    pauseReasonCode = pauseState.pauseReasonCode || null;
    lastStatus = { connected: false, message: pauseReason };
    scheduleAutoReopenFlow();
  } else if (pauseState) {
    clearPauseState();
  }
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
      scheduleLoop(POLL_INTERVAL_MS);
    });
  }, delayMs);
}

async function runLoop() {
  if (running) return;
  running = true;
  let scheduleNext = true;
  try {
    if (currentTasks.length >= CONCURRENCY) {
      nextPollAt = 0;
      broadcast({ type: 'COUNTDOWN_UPDATE', nextPollAt });
      scheduleNext = false;
      return;
    }

    const waitForTranslateSlot = lastTranslateStartAt + TRANSLATE_START_INTERVAL_MS - Date.now();
    if (waitForTranslateSlot > 0) {
      scheduleLoop(waitForTranslateSlot);
      scheduleNext = false;
      return;
    }

    const config = await loadConfig();
    const services = config.services.filter((s) => s.enabled !== false && s.baseUrl && s.token);
    const conn = await checkConnection().catch((e) => ({ connected: false, reason: e.message }));
    lastStatus = { connected: conn.connected, message: conn.reason || 'Connected', projectId: conn.projectId };
    broadcast({ type: 'CONNECTION_CHANGED', ...lastStatus });

    if (!conn.connected) {
      return;
    }
    if (services.length === 0) {
      return;
    }

    // 单次 tick 至多启动 1 个任务（对齐 nano-b lt 调度器每 tick 至多 g() 一次）
    const orderedServices = rotateServices(services);
    for (let i = 0; i < orderedServices.length; i++) {
      const service = orderedServices[i];
      const task = await pollTask(service, conn, CONCURRENCY);
      if (task?.hasTask) {
        const staggerIndex = currentTasks.length; // 当前已占槽位 0..3，对齐 nano-b 的 250*i 错峰
        addLog('info', `Task received: ${task.subTaskId} from ${service.baseUrl}`);
        serviceCursor = (serviceCursor + i + 1) % orderedServices.length;
        lastTranslateStartAt = Date.now();
        executeTask(service, task, staggerIndex, conn).catch((e) => addLog('error', `Task runner error: ${e.message}`));
        break;
      }
    }
  } finally {
    running = false;
    if (scheduleNext) {
      scheduleLoop(POLL_INTERVAL_MS);
    }
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

async function executeTask(service, task, staggerIndex = 0, conn = null) {
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

  // 对齐 nano-b 任务内 await ae(250 * i) 错峰
  if (staggerIndex > 0) {
    await sleep(STAGGER_STEP_MS * staggerIndex);
  }

  const startedAt = Date.now();
  try {
    const result = await runWithTimeout(translateImage(task, conn), TRANSLATE_TIMEOUT_MS, 'translate timeout (180s)');
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
    // 不再在每任务成功后清零 reCAPTCHA 恢复计数：对齐 nano-b 仅在批次开始/resumePoll 时清零的粒度，
    // 避免反复风控期间恢复预算被打穿（每次成功就回血 3 次，会让总恢复尝试远超 MAX_TOTAL_RECOVERY）。
    scheduleLoop(POLL_INTERVAL_MS);
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
    // RECAPTCHA_BLOCKED / GOOGLE_BLOCKED / DAILY_QUOTA_REACHED 都是账号级问题，自动重试无意义 → 暂停 poll；
    // 其它错误（FLOW_DISCONNECTED 由 watchdog 兜底，TIMEOUT 等可能自动恢复）走 500ms 后重试。
    if (errorCode === 'RECAPTCHA_BLOCKED' || errorCode === 'GOOGLE_BLOCKED') {
      pausePoll('⚠️ Google 风控触发 — 关闭并重开 Flow 标签页后点 Run Now');
    } else if (errorCode === 'DAILY_QUOTA_REACHED') {
      pausePoll('Google daily quota reached; paused until the next Pacific Time quota window', {
        code: 'DAILY_QUOTA_REACHED',
        untilAt: getNextDailyQuotaResumeAt(),
      });
    } else {
      scheduleLoop(POLL_INTERVAL_MS);
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

async function translateImage(task, conn) {
  // 由调用方（executeTask）传入已 check 过的 conn，避免重复触发 grecaptcha/getSessionToken。
  if (!conn || !conn.tabId || !conn.projectId) throw new Error('Flow is not connected');

  const token = await getSessionToken(conn.tabId);
  if (!token) throw new Error('Failed to get Flow session token');
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

async function clearFlowPageCache(tabId) {
  clearTokenCache();
  clearProjectIdCache();
  await chrome.scripting.executeScript({
    target: { tabId },
    world: 'MAIN',
    func: async () => {
      if (window.caches) {
        const keys = await caches.keys();
        await Promise.all(keys.map((key) => caches.delete(key)));
      }
      try { sessionStorage.clear(); } catch {}
      try { localStorage.clear(); } catch {}
      try {
        if (indexedDB?.databases) {
          const dbs = await indexedDB.databases();
          for (const db of dbs) {
            if (db.name) indexedDB.deleteDatabase(db.name);
          }
        }
      } catch {}
      return true;
    },
  });
  await chrome.tabs.reload(tabId, { bypassCache: true });
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
