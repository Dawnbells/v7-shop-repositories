import {
  checkConnection,
  uploadImageToFlow,
  buildPolicyFallbackCompletion,
  generateWithReference,
  getMediaRedirectUrl,
  fetchImageAsBase64,
  clearTokenCache,
  clearProjectIdCache,
  getSessionToken,
  runRecoveryChain,
  deleteAllUserProjects,
} from './flow-api.js';
import {
  findImagePolicyFallback,
  rememberImagePolicyFallback,
  clearPendingPolicyCompletion,
  listPendingPolicyCompletions,
  rememberPendingPolicyCompletion,
  reportPolicyFallback,
} from './policy-fallback-state.js';
import { imageDigest } from './image-digest.js';
import {
  emptyStats,
  normalizeStats,
  recordOutcome,
  STAT_FAILED,
  STAT_POLICY,
  STAT_SUCCESS,
} from './bridge-stats.js';
import {
  consumeReuseWindow,
  findTranslatedImage,
  forgetTranslatedImage,
  rememberTranslatedImage,
  summarizeTranslatedImages,
  REUSE_MATCH_WINDOW,
} from './translated-image-cache.js';
import {
  classifyErrorCode,
  nextConsecutiveFailureCount,
  nextFlowDisconnectedCount,
  shouldPauseForRunNow,
  CONSECUTIVE_FAILURE_PAUSE_THRESHOLD,
  FLOW_DISCONNECTED_PAUSE_THRESHOLD,
  FAILURE_STREAK_INCREMENT,
  FAILURE_STREAK_RESET,
} from './task-error-policy.js';

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
const STATS_STORAGE_KEY = 'bridgeStats';
// 历史每条都存了完整的 sourceImage + resultImage base64，10 条足够回看最近一轮，
// 也给译图复用缓存腾出存储空间。当日/累计统计已改由 bridgeStats 独立维护，不再依赖这个数组。
const MAX_TASK_HISTORY = 10;
const MAX_LOG_HISTORY = 500;
/** 服务端 TurboFlowReprocessRequiredException.REASON：译图收到了但后处理失败，assignment 已失效。 */
const REPROCESS_REQUIRED_REASON = 'REPROCESS_REQUIRED';
// 单次翻译总超时（含上传/生成/下载）。Flow 正常约 30~60 秒；网速慢时下载重试可达 120 秒；
// 4 并发场景下任务内 sleep(250*i) 错峰最多 0.75 秒；总预算放宽到 300 秒。
const TRANSLATE_TIMEOUT_MS = 300 * 1000;
// fail 上报失败的重试次数与基础间隔（指数退避）。尽量保证 server 端能及时收到失败信号，避免等到 lease 过期。
const FAIL_REPORT_MAX_RETRIES = 3;
const FAIL_REPORT_RETRY_BASE_MS = 1000;
const PENDING_POLICY_REPORT_ALARM = 'retry-pending-policy-fallbacks';
let pendingPolicyFlushRunning = false;

/**
 * 把错误码翻译成更友好的描述（写日志用），原始 message 仍随 reportFail 上报给服务端。
 */
function friendlyErrorMessage(errorCode, rawMessage) {
  if (errorCode === 'FLOW_DISCONNECTED') return 'Flow tab unavailable (closed / navigated away)';
  if (errorCode === 'DAILY_QUOTA_REACHED') return '⚠️ Google 账号每日额度已用尽，需等几小时自然恢复';
  if (errorCode === 'FLOW_AUTHENTICATION_FAILED') return '⚠️ Google Flow 认证已失效，请重新登录后点击 Run Now';
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
// 当日 + 累计统计。独立于 taskHistory 持久化，所以历史裁剪到 10 条也不影响计数。
let bridgeStats = emptyStats();
// 待重投译图的概要，供 GET_STATUS 同步返回（避免每次状态轮询都读一遍 storage）
let reuseSummary = { count: 0, minRemaining: 0 };

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
//   consecutiveFlowDisconnects 「tab 在却断连失败」的连续数；达 FLOW_DISCONNECTED_PAUSE_THRESHOLD(3) 暂停
//   consecutiveFailures        任意错误的连续失败数；达 CONSECUTIVE_FAILURE_PAUSE_THRESHOLD(5) 暂停
//   lastRecoveryLevel          'NONE' | 'L1' | 'L2'；决定下次 reCAPTCHA 是 L1 还是升 L2
let recoveryState = {
  successSinceLastRecovery: 0,
  consecutiveDownloadFails: 0,
  consecutiveFlowDisconnects: 0,
  consecutiveFailures: 0,
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
      consecutiveFlowDisconnects: Number(s.consecutiveFlowDisconnects) || 0,
      consecutiveFailures: Number(s.consecutiveFailures) || 0,
      lastRecoveryLevel: ['NONE', 'L1', 'L2'].includes(s.lastRecoveryLevel) ? s.lastRecoveryLevel : 'NONE',
    };
  }
}

function resetRecoveryStateAll() {
  recoveryState = {
    successSinceLastRecovery: 0,
    consecutiveDownloadFails: 0,
    consecutiveFlowDisconnects: 0,
    consecutiveFailures: 0,
    lastRecoveryLevel: 'NONE',
  };
  persistRecoveryState();
}

/**
 * 记一次任务结局对「连续失败」计数的影响，并在越过阈值时进入停止态。
 * 返回是否已经（或本来就）处于停止态。
 */
function applyFailureStreak(outcome, { errorCode = null } = {}) {
  recoveryState.consecutiveFailures = nextConsecutiveFailureCount(
    recoveryState.consecutiveFailures,
    outcome,
  );
  persistRecoveryState();
  const pause = shouldPauseForRunNow(errorCode, {
    consecutiveFlowDisconnects: recoveryState.consecutiveFlowDisconnects,
    consecutiveFailures: recoveryState.consecutiveFailures,
  });
  if (pause) {
    pausePoll(buildPauseReason(errorCode), { code: pauseCodeFor(errorCode) });
  }
  return pause;
}

function buildPauseReason(errorCode) {
  if (errorCode === 'FLOW_AUTHENTICATION_FAILED') {
    return 'Google Flow authentication failed — sign in again, then click Run Now';
  }
  if (errorCode === 'FLOW_DISCONNECTED'
    && recoveryState.consecutiveFlowDisconnects >= FLOW_DISCONNECTED_PAUSE_THRESHOLD) {
    return `Flow disconnected ${recoveryState.consecutiveFlowDisconnects} consecutive times while the tab was open — click Run Now to resume`;
  }
  return `${recoveryState.consecutiveFailures} consecutive task failures (limit ${CONSECUTIVE_FAILURE_PAUSE_THRESHOLD}) — click Run Now to resume`;
}

function pauseCodeFor(errorCode) {
  if (errorCode === 'FLOW_AUTHENTICATION_FAILED') return errorCode;
  if (errorCode === 'FLOW_DISCONNECTED'
    && recoveryState.consecutiveFlowDisconnects >= FLOW_DISCONNECTED_PAUSE_THRESHOLD) {
    return errorCode;
  }
  return 'CONSECUTIVE_FAILURES';
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
 * 恢复链跑通后清账：换来的是全新环境（新 project、storage 已清、grecaptcha token 验过），
 * 旧环境攒下的失败连续性不该算到新环境头上。
 */
function markRecoverySucceeded(level) {
  recoveryState.lastRecoveryLevel = level;
  recoveryState.successSinceLastRecovery = 0;
  recoveryState.consecutiveDownloadFails = 0;
  recoveryState.consecutiveFailures = 0;
  recoveryState.consecutiveFlowDisconnects = 0;
  persistRecoveryState();
}

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
async function triggerRecovery(level, options = {}) {
  if (recoveryPromise) return recoveryPromise;
  // 本次失败刚把 bridge 推进停止态时仍要跑恢复链（风控该清还是要清，否则用户点 Run Now
  // 立刻又撞墙）；但跑完不自动恢复轮询。已停止态是历史遗留时照旧短路。
  if (pollPaused && options.allowWhilePaused !== true) return false;
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
      markRecoverySucceeded(level);
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
    if (ok && !pollPaused) {
      // 恢复成功 — 立即排一次 poll 拉新 task。停止态下不自动恢复，等用户点 Run Now。
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
    markRecoverySucceeded(level);
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
chrome.alarms.onAlarm.addListener((alarm) => {
  if (alarm.name === PENDING_POLICY_REPORT_ALARM) {
    flushPendingPolicyReports().catch((error) => addLog('warn', `Pending policy report flush failed: ${error.message}`));
  }
});


// service worker 唤醒（含模块顶层执行）时也启动一次，覆盖 onInstalled/onStartup 都未触发的场景
startConnectionWatchdog();
startPendingPolicyReportRetry();

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
      reuseSummary,
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

  if (msg.type === 'GET_STATS') {
    // 跨天时读的这一刻就滚动，不用等下一次任务结束
    bridgeStats = normalizeStats(bridgeStats, Date.now());
    sendResponse({ stats: bridgeStats });
    return false;
  }

  if (msg.type === 'RESET_STATS') {
    bridgeStats = emptyStats(Date.now());
    persistBridgeStats();
    addLog('info', 'Stats reset by user');
    broadcast({ type: 'STATS_UPDATED', stats: bridgeStats });
    sendResponse({ ok: true, stats: bridgeStats });
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
    STATS_STORAGE_KEY,
    STOP_STATE_STORAGE_KEY,
    LEGACY_PAUSE_STATE_STORAGE_KEY,
  ]);
  taskHistory = Array.isArray(stored.taskHistory) ? stored.taskHistory : [];
  if (taskHistory.length > MAX_TASK_HISTORY) {
    // 上限从 50 降到 10 —— 启动时就把旧记录裁掉，顺带回收它们占的 base64 图空间
    taskHistory.length = MAX_TASK_HISTORY;
    chrome.storage.local.set({ taskHistory }).catch(() => {});
  }
  logHistory = Array.isArray(stored.logHistory) ? stored.logHistory : [];
  bridgeStats = normalizeStats(stored[STATS_STORAGE_KEY], Date.now());
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
  await refreshReuseSummary();
}

async function loadConfig() {
  await ensureBridgeId();
  const stored = await chrome.storage.local.get(['services']);
  return {
    bridgeId,
    services: Array.isArray(stored.services) ? stored.services : [],
  };
}
function startPendingPolicyReportRetry() {
  try {
    const created = chrome.alarms.create(PENDING_POLICY_REPORT_ALARM, { periodInMinutes: 1 });
    if (created && typeof created.catch === 'function') created.catch(() => {});
  } catch {
    // Immediate flush below still provides a retry opportunity on every worker start.
  }
  flushPendingPolicyReports().catch((error) =>
    addLog('warn', `Pending policy report startup flush failed: ${error.message}`));
}

async function flushPendingPolicyReports() {
  if (pendingPolicyFlushRunning) return;
  pendingPolicyFlushRunning = true;
  try {
    const pendingReports = await listPendingPolicyCompletions(chrome.storage.local);
    if (pendingReports.length === 0) return;
    const config = await loadConfig();
    for (const record of pendingReports) {
      const service = config.services.find((candidate) =>
        candidate.enabled !== false
        && candidate.baseUrl
        && candidate.token
        && normalizeBaseUrl(candidate.baseUrl) === normalizeBaseUrl(record.serviceBaseUrl || ''));
      if (!service) continue;
      try {
        await postJson(service, '/turboflow-bridge/tasks/complete', record.payload);
        await clearPendingPolicyCompletion(chrome.storage.local, record);
        addLog('info', `Pending policy fallback reported: ${record.payload.assignmentId}`);
      } catch (error) {
        addLog('warn', `Pending policy fallback still waiting: ${record.payload.assignmentId} (${error.message})`);
      }
    }
  } finally {
    pendingPolicyFlushRunning = false;
  }
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

// ── 统计 + 译图复用概要 ──────────────────────────────────────────

function persistBridgeStats() {
  chrome.storage.local.set({ [STATS_STORAGE_KEY]: bridgeStats }).catch(() => {});
}

/** 记一次任务结局到当日/累计统计。跨天滚动由 recordOutcome 内部处理。 */
function recordStat(outcome) {
  bridgeStats = recordOutcome(bridgeStats, outcome, Date.now());
  persistBridgeStats();
  broadcast({ type: 'STATS_UPDATED', stats: bridgeStats });
}

function setReuseSummary(summary) {
  reuseSummary = summary || { count: 0, minRemaining: 0 };
  broadcast({ type: 'REUSE_SUMMARY_UPDATED', reuseSummary });
}

/** 全量重算待重投概要。只在启动和缓存增删时调 —— 它要读全量 storage。 */
async function refreshReuseSummary() {
  setReuseSummary(await summarizeTranslatedImages(chrome.storage.local, Date.now()));
}

function logDroppedTranslations(dropped) {
  for (const record of dropped) {
    addLog('warn', `⚠️ 译图未被复用即丢弃 (${record.reason}): imageHash=${String(record.imageHash).slice(0, 12)}…`);
  }
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
  const targetLanguageKey = task.targetLanguageCode || task.targetLanguage || targetLang;
  const context = { sourceThumb, sourceImage, targetLang, targetLanguageKey, startedAt };
  try {
    const imageHash = await imageDigest(task.imageBase64);

    // 1. 译图复用：上一轮已经翻译成功、只是没送到服务端的图，直接重投，不再调 Google。
    // 缓存为空时整段跳过 —— 那是绝大多数情况，而 consumeReuseWindow 要 storage.get(null)
    // 读全量（含 taskHistory 里的 base64 图），绝不能进每个任务的热路径。
    if (reuseSummary.count > 0) {
      const cachedTranslation = await findTranslatedImage(chrome.storage.local, {
        serviceBaseUrl: service.baseUrl,
        targetLanguage: targetLanguageKey,
        imageHash,
        now: Date.now(),
      });
      if (cachedTranslation) {
        await completeWithCachedTranslation(service, task, context, cachedTranslation);
        return;
      }
      // 没命中就消耗一次匹配窗口；被窗口/TTL 淘汰的记录打 warn，不静默丢
      const { dropped, summary } = await consumeReuseWindow(chrome.storage.local, Date.now());
      logDroppedTranslations(dropped);
      setReuseSummary(summary);
    }

    // 2. 政策缓存：这张图上传必被拒，跳过上传直接保留原图
    const cachedPolicy = await findImagePolicyFallback(chrome.storage.local, task.imageBase64);
    if (cachedPolicy) {
      addLog('warn', `Local policy cache hit [${cachedPolicy.reason}]: skipping upload for ${task.subTaskId}`);
      await completePolicyFallbackTask(service, task, context, cachedPolicy);
      return;
    }

    // 3. 真正翻译
    const result = await runWithTimeout(
      translateImage(task, conn),
      TRANSLATE_TIMEOUT_MS,
      `translate timeout (${Math.round(TRANSLATE_TIMEOUT_MS / 1000)}s)`,
    );
    try {
      await postCompletionWithRetry(service, {
        bridgeId,
        assignmentId: task.assignmentId,
        imageHash,
        resultImageBase64: result.resultDataUrl,
        resultMimeType: 'image/png',
        resultUrl: result.resultUrl || null,
        elapsedMs: Date.now() - startedAt,
      });
    } catch (reportError) {
      // 图已经翻译好了 —— 落盘留着，等服务端重派时按 sha256 复用，绝不让这次 Google 额度白烧
      await retainTranslationForReuse(service, task, context, result, imageHash, reportError);
      return;
    }
    const elapsed = Date.now() - startedAt;
    const resultImage = ensureDataUrl(result.resultDataUrl);
    const resultThumb = await createThumbnail(result.resultDataUrl, 64);
    addLog('info', `Task completed: ${task.subTaskId}`);
    recordStat(STAT_SUCCESS);
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
    // 成功会打断所有连续失败计数。
    recoveryState.successSinceLastRecovery++;
    recoveryState.consecutiveDownloadFails = 0;
    recoveryState.consecutiveFlowDisconnects = 0;
    recoveryState.consecutiveFailures = nextConsecutiveFailureCount(
      recoveryState.consecutiveFailures,
      FAILURE_STREAK_RESET,
    );
    persistRecoveryState();
    scheduleLoop(POLL_INTERVAL_MS);
  } catch (e) {
    const elapsed = Date.now() - startedAt;
    if (e.code === 'FLOW_UPLOAD_POLICY_REJECTED') {
      const policy = await rememberImagePolicyFallback(chrome.storage.local, task.imageBase64, {
        apiStatus: e.apiStatus || 'INVALID_ARGUMENT',
        reason: e.reason || e.apiStatus || 'INVALID_ARGUMENT',
      });
      await completePolicyFallbackTask(service, task, {
        sourceThumb,
        sourceImage,
        targetLang,
        startedAt,
      }, policy);
      return;
    }
    const errorCode = classifyErrorCode(e);
    // 断连失败要区分「tab 真的没了」和「tab 在却僵死」：前者 watchdog 会在 tab 重开后自动恢复，
    // 不该消耗人工介入配额；后者才是这个阈值要抓的对象。所以当场主动探一次，而不是读缓存的
    // flowTabAvailable —— watchdog 最多有 1 秒延迟，in-flight 任务往往比它先抛错。
    const flowTabPresent = errorCode === 'FLOW_DISCONNECTED'
      ? await probeFlowTabAvailable()
      : true;
    recoveryState.consecutiveFlowDisconnects = nextFlowDisconnectedCount(
      recoveryState.consecutiveFlowDisconnects,
      errorCode,
      { flowTabPresent },
    );
    // 先停 poll，再上报失败；即使 fail 上报需要退避重试，也不能继续领取新的翻译任务。
    // 全局连续失败计数在这里推进（tab 缺失的断连也算 —— 反复关 tab 本身就该停下来）。
    const pauseForRunNow = applyFailureStreak(FAILURE_STREAK_INCREMENT, { errorCode });
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
    recordStat(STAT_FAILED);
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

    // 错误处理状态机。注意各错误码的专属处理**照旧执行**，即便本次失败已经把 bridge 推进
    // 停止态 —— 风控该清还是要清，否则用户点 Run Now 会立刻又撞墙。停止态只保证「不领新任务」，
    // 所以下面所有 scheduleLoop 在停止态下都会被 scheduleLoop 自身短路。
    // - FLOW_AUTHENTICATION_FAILED → 立即停止 poll，等用户重新登录后点 Run Now（不删 project）
    // - DAILY_QUOTA_REACHED → 终态停止 + 删 project（账号级硬限制，重试无意义）
    // - RECAPTCHA_BLOCKED   → 决策 L1/L2 触发恢复链；recovery 失败由 triggerRecovery 内部走 stopAndDelete
    // - DOWNLOAD_FAILED     → 连续计数；达 3 张触发 L1 恢复
    // - FLOW_DISCONNECTED   → tab 在却连续 3 次失败则暂停；tab 真没了交给 watchdog 自动恢复
    // - 任意错误连续 5 次   → 兜底暂停（applyFailureStreak 已处理）
    // - GOOGLE_BLOCKED / TIMEOUT / 其它 → 500ms 后正常重试
    if (errorCode === 'DAILY_QUOTA_REACHED') {
      stopAndDelete('Google daily quota reached — stopped and deleting all projects', { code: 'DAILY_QUOTA_REACHED' });
    } else if (errorCode === 'RECAPTCHA_BLOCKED') {
      if (recoveryState.lastRecoveryLevel === 'L2') {
        // L2 后仍 reCAPTCHA → 终态停止 + 删 project（策略第 6 条）
        stopAndDelete('reCAPTCHA blocked after L2 recovery — stopped and deleting all projects', { code: 'RECAPTCHA_BLOCKED_AFTER_L2' });
      } else {
        const level = decideRecoveryLevel();
        triggerRecovery(level, { allowWhilePaused: pauseForRunNow });
      }
    } else if (errorCode === 'DOWNLOAD_FAILED') {
      recoveryState.consecutiveDownloadFails++;
      persistRecoveryState();
      if (recoveryState.consecutiveDownloadFails >= RECOVERY_DOWNLOAD_FAIL_THRESHOLD) {
        addLog('warn', `🔄 连续 ${recoveryState.consecutiveDownloadFails} 张下载失败 — 触发 L1 恢复`);
        const level = decideRecoveryLevel();
        triggerRecovery(level, { allowWhilePaused: pauseForRunNow });
      } else {
        scheduleLoop(POLL_INTERVAL_MS);
      }
    } else {
      // FLOW_DISCONNECTED / GOOGLE_BLOCKED / TIMEOUT / 其它：普通可重试失败。
      // 停止态下 scheduleLoop 自身会短路，不会领到新任务。
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
 * 完成政策回退。上报失败时不把本地任务标记成已完成；图片摘要已经持久化，
 * 服务端租约重派后会直接重发完成结果，绝不再次调用 Google 上传。
 */
async function completePolicyFallbackTask(service, task, context, policy) {
  const elapsed = Date.now() - context.startedAt;
  const reason = policy.reason || policy.apiStatus || 'INVALID_ARGUMENT';
  // 内容政策拒绝能跑通恰恰证明 Flow tab 是活的，断连连续性确实被打断了 → 清零。
  // 但它对「连续失败」计数是中立的（既不算失败，也不打断之前的失败连续性），所以不碰
  // consecutiveFailures —— 见 FAILURE_STREAK_NEUTRAL 的约定。
  recoveryState.consecutiveFlowDisconnects = 0;
  persistRecoveryState();
  const payload = buildPolicyFallbackCompletion({
    bridgeId,
    assignmentId: task.assignmentId,
    imageHash: policy.imageHash,
    apiStatus: policy.apiStatus || 'INVALID_ARGUMENT',
    reason,
    elapsedMs: elapsed,
  });
  const pendingRecord = await rememberPendingPolicyCompletion(chrome.storage.local, policy, {
    serviceBaseUrl: service.baseUrl,
    payload,
  });
  if (policy.storageError || pendingRecord.storageError) {
    addLog('warn', 'Policy fallback is retained in memory because extension storage is unavailable');
  }

  try {
    await reportPolicyFallbackWithRetry(service, payload);
    await clearPendingPolicyCompletion(chrome.storage.local, pendingRecord);
  } catch (error) {
    addLog('warn', `Policy fallback completion report pending for ${task.subTaskId}: ${error.message}; upload remains blocked by local image digest`);
    // 服务端收不到结果就是一次失败：连续 5 次后停下，别在「算得出来但送不出去」上空转。
    applyFailureStreak(FAILURE_STREAK_INCREMENT);
    recordStat(STAT_FAILED);
    addTaskHistory({
      taskId: task.taskId,
      subTaskId: task.subTaskId,
      service: service.baseUrl,
      status: 'pending-report',
      error: error.message,
      elapsedMs: elapsed,
      time: Date.now(),
      sourceThumb: context.sourceThumb,
      sourceImage: context.sourceImage,
      resultThumb: context.sourceThumb,
      resultImage: context.sourceImage,
      targetLang: context.targetLang,
      policyFallbackReason: reason,
    });
    removeCurrentTask(task.assignmentId);
    scheduleLoop(POLL_INTERVAL_MS);
    setTimeout(() => flushPendingPolicyReports().catch(() => {}), 15 * 1000);
    return false;
  }

  addLog('warn', `Task policy fallback [${reason}]: kept original image for ${task.subTaskId}`);
  recordStat(STAT_POLICY);
  addTaskHistory({
    taskId: task.taskId,
    subTaskId: task.subTaskId,
    service: service.baseUrl,
    status: 'completed',
    elapsedMs: elapsed,
    time: Date.now(),
    sourceThumb: context.sourceThumb,
    sourceImage: context.sourceImage,
    resultThumb: context.sourceThumb,
    resultImage: context.sourceImage,
    targetLang: context.targetLang,
    policyFallbackReason: reason,
  });
  removeCurrentTask(task.assignmentId);
  recoveryState.successSinceLastRecovery++;
  recoveryState.consecutiveDownloadFails = 0;
  recoveryState.consecutiveFlowDisconnects = 0;
  persistRecoveryState();
  scheduleLoop(POLL_INTERVAL_MS);
  return true;
}

/**
 * 服务端明确说「这次要重来」——译图收到了但后处理失败，assignment 已失效。
 * 再退避重投同一个 assignmentId 必然还是 404，白等 7 秒，所以要立即放弃重试转落盘。
 */
function isReprocessRequired(error) {
  return typeof error?.message === 'string' && error.message.includes(REPROCESS_REQUIRED_REASON);
}

/**
 * 译图完成上报带指数退避（此前完全没有重试，一次网络抖动就当翻译失败、整张图重译）。
 */
async function postCompletionWithRetry(service, payload) {
  for (let attempt = 0; attempt <= FAIL_REPORT_MAX_RETRIES; attempt++) {
    try {
      await postJson(service, '/turboflow-bridge/tasks/complete', payload);
      if (attempt > 0) {
        addLog('info', `Completion reported on retry ${attempt}: ${payload.assignmentId}`);
      }
      return;
    } catch (err) {
      if (isReprocessRequired(err)) {
        addLog('warn', `Server asked to reprocess ${payload.assignmentId} — caching the translated image instead of retrying`);
        throw err;
      }
      if (attempt === FAIL_REPORT_MAX_RETRIES) {
        throw err;
      }
      const backoff = FAIL_REPORT_RETRY_BASE_MS * Math.pow(2, attempt);
      addLog('warn', `Completion report attempt ${attempt + 1} failed (${err.message}), retrying in ${backoff}ms`);
      await sleep(backoff);
    }
  }
}

/**
 * 翻译成功但上报失败：把译图按源图 sha256 落盘，等服务端重派同一张图时复用。
 * 同时照常 reportFail，让服务端立刻重排而不用等 lease 过期（6 分钟）。
 */
async function retainTranslationForReuse(service, task, context, result, imageHash, reportError) {
  const elapsed = Date.now() - context.startedAt;
  const { record, dropped } = await rememberTranslatedImage(chrome.storage.local, {
    serviceBaseUrl: service.baseUrl,
    targetLanguage: context.targetLanguageKey,
    imageHash,
    resultDataUrl: result.resultDataUrl,
    resultUrl: result.resultUrl || null,
    resultMimeType: 'image/png',
    elapsedMs: elapsed,
    now: Date.now(),
  });
  logDroppedTranslations(dropped);
  if (record.storageError) {
    addLog('warn', 'Translated image is retained in memory only because extension storage is unavailable');
  }
  await refreshReuseSummary();
  addLog('warn', `Completion报送失败，已保留译图待复用（${REUSE_MATCH_WINDOW} 次任务内匹配同源图即直接重投）: ${task.subTaskId} (${reportError.message})`);

  // 服务端收不到结果就是一次失败，同样受连续 5 次闸门约束
  applyFailureStreak(FAILURE_STREAK_INCREMENT);
  recordStat(STAT_FAILED);
  await reportFailWithRetry(service, {
    bridgeId,
    assignmentId: task.assignmentId,
    errorCode: 'COMPLETION_REPORT_FAILED',
    message: reportError.message,
    stack: reportError.stack || null,
    retryable: true,
    elapsedMs: elapsed,
  });
  const resultThumb = await createThumbnail(result.resultDataUrl, 64);
  addTaskHistory({
    taskId: task.taskId,
    subTaskId: task.subTaskId,
    service: service.baseUrl,
    status: 'pending-report',
    error: reportError.message,
    elapsedMs: elapsed,
    time: Date.now(),
    sourceThumb: context.sourceThumb,
    sourceImage: context.sourceImage,
    resultThumb,
    resultImage: ensureDataUrl(result.resultDataUrl),
    targetLang: context.targetLang,
  });
  removeCurrentTask(task.assignmentId);
  scheduleLoop(POLL_INTERVAL_MS);
}

/**
 * 命中译图复用缓存：直接把上次翻译好的图上送，一次 Google 调用都不花。
 * 带上 imageHash 让服务端校验源图一致（防止哈希算错把 A 图的译图配给 B 图）。
 * elapsedMs 沿用原始耗时，而不是复用这一刻的接近 0 的值。
 */
async function completeWithCachedTranslation(service, task, context, cached) {
  addLog('info', `♻️ 命中译图复用缓存，跳过翻译直接重投: ${task.subTaskId}`);
  try {
    await postCompletionWithRetry(service, {
      bridgeId,
      assignmentId: task.assignmentId,
      imageHash: cached.imageHash,
      resultImageBase64: cached.resultDataUrl,
      resultMimeType: cached.resultMimeType || 'image/png',
      resultUrl: cached.resultUrl || null,
      elapsedMs: cached.elapsedMs ?? (Date.now() - context.startedAt),
    });
  } catch (reportError) {
    // 还是送不出去：记录留着，匹配窗口继续倒数
    addLog('warn', `译图重投仍失败，记录保留: ${task.subTaskId} (${reportError.message})`);
    applyFailureStreak(FAILURE_STREAK_INCREMENT);
    recordStat(STAT_FAILED);
    await reportFailWithRetry(service, {
      bridgeId,
      assignmentId: task.assignmentId,
      errorCode: 'COMPLETION_REPORT_FAILED',
      message: reportError.message,
      stack: reportError.stack || null,
      retryable: true,
      elapsedMs: Date.now() - context.startedAt,
    });
    removeCurrentTask(task.assignmentId);
    scheduleLoop(POLL_INTERVAL_MS);
    return false;
  }

  await forgetTranslatedImage(chrome.storage.local, cached);
  await refreshReuseSummary();
  const elapsed = Date.now() - context.startedAt;
  recordStat(STAT_SUCCESS);
  addTaskHistory({
    taskId: task.taskId,
    subTaskId: task.subTaskId,
    service: service.baseUrl,
    status: 'completed',
    reused: true,
    elapsedMs: cached.elapsedMs ?? elapsed,
    time: Date.now(),
    sourceThumb: context.sourceThumb,
    sourceImage: context.sourceImage,
    resultThumb: await createThumbnail(cached.resultDataUrl, 64),
    resultImage: ensureDataUrl(cached.resultDataUrl),
    targetLang: context.targetLang,
  });
  removeCurrentTask(task.assignmentId);
  // 成功打断连续失败计数
  recoveryState.successSinceLastRecovery++;
  recoveryState.consecutiveDownloadFails = 0;
  recoveryState.consecutiveFlowDisconnects = 0;
  recoveryState.consecutiveFailures = nextConsecutiveFailureCount(
    recoveryState.consecutiveFailures,
    FAILURE_STREAK_RESET,
  );
  persistRecoveryState();
  scheduleLoop(POLL_INTERVAL_MS);
  return true;
}

/**
 * 政策回退按完成上报；这里只重试上报本身，绝不重新上传或生成图片。
 */
async function reportPolicyFallbackWithRetry(service, payload) {
  return reportPolicyFallback({
    post: (completion) => postJson(service, '/turboflow-bridge/tasks/complete', completion),
    payload,
    maxRetries: FAIL_REPORT_MAX_RETRIES,
    retryBaseMs: FAIL_REPORT_RETRY_BASE_MS,
    sleep,
    onRetry: (attempt, backoff, error) => {
      addLog('warn', `Policy fallback report attempt ${attempt} failed (${error.message}), retrying in ${backoff}ms`);
    },
    onRecovered: (attempt) => {
      addLog('info', `Policy fallback reported on retry ${attempt}: ${payload.assignmentId}`);
    },
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
