import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFileSync } from 'node:fs';

const source = readFileSync(new URL('../background.js', import.meta.url), 'utf8');

function reportHarness(postJson) {
  const phases = [];
  const waits = [];
  const context = vm.createContext({
    COMPLETION_REPORT_MAX_RETRIES: 8, COMPLETION_REPORT_TIMEOUT_MS: 120000,
    TRANSLATED_NOTICE_TIMEOUT_MS: 30000, FAIL_REPORT_RETRY_BASE_MS: 1000,
    postJson, addLog() {}, isReprocessRequired: e => e.message.includes('REPROCESS_REQUIRED'),
    setTaskPhase: (...args) => phases.push(args), sleep: async ms => waits.push(ms),
  });
  vm.runInContext(source.slice(source.indexOf('async function postCompletionWithRetry('),
    source.indexOf('async function retainTranslationForReuse(')), context);
  return { report: context.postCompletionWithRetry, phases, waits };
}

test('extends the lease before each upload and retries the same image and assignment', async () => {
  const calls = [];
  const payload = { bridgeId: 'bridge', assignmentId: 'assignment', resultImageBase64: 'translated' };
  let uploads = 0;
  const { report, phases, waits } = reportHarness(async (_service, path, body, options) => {
    calls.push(path);
    if (path.endsWith('/translated')) {
      assert.equal(body.assignmentId, payload.assignmentId);
      assert.equal(body.resultImageBase64, undefined);
      return { accepted: true };
    }
    assert.equal(options.timeoutMs, 120000);
    assert.equal(body, payload);
    if (++uploads < 3) throw new Error('HTTP 503: COMPLETION_RETRY_REQUIRED');
  });
  await report({}, payload);
  assert.equal(uploads, 3);
  assert.deepEqual(calls, Array(3).fill(['/turboflow-bridge/tasks/translated', '/turboflow-bridge/tasks/complete']).flat());
  assert.deepEqual(waits, [1000, 2000]);
  assert.ok(phases.some(([, phase, retry]) => phase === 'reporting_retry' && retry === 2));
});

test('a failed notice still attempts upload; retries are bounded with capped backoff', async () => {
  let uploads = 0;
  const { report, waits } = reportHarness(async (_service, path) => {
    if (path.endsWith('/translated')) throw new Error('HTTP 404');
    uploads++;
    throw new Error('network unavailable');
  });
  await assert.rejects(report({}, { assignmentId: 'same' }), /network unavailable/);
  assert.equal(uploads, 9);
  assert.equal(waits.length, 8);
  assert.ok(waits.every(ms => ms <= 30000));
});

test('old server invalidating the assignment exits to the existing image cache fallback', async () => {
  let uploads = 0;
  const { report, waits } = reportHarness(async (_service, path) => {
    if (path.endsWith('/complete')) {
      uploads++;
      throw new Error('HTTP 409: REPROCESS_REQUIRED');
    }
  });
  await assert.rejects(report({}, {}), /REPROCESS_REQUIRED/);
  assert.equal(uploads, 1);
  assert.equal(waits.length, 0);
});

function jsonHarness(fetch, timers = {}) {
  const context = vm.createContext({
    fetch, AbortController, setTimeout, clearTimeout, ...timers,
    normalizeBaseUrl: url => url,
  });
  vm.runInContext(source.slice(source.indexOf('async function postJson('),
    source.indexOf('function normalizeBaseUrl(')), context);
  return context.postJson;
}

test('HTTP 200 with accepted=false is rejected instead of dropping the result', async () => {
  const post = jsonHarness(async () => ({ ok: true, text: async () => '{"accepted":false,"message":"invalid bridge token"}' }));
  await assert.rejects(post({ baseUrl: 'https://server' }, '/complete', {}), /invalid bridge token/);
});

test('timeout aborts a stalled response body and clears the timer', async () => {
  let expire;
  let cleared = false;
  const post = jsonHarness(async (_url, { signal }) => ({
    ok: true,
    text: () => new Promise((_resolve, reject) => signal.addEventListener('abort', () => reject(new Error('aborted')))),
  }), { setTimeout: callback => { expire = callback; return 42; }, clearTimeout: id => { cleared = id === 42; } });
  const pending = post({ baseUrl: 'https://server' }, '/complete', {});
  await new Promise(resolve => setImmediate(resolve));
  expire();
  await assert.rejects(pending, /aborted/);
  assert.equal(cleared, true);
});

test('current cards distinguish stages and retain their before/during/after colors', () => {
  const panel = readFileSync(new URL('../sidepanel.js', import.meta.url), 'utf8');
  const currentTaskEl = {};
  const stages = [
    ['fetching', 'standby', '获取中'], ['downloading_source', 'standby', '下载中'],
    ['standby', 'standby', '预备'], ['submitting', 'standby', '上传中'],
    ['generating', 'running', '翻译中'], ['downloading_result', 'reporting', '下载中'],
    ['reporting', 'reporting', '回传中'], ['reporting_retry', 'reporting', '回传重试中(2)'],
  ];
  const context = vm.createContext({ currentTaskEl, activeTasks: [], esc: s => s, shortenUrl: s => s });
  vm.runInContext(panel.slice(panel.indexOf('  function updateCurrentTaskContent('), panel.indexOf('  function startCountdownTicker(')), context);
  for (const [phase, colorClass, label] of stages) {
    context.activeTasks = [{ phase, reportRetry: 2, startedAt: Date.now(), service: 'server' }];
    context.updateCurrentTaskContent();
    assert.ok(currentTaskEl.innerHTML.includes(`task-status-badge ${colorClass}">${label}</span>`));
    assert.ok(!currentTaskEl.innerHTML.includes('NaN'));
  }
});
