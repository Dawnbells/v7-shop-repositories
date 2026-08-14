/**
 * 译图复用缓存。
 * <p>
 * 场景：图已经在 Google 那边翻译好了，但 /tasks/complete 送不出去（服务端 5xx、网络抖动、
 * 或服务端回 REPROCESS_REQUIRED 表示 assignment 已失效）。这时把译图按**源图 sha256**落盘；
 * 服务端用新 assignmentId 重派同一张图时直接命中，一次 Google 调用都不用花。
 * <p>
 * 三道生命周期闸门，任一命中就丢弃：
 *   - 匹配窗口：接下来 REUSE_MATCH_WINDOW(20) 次任务内没等到对应源图就丢
 *   - 条数上限：最多 REUSE_MAX_ENTRIES(20) 条，超出丢最旧的
 *   - TTL：REUSE_TTL_MS(24h) 兜底，防止插件长期停止态时记录永久积压
 * 丢弃一律打 warn 日志，不静默。
 */
import { imageDigest } from './image-digest.js';

const REUSE_PREFIX = 'translatedImageReuse:';

export const REUSE_MATCH_WINDOW = 20;
export const REUSE_MAX_ENTRIES = 20;
export const REUSE_TTL_MS = 24 * 60 * 60 * 1000;

export const DROP_WINDOW_EXHAUSTED = 'window-exhausted';
export const DROP_EXPIRED = 'expired';
export const DROP_OVER_CAPACITY = 'over-capacity';

// service worker 存活期间的镜像，storage 不可用时至少本次会话还能复用
const memoryCache = new Map();
// storage 写失败后由内存临时充当权威来源。只给明确写失败的 key 打标，避免普通的
// storage 删除被内存镜像“复活”。一旦后续写盘成功或显式删除，就清掉该标记。
const memoryFallbackKeys = new Set();

function reuseKey(serviceBaseUrl, targetLanguage, imageHash) {
  return `${REUSE_PREFIX}${serviceBaseUrl || ''}::${targetLanguage || ''}::${imageHash}`;
}

function isUsableRecord(record) {
  return !!record
    && typeof record.imageHash === 'string'
    && typeof record.resultDataUrl === 'string'
    && record.resultDataUrl.length > 0;
}

/**
 * storage 是权威来源，内存镜像只在 storage 读失败时兜底。
 * 反过来（镜像优先）会让已被删掉的记录复活，进而把过期译图重投给不该匹配的任务。
 */
async function readAll(storage) {
  try {
    const stored = await storage.get(null);
    const records = [];
    const liveKeys = new Set();
    for (const [key, value] of Object.entries(stored || {})) {
      if (key.startsWith(REUSE_PREFIX) && isUsableRecord(value)) {
        const memoryRecord = memoryFallbackKeys.has(key) ? memoryCache.get(key) : null;
        const record = isUsableRecord(memoryRecord) ? { ...memoryRecord, key } : { ...value, key };
        records.push(record);
        liveKeys.add(key);
      }
    }
    // storage.set 可能单独失败而 storage.get 仍正常（例如写入层故障）。这些 key 必须继续
    // 从内存返回，否则刚保住的译图会在本函数里立刻被清掉。
    for (const key of memoryFallbackKeys) {
      if (liveKeys.has(key)) continue;
      const value = memoryCache.get(key);
      if (isUsableRecord(value)) {
        records.push({ ...value, key });
        liveKeys.add(key);
      }
    }
    for (const key of [...memoryCache.keys()]) {
      if (!liveKeys.has(key)) {
        memoryCache.delete(key);
        memoryFallbackKeys.delete(key);
      }
    }
    for (const record of records) memoryCache.set(record.key, record);
    return records;
  } catch {
    return Array.from(memoryCache.entries())
      .filter(([, value]) => isUsableRecord(value))
      .map(([key, value]) => ({ ...value, key }));
  }
}

async function removeKeys(storage, keys) {
  for (const key of keys) {
    memoryCache.delete(key);
    memoryFallbackKeys.delete(key);
  }
  if (keys.length === 0) return;
  try {
    await storage.remove(keys);
  } catch {
    // 下一轮 consumeReuseWindow 会再试一次
  }
}

/**
 * 把译图落盘等复用。同一源图重复落盘时刷新剩余窗口，不叠加条数。
 * 超出条数上限时丢最旧的，返回被丢弃的记录供调用方打日志。
 */
export async function rememberTranslatedImage(storage, entry) {
  const now = Number(entry.now) || Date.now();
  const imageHash = entry.imageHash || await imageDigest(entry.sourceImageBase64);
  const targetLanguage = entry.targetLanguage || '';
  const key = reuseKey(entry.serviceBaseUrl, targetLanguage, imageHash);
  const record = {
    key,
    imageHash,
    serviceBaseUrl: entry.serviceBaseUrl,
    targetLanguage,
    resultDataUrl: entry.resultDataUrl,
    resultUrl: entry.resultUrl || null,
    resultMimeType: entry.resultMimeType || 'image/png',
    elapsedMs: Number.isFinite(Number(entry.elapsedMs)) ? Number(entry.elapsedMs) : null,
    remaining: REUSE_MATCH_WINDOW,
    recordedAt: now,
  };
  memoryCache.set(key, record);
  try {
    await storage.set({ [key]: record });
    memoryFallbackKeys.delete(key);
  } catch (error) {
    record.storageError = error.message || String(error);
    memoryFallbackKeys.add(key);
  }

  // 条数上限：留最新的，丢最旧的
  const all = await readAll(storage);
  const dropped = [];
  if (all.length > REUSE_MAX_ENTRIES) {
    const sorted = all.sort((a, b) => (Number(b.recordedAt) || 0) - (Number(a.recordedAt) || 0));
    for (const stale of sorted.slice(REUSE_MAX_ENTRIES)) {
      dropped.push({ ...stale, reason: DROP_OVER_CAPACITY });
    }
    await removeKeys(storage, dropped.map((item) => item.key));
  }
  return { record, dropped };
}

/** 按源图 sha256 + 目标语言 + 服务端找译图。过期记录当作未命中（由 consumeReuseWindow 负责清理）。 */
export async function findTranslatedImage(storage, {
  serviceBaseUrl,
  targetLanguage,
  imageHash,
  now = Date.now(),
}) {
  if (!imageHash) return null;
  const key = reuseKey(serviceBaseUrl, targetLanguage, imageHash);
  let record = null;
  try {
    const stored = await storage.get([key]);
    record = memoryFallbackKeys.has(key) ? memoryCache.get(key) : stored?.[key] ?? null;
    if (!isUsableRecord(record)) {
      // storage 里没有就是没有 —— 清掉可能残留的镜像，别让删掉的记录复活
      memoryCache.delete(key);
      return null;
    }
    memoryCache.set(key, record);
  } catch {
    record = memoryCache.get(key) ?? null;
    if (!isUsableRecord(record)) return null;
  }
  if (now - (Number(record.recordedAt) || 0) > REUSE_TTL_MS) return null;
  return { ...record, key };
}

export async function forgetTranslatedImage(storage, record) {
  const key = typeof record === 'string' ? record : record?.key;
  if (!key) return;
  await removeKeys(storage, [key]);
}

/**
 * 领到一个新任务但没命中缓存时调用：所有存活记录的匹配窗口 -1，归零或过期的丢弃。
 * 返回 { dropped, summary }：dropped 带 reason 供调用方打 warn 日志（静默丢弃会让人
 * 以为复用生效了），summary 顺带算出来，免得调用方再读一次 storage。
 */
export async function consumeReuseWindow(storage, now = Date.now()) {
  const all = await readAll(storage);
  if (all.length === 0) return { dropped: [], summary: { count: 0, minRemaining: 0 } };
  const dropped = [];
  const survivors = {};
  for (const record of all) {
    if (now - (Number(record.recordedAt) || 0) > REUSE_TTL_MS) {
      dropped.push({ ...record, reason: DROP_EXPIRED });
      continue;
    }
    const remaining = (Number.isFinite(Number(record.remaining)) ? Number(record.remaining) : REUSE_MATCH_WINDOW) - 1;
    if (remaining <= 0) {
      dropped.push({ ...record, reason: DROP_WINDOW_EXHAUSTED });
      continue;
    }
    const next = { ...record, remaining };
    memoryCache.set(record.key, next);
    survivors[record.key] = next;
  }
  await removeKeys(storage, dropped.map((item) => item.key));
  const survivorList = Object.values(survivors);
  if (survivorList.length > 0) {
    try {
      await storage.set(survivors);
      for (const key of Object.keys(survivors)) memoryFallbackKeys.delete(key);
    } catch {
      // 内存镜像已更新，下一轮继续以它为准，避免成功的译图因单次写盘失败丢失
      for (const key of Object.keys(survivors)) memoryFallbackKeys.add(key);
    }
  }
  return { dropped, summary: summarize(survivorList) };
}

function summarize(records) {
  if (records.length === 0) return { count: 0, minRemaining: 0 };
  const minRemaining = records.reduce((min, record) => {
    const remaining = Number.isFinite(Number(record.remaining)) ? Number(record.remaining) : REUSE_MATCH_WINDOW;
    return Math.min(min, remaining);
  }, REUSE_MATCH_WINDOW);
  return { count: records.length, minRemaining };
}

/**
 * 面板提示用：还有几张译图待重投、最紧的那张还剩几次匹配机会。
 * 注意这里会 storage.get(null) 读全量，调用方应只在确实可能有记录时调（见 background.js
 * 用内存里的 reuseSummary 短路），别放在每个任务的热路径上。
 */
export async function summarizeTranslatedImages(storage, now = Date.now()) {
  const all = await readAll(storage);
  return summarize(all.filter((record) => now - (Number(record.recordedAt) || 0) <= REUSE_TTL_MS));
}
