/**
 * 任务统计（当日 + 累计），独立于 taskHistory 持久化。
 * <p>
 * 以前的 GET_TODAY_STATS 是遍历 taskHistory 数组算出来的，所以有两个硬伤：
 * 超过历史条数上限后当日数就不准，历史一被裁剪数据就永久丢失。
 * 把 TASK HISTORY 降到 10 条之后那套彻底不可用，所以改成独立计数器。
 *
 * 四个口径：total = success + failed + policy
 *   success 翻译成功，含 sha256 命中后直接重投译图的那次
 *   failed  走 reportFail 的失败，以及译图/政策回退上报失败（服务端收不到就是失败）
 *   policy  内容政策限制保留原图/原文
 *
 * 当日按**本地时区** 0 点滚动；累计自升级后起算，可在 Settings 手动重置。
 */
export const STAT_SUCCESS = 'success';
export const STAT_FAILED = 'failed';
export const STAT_POLICY = 'policy';

const COUNTED_OUTCOMES = [STAT_SUCCESS, STAT_FAILED, STAT_POLICY];

/** 本地时区的日期键（YYYY-MM-DD）。用它比较而不是存时间戳，跨天判断才不受时区偏移影响。 */
export function localDayKey(timestamp) {
  const date = new Date(timestamp);
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

function emptyBucket() {
  return { total: 0, success: 0, failed: 0, policy: 0 };
}

/** total 一律由三个分项重算，storage 里被写坏也能自愈。 */
function normalizeBucket(bucket) {
  const normalized = emptyBucket();
  for (const key of COUNTED_OUTCOMES) {
    const value = Number(bucket?.[key]);
    normalized[key] = Number.isFinite(value) && value > 0 ? Math.floor(value) : 0;
  }
  normalized.total = normalized.success + normalized.failed + normalized.policy;
  return normalized;
}

export function emptyStats(now = Date.now()) {
  return { allTime: emptyBucket(), today: { day: localDayKey(now), ...emptyBucket() } };
}

/** 读盘后归一化，顺带处理跨天：storage 里的 day 不是今天就把当日桶清零。 */
export function normalizeStats(stored, now = Date.now()) {
  const today = localDayKey(now);
  const storedDay = typeof stored?.today?.day === 'string' ? stored.today.day : null;
  const todayBucket = storedDay === today ? normalizeBucket(stored.today) : emptyBucket();
  return {
    allTime: normalizeBucket(stored?.allTime),
    today: { day: today, ...todayBucket },
  };
}

export function recordOutcome(stats, outcome, now = Date.now()) {
  const next = normalizeStats(stats, now);
  if (!COUNTED_OUTCOMES.includes(outcome)) return next;
  next.allTime[outcome] += 1;
  next.allTime.total += 1;
  next.today[outcome] += 1;
  next.today.total += 1;
  return next;
}
