// upload-rejection-state.js — maseQ RPC 3（INVALID_ARGUMENT）上传拒绝的本地计数
//
// 单次 RPC 3 不能确定是内容政策、图片格式还是请求字段问题，所以不能像旧版
// /flow/uploadImage 那样把它直接当作政策拒绝。这里先把任务以可重试失败退回服务端重派，
// 本地按图片摘要记住错误并计数：同一张图再派到本 bridge 时不再上传，直接退回；
// 收到次数达到阈值才走旧的政策回退链路保留原图；长时间没再出现就取消标记。

export const UPLOAD_REJECTION_STORAGE_KEY = 'uploadRejectionState';
/** 含首次真实拒绝在内，同一张图第 N 次派到本 bridge 时改走政策回退。 */
export const UPLOAD_REJECTION_POLICY_THRESHOLD = 4;
/** 最后一次收到该图之后，连续 N 条其他任务都没再出现，就取消标记。 */
export const UPLOAD_REJECTION_FORGET_AFTER_TASKS = 20;

let cachedState = null;
let queue = Promise.resolve();

function emptyState() {
  return { sequence: 0, records: {} };
}

function normalizeState(value) {
  const sequence = Number.isFinite(Number(value?.sequence))
    ? Math.max(0, Math.floor(Number(value.sequence)))
    : 0;
  const records = {};
  for (const [hash, record] of Object.entries(value?.records || {})) {
    if (record && typeof record === 'object' && record.imageHash === hash) {
      records[hash] = { ...record };
    }
  }
  return { sequence, records };
}

async function loadState(storage) {
  if (cachedState) return cachedState;
  try {
    const stored = await storage.get([UPLOAD_REJECTION_STORAGE_KEY]);
    cachedState = normalizeState(stored?.[UPLOAD_REJECTION_STORAGE_KEY]);
  } catch {
    cachedState = emptyState();
  }
  return cachedState;
}

async function saveState(storage, state) {
  try {
    await storage.set({ [UPLOAD_REJECTION_STORAGE_KEY]: state });
    return null;
  } catch (error) {
    // 内存状态仍然有效，直到 service worker 退出。
    return error?.message || String(error);
  }
}

// 所有读写串行化：并发任务同时推进序号、或同一张图并发被拒时不丢更新。
function serialized(work) {
  const run = queue.then(work, work);
  queue = run.then(() => {}, () => {});
  return run;
}

function expireStaleRecords(state, sequence, keepHash) {
  const forgotten = [];
  for (const [hash, record] of Object.entries(state.records)) {
    if (hash === keepHash) continue;
    if (sequence - Number(record.lastSequence || 0) >= UPLOAD_REJECTION_FORGET_AFTER_TASKS) {
      delete state.records[hash];
      forgotten.push(record);
    }
  }
  return forgotten;
}

export function shouldFallBackToPolicy(record) {
  return Number(record?.receipts || 0) >= UPLOAD_REJECTION_POLICY_THRESHOLD;
}

/**
 * 每个任务开始时调用：推进任务序号、清理过期标记；该图片已有标记则计一次收到。
 * 返回 { sequence, record, forgotten }，record 为 null 表示这张图没有标记。
 * 没有任何标记时不写 storage，避免进入每个任务的热路径。
 */
export async function observeTaskForUploadRejection(storage, imageHash) {
  return serialized(async () => {
    const state = await loadState(storage);
    state.sequence += 1;
    const sequence = state.sequence;
    const forgotten = expireStaleRecords(state, sequence, imageHash);
    let record = null;
    if (state.records[imageHash]) {
      const stored = state.records[imageHash];
      stored.receipts = Number(stored.receipts || 0) + 1;
      stored.lastSequence = sequence;
      stored.lastReceivedAt = Date.now();
      record = { ...stored };
    }
    let storageError = null;
    if (record || forgotten.length || Object.keys(state.records).length) {
      storageError = await saveState(storage, state);
    }
    return { sequence, record, forgotten, storageError };
  });
}

/**
 * 真实的上传 RPC 3：记住摘要、错误文案和 reason，并计一次收到。
 * 同一张图在并发中被 Google 拒绝多次，每次都算一次收到。
 */
export async function rememberUploadRejection(storage, imageHash, details = {}) {
  return serialized(async () => {
    const state = await loadState(storage);
    const sequence = Number.isFinite(Number(details.sequence)) ? Number(details.sequence) : state.sequence;
    const existing = state.records[imageHash];
    const record = existing ? { ...existing } : {
      imageHash,
      receipts: 0,
      firstSequence: sequence,
      recordedAt: Date.now(),
    };
    record.receipts = Number(record.receipts || 0) + 1;
    record.lastSequence = Math.max(Number(record.lastSequence || 0), sequence);
    record.lastReceivedAt = Date.now();
    record.rpcId = details.rpcId || record.rpcId || null;
    record.rpcStatus = details.rpcStatus ?? record.rpcStatus ?? null;
    record.apiStatus = details.apiStatus || record.apiStatus || 'INVALID_ARGUMENT';
    record.reason = details.reason || record.reason || record.apiStatus;
    record.errorMessage = details.errorMessage || record.errorMessage || '';
    state.records[imageHash] = record;
    const storageError = await saveState(storage, state);
    return { ...record, ...(storageError ? { storageError } : {}) };
  });
}

export async function forgetUploadRejection(storage, imageHash) {
  return serialized(async () => {
    const state = await loadState(storage);
    const record = state.records[imageHash] || null;
    if (!record) return null;
    delete state.records[imageHash];
    await saveState(storage, state);
    return { ...record };
  });
}

export async function listUploadRejections(storage) {
  return serialized(async () => Object.values((await loadState(storage)).records).map(record => ({ ...record })));
}

/** 仅供测试：丢弃内存缓存，模拟 service worker 重启后从 storage 重新加载。 */
export function resetUploadRejectionMemory() {
  cachedState = null;
  queue = Promise.resolve();
}
