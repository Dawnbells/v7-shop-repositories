import {
  checkConnection,
  buildPolicyFallbackCompletion,
  clearTokenCache,
  clearProjectIdCache,
  setSessionToken,
  openFlowHome,
  armModernGenerateMonitor,
  waitModernGenerateSent,
  resolveModernGenerateResponse,
} from './flow-api.js';
import {
  FLOW_TAB_URL_PATTERNS,
  isFlowUrl,
  isModernFlowUrl,
  buildFlowHomeUrl,
} from './flow-sites.js';
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
  UPLOAD_REJECTION_FORGET_AFTER_TASKS,
  UPLOAD_REJECTION_POLICY_THRESHOLD,
  forgetUploadRejection,
  observeTaskForUploadRejection,
  rememberUploadRejection,
  shouldFallBackToPolicy,
} from './upload-rejection-state.js';
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
  isQuotaErrorCode,
  nextConsecutiveFailureCount,
  nextFlowDisconnectedCount,
  shouldPauseForRunNow,
  CONSECUTIVE_FAILURE_PAUSE_THRESHOLD,
  FLOW_DISCONNECTED_PAUSE_THRESHOLD,
  FAILURE_STREAK_INCREMENT,
  FAILURE_STREAK_RESET,
} from './task-error-policy.js';
import { FlowTaskRegistry } from './flow-task-registry.js';
import { initializeDefaultConfig } from './bridge-defaults.js';
import { FlowRecoveryController } from './flow-recovery-controller.js';
import { restartFlowProject, getProjectCleanupTarget, deleteProjectsAtHome } from './flow-project-lifecycle.js';
import { FlowSubmissionPacer } from './flow-submission-pacer.js';
import { translateImageForMode } from './flow-generation-mode.js';
import { releaseApi2351 } from './flow-api-2.3.5.1.js';
import { normalizeGenerationMode, generationModeLabel } from './generation-mode.js';

const VERSION = '1.5.10';
const POLL_INTERVAL_MS = 500;
// 同时「翻译中」的上限，设置页可调（1–10，默认 1 = 单线程）。上传 / 挂图 / 写 prompt / 提交
// 始终串行；译图下载到扩展后立即释放槽位，服务端回传与后续任务并行。
const FLOW_CONCURRENCY_STORAGE_KEY = 'flowConcurrency';
const DEFAULT_FLOW_CONCURRENCY = 4;
const MAX_FLOW_CONCURRENCY = 10;
const PREFETCH_LIMIT = 1;
const DOM_TRANSLATE_MESSAGE = 'RUN_DOM_TRANSLATE_V26';
const DOM_TRANSLATE_PORT = 'TURBOFLOW_DOM_V26';
const GENERATION_MODE_STORAGE_KEY = 'generationMode';
const DOM_PORT_ACK_TIMEOUT_MS = 5000;
const DOM_PORT_CONNECT_ATTEMPTS = 3;
const STOP_STATE_STORAGE_KEY = 'bridgeStopState';
const RECOVERY_STATE_STORAGE_KEY = 'bridgeRecoveryState';
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
// Keep the original assignment through slow uploads and transient backend errors.
// Nine 120s uploads, 30s notices and capped backoff fit inside the 30-minute report lease.
const COMPLETION_REPORT_MAX_RETRIES = 8;
const COMPLETION_REPORT_TIMEOUT_MS = 120 * 1000;
const TRANSLATED_NOTICE_TIMEOUT_MS = 30 * 1000;
const PENDING_POLICY_REPORT_ALARM = 'retry-pending-policy-fallbacks';
let pendingPolicyFlushRunning = false;

/**
 * 把错误码翻译成更友好的描述（写日志用），原始 message 仍随 reportFail 上报给服务端。
 */
function friendlyErrorMessage(errorCode, rawMessage) {
  if (errorCode === 'FLOW_DISCONNECTED') return 'Flow tab unavailable (closed / navigated away)';
  if (errorCode === 'DAILY_QUOTA_REACHED') return '⚠️ Google Flow 每日额度已用尽，等待额度恢复后点击 Run Now';
  if (errorCode === 'FLOW_AUTHENTICATION_FAILED') return '⚠️ Google Flow 认证已失效，请重新登录后点击 Run Now';
  return rawMessage;
}

let bridgeId = null;
let generationMode = 'api-2.3.5.1';
let flowConcurrency = DEFAULT_FLOW_CONCURRENCY;
let running = false;
let currentTasks = [];
const flowTasks = new FlowTaskRegistry(flowConcurrency);
let prefetchedTask = null;
let timerId = null;
let serviceCursor = 0;
let lastStatus = { connected: false, message: 'Not checked' };
let nextPollAt = 0;
const submissionPacer = new FlowSubmissionPacer();
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
let openingFlowPromise = null;

// Flow 标签页可用性看门狗：每秒探测，标签关闭 → 立即阻断 poll；重新打开 → 自动恢复
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
    const tabs = await chrome.tabs.query({ url: FLOW_TAB_URL_PATTERNS });
    return tabs.some((t) => t.url && isFlowUrl(t.url) && t.status === 'complete');
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
      // Open Flow 打开页面期间 tab 可能短暂不可用；预取任务由打开动作自行处理。
      if (!openingFlowPromise) releasePrefetchedTask('Flow tab closed');
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
function applyFailureStreak(outcome, { errorCode = null, errorMessage = '' } = {}) {
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
    pausePoll(buildPauseReason(errorCode, errorMessage), { code: pauseCodeFor(errorCode) });
  }
  return pause;
}

function buildPauseReason(errorCode, errorMessage = '') {
  if (isQuotaErrorCode(errorCode)) {
    return `${errorCode === 'DAILY_QUOTA_REACHED' ? 'Google Flow daily quota reached' : 'Google Flow resource/quota exhausted (RPC status 8 / RESOURCE_EXHAUSTED)'} — no new tasks or submissions; submitted translations will finish. Wait for quota to recover, then click Run Now`;
  }
  if (errorCode === 'FLOW_RPC_REJECTED') {
    return `${errorMessage || 'Google Flow RPC rejected'} — check the Flow error before clicking Run Now`;
  }
  if (errorCode === 'FLOW_VERIFICATION_REQUIRED') {
    return 'Google Flow verification required — open Flow and complete verification, then click Run Now';
  }
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
  if (shouldPauseForRunNow(errorCode)) return errorCode;
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
  // Later in-flight failures must not replace the quota stop reason.
  if (alreadyPaused && isQuotaErrorCode(pauseReasonCode)) return;
  const reasonChanged = pauseReason !== reason;
  pollPaused = true;
  roundRecovery.halt();
  pauseReason = reason;
  pauseReasonCode = options.code || pauseReasonCode || null;
  // 停止态下预取任务永远启动不了，还给服务端重派。
  releasePrefetchedTask('bridge stopped');
  nextPollAt = 0;
  if (timerId) {
    clearTimeout(timerId);
    timerId = null;
  }
  if (alreadyPaused && !reasonChanged) {
    persistStopState();
    return;
  }
  if (!alreadyPaused) pausedAt = Date.now();
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
let deletingProjects = false;
let cleanupProgress = null;
const activeOperations = new Set();
const ROUND_STATE_KEY = 'flowRecoveryRoundV1';
const roundRecovery = new FlowRecoveryController({
  persist: state => chrome.storage.local.set({ [ROUND_STATE_KEY]: state }),
  drained: () => !running && !currentTasks.length && !flowTasks.inUse
    && !activeOperations.size && !pendingPolicyFlushRunning,
  restart: async (target, phase) => {
    await releaseApi2351();
    const phaseLogs = {
      clearing: 'Flow recovery: clearing flow.google.com local/session storage in Flow tabs',
      closing: 'Flow recovery: closing Flow tabs',
      opening: 'Flow recovery: opening Flow home and waiting for it to load',
      creating: 'Flow recovery: creating a project and waiting for the editor to load',
    };
    await restartFlowProject(target, async (name) => {
      if (phaseLogs[name]) addLog('info', phaseLogs[name]);
      await phase(name);
    }, chrome, undefined, (level, message) => addLog(level, `Flow recovery: ${message}`));
    addLog('info', 'Flow recovery: new project ready, resuming tasks');
    flowTabAvailable = true;
    resetRecoveryStateAll();
  },
  stop: (reason, code) => pausePoll(reason, { code }),
  changed: state => broadcast({ type: 'FLOW_RECOVERY_CHANGED', recovery: state }),
});

function flowWorkBlocked() {
  return pollPaused || roundRecovery.blocked || !!openingFlowPromise || deletingProjects;
}

function trackOperation(promise) {
  activeOperations.add(promise);
  promise.then(() => activeOperations.delete(promise), () => activeOperations.delete(promise));
  return promise;
}

function beginFlowRecovery(conn, manual = false) {
  if (roundRecovery.busy) return roundRecovery.pending;
  if (deletingProjects || openingFlowPromise) return Promise.resolve(false);
  if (!manual && (pollPaused || roundRecovery.blocked)) return Promise.resolve(false);
  if (manual) resumePoll();
  const target = conn?.flowUrl
    ? { ...roundRecovery.state.target, homeUrl: buildFlowHomeUrl(conn.flowUrl), tabId: conn.tabId }
    : roundRecovery.state.target;
  const operation = roundRecovery.request(target, { manual });
  recoveryPromise = operation;
  if (timerId) clearTimeout(timerId);
  timerId = null;
  nextPollAt = 0;
  releasePrefetchedTask('Flow recovery awaiting task completion');
  addLog('warn', 'Flow recovery: waiting for all current tasks to finish');
  broadcast({ type: 'COUNTDOWN_UPDATE', nextPollAt: 0 });
  operation.finally(() => {
    recoveryPromise = null;
    if (!pollPaused && !roundRecovery.blocked) scheduleLoop(100);
  });
  return operation;
}

function isUnusualActivity(error) {
  return /unusual[_ ]activity/i.test(`${error?.reason || ''} ${error?.message || error || ''}`);
}

function observeFlowError(error, conn) {
  if (!isUnusualActivity(error)) return false;
  beginFlowRecovery(conn);
  return true;
}

async function executeTestTranslation(msg, uiOnly = false) {
  let conn;
  const roundId = roundRecovery.state.roundId;
  const taskId = 'test:' + crypto.randomUUID();
  try {
    assertFlowSubmissionAllowed();
    const mode = uiOnly ? 'ui' : generationMode;
    conn = await checkConnection({ requireApiSession: mode === 'api' });
    if (!conn.connected) throw new Error(conn.reason || 'Flow is not connected');
    const tab = await chrome.tabs.get(conn.tabId);
    roundRecovery.state.target = { homeUrl: buildFlowHomeUrl(conn.flowUrl), windowId: tab.windowId, tabId: tab.id };
    await roundRecovery.save();
    assertFlowSubmissionAllowed();
    const options = { ...msg, assignmentId: taskId,
      fileName: buildDomUploadFileName(msg, 'test.png'),
      prompt: msg.prompt || buildPrompt({ targetLanguage: msg.targetLanguage || 'Simplified Chinese' }),
      aspectRatio: msg.aspectRatio === 'auto' ? aspectRatioFor(msg.width, msg.height) : msg.aspectRatio,
      beforeSubmit: assertFlowSubmissionAllowed };
    const result = await translateImageForMode(mode, conn, options, runFlowDomTranslation);
    roundRecovery.success(roundId, taskId);
    return { ok: true, ...result };
  } catch (error) {
    if (!observeFlowError(error, conn) && error.code !== 'FLOW_SUBMISSION_PAUSED') {
      const code = classifyErrorCode(error);
      if (isQuotaErrorCode(code) || ['FLOW_AUTHENTICATION_FAILED', 'FLOW_VERIFICATION_REQUIRED', 'FLOW_RPC_REJECTED'].includes(code)) {
        pausePoll(error.message, { code });
      }
    }
    return { ok: false, error: error.message };
  }
}

async function cleanupStatus(windowId) {
  if (flowTasks.inUse || currentTasks.length || activeOperations.size || running || pendingPolicyFlushRunning
    || roundRecovery.busy || openingFlowPromise || deletingProjects || roundRecovery.state.phase === 'initializing') {
    return { allowed: false, reason: '等待任务、恢复或删除完成', progress: cleanupProgress };
  }
  return getProjectCleanupTarget(windowId);
}

async function manuallyDeleteProjects(tabId, windowId) {
  const status = await cleanupStatus(windowId);
  if (!status.allowed || status.tabId !== tabId) return { ok: false, error: status.reason || 'Flow target changed' };
  if (deletingProjects || running || activeOperations.size || flowTasks.inUse || roundRecovery.busy || openingFlowPromise) {
    return { ok: false, error: 'Flow is busy' };
  }
  deletingProjects = true;
  cleanupProgress = { phase: 'starting', deleted: 0, failed: 0 };
  if (timerId) clearTimeout(timerId);
  timerId = null;
  nextPollAt = 0;
  try {
    const result = await deleteProjectsAtHome(status, progress => {
      cleanupProgress = progress;
      broadcast({ type: 'FLOW_CLEANUP_PROGRESS', ...progress });
    });
    addLog(result.failed || result.remaining ? 'warn' : 'info',
      `Flow projects: deleted ${result.deleted}, failed ${result.failed}, remaining ${result.remaining}`);
    return { ok: true, ...result };
  } catch (error) {
    addLog('error', `Flow project cleanup stopped: ${error.message}`);
    return { ok: false, error: error.message };
  } finally {
    deletingProjects = false;
    cleanupProgress = null;
    broadcast({ type: 'FLOW_CLEANUP_PROGRESS', phase: 'idle' });
  }
}

function openFlowForManualProject() {
  if (openingFlowPromise) return openingFlowPromise;
  if (roundRecovery.busy || deletingProjects) return Promise.resolve({ ok: false, error: 'Flow is busy' });
  openingFlowPromise = (async () => {
    if (timerId) clearTimeout(timerId);
    timerId = null;
    nextPollAt = 0;
    broadcast({ type: 'COUNTDOWN_UPDATE', nextPollAt: 0 });
    lastStatus = { connected: false, message: 'Opening Flow...' };
    broadcast({ type: 'CONNECTION_CHANGED', ...lastStatus, projectId: null });
    // Let existing work finish before changing the active Flow tab. New work is gated.
    while (running || currentTasks.length || flowTasks.inUse || activeOperations.size || recoveryPromise || deletingProjects) {
      await new Promise((resolve) => setTimeout(resolve, 100));
    }
    releasePrefetchedTask('Open Flow awaiting manual project selection');
    const result = await openFlowHome();
    flowTabAvailable = true;
    lastStatus = { connected: false, message: 'Flow opened. Create or open a project in Flow.', projectId: null };
    broadcast({ type: 'CONNECTION_CHANGED', ...lastStatus });
    addLog('info', 'Flow opened. Create or select a project manually.');
    return { ok: true, ...result };
  })().catch((error) => {
    lastStatus = { connected: false, message: `Open Flow failed: ${error.message}` };
    broadcast({ type: 'CONNECTION_CHANGED', ...lastStatus, projectId: null });
    addLog('error', lastStatus.message);
    return { ok: false, error: error.message };
  }).finally(() => {
    openingFlowPromise = null;
    scheduleLoop(100);
  });
  return openingFlowPromise;
}

chrome.sidePanel.setPanelBehavior({ openPanelOnActionClick: true }).catch(() => {});

chrome.tabs.onUpdated.addListener((tabId, _changeInfo, tab) => {
  if (tab.url && isFlowUrl(tab.url)) {
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
    if (openingFlowPromise || roundRecovery.busy) {
      sendResponse({ connected: false, reason: 'Opening Flow...' });
      return false;
    }
    checkConnection().then((state) => {
      if (openingFlowPromise || roundRecovery.busy) {
        sendResponse({ connected: false, reason: 'Opening Flow...' });
        return;
      }
      lastStatus = { connected: state.connected, message: state.reason || 'Connected', projectId: state.projectId };
      sendResponse(state);
    }).catch((e) => sendResponse({ connected: false, reason: e.message }));
    return true;
  }

  if (msg.type === 'OPEN_FLOW') {
    openFlowForManualProject().then(sendResponse);
    return true;
  }

  if (msg.type === 'SESSION_TOKEN_CAPTURED') {
    if (_sender.tab?.url && isFlowUrl(_sender.tab.url) && msg.token) {
      setSessionToken(msg.token, msg.capturedAt || Date.now(), _sender.tab.id, _sender.tab.url);
    }
    sendResponse({ ok: true });
    return false;
  }

  if (msg.type === 'FLOW_DOM_TRANSLATION_SUBMITTED') {
    const assignmentId = msg.assignmentId || null;
    const fromFlowTab = !!_sender.tab?.url && isFlowUrl(_sender.tab.url);
    if (fromFlowTab && flowTasks.acceptSubmission(assignmentId)) {
      submissionPacer.submitted();
      const taskState = currentTasks.find((task) => task.assignmentId === assignmentId);
      if (taskState) taskState.phase = 'generating';
      broadcastTasksChanged();
      addLog('info', `Flow accepted task: ${taskState?.subTaskId || assignmentId}; ${flowTasks.generatingOwners.size}/${flowTasks.limit} generating`);
      if (!startPrefetchedTask()) scheduleLoop(0);
    }
    sendResponse({ ok: true });
    return false;
  }

  if (msg.type === 'FLOW_TRUSTED_SUBMIT') {
    try { assertFlowSubmissionAllowed(); }
    catch (error) { sendResponse({ ok: false, error: error.message }); return false; }
    const tabId = _sender.tab?.id;
    if (!tabId || !isFlowUrl(_sender.tab.url || '')) {
      sendResponse({ ok: false, error: 'Trusted submit is only available to the Flow tab' });
      return false;
    }
    submitFlowGeneration(tabId, msg.selector, _sender.tab.url, msg.referenceMediaIds)
      .then((generateToken) => sendResponse({ ok: true, generateToken }))
      .catch((error) => sendResponse({ ok: false, error: error.message }));
    return true;
  }

  if (msg.type === 'FLOW_DOM_BEFORE_SUBMIT') {
    try {
      assertFlowSubmissionAllowed();
      sendResponse({ ok: true });
    } catch (error) {
      sendResponse({ ok: false, error: error.message });
    }
    return false;
  }

  if (msg.type === 'GET_CONFIG') {
    loadPersistedState().then(loadConfig).then(sendResponse)
      .catch(error => sendResponse({ error: error.message }));
    return true;
  }

  if (msg.type === 'SAVE_CONFIG') {
    saveConfig(msg.config || {}).then(async () => {
      const stored = await chrome.storage.local.get(['services']);
      const count = Array.isArray(stored.services) ? stored.services.length : 0;
      addLog('info', `Config saved (${count} services, ${generationMode.toUpperCase()} mode, concurrency ${flowConcurrency})`);
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
      recovery: roundRecovery.snapshot(),
      recoveryBusy: roundRecovery.busy,
      drainingCount: Math.max(currentTasks.length, flowTasks.inUse, activeOperations.size),
      deletingProjects,
      reuseSummary,
      flowSlotOwner: flowTasks.submissionOwner,
      flowConcurrency: flowTasks.snapshot(),
      prefetchedTask: getPrefetchedTaskSummary(),
    });
    return false;
  }

  if (msg.type === 'RUN_NOW') {
    if (roundRecovery.busy || openingFlowPromise || deletingProjects) {
      sendResponse({ ok: false, error: 'Flow is busy' });
      return false;
    }
    beginFlowRecovery(null, true);
    sendResponse({ ok: true });
    return false;
  }

  if (msg.type === 'GET_PROJECT_CLEANUP_STATUS') {
    cleanupStatus(msg.windowId).then(sendResponse).catch(e => sendResponse({ allowed: false, reason: e.message }));
    return true;
  }
  if (msg.type === 'DELETE_ALL_FLOW_PROJECTS') {
    manuallyDeleteProjects(msg.tabId, msg.windowId).then(sendResponse).catch(e => sendResponse({ ok: false, error: e.message }));
    return true;
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

  if (msg.action === 'injectFlowUpload') {
    const tabId = _sender.tab?.id || msg.tabId;
    if (!tabId) {
      sendResponse({ success: false, error: 'No tab ID' });
      return true;
    }
    chrome.scripting.executeScript({
      target: { tabId },
      world: 'MAIN',
      func: async (dataUrl, fileName, requestedMime) => {
        const uploadRpcId = 'maseQ';
        const isUploadRequest = (rawUrl) => {
          try {
            const url = new URL(String(rawUrl || ''), location.href);
            return (url.searchParams.get('rpcids') || '').split(',').includes(uploadRpcId);
          } catch {
            return String(rawUrl || '').includes(`rpcids=${uploadRpcId}`);
          }
        };
        const validateUploadResponse = (status, responseText) => {
          if (status < 200 || status >= 300) {
            return { status: 'error', error: `Flow upload HTTP ${status || 0}` };
          }
          const text = String(responseText || '');
          if (!text.includes(`"wrb.fr","${uploadRpcId}"`)) {
            return { status: 'error', error: `Flow upload response did not contain ${uploadRpcId}` };
          }
          if (text.includes(`"wrb.fr","${uploadRpcId}","[]"`)) {
            return { status: 'error', error: 'Flow upload returned no media record' };
          }
          return { status: 'ok', rpcId: uploadRpcId, httpStatus: status, mediaId: extractUploadMediaId(text) };
        };
        // maseQ 响应 [[mediaId, projectId, ...], ...]：并发时用它核对 ogiZ0b 引用的是哪张源图。
        const extractUploadMediaId = (text) => {
          for (const line of String(text || '').split('\n')) {
            if (!line.trim().startsWith('[')) continue;
            try {
              for (const entry of JSON.parse(line)) {
                if (entry?.[0] !== 'wrb.fr' || entry?.[1] !== uploadRpcId || typeof entry[2] !== 'string') continue;
                const mediaId = JSON.parse(entry[2])?.[0]?.[0];
                if (typeof mediaId === 'string' && mediaId) return mediaId;
              }
            } catch {}
          }
          return null;
        };

        let captureUpload = false;
        let observedUpload = false;
        let uploadSettled = false;
        let settleUpload;
        const uploadCompletion = new Promise((resolve) => { settleUpload = resolve; });
        const settleOnce = (value) => {
          if (uploadSettled) return;
          uploadSettled = true;
          settleUpload(value);
        };

        const originalFetch = window.fetch;
        const wrappedFetch = function (...args) {
          const rawUrl = typeof args[0] === 'string' || args[0] instanceof URL
            ? args[0]
            : args[0]?.url;
          const isTarget = captureUpload && isUploadRequest(rawUrl);
          if (isTarget) observedUpload = true;
          const request = originalFetch.apply(this, args);
          if (isTarget) {
            Promise.resolve(request).then(async (response) => {
              let responseText = '';
              try { responseText = await response.clone().text(); } catch {}
              settleOnce(validateUploadResponse(response.status, responseText));
            }).catch((error) => settleOnce({ status: 'error', error: `Flow upload fetch failed: ${error.message}` }));
          }
          return request;
        };

        const originalOpen = XMLHttpRequest.prototype.open;
        const originalSend = XMLHttpRequest.prototype.send;
        const wrappedOpen = function (method, url, ...rest) {
          this.__turboFlowUploadUrl = String(url || '');
          return originalOpen.call(this, method, url, ...rest);
        };
        const wrappedSend = function (...args) {
          const isTarget = captureUpload && isUploadRequest(this.__turboFlowUploadUrl);
          if (isTarget) {
            observedUpload = true;
            this.addEventListener('loadend', () => {
              let responseText = '';
              try { responseText = this.responseText || ''; } catch {}
              settleOnce(validateUploadResponse(this.status, responseText));
            }, { once: true });
            this.addEventListener('error', () => {
              settleOnce({ status: 'error', error: 'Flow upload XHR failed' });
            }, { once: true });
          }
          return originalSend.apply(this, args);
        };
        window.fetch = wrappedFetch;
        XMLHttpRequest.prototype.open = wrappedOpen;
        XMLHttpRequest.prototype.send = wrappedSend;
        const restoreUploadMonitor = () => {
          captureUpload = false;
          if (window.fetch === wrappedFetch) window.fetch = originalFetch;
          if (XMLHttpRequest.prototype.open === wrappedOpen) XMLHttpRequest.prototype.open = originalOpen;
          if (XMLHttpRequest.prototype.send === wrappedSend) XMLHttpRequest.prototype.send = originalSend;
        };

        const findUploadButton = () => Array.from(document.querySelectorAll('.cdk-overlay-pane button, [role="dialog"] button'))
          .find((button) => {
            if (button.offsetParent === null) return false;
            const text = (button.textContent || '').replace(/\s+/g, ' ').trim().toLowerCase();
            const aria = (button.getAttribute('aria-label') || '').trim().toLowerCase();
            const icon = (button.querySelector('mat-icon, i')?.textContent || '').trim().toLowerCase();
            // textContent 含图标 ligature，实际是 "uploadUpload media"
            return text === 'upload media'
              || text === 'uploadupload media'
              || aria === 'upload media'
              || icon === 'upload'
              || icon === 'upload_file'
              || icon === 'drive_folder_upload';
          });
        let uploadButton = null;
        const buttonDeadline = Date.now() + 10000;
        while (!uploadButton && Date.now() < buttonDeadline) {
          uploadButton = findUploadButton();
          if (!uploadButton) await new Promise((resolve) => setTimeout(resolve, 200));
        }
        if (!uploadButton) {
          restoreUploadMonitor();
          return { status: 'error', error: 'Upload media button not found' };
        }

        let fileInput = Array.from(document.querySelectorAll('input[type="file"]'))
          .find((input) => !input.accept || input.accept.includes('image')) || null;
        const originalClick = HTMLInputElement.prototype.click;
        HTMLInputElement.prototype.click = function (...clickArgs) {
          if (String(this.type).toLowerCase() === 'file') {
            fileInput = this;
            return;
          }
          return originalClick.apply(this, clickArgs);
        };
        try {
          uploadButton.click();
          const inputDeadline = Date.now() + 3000;
          while (!fileInput && Date.now() < inputDeadline) {
            fileInput = Array.from(document.querySelectorAll('input[type="file"]'))
              .find((input) => !input.accept || input.accept.includes('image')) || null;
            if (!fileInput) await new Promise((resolve) => setTimeout(resolve, 50));
          }
        } finally {
          HTMLInputElement.prototype.click = originalClick;
        }
        fileInput = fileInput || Array.from(document.querySelectorAll('input[type="file"]'))
          .find((input) => !input.accept || input.accept.includes('image'));
        if (!fileInput) {
          restoreUploadMonitor();
          return { status: 'error', error: 'Flow did not create an upload input' };
        }

        const comma = dataUrl.indexOf(',');
        if (comma < 0) {
          restoreUploadMonitor();
          return { status: 'error', error: 'Invalid image data' };
        }
        const meta = dataUrl.slice(0, comma);
        const binary = atob(dataUrl.slice(comma + 1));
        const bytes = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
        const mime = requestedMime || meta.match(/data:([^;]+)/)?.[1] || 'image/png';
        const transfer = new DataTransfer();
        transfer.items.add(new File([bytes], fileName, { type: mime }));
        fileInput.files = transfer.files;
        captureUpload = true;
        // 账号首次上传时 Flow 会在选完文件后弹 "Rights to use this image"，
        // 必须点 I agree 才会真正发出 maseQ；否则只能等到 45s 超时。
        const acceptUploadRightsDialog = async () => {
          const deadline = Date.now() + 10000;
          while (!uploadSettled && Date.now() < deadline) {
            const dialog = Array.from(document.querySelectorAll('mat-dialog-container, [role="dialog"], [role="alertdialog"], .cdk-overlay-pane'))
              .find((element) => element.getClientRects().length > 0
                && /rights to use this image/i.test(element.textContent || ''));
            const agree = dialog && Array.from(dialog.querySelectorAll('button'))
              .find((button) => /^i agree$/i.test((button.textContent || '').replace(/\s+/g, ' ').trim()));
            if (agree) {
              agree.click();
              return true;
            }
            await new Promise((resolve) => setTimeout(resolve, 150));
          }
          return false;
        };
        try {
          fileInput.dispatchEvent(new Event('input', { bubbles: true, composed: true }));
          fileInput.dispatchEvent(new Event('change', { bubbles: true, composed: true }));
          acceptUploadRightsDialog().catch(() => {});
          const timeoutResult = new Promise((resolve) => setTimeout(() => resolve({
            status: 'error',
            error: observedUpload
              ? `Timed out waiting for Flow upload RPC ${uploadRpcId}`
              : `Flow upload RPC ${uploadRpcId} was not observed`,
          }), 45000));
          return await Promise.race([uploadCompletion, timeoutResult]);
        } finally {
          restoreUploadMonitor();
        }
      },
      args: [msg.dataUrl, msg.fileName, msg.mimeType],
    }).then((results) => {
      sendResponse({ success: true, result: results?.[0]?.result });
    }).catch((error) => {
      sendResponse({ success: false, error: error.message });
    });
    return true;
  }

  if (msg.type === 'TEST_TRANSLATE') {
    trackOperation(executeTestTranslation(msg)).then(sendResponse);
    return true;
  }

  if (msg.type === 'TEST_TRANSLATE_DOM') {
    trackOperation(executeTestTranslation(msg, true)).then(sendResponse);
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

let stateLoading;
function loadPersistedState() {
  return stateLoading ||= restorePersistedState();
}
async function restorePersistedState() {
  await initializeDefaultConfig(chrome.storage.local, async () => {
    const response = await fetch(chrome.runtime.getURL('private-service-preset.json'));
    if (!response.ok) throw new Error('Private service preset is unavailable');
    return response.json();
  });
  const stored = await chrome.storage.local.get([
    'taskHistory',
    'logHistory',
    ROUND_STATE_KEY,
    STATS_STORAGE_KEY,
    GENERATION_MODE_STORAGE_KEY,
    FLOW_CONCURRENCY_STORAGE_KEY,
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
  generationMode = normalizeGenerationMode(stored[GENERATION_MODE_STORAGE_KEY]);
  flowConcurrency = normalizeFlowConcurrency(stored[FLOW_CONCURRENCY_STORAGE_KEY]);
  flowTasks.setLimit(flowConcurrency);
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
  await roundRecovery.restore(stored[ROUND_STATE_KEY], pollPaused);
  if (roundRecovery.state.phase === 'stopped' && !pollPaused) {
    pausePoll('Flow stopped; click Run Now', { code: 'RECOVERY_INTERRUPTED' });
  }
  if (pollPaused || generationMode !== 'api-2.3.5.1') {
    releaseApi2351().catch(error => addLog('warn', `Verification helper cleanup: ${error.message}`));
  }
  await refreshReuseSummary();
}

async function loadConfig() {
  await ensureBridgeId();
  const stored = await chrome.storage.local.get(['services', GENERATION_MODE_STORAGE_KEY, FLOW_CONCURRENCY_STORAGE_KEY]);
  return {
    bridgeId,
    services: Array.isArray(stored.services) ? stored.services : [],
    generationMode: normalizeGenerationMode(stored[GENERATION_MODE_STORAGE_KEY]),
    flowConcurrency: normalizeFlowConcurrency(stored[FLOW_CONCURRENCY_STORAGE_KEY]),
    maxFlowConcurrency: MAX_FLOW_CONCURRENCY,
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
  const nextGenerationMode = normalizeGenerationMode(config.generationMode);
  const nextConcurrency = normalizeFlowConcurrency(config.flowConcurrency);
  await chrome.storage.local.set({
    services,
    [GENERATION_MODE_STORAGE_KEY]: nextGenerationMode,
    [FLOW_CONCURRENCY_STORAGE_KEY]: nextConcurrency,
  });
  generationMode = nextGenerationMode;
  if (generationMode !== 'api-2.3.5.1') {
    releaseApi2351().catch(error => addLog('warn', `Verification helper cleanup: ${error.message}`));
  }
  flowConcurrency = nextConcurrency;
  flowTasks.setLimit(nextConcurrency);
}

function normalizeFlowConcurrency(value) {
  const n = Math.round(Number(value));
  if (!Number.isFinite(n)) return DEFAULT_FLOW_CONCURRENCY;
  return Math.min(MAX_FLOW_CONCURRENCY, Math.max(1, n));
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
  if (flowWorkBlocked()) {
    return;
  }
  // reCAPTCHA 恢复链运行中（reload + 建 project + settle）— 不发起新 poll，避免撞上半残 Flow tab
  if (recoveryPromise || openingFlowPromise) {
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

function getPrefetchedTaskSummary() {
  if (!prefetchedTask) return null;
  const { service, task, preparedAt } = prefetchedTask;
  return {
    service: service.baseUrl,
    taskId: task.taskId,
    subTaskId: task.subTaskId,
    assignmentId: task.assignmentId,
    targetLang: task.targetLanguage || task.targetLanguageCode || 'Simplified Chinese',
    preparedAt,
    phase: 'standby',
    sourceImage: ensureDataUrl(task.imageBase64),
    sourceThumb: prefetchedTask.sourceThumb || null,
    prompt: buildPrompt(task),
  };
}

function reserveFlowSlotAndExecute(service, task, conn, { prefetched = false } = {}) {
  if (flowWorkBlocked()) return false;
  if (submissionPacer.remainingMs > 0 || !flowTasks.reserveSubmission(task.assignmentId)) return false;
  addLog('info', `${prefetched ? 'Starting prefetched task' : 'Task received'}: ${task.subTaskId} from ${service.baseUrl}`);
  trackOperation(executeTask(service, task, conn))
    .catch((e) => addLog('error', `Task runner error: ${e.message}`));
  return true;
}

function startPrefetchedTask() {
  if (!prefetchedTask || flowTasks.submissionOwner || !flowTasks.hasCapacity
      || flowWorkBlocked() || recoveryPromise || !flowTabAvailable) {
    return false;
  }
  if (submissionPacer.remainingMs > 0) {
    scheduleLoop(submissionPacer.remainingMs);
    return false;
  }
  const next = prefetchedTask;
  prefetchedTask = null;
  const started = reserveFlowSlotAndExecute(next.service, next.task, next.conn, { prefetched: true });
  if (!started) prefetchedTask = next;
  else scheduleLoop(0); // Refill the single standby slot while generation runs.
  return started;
}

/**
 * 预取的图还没提交给 Flow，而本 bridge 暂时跑不了它（翻译失败 / 已停止 / Flow tab 关闭）时，
 * 立即 reportFail 把它还给服务端重派给别的 bridge，不要攥在内存里等 lease 过期。
 * 不计失败统计、不推进连续失败数 —— 这张图根本没碰过 Google。
 * 同步摘掉 prefetchedTask，保证之后任何 releaseFlowSlot 都不会再把它启动起来。
 */
function releasePrefetchedTask(reason) {
  if (!prefetchedTask) return false;
  const { service, task, preparedAt } = prefetchedTask;
  prefetchedTask = null;
  broadcastTasksChanged();
  addLog('warn', `Standby task released for another bridge: ${task.subTaskId} (${reason})`);
  trackOperation(reportFailWithRetry(service, {
    bridgeId,
    assignmentId: task.assignmentId,
    errorCode: 'PREFETCH_RELEASED',
    message: `Prefetched task released before submission: ${reason}`,
    retryable: true,
    elapsedMs: Date.now() - preparedAt,
  })).catch((e) => addLog('warn', `Standby release report failed: ${e.message}`));
  return true;
}

function releaseFlowSlot(assignmentId) {
  if (!flowTasks.release(assignmentId)) return false;
  if (!startPrefetchedTask()) scheduleLoop(0);
  return true;
}

async function runLoop() {
  if (running) return;
  running = true;
  let scheduleNext = true;
  try {
    if (flowWorkBlocked() || recoveryPromise || !flowTabAvailable) return;
    startPrefetchedTask();
    // Download one standby source image while the single Flow slot is busy.
    if ((prefetchedTask ? 1 : 0) >= PREFETCH_LIMIT) {
      if (!flowTasks.submissionOwner && flowTasks.hasCapacity) {
        scheduleLoop(submissionPacer.remainingMs);
      } else {
        nextPollAt = 0;
        broadcast({ type: 'COUNTDOWN_UPDATE', nextPollAt });
      }
      scheduleNext = false;
      return;
    }

    const config = await loadConfig();
    const services = config.services.filter((s) => s.enabled !== false && s.baseUrl && s.token);
    const conn = await checkConnection().catch((e) => ({ connected: false, reason: e.message }));
    if (flowWorkBlocked()) return;
    lastStatus = { connected: conn.connected, message: conn.reason || 'Connected', projectId: conn.projectId };
    broadcast({ type: 'CONNECTION_CHANGED', ...lastStatus });

    if (!conn.connected) {
      return;
    }
    const flowTab = await chrome.tabs.get(conn.tabId);
    roundRecovery.state.target = { homeUrl: buildFlowHomeUrl(conn.flowUrl), windowId: flowTab.windowId, tabId: conn.tabId };
    await roundRecovery.save();
    if (flowWorkBlocked()) return;
    if (services.length === 0) {
      return;
    }

    // 单次 tick 至多启动 1 个任务（对齐 nano-b lt 调度器每 tick 至多 g() 一次）
    const orderedServices = rotateServices(services);
    for (let i = 0; i < orderedServices.length; i++) {
      if (flowWorkBlocked()) break;
      const service = orderedServices[i];
      const task = await pollTask(service, conn, flowTasks.inUse > 0 || !!prefetchedTask);
      if (task?.hasTask) {
        serviceCursor = (serviceCursor + i + 1) % orderedServices.length;
        // Poll returns the downloaded source bytes, so this slot is ready locally.
        const prepared = { service, task, conn, preparedAt: Date.now() };
        prefetchedTask = prepared;
        broadcastTasksChanged();
        // poll 在途时 bridge 可能已经停止 / tab 已关：这张图跑不了，立刻还回去。
        if (flowWorkBlocked() || !flowTabAvailable) {
          releasePrefetchedTask(pollPaused ? 'bridge stopped while polling' : 'Flow tab closed while polling');
          break;
        }
        prepared.sourceThumb = await createThumbnail(task.imageBase64, 64);
        addLog('info', `Next image prepared locally: ${task.subTaskId}`);
        broadcastTasksChanged();
        startPrefetchedTask();
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

async function pollTask(service, conn, busy) {
  if (flowWorkBlocked()) return null;
  const pending = { service: service.baseUrl, startedAt: Date.now(), phase: 'downloading_source' };
  let pendingShown = false;
  return postJson(service, '/turboflow-bridge/tasks/poll', {
    bridgeId,
    version: VERSION,
    flowConnected: !!conn.connected,
    projectId: conn.projectId || null,
    currentUrl: null,
    busy: !!busy,
  }, { onResponse: () => {
    currentTasks.push(pending);
    pendingShown = true;
    broadcastTasksChanged();
  } }).catch((e) => {
    addLog('warn', `Poll failed: ${service.baseUrl} ${e.message}`);
    return null;
  }).finally(() => {
    if (pendingShown) {
      currentTasks = currentTasks.filter((item) => item !== pending);
      broadcastTasksChanged();
    }
  });
}

async function executeTask(service, task, conn = null) {
  const roundId = roundRecovery.state.roundId;
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
    phase: 'preparing',
  };
  currentTasks.push(taskState);
  broadcastTasksChanged();
  const sourceThumb = await createThumbnail(task.imageBase64, 64);
  taskState.sourceThumb = sourceThumb;
  broadcastTasksChanged();

  const startedAt = Date.now();
  const targetLanguageKey = task.targetLanguageCode || task.targetLanguage || targetLang;
  const context = { sourceThumb, sourceImage, targetLang, targetLanguageKey, startedAt };
  let translationPromise = null;
  let imageHash = null;
  let taskSequence = null;
  try {
    imageHash = await imageDigest(task.imageBase64);

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

    // 2.5 上传拒绝计数：maseQ RPC 3 的图片已退回服务端重派。同一张图再派到本 bridge 时
    // 不再上传，直接按记住的错误退回；收到次数达到阈值才按政策回退保留原图。
    // 每个任务都推进序号，最后一次收到后连续 20 条其他任务没再出现就取消标记。
    const observed = await observeTaskForUploadRejection(chrome.storage.local, imageHash);
    taskSequence = observed.sequence;
    for (const stale of observed.forgotten) {
      addLog('info', `Upload rejection mark expired for image ${String(stale.imageHash).slice(0, 12)} after ${UPLOAD_REJECTION_FORGET_AFTER_TASKS} other tasks`);
    }
    if (observed.record) {
      if (shouldFallBackToPolicy(observed.record)) {
        await completeUploadRejectionAsPolicy(service, task, context, observed.record);
      } else {
        await failUploadRejectedTask(service, task, context, observed.record);
      }
      return;
    }

    // 3. 真正翻译
    taskState.phase = 'submitting';
    broadcastTasksChanged();
    translationPromise = trackOperation(translateImage(task, conn).then(result => {
      roundRecovery.success(roundId, task.assignmentId);
      return result;
    }));
    const result = await runWithTimeout(
      translationPromise,
      TRANSLATE_TIMEOUT_MS,
      `translate timeout (${Math.round(TRANSLATE_TIMEOUT_MS / 1000)}s)`,
    );
    // The generated image is now fully downloaded into extension memory. Free
    // this generation slot before reporting so another serial submission can
    // start while this task's server upload continues.
    taskState.phase = 'reporting';
    broadcastTasksChanged();
    releaseFlowSlot(task.assignmentId);
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
    if (e.code === 'FLOW_SUBMISSION_PAUSED' || /New Flow submissions.*paused/i.test(e.message || '')) {
      // This assignment never submitted generation. Return it for retry without
      // counting another Google failure or disturbing already-submitted tasks.
      await reportFailWithRetry(service, {
        bridgeId, assignmentId: task.assignmentId,
        errorCode: e.code, message: e.message, retryable: true, elapsedMs: elapsed,
      });
      addLog('info', `Task not submitted while paused: ${task.subTaskId}`);
      removeCurrentTask(task.assignmentId);
      return;
    }
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
    const unusual = observeFlowError(e, conn);
    const errorCode = classifyErrorCode(e);
    if (errorCode === 'FLOW_UPLOAD_REJECTED' && imageHash) {
      // 首次 maseQ RPC 3：记住摘要和错误，任务沿下面的普通失败路径以可重试上报、由服务端重派。
      // 全局连续失败照常计数：如果 Flow 改了上传协议导致所有图都返回 3，5 张后仍会兜底停下。
      const record = await rememberUploadRejection(chrome.storage.local, imageHash, {
        rpcId: e.rpcId || null,
        rpcStatus: e.rpcStatus ?? 3,
        apiStatus: 'INVALID_ARGUMENT',
        reason: e.reason || null,
        errorMessage: e.message,
        sequence: taskSequence,
      });
      if (shouldFallBackToPolicy(record)) {
        await completeUploadRejectionAsPolicy(service, task, context, record);
        return;
      }
      addLog('warn', `Upload rejected by Flow (${record.receipts}/${UPLOAD_REJECTION_POLICY_THRESHOLD}) for ${task.subTaskId}; returning the task for reassignment`);
    }
    // 翻译失败说明本 bridge 当前状态不可靠：预取的那张图同步还给服务端，让别的 bridge 去跑。
    // 必须抢在下面任何 await 之前，否则别的任务释放槽位时会先把它启动起来。
    releasePrefetchedTask(`task ${task.subTaskId} failed [${errorCode}]`);
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
    if (!unusual) applyFailureStreak(FAILURE_STREAK_INCREMENT, { errorCode, errorMessage: e.message });
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
    // - DAILY_QUOTA_REACHED / FLOW_RESOURCE_EXHAUSTED → 立即停止，等待额度恢复
    // - RECAPTCHA_BLOCKED   → 决策 L1/L2 触发恢复链；recovery 失败由 triggerRecovery 内部走 stopAndDelete
    // - DOWNLOAD_FAILED     → 连续计数；达 3 张触发 L1 恢复
    // - FLOW_DISCONNECTED   → tab 在却连续 3 次失败则暂停；tab 真没了交给 watchdog 自动恢复
    // - 任意错误连续 5 次   → 兜底暂停（applyFailureStreak 已处理）
    // - GOOGLE_BLOCKED / TIMEOUT / 其它 → 500ms 后正常重试
    if (pollPaused && isQuotaErrorCode(pauseReasonCode)) return;
    if (errorCode === 'DOWNLOAD_FAILED') {
      recoveryState.consecutiveDownloadFails++;
      persistRecoveryState();
      if (recoveryState.consecutiveDownloadFails >= RECOVERY_DOWNLOAD_FAIL_THRESHOLD) {
        pausePoll('连续下载失败，请点击 Run Now 恢复', { code: 'DOWNLOAD_FAILED' });
      } else {
        scheduleLoop(POLL_INTERVAL_MS);
      }
    } else if (!unusual && errorCode === 'RECAPTCHA_BLOCKED') {
      pausePoll(e.message, { code: 'FLOW_VERIFICATION_REQUIRED' });
    } else {
      // FLOW_DISCONNECTED / GOOGLE_BLOCKED / TIMEOUT / 其它：普通可重试失败。
      // 停止态下 scheduleLoop 自身会短路，不会领到新任务。
      scheduleLoop(POLL_INTERVAL_MS);
    }
  } finally {
    // Cache hits, policy fallbacks and failures may exit before or after the
    // submitted event. Release either the serial submission slot or the exact
    // generation slot owned by this assignment.
    // A local timeout does not cancel the page request. Retain its slot until
    // that request settles, otherwise a fifth generation could be launched.
    if (translationPromise) {
      translationPromise.then(
        () => releaseFlowSlot(task.assignmentId),
        () => releaseFlowSlot(task.assignmentId),
      );
    } else {
      releaseFlowSlot(task.assignmentId);
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
 * 同一张图在标记期内再次派到本 bridge：不调 Google，直接用记住的错误退回服务端重派。
 * 没有真的上传，所以对全局连续失败计数中立，也不碰断连计数和预取任务。
 */
async function failUploadRejectedTask(service, task, context, record) {
  const elapsed = Date.now() - context.startedAt;
  const message = `Upload previously rejected by Flow (${record.receipts}/${UPLOAD_REJECTION_POLICY_THRESHOLD}): ${record.errorMessage || record.reason || 'INVALID_ARGUMENT'}`;
  await reportFailWithRetry(service, {
    bridgeId,
    assignmentId: task.assignmentId,
    errorCode: 'FLOW_UPLOAD_REJECTED',
    message,
    retryable: true,
    elapsedMs: elapsed,
  });
  addLog('warn', `Task returned without upload [FLOW_UPLOAD_REJECTED ${record.receipts}/${UPLOAD_REJECTION_POLICY_THRESHOLD}]: ${task.subTaskId}`);
  recordStat(STAT_FAILED);
  addTaskHistory({
    taskId: task.taskId,
    subTaskId: task.subTaskId,
    service: service.baseUrl,
    status: 'failed',
    error: message,
    elapsedMs: elapsed,
    time: Date.now(),
    sourceThumb: context.sourceThumb,
    sourceImage: context.sourceImage,
    targetLang: context.targetLang,
  });
  removeCurrentTask(task.assignmentId);
  scheduleLoop(POLL_INTERVAL_MS);
}

/**
 * 同一张图被派回达到阈值：按旧的政策回退链路保留原图，服务端写入政策缓存后不再下发。
 */
async function completeUploadRejectionAsPolicy(service, task, context, record) {
  const policy = await rememberImagePolicyFallback(chrome.storage.local, task.imageBase64, {
    apiStatus: record.apiStatus || 'INVALID_ARGUMENT',
    reason: record.reason || record.apiStatus || 'INVALID_ARGUMENT',
  });
  await forgetUploadRejection(chrome.storage.local, record.imageHash);
  addLog('warn', `Upload rejected ${record.receipts} times for ${task.subTaskId}; keeping the original image by policy fallback`);
  await completePolicyFallbackTask(service, task, context, policy);
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
  for (let attempt = 0; attempt <= COMPLETION_REPORT_MAX_RETRIES; attempt++) {
    setTaskPhase(payload.assignmentId, attempt ? 'reporting_retry' : 'reporting', attempt);
    try {
      // Notify before sending the large image. A missing/temporarily unreachable
      // notice endpoint must not discard a finished translation (older servers).
      try {
        await postJson(service, '/turboflow-bridge/tasks/translated', {
          bridgeId: payload.bridgeId, assignmentId: payload.assignmentId,
        }, { timeoutMs: TRANSLATED_NOTICE_TIMEOUT_MS });
      } catch (noticeError) {
        addLog('warn', `Translation notice failed; still attempting image upload: ${noticeError.message}`);
      }
      await postJson(service, '/turboflow-bridge/tasks/complete', payload,
        { timeoutMs: COMPLETION_REPORT_TIMEOUT_MS });
      if (attempt > 0) {
        addLog('info', `Completion reported on retry ${attempt}: ${payload.assignmentId}`);
      }
      return;
    } catch (err) {
      if (isReprocessRequired(err)) {
        addLog('warn', `Server asked to reprocess ${payload.assignmentId} — caching the translated image instead of retrying`);
        throw err;
      }
      if (attempt === COMPLETION_REPORT_MAX_RETRIES) {
        throw err;
      }
      setTaskPhase(payload.assignmentId, 'reporting_retry', attempt + 1);
      const backoff = Math.min(30000, FAIL_REPORT_RETRY_BASE_MS * Math.pow(2, attempt));
      addLog('warn', `Completion report attempt ${attempt + 1} failed (${err.message}), retrying in ${backoff}ms`);
      await sleep(backoff);
    }
  }
}

/**
 * 翻译成功但上报失败：把译图按源图 sha256 落盘，等服务端重派同一张图时复用。
 * 原 assignment 的回传重试耗尽后才 reportFail，释放任务并保留译图作最后的复用兜底。
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
  setTaskPhase(task.assignmentId, 'reporting');
  releaseFlowSlot(task.assignmentId);
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
    await retainTranslationForReuse(service, task, context, cached, cached.imageHash, reportError);
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
  setTaskPhase(payload.assignmentId, 'reporting');
  return reportPolicyFallback({
    post: (completion) => postJson(service, '/turboflow-bridge/tasks/complete', completion),
    payload,
    maxRetries: FAIL_REPORT_MAX_RETRIES,
    retryBaseMs: FAIL_REPORT_RETRY_BASE_MS,
    sleep,
    onRetry: (attempt, backoff, error) => {
      setTaskPhase(payload.assignmentId, 'reporting_retry', attempt);
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
  const tasks = currentTasks.map((task) => ({ ...task }));
  const standby = getPrefetchedTaskSummary();
  if (standby) tasks.push(standby);
  return tasks;
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

function assertFlowSubmissionAllowed() {
  if (flowWorkBlocked()) {
    throw Object.assign(new Error('New Flow submissions paused; already-submitted translations continue'), {
      code: 'FLOW_SUBMISSION_PAUSED',
    });
  }
}

async function translateImage(task, conn) {
  if (!conn || !conn.tabId || !conn.projectId) throw new Error('Flow is not connected');
  const mode = generationMode;
  // 每张图按自身实际像素选最接近的比例；服务端元数据宽高可能缺失或不准，只作兜底。
  const size = await readImageSize(task.imageBase64)
    || { width: task.sourceWidth, height: task.sourceHeight, fromServer: true };
  const aspectRatio = aspectRatioFor(size.width, size.height);
  addLog('info', `Aspect ratio for ${task.subTaskId || task.assignmentId}: ${size.width || '?'}x${size.height || '?'}${size.fromServer ? ' (server)' : ''} → ${ASPECT_RATIO_LABELS[aspectRatio]}`);
  const options = {
    ...task,
    imageBase64: ensureDataUrl(task.imageBase64),
    fileName: buildDomUploadFileName(task, task.fileName || 'source.png'),
    prompt: buildPrompt(task),
    aspectRatio,
    model: sanitizeModel(task.model),
  };
  assertFlowSubmissionAllowed();
  return translateImageForMode(mode, conn, {
    ...options,
    beforeSubmit: assertFlowSubmissionAllowed,
    onPhase: (phase) => setTaskPhase(task.assignmentId, phase),
    onSubmitted: ({ submittedAt }) => {
      if (!flowTasks.acceptSubmission(task.assignmentId)) return;
      submissionPacer.submitted(submittedAt);
      const state = currentTasks.find((item) => item.assignmentId === task.assignmentId);
      if (state) state.phase = 'generating';
      broadcastTasksChanged();
      addLog('info', `${generationModeLabel(mode)} translation submitted: ${task.subTaskId}; ${flowTasks.generatingOwners.size}/${flowTasks.limit} generating`);
      scheduleLoop(0);
    },
  }, runFlowDomTranslation);
}

async function runFlowDomTranslation(conn, task) {
  const requestId = `${task.assignmentId || 'flow'}:${crypto.randomUUID()}`;
  const taskPayload = {
    assignmentId: task.assignmentId || null,
    imageBase64: ensureDataUrl(task.imageBase64),
    fileName: task.fileName || 'turboflow-source.png',
    mimeType: task.mimeType || 'image/png',
    aspectRatio: task.aspectRatio || 'IMAGE_ASPECT_RATIO_LANDSCAPE',
    model: sanitizeModel(task.model),
    stealthMode: task.stealthMode !== false,
    delayMin: Number(task.delayMin || 0),
    delayMax: Number(task.delayMax || 0),
    prompt: task.prompt || '',
  };
  let result = null;
  let lastChannelError = null;
  for (let attempt = 1; attempt <= DOM_PORT_CONNECT_ATTEMPTS; attempt++) {
    // Re-execution is idempotent: V25 refreshes its listeners without resetting
    // the UI queue, claimed Tile registry, or in-flight request state.
    await chrome.scripting.executeScript({
      target: { tabId: conn.tabId },
      files: ['flow-dom-method.js'],
      world: 'ISOLATED',
    });
    try {
      result = await sendFlowDomRequestOverPort(conn.tabId, requestId, taskPayload);
      break;
    } catch (error) {
      lastChannelError = error;
      if (!error?.retryableChannel || attempt >= DOM_PORT_CONNECT_ATTEMPTS) throw error;
      addLog('warn', `Flow page channel retry ${attempt}/${DOM_PORT_CONNECT_ATTEMPTS} for ${task.subTaskId || task.assignmentId}: ${error.message}`);
      await sleep(200 * attempt);
    }
  }
  if (!result) throw lastChannelError || new Error(`Flow page automation returned no result for ${requestId}`);
  if (!result.generateToken) throw new Error(`Flow page automation did not report a submitted generation for ${requestId}`);

  // 页面只负责到「ogiZ0b 已发出」为止；结果以 ogiZ0b 响应里的 media 为准，
  // 不再按 DOM Tile 猜测（Tile 网格重渲染会让旧 Tile 看起来像新 Tile）。
  const generated = await resolveModernGenerateResponse(conn.tabId, result.generateToken, { projectId: conn.projectId });
  const resultDataUrl = generated.resultDataUrl || null;
  if (!resultDataUrl) throw new Error('Flow generation completed but no readable result image was returned');
  addLog('info', `Flow generated ${generated.mediaId || 'image'} for ${task.subTaskId || task.assignmentId || requestId}`);
  return { resultDataUrl, resultUrl: generated.resultUrl || null };
}

// Flow 的 Start generation 只响应 isTrusted 事件：el.click()、合成 pointer/mouse 事件和
// 合成 Enter 都会被忽略（不发 ogiZ0b，Tile 不出现，任务卡在「上传中」）。
// 这里通过 CDP Input.dispatchMouseEvent 发一次真实点击，点完立即 detach。
let trustedClickQueue = Promise.resolve();
const SUBMIT_CLICK_ATTEMPTS = 3;
const SUBMIT_SENT_TIMEOUT_MS = 5000;
const SUBMIT_CLEARED_GRACE_MS = 15000;

// 提交 = 先挂 ogiZ0b 监听，再真实点击；以「ogiZ0b 真的发出」为提交成功。
// 没发出且 prompt 还在 → 重点（最多 3 次）；prompt 已被清空说明 Flow 已受理，只延长等待，
// 绝不重复点击，避免同一张图生成两次。
async function submitFlowGeneration(tabId, selector, tabUrl, referenceMediaIds = []) {
  if (!isModernFlowUrl(tabUrl || '')) {
    throw new Error('Flow UI mode requires flow.google.com; open the new Flow and retry');
  }
  const expected = Array.isArray(referenceMediaIds) ? referenceMediaIds.filter((id) => typeof id === 'string' && id) : [];
  if (!expected.length) addLog('warn', 'Source media id unavailable; generation request will be matched by submit order only');
  const token = await armModernGenerateMonitor(tabId, { referenceMediaIds: expected });
  let lastError = null;
  for (let attempt = 1; attempt <= SUBMIT_CLICK_ATTEMPTS; attempt++) {
    try {
      await dispatchTrustedClick(tabId, selector);
    } catch (error) {
      lastError = error;
    }
    let state = await waitModernGenerateSent(tabId, token, SUBMIT_SENT_TIMEOUT_MS);
    if (state.sent) return token;
    if (state.lost) throw new Error('Flow page reloaded while submitting generation');
    if (state.promptCleared) {
      state = await waitModernGenerateSent(tabId, token, SUBMIT_CLEARED_GRACE_MS);
      if (state.sent) return token;
      throw new Error('Flow cleared the prompt but no generation request was sent');
    }
    addLog('warn', `Start generation not accepted (attempt ${attempt}/${SUBMIT_CLICK_ATTEMPTS})${lastError ? ': ' + lastError.message : ''}; retrying click`);
    await sleep(500);
  }
  throw new Error(`Flow did not send the generation request after ${SUBMIT_CLICK_ATTEMPTS} clicks${lastError ? ': ' + lastError.message : ''}`);
}

function dispatchTrustedClick(tabId, selector) {
  const run = trustedClickQueue.then(() => dispatchTrustedClickNow(tabId, selector));
  trustedClickQueue = run.catch(() => {});
  return run;
}

async function dispatchTrustedClickNow(tabId, selector) {
  if (typeof selector !== 'string' || !selector) throw new Error('Trusted click selector is missing');
  const target = { tabId };
  const send = (method, params) => chrome.debugger.sendCommand(target, method, params);
  let attached = false;
  try {
    await chrome.debugger.attach(target, '1.3');
    attached = true;
  } catch (error) {
    if (!/already attached/i.test(error?.message || '')) {
      throw new Error(`Flow trusted click unavailable (close DevTools on the Flow tab and retry): ${error?.message || error}`);
    }
  }
  try {
    // attach 会弹出「正在调试此浏览器」信息栏并改变视口高度；等按钮位置稳定后再取坐标。
    const measure = async () => {
      const { result } = await send('Runtime.evaluate', {
        expression: `(() => {
          const el = document.querySelector(${JSON.stringify(selector)});
          if (!el || el.disabled || el.getAttribute('aria-disabled') === 'true') return null;
          el.scrollIntoView({ block: 'center', inline: 'center' });
          const r = el.getBoundingClientRect();
          if (!r.width || !r.height) return null;
          const x = r.left + r.width / 2;
          const y = r.top + r.height / 2;
          return { x, y, vh: window.innerHeight, hit: el.contains(document.elementFromPoint(x, y)) };
        })()`,
        returnByValue: true,
      });
      return result?.value || null;
    };
    // 信息栏出现有延迟和动画：视口高度与按钮坐标连续 3 次（约 300ms）不变才点击。
    await sleep(200);
    let point = null;
    let stableReads = 0;
    for (let attempt = 0; attempt < 30 && stableReads < 3; attempt++) {
      const next = await measure();
      const same = next && point && next.vh === point.vh
        && Math.abs(next.x - point.x) < 1 && Math.abs(next.y - point.y) < 1;
      stableReads = same && next.hit ? stableReads + 1 : 0;
      point = next;
      if (stableReads < 3) await sleep(100);
    }
    if (!point) throw new Error(`Flow trusted click target not found or disabled: ${selector}`);
    if (!point.hit) throw new Error(`Flow trusted click target is covered: ${selector}`);
    const base = { x: point.x, y: point.y, button: 'left', clickCount: 1 };
    await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: point.x, y: point.y });
    await sleep(60);
    await send('Input.dispatchMouseEvent', { ...base, type: 'mousePressed', buttons: 1 });
    await sleep(40);
    await send('Input.dispatchMouseEvent', { ...base, type: 'mouseReleased', buttons: 0 });
  } finally {
    if (attached) await chrome.debugger.detach(target).catch(() => {});
  }
}

function sendFlowDomRequestOverPort(tabId, requestId, taskPayload) {
  const port = chrome.tabs.connect(tabId, { name: DOM_TRANSLATE_PORT, frameId: 0 });
  return new Promise((resolve, reject) => {
    let settled = false;
    let accepted = false;
    const finish = (callback, value) => {
      if (settled) return;
      settled = true;
      clearTimeout(ackTimer);
      callback(value);
      try { port.disconnect(); } catch {}
    };
    const fail = (message, retryableChannel = false) => {
      const error = new Error(message);
      error.retryableChannel = retryableChannel;
      finish(reject, error);
    };
    const ackTimer = setTimeout(() => {
      fail(`Flow page automation did not acknowledge request ${requestId} within ${DOM_PORT_ACK_TIMEOUT_MS / 1000}s`, true);
    }, DOM_PORT_ACK_TIMEOUT_MS);

    port.onMessage.addListener((message) => {
      if (message?.requestId !== requestId) return;
      if (message.type === 'FLOW_DOM_TRANSLATION_ACCEPTED') {
        accepted = true;
        clearTimeout(ackTimer);
        return;
      }
      if (message.type !== 'FLOW_DOM_TRANSLATION_RESULT') return;
      if (!message.ok) {
        fail(message.error || `Flow page automation failed after acceptance (${requestId})`);
        return;
      }
      finish(resolve, message);
    });
    port.onDisconnect.addListener(() => {
      if (settled) return;
      const detail = chrome.runtime.lastError?.message || 'message port disconnected';
      fail(`Flow page automation channel closed before result (accepted=${accepted}): ${detail}`, true);
    });

    try {
      port.postMessage({
        type: DOM_TRANSLATE_MESSAGE,
        requestId,
        task: taskPayload,
      });
    } catch (error) {
      fail(`Flow page automation request could not be sent: ${error?.message || String(error)}`, true);
    }
  });
}

function buildDomUploadFileName(task, fallbackName) {
  const mimeType = String(task?.mimeType || '').toLowerCase();
  const fallback = String(fallbackName || 'source.png');
  const extensionMatch = fallback.match(/(\.[a-z0-9]{2,5})$/i);
  const extension = extensionMatch?.[1]
    || (mimeType.includes('jpeg') ? '.jpg' : mimeType.includes('webp') ? '.webp' : '.png');
  const identity = task?.assignmentId || task?.subTaskId || task?.taskId || crypto.randomUUID();
  const safeIdentity = String(identity).replace(/[^a-z0-9_-]+/gi, '-').slice(0, 80) || 'image';
  return `turboflow-${safeIdentity}${extension.toLowerCase()}`;
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

// Flow 已下线 Imagen 4（IMAGEN_3_5），旧任务带该模型时回落到 NARWHAL。
const SUPPORTED_MODELS = ['GEM_PIX_2', 'NARWHAL'];

function sanitizeModel(model) {
  if (model && SUPPORTED_MODELS.includes(model)) return model;
  return 'NARWHAL';
}

const ASPECT_RATIO_LABELS = {
  IMAGE_ASPECT_RATIO_LANDSCAPE: '16:9',
  IMAGE_ASPECT_RATIO_LANDSCAPE_FOUR_THREE: '4:3',
  IMAGE_ASPECT_RATIO_SQUARE: '1:1',
  IMAGE_ASPECT_RATIO_PORTRAIT_THREE_FOUR: '3:4',
  IMAGE_ASPECT_RATIO_PORTRAIT: '9:16',
};

function aspectRatioFor(width, height) {
  if (!(width > 0) || !(height > 0)) return 'IMAGE_ASPECT_RATIO_LANDSCAPE';
  const ratio = width / height;
  const options = [
    { value: 16 / 9, key: 'IMAGE_ASPECT_RATIO_LANDSCAPE' },
    { value: 4 / 3, key: 'IMAGE_ASPECT_RATIO_LANDSCAPE_FOUR_THREE' },
    { value: 1, key: 'IMAGE_ASPECT_RATIO_SQUARE' },
    { value: 3 / 4, key: 'IMAGE_ASPECT_RATIO_PORTRAIT_THREE_FOUR' },
    { value: 9 / 16, key: 'IMAGE_ASPECT_RATIO_PORTRAIT' },
  ];
  // 按对数距离比较，横图和竖图对称（1.5 与 2/3 分别落到 4:3 与 3:4）。
  const distance = (value) => Math.abs(Math.log(ratio / value));
  return options.reduce((best, item) =>
    distance(item.value) < distance(best.value) ? item : best
  ).key;
}

async function readImageSize(base64OrDataUrl) {
  if (!base64OrDataUrl) return null;
  try {
    const blob = await (await fetch(ensureDataUrl(base64OrDataUrl))).blob();
    const bmp = await createImageBitmap(blob);
    const size = { width: bmp.width, height: bmp.height };
    bmp.close();
    return size.width > 0 && size.height > 0 ? size : null;
  } catch {
    return null;
  }
}

function ensureDataUrl(value) {
  if (!value) return null;
  return value.startsWith('data:') ? value : 'data:image/png;base64,' + value;
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

function setTaskPhase(assignmentId, phase, reportRetry = 0) {
  const task = currentTasks.find((item) => item.assignmentId === assignmentId);
  if (!task) return;
  task.phase = phase;
  task.reportRetry = reportRetry;
  broadcastTasksChanged();
}

async function postJson(service, path, body, { timeoutMs = 120000, onResponse } = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(normalizeBaseUrl(service.baseUrl) + path, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${service.token}`,
      },
      body: JSON.stringify(body),
      signal: controller.signal,
    });
    if (res.ok) onResponse?.();
    const text = await res.text();
    if (!res.ok) {
      throw new Error(`HTTP ${res.status}: ${text.substring(0, 300)}`);
    }
    const result = text ? JSON.parse(text) : {};
    if (result.accepted === false) throw new Error(result.reason || result.message || 'Server rejected request');
    return result;
  } finally {
    clearTimeout(timer);
  }
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
