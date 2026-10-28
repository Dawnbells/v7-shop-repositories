const PREFIX = 'unsafeGenerationDecision:';
export const GENERATION_DECISION_TIMEOUT_MS = 10000;

export function isUnsafeGeneration(error) {
  const message = String(typeof error === 'string' ? error : error?.message || '');
  const reason = String(error?.reason || '');
  return /\bPUBLIC_ERROR_UNSAFE_GENERATION\b/.test(`${reason} ${message}`);
}

// One key per assignment: concurrent failures cannot overwrite each other.
// Credentials stay in the service configuration, never in a decision record.
export function createGenerationDecisions(storage, now = Date.now) {
  const records = new Map();
  return {
    async restore() {
      const stored = await storage.get(null);
      for (const [key, record] of Object.entries(stored)) {
        if (key.startsWith(PREFIX) && record?.id && record.task?.imageBase64) await this.save(record);
      }
    },
    list: () => [...records.values()],
    get: id => records.get(id),
    async save(record) {
      record = { ...record, deadlineAt: records.get(record.id)?.deadlineAt
        ?? record.deadlineAt ?? now() + GENERATION_DECISION_TIMEOUT_MS };
      records.set(record.id, record);
      try { await storage.set({ [PREFIX + record.id]: record }); }
      catch (error) { record.storageError = error.message; }
      return record;
    },
    async remove(id) {
      // Do not hide the choice if its deletion could not be persisted.
      await storage.remove(PREFIX + id);
      records.delete(id);
    },
    summaries() {
      return [...records.values()].map(record => ({
        id: record.id, subTaskId: record.task.subTaskId, sourceThumb: record.context.sourceThumb,
        targetLang: record.context.targetLang, error: record.error,
        returned: !!record.returned, action: record.action || null,
        deadlineAt: record.deadlineAt,
      }));
    },
  };
}

// Runs in the service worker, independent of whether the side panel is open.
// The persisted deadline and alarm recover an interrupted timer after restart.
export function createGenerationDecisionTimeout({ list, resolve, armAlarm, clearAlarm, onError,
  now = Date.now, setTimer = setTimeout, clearTimer = clearTimeout }) {
  let timer = null;
  let running = false;
  let retryAt = 0;
  function schedule() {
    if (timer !== null) clearTimer(timer);
    timer = null;
    if (running) return;
    const pending = list().filter(record => !record.action);
    if (!pending.length) { clearAlarm(); return; }
    const when = Math.max(retryAt, Math.min(...pending.map(record => record.deadlineAt)));
    timer = setTimer(run, Math.max(0, when - now()));
    armAlarm(Math.max(now() + 1, when));
  }
  async function run() {
    if (running) return;
    running = true;
    if (timer !== null) clearTimer(timer);
    timer = null;
    try {
      for (const record of list()) {
        if (!record.action && record.deadlineAt <= now()) {
          try { await resolve(record.id, 'original', { automatic: true }); }
          catch (error) { retryAt = now() + 1000; onError(error); }
        }
      }
    } finally { running = false; schedule(); }
  }
  return { schedule, run };
}
