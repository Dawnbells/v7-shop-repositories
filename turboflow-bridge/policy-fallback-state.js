const IMAGE_POLICY_CACHE_PREFIX = 'imagePolicyFallback:';
const PENDING_POLICY_REPORT_PREFIX = 'pendingPolicyFallbackReport:';

const memoryPolicyCache = new Map();
const memoryPendingReports = new Map();
const pendingPolicyWrites = new Map();

function stripDataUrl(value) {
  if (!value) return '';
  const comma = value.indexOf(',');
  return comma >= 0 ? value.substring(comma + 1) : value;
}

async function imageDigest(imageBase64) {
  const binary = atob(stripDataUrl(imageBase64));
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, '0')).join('');
}

function policyStorageKey(digest) {
  return IMAGE_POLICY_CACHE_PREFIX + digest;
}

function pendingRecordKey(serviceBaseUrl, assignmentId) {
  return `${serviceBaseUrl || ''}::${assignmentId || ''}`;
}

function pendingStorageKey(pendingKey) {
  return PENDING_POLICY_REPORT_PREFIX + pendingKey;
}

async function readStorageValue(storage, key) {
  try {
    const stored = await storage.get([key]);
    return stored?.[key] || null;
  } catch {
    return null;
  }
}

export async function findImagePolicyFallback(storage, imageBase64) {
  const digest = await imageDigest(imageBase64);
  if (memoryPolicyCache.has(digest)) return memoryPolicyCache.get(digest);
  const entry = await readStorageValue(storage, policyStorageKey(digest));
  if (entry) memoryPolicyCache.set(digest, entry);
  return entry;
}

export async function rememberImagePolicyFallback(storage, imageBase64, policy) {
  const digest = await imageDigest(imageBase64);
  if (memoryPolicyCache.has(digest)) return memoryPolicyCache.get(digest);
  if (pendingPolicyWrites.has(digest)) return pendingPolicyWrites.get(digest);

  const write = (async () => {
    const key = policyStorageKey(digest);
    const stored = await readStorageValue(storage, key);
    if (stored) {
      memoryPolicyCache.set(digest, stored);
      return stored;
    }
    const entry = {
      imageHash: digest,
      apiStatus: policy.apiStatus || 'INVALID_ARGUMENT',
      reason: policy.reason || policy.apiStatus || 'INVALID_ARGUMENT',
      recordedAt: Date.now(),
    };
    memoryPolicyCache.set(digest, entry);
    try {
      await storage.set({ [key]: entry });
    } catch (error) {
      entry.storageError = error.message || String(error);
    }
    return entry;
  })();

  pendingPolicyWrites.set(digest, write);
  try {
    return await write;
  } finally {
    if (pendingPolicyWrites.get(digest) === write) pendingPolicyWrites.delete(digest);
  }
}

export async function rememberPendingPolicyCompletion(storage, policy, pending) {
  const assignmentId = pending.payload?.assignmentId;
  if (!assignmentId) throw new Error('Policy fallback completion requires assignmentId');
  const pendingKey = pendingRecordKey(pending.serviceBaseUrl, assignmentId);
  const record = {
    imageHash: policy.imageHash,
    pendingKey,
    serviceBaseUrl: pending.serviceBaseUrl,
    payload: pending.payload,
    updatedAt: Date.now(),
  };
  memoryPendingReports.set(pendingKey, record);
  try {
    await storage.set({ [pendingStorageKey(pendingKey)]: record });
  } catch (error) {
    record.storageError = error.message || String(error);
  }
  return record;
}

export async function listPendingPolicyCompletions(storage) {
  const records = new Map(memoryPendingReports);
  try {
    const stored = await storage.get(null);
    for (const [key, value] of Object.entries(stored || {})) {
      if (key.startsWith(PENDING_POLICY_REPORT_PREFIX) && value?.imageHash) {
        const pendingKey = value.pendingKey
          || pendingRecordKey(value.serviceBaseUrl, value.payload?.assignmentId);
        const existing = records.get(pendingKey);
        const storageKeys = [...new Set([...(existing?.storageKeys || []), key])];
        records.set(pendingKey, { ...existing, ...value, pendingKey, storageKeys });
      }
    }
  } catch {
    // In-memory records still allow retries until the service worker exits.
  }
  return Array.from(records.values());
}

export async function clearPendingPolicyCompletion(storage, pending) {
  const pendingKey = typeof pending === 'string' ? pending : pending?.pendingKey;
  if (!pendingKey) return;
  memoryPendingReports.delete(pendingKey);
  try {
    const storageKeys = typeof pending === 'string' || !pending.storageKeys?.length
      ? [pendingStorageKey(pendingKey)]
      : pending.storageKeys;
    await storage.remove(storageKeys);
  } catch {
    // A later idempotent retry can safely clear a stale persisted record.
  }
}

export async function reportPolicyFallback({
  post,
  payload,
  maxRetries,
  retryBaseMs,
  sleep,
  onRetry,
  onRecovered,
}) {
  let lastError;
  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    try {
      await post(payload);
      if (attempt > 0) onRecovered?.(attempt);
      return;
    } catch (error) {
      lastError = error;
      if (attempt === maxRetries) break;
      const backoff = retryBaseMs * Math.pow(2, attempt);
      onRetry?.(attempt + 1, backoff, error);
      await sleep(backoff);
    }
  }
  throw lastError;
}
