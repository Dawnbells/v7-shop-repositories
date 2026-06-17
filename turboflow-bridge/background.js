import {
  checkConnection,
  uploadImageToFlow,
  generateWithReference,
  getMediaRedirectUrl,
  fetchImageAsBase64,
  clearTokenCache,
  clearProjectIdCache,
  getSessionToken,
  runRecoveryChain,
  deleteAllUserProjects,
} from './flow-api.js';

const VERSION = '1.1.0';
const FLOW_URL = 'https://labs.google/fx/zh/tools/flow/';
const POLL_INTERVAL_MS = 500;
const STAGGER_STEP_MS = 250;
const CONCURRENCY = 4;
// 两次「启动翻译任务」之间的最小间隔，每次启动时在 [MIN, MAX] 内随机摇定一个下次允许时间戳
// （见 nextTranslateAllowedAt）。随机化对齐本扩展整体的反风控风格，避免固定节拍被识别。
const TRANSLATE_START_INTERVAL_MIN_MS = 2 * 1000;
const TRANSLATE_START_INTERVAL_MAX_MS = 5 * 1000;
const STOP_STATE_STORAGE_KEY = 'bridgeStopState';
const RECOVERY_STATE_STORAGE_KEY = 'bridgeRecoveryState';
const RECOVERY_SUCCESS_THRESHOLD = 20;
const RECOVERY_DOWNLOAD_FAIL_THRESHOLD = 3;
const LEGACY_PAUSE_STATE_STORAGE_KEY = 'bridgePauseState';
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
 * 与 flow-api.js 中 callFlowApi / fetchImageAsBase64 抛出的特定文案保持同步。
 */
function classifyErrorCode(message) {
  const text = (message || '').toLowerCase();
  // 每日额度耗尽是账号级硬性限制 — 走 stopAndDelete 终态，删 project 等账号自然恢复
  if (text.includes('daily_quota_reached') || text.includes('resource_exhausted')) return 'DAILY_QUOTA_REACHED';
  // 下载结果图失败（fetchImageAsBase64 全部重试都失败）— 连续 3 张触发 L1 恢复
  if (text.includes('[download_failed]')) return 'DOWNLOAD_FAILED';
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
// 启动任务时摇定的「下次允许启动翻译」时间戳（now + random(2~5s)）；runLoop 只跟这个固定值比较，
// 避免每个 poll tick 重新摇随机导致阈值乱跳。
let nextTranslateAllowedAt = 0;
let taskHistory = [];
let logHistory = [];

// "已停止"终态：触发条件是 L2 后仍 reCAPTCHA / 日限额 / 其它终态错误。
// 不进 1 小时冷静期、不自动重开 Flow tab，必须用户在 sidepanel 点 Run Now 显式恢复。
// 复用 pollPaused 变量名以避免改 sidepanel 的 BRIDGE_PAUSED / BRIDGE_RESUMED 事件协议。
let pollPaused = false;
let pauseReason = null;
let pausedAt = 0;
let pauseReasonCode = null;

// reCAPTCHA 双档恢复状态机（持久化到 chrome.storage.local，service worker 重启后保留）：
//   successSinceLastRecovery   自上次 L1/L2 触发以来 translateImage 全流程成功的图片数
//   consecutiveDownloadFails   连续 [DOWNLOAD_FAILED] 计数；达 RECOVERY_DOWNLOAD_FAIL_THRESHOLD 触发 L1
//   lastRecoveryLevel          'NONE' | 'L1' | 'L2'；决定下次 reCAPTCHA 是 L1 还是升 L2
let recoveryState = {
  successSinceLastRecovery: 0,
  consecutiveDownloadFails: 0,
  lastRecoveryLevel: 'NONE',
};
// 恢复链运行中的 Promise 门闩：scheduleLoop 在 pending 时不发起新 poll，
// 4 并发场景下避免一边 reload 一边新 poll 拉 task 撞上半残页面。
let recoveryPromise = null;

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
      // 已停止终态下不再自动恢复 — 用户必须显式点 Run Now，避免日限额/L2 后风控未消时被动撞墙
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

// ── recoveryState 持久化 ──────────────────────────────────────────

function persistRecoveryState() {
  chrome.storage.local.set({ [RECOVERY_STATE_STORAGE_KEY]: recoveryState }).catch(() => {});
}

async function loadRecoveryState() {
  const stored = await chrome.storage.local.get([RECOVERY_STATE_STORAGE_KEY]);
  const s = stored[RECOVERY_STATE_STORAGE_KEY];
  if (s && typeof s === 'object') {
    recoveryState = {
      successSinceLastRecovery: Number(s.successSinceLastRecovery) || 0,
      consecutiveDownloadFails: Number(s.consecutiveDownloadFails) || 0,
      lastRecoveryLevel: ['NONE', 'L1', 'L2'].includes(s.lastRecoveryLevel) ? s.lastRecoveryLevel : 'NONE',
    };
  }
}

function resetRecoveryStateAll() {
  recoveryState = {
    successSinceLastRecovery: 0,
    consecutiveDownloadFails: 0,
    lastRecoveryLevel: 'NONE',
  };
  persistRecoveryState();
}

// ── 已停止终态 + 用户显式恢复 ─────────────────────────────────────

function persistStopState() {
  chrome.storage.local.set({
    [STOP_STATE_STORAGE_KEY]: { pollPaused, pauseReason, pausedAt, pauseReasonCode },
  }).catch(() => {});
}

function clearStopState() {
  chrome.storage.local.remove([STOP_STATE_STORAGE_KEY]).catch(() => {});
}

/**
 * 进入"已停止"终态：停 poll、写 badge、广播给 sidepanel。
 * 与旧版冷静期不同 — 不关 Flow tab、不计划自动重开、不限制 Run Now 时机。
 * 触发条件：日限额、L2 后仍 reCAPTCHA、其它终态错误。复用 BRIDGE_PAUSED 事件名以兼容 sidepanel。
 * 实际"删除所有 project"动作由 stopAndDelete 包装这个函数。
 */
function pausePoll(reason, options = {}) {
  const alreadyPaused = pollPaused;
  pollPaused = true;
  pauseReason = reason;
  pauseReasonCode = options.code || pauseReasonCode || null;
  nextPollAt = 0;
  if (timerId) {
    clearTimeout(timerId);
    timerId = null;
  }
  if (alreadyPaused) {
    persistStopState();
    return;
  }
  pausedAt = Date.now();
  safeAction((action) => action.setBadgeText({ text: '!' }));
  safeAction((action) => action.setBadgeBackgroundColor({ color: '#d32f2f' }));
  lastStatus = { connected: false, message: reason };
  broadcast({ type: 'BRIDGE_PAUSED', reason });
  broadcast({ type: 'CONNECTION_CHANGED', connected: false, message: reason, projectId: null });
  broadcast({ type: 'COUNTDOWN_UPDATE', nextPollAt: 0 });
  addLog('error', '⛔ Poll stopped: ' + reason);
  persistStopState();
}

/**
 * 用户在 sidepanel 显式点 Run Now 恢复（已无冷却限制，force 参数保留只为兼容旧 RUN_NOW 协议）。
 * 同时把 recoveryState 全部清零，让恢复后的批次从干净状态开始。
 */
function resumePoll(_force = false) {
  if (!pollPaused) return false;
  pollPaused = false;
  pauseReason = null;
  pausedAt = 0;
  pauseReasonCode = null;
  clearStopState();
  resetRecoveryStateAll();
  safeAction((action) => action.setBadgeText({ text: '' }));
  broadcast({ type: 'BRIDGE_RESUMED' });
  addLog('info', '✅ Poll resumed by user');
  return true;
}

// ── reCAPTCHA 恢复 + 终态停止 + 删除所有 project ───────────────────

/**
 * 决定下次 reCAPTCHA 应走 L1 还是 L2：
 *   首次（lastRecoveryLevel='NONE'）→ L1
 *   上次 L1 后连续生成 >= 20 张才再次踩到 → 仍按"L1 见效"评估，重新做 L1
 *   上次 L1 后不到 20 张就再踩到 → 升级 L2（多清 _GRECAPTCHA cookie）
 *   上次 L2 后仍 reCAPTCHA → 调用方应直接 stopAndDelete，不再调本函数
 */
function decideRecoveryLevel() {
  if (recoveryState.lastRecoveryLevel === 'NONE') return 'L1';
  if (recoveryState.lastRecoveryLevel === 'L1') {
    return recoveryState.successSinceLastRecovery >= RECOVERY_SUCCESS_THRESHOLD ? 'L1' : 'L2';
  }
  return 'L2';
}

/**
 * 触发一次恢复链。设置 recoveryPromise 门闩阻止 scheduleLoop 发起新 poll；
 * 成功 → 更新 lastRecoveryLevel + 计数归零 + 恢复 poll；
 * 失败 → 调 stopAndDelete 进入终态。
 * 并发安全：多个 in-flight task 同时抛 reCAPTCHA 时，第二个之后的调用 await 同一个 Promise，不会重复触发恢复链。
 */
async function triggerRecovery(level) {
  if (recoveryPromise) return recoveryPromise;
  if (pollPaused) return false;
  recoveryPromise = (async () => {
    const conn = await checkConnection().catch(() => null);
    const tabId = conn?.tabId;
    if (!tabId) {
      addLog('error', `⛔ Recovery ${level} failed: no Flow tab`);
      await stopAndDelete(`Recovery ${level} failed: no Flow tab`, { code: 'RECOVERY_FAILED' });
      return false;
    }
    addLog('warn', `🔄 Recovery ${level} starting — clearing storage${level === 'L2' ? ' + _GRECAPTCHA cookie' : ''}, reloading, creating new project`);
    broadcast({ type: 'CONNECTION_CHANGED', connected: false, message: `Recovery ${level} in progress…`, projectId: null });
    try {
      const newProjectId = await runRecoveryChain(tabId, level);
      recoveryState.lastRecoveryLevel = level;
      recoveryState.successSinceLastRecovery = 0;
      recoveryState.consecutiveDownloadFails = 0;
      persistRecoveryState();
      addLog('info', `✅ Recovery ${level} succeeded — new project ${newProjectId}`);
      return true;
    } catch (e) {
      addLog('error', `⛔ Recovery ${level} failed: ${e.message}`);
      // L1 失败 → 升级 L2；L2 失败 → stopAndDelete（按方案 A 的兜底）
      if (level === 'L1') {
        recoveryState.lastRecoveryLevel = 'L1';
        persistRecoveryState();
        return await triggerRecoveryInner('L2');
      }
      await stopAndDelete(`Recovery L2 failed: ${e.message}`, { code: 'RECOVERY_FAILED' });
      return false;
    }
  })();
  try {
    const ok = await recoveryPromise;
    if (ok) {
      // 恢复成功 — 立即排一次 poll 拉新 task
      scheduleLoop(100);
    }
    return ok;
  } finally {
    recoveryPromise = null;
  }
}

/**
 * triggerRecovery 内部的升档实现：不再设置 recoveryPromise（外层已设），直接跑链。
 */
async function triggerRecoveryInner(level) {
  const conn = await checkConnection().catch(() => null);
  const tabId = conn?.tabId;
  if (!tabId) {
    await stopAndDelete(`Recovery ${level} failed: no Flow tab`, { code: 'RECOVERY_FAILED' });
    return false;
  }
  addLog('warn', `🔄 Recovery ${level} starting (escalated)`);
  try {
    const newProjectId = await runRecoveryChain(tabId, level);
    recoveryState.lastRecoveryLevel = level;
    recoveryState.successSinceLastRecovery = 0;
    recoveryState.consecutiveDownloadFails = 0;
    persistRecoveryState();
    addLog('info', `✅ Recovery ${level} succeeded — new project ${newProjectId}`);
    return true;
  } catch (e) {
    addLog('error', `⛔ Recovery ${level} failed: ${e.message}`);
    await stopAndDelete(`Recovery ${level} failed: ${e.message}`, { code: 'RECOVERY_FAILED' });
    return false;
  }
}

/**
 * 终态停止：先 pausePoll 进入"已停止"，再列举并删除账号下所有 project（fire-and-forget）。
 * 删除进度通过 BRIDGE_LOG 上报到 sidepanel；不阻塞 stop 状态切换。
 * 幂等：多个 in-flight 任务并发触发终态时只删一轮 project。
 */
let deletingProjects = false;
async function stopAndDelete(reason, options = {}) {
  const wasAlreadyPaused = pollPaused;
  pausePoll(reason, options);
  if (wasAlreadyPaused || deletingProjects) {
    // 已经在停止态或正在删除中 — 不重复发起 deleteAllUserProjects
    return;
  }
  deletingProjects = true;
  try {
    const conn = await checkConnection().catch(() => null);
    const tabId = conn?.tabId;
    if (!tabId) {
      addLog('warn', '⚠️ 无可用 Flow tab — 跳过删除 project 步骤');
      return;
    }
    addLog('info', '🧹 开始列举并删除账号下所有 project');
    await deleteAllUserProjects(tabId, (progress) => {
      if (progress.phase === 'list-failed') {
        addLog('error', `🧹 列举 project 失败: ${progress.error}`);
      } else if (progress.phase === 'start') {
        addLog('info', `🧹 共 ${progress.total} 个 project 待删除`);
      } else if (progress.phase === 'progress' && (progress.current % 10 === 0 || progress.current === progress.total)) {
        addLog('info', `🧹 删除中: ${progress.current}/${progress.total}（成功 ${progress.deleted} / 失败 ${progress.failed}）`);
      } else if (progress.phase === 'done') {
        addLog('info', `🧹 删除完成: 成功 ${progress.deleted} / 失败 ${progress.failed} / 总计 ${progress.total}`);
      }
    });
  } catch (e) {
    addLog('error', `🧹 删除 project 失败: ${e.message}`);
  } finally {
    deletingProjects = false;
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
    // 已停止终态无冷却倒计时：cooldownRemainingMs / pauseUntilAt 始终为 0，
    // sidepanel 的 "Run Now (X min)" 标签会自动退化为 "Run Now"
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
      pauseUntilAt: 0,
      cooldownRemainingMs: 0,
      recoveryState,
    });
    return false;
  }

  if (msg.type === 'RUN_NOW') {
    // 用户主动触发：终态停止下点击即恢复（已无冷却限制）；force 字段仍接受以兼容旧 sidepanel
    resumePoll(msg.force === true);
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
  const stored = await chrome.storage.local.get([
    'taskHistory',
    'logHistory',
    STOP_STATE_STORAGE_KEY,
    LEGACY_PAUSE_STATE_STORAGE_KEY,
  ]);
  taskHistory = Array.isArray(stored.taskHistory) ? stored.taskHistory : [];
  logHistory = Array.isArray(stored.logHistory) ? stored.logHistory : [];
  // 旧版冷静期遗留 state：直接清掉，新方案不再使用
  if (stored[LEGACY_PAUSE_STATE_STORAGE_KEY]) {
    chrome.storage.local.remove([LEGACY_PAUSE_STATE_STORAGE_KEY]).catch(() => {});
  }
  const stopState = stored[STOP_STATE_STORAGE_KEY];
  if (stopState?.pollPaused) {
    pollPaused = true;
    pauseReason = stopState.pauseReason || 'Poll stopped';
    pausedAt = stopState.pausedAt || Date.now();
    pauseReasonCode = stopState.pauseReasonCode || null;
    lastStatus = { connected: false, message: pauseReason };
    safeAction((action) => action.setBadgeText({ text: '!' }));
    safeAction((action) => action.setBadgeBackgroundColor({ color: '#d32f2f' }));
  }
  await loadRecoveryState();
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
  // reCAPTCHA 恢复链运行中（reload + 建 project + settle）— 不发起新 poll，避免撞上半残 Flow tab
  if (recoveryPromise) {
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

    const waitForTranslateSlot = nextTranslateAllowedAt - Date.now();
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
        // 启动这一刻摇定下次允许时间：2~5s 均匀随机
        const interval = TRANSLATE_START_INTERVAL_MIN_MS
          + Math.random() * (TRANSLATE_START_INTERVAL_MAX_MS - TRANSLATE_START_INTERVAL_MIN_MS);
        nextTranslateAllowedAt = Date.now() + interval;
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
    // 成功 → successSinceLastRecovery++ + consecutiveDownloadFails 清零，状态持久化
    recoveryState.successSinceLastRecovery++;
    recoveryState.consecutiveDownloadFails = 0;
    persistRecoveryState();
    scheduleLoop(POLL_INTERVAL_MS);
  } catch (e) {
    const elapsed = Date.now() - startedAt;
    const errorCode = classifyErrorCode(e.message);
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

    // 错误处理状态机：
    // - DAILY_QUOTA_REACHED → 终态停止 + 删 project（账号级硬限制，重试无意义）
    // - RECAPTCHA_BLOCKED   → 决策 L1/L2 触发恢复链；recovery 失败由 triggerRecovery 内部走 stopAndDelete
    // - DOWNLOAD_FAILED     → 连续计数；达 3 张触发 L1 恢复
    // - FLOW_DISCONNECTED / GOOGLE_BLOCKED / TIMEOUT / 其它 → 不动 recoveryState，500ms 后正常重试
    if (errorCode === 'DAILY_QUOTA_REACHED') {
      stopAndDelete('Google daily quota reached — stopped and deleting all projects', { code: 'DAILY_QUOTA_REACHED' });
    } else if (errorCode === 'RECAPTCHA_BLOCKED') {
      if (recoveryState.lastRecoveryLevel === 'L2') {
        // L2 后仍 reCAPTCHA → 终态停止 + 删 project（策略第 6 条）
        stopAndDelete('reCAPTCHA blocked after L2 recovery — stopped and deleting all projects', { code: 'RECAPTCHA_BLOCKED_AFTER_L2' });
      } else {
        const level = decideRecoveryLevel();
        triggerRecovery(level);
      }
    } else if (errorCode === 'DOWNLOAD_FAILED') {
      recoveryState.consecutiveDownloadFails++;
      persistRecoveryState();
      if (recoveryState.consecutiveDownloadFails >= RECOVERY_DOWNLOAD_FAIL_THRESHOLD) {
        addLog('warn', `🔄 连续 ${recoveryState.consecutiveDownloadFails} 张下载失败 — 触发 L1 恢复`);
        const level = decideRecoveryLevel();
        triggerRecovery(level);
      } else {
        scheduleLoop(POLL_INTERVAL_MS);
      }
    } else {
      // FLOW_DISCONNECTED / GOOGLE_BLOCKED / TIMEOUT / 其它：reload 副伤等被动失败 — 不动 counter
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
