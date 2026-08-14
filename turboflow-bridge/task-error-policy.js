/**
 * Map task errors to the stable error codes reported to the bridge service.
 * Prefer structured error codes from flow-api.js, while retaining message
 * matching for errors restored from logs or thrown by older call sites.
 */

/** 连续 N 次「Flow tab 明明在、却还是断连失败」后停下等 Run Now。 */
export const FLOW_DISCONNECTED_PAUSE_THRESHOLD = 3;

/** 连续 N 次任务失败（任何错误码，含译图上报失败）后停下等 Run Now。 */
export const CONSECUTIVE_FAILURE_PAUSE_THRESHOLD = 5;

/** 一次任务结局对「连续失败」计数的影响。 */
export const FAILURE_STREAK_INCREMENT = 'increment';
export const FAILURE_STREAK_RESET = 'reset';
export const FAILURE_STREAK_NEUTRAL = 'neutral';

export function classifyErrorCode(errorOrMessage) {
  const structuredCode = typeof errorOrMessage === 'object' && errorOrMessage
    ? errorOrMessage.code
    : null;
  if (structuredCode === 'FLOW_AUTHENTICATION_FAILED') return structuredCode;

  const message = typeof errorOrMessage === 'string'
    ? errorOrMessage
    : errorOrMessage?.message;
  const text = (message || '').toLowerCase();

  // 上传或生成接口返回 401/UNAUTHENTICATED 表示当前 Flow 凭据已失效。该错误必须暂停，
  // 等用户重新登录并点击 Run Now；不能按普通 FLOW_EXECUTION_FAILED 持续重试。
  // flow-api.js 现在会抛带 code 的结构化错误，这里的文案匹配只兜从日志恢复的旧错误。
  if (text.includes('http 401')
    && (text.includes('unauthenticated') || text.includes('invalid authentication credentials'))) {
    return 'FLOW_AUTHENTICATION_FAILED';
  }
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
 * Errors that put the bridge into its persisted stopped state until the user
 * explicitly clicks Run Now. Kept separate from stop-and-delete conditions.
 *
 * 三个触发源：
 *   1. Flow 凭据失效 — 必须人重新登录，重试没有意义
 *   2. Flow tab 在却连续 3 次断连失败 — watchdog 救不了的「僵死」页面
 *   3. 任意错误连续 5 次 — 兜底闸门，防止无限重试空转烧额度
 */
export function shouldPauseForRunNow(errorCode, options = {}) {
  if (errorCode === 'FLOW_AUTHENTICATION_FAILED') return true;
  if (errorCode === 'FLOW_DISCONNECTED'
    && Number(options.consecutiveFlowDisconnects) >= FLOW_DISCONNECTED_PAUSE_THRESHOLD) {
    return true;
  }
  return Number(options.consecutiveFailures) >= CONSECUTIVE_FAILURE_PAUSE_THRESHOLD;
}

/**
 * Count consecutive task failures caused by an unavailable Flow tab. Any
 * other error breaks the streak. Successful/policy-fallback tasks reset the
 * persisted streak directly in background.js.
 *
 * 关键点：`flowTabPresent === false`（抛错当下主动探测确认 tab 真的没了）时归零而不是累加。
 * 那种情况 watchdog 会在 tab 重开后自动恢复轮询，不需要人工 Run Now；本阈值只针对
 * 「tab 还在、status 也是 complete，但 executeScript/fetch 一直失败」的僵死页面。
 * 注意 tab 缺失的那次失败在 background.js 里仍然计入全局连续失败数 —— 反复关 tab 本身就该停。
 */
export function nextFlowDisconnectedCount(currentCount, errorCode, options = {}) {
  if (errorCode !== 'FLOW_DISCONNECTED' || options.flowTabPresent === false) return 0;
  const normalizedCount = Number.isFinite(Number(currentCount))
    ? Math.max(0, Number(currentCount))
    : 0;
  return normalizedCount + 1;
}

/**
 * 推进「连续失败」计数。
 *   increment — 走了 reportFail 的失败，以及译图/政策回退上报失败（服务端收不到就是失败）
 *   reset     — 翻译成功、恢复链跑通、用户点 Run Now
 *   neutral   — 内容政策回退：不算失败，但也不打断之前的失败连续性
 */
export function nextConsecutiveFailureCount(currentCount, outcome) {
  const normalizedCount = Number.isFinite(Number(currentCount))
    ? Math.max(0, Number(currentCount))
    : 0;
  if (outcome === FAILURE_STREAK_RESET) return 0;
  if (outcome === FAILURE_STREAK_NEUTRAL) return normalizedCount;
  return normalizedCount + 1;
}
