import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFileSync } from 'node:fs';

const source = readFileSync(new URL('../background.js', import.meta.url), 'utf8');

function reportHarness(postJson, { uploadConcurrency = 3, handlesUploadSlot = false } = {}) {
  const phases = [];
  const waits = [];
  const logs = [];
  const heartbeat = { ticks: [], cleared: 0 };
  const context = vm.createContext({
    COMPLETION_REPORT_MAX_RETRIES: 8, COMPLETION_REPORT_TIMEOUT_MS: 120000,
    TRANSLATED_NOTICE_TIMEOUT_MS: 30000, FAIL_REPORT_RETRY_BASE_MS: 1000,
    COMPLETION_UPLOAD_CONCURRENCY: uploadConcurrency, activeCompletionUploads: 0, completionUploadWaiters: [],
    SERVER_UPLOAD_SLOT_RETRY_MS: 1000, uploadSlotUnsupportedServices: new Set(),
    REPORT_HEARTBEAT_INTERVAL_MS: 60000,
    setInterval: (tick, ms) => { heartbeat.ticks.push([tick, ms]); return heartbeat.ticks.length; },
    clearInterval: () => { heartbeat.cleared++; },
    // 默认服务端直接发名额，只关心回传本身的用例不必处理 upload-slot
    postJson: (service, path, body, options) => (!handlesUploadSlot && path.endsWith('/upload-slot')
      ? Promise.resolve({ accepted: true, status: 'GRANTED' })
      : postJson(service, path, body, options)),
    addLog: (level, message) => logs.push([level, message]),
    isReprocessRequired: e => e.message.includes('REPROCESS_REQUIRED'),
    setTaskPhase: (...args) => phases.push(args), sleep: async ms => waits.push(ms),
  });
  vm.runInContext(source.slice(source.indexOf('async function postCompletionWithRetry('),
    source.indexOf('async function retainTranslationForReuse(')), context);
  return { report: context.postCompletionWithRetry, phases, waits, logs, heartbeat };
}

test('waits for the per-IP upload slot before sending the image, without spending report retries', async () => {
  const calls = [];
  let busy = 3;
  const { report, waits } = reportHarness(async (_service, path, body) => {
    calls.push(path.split('/').pop());
    if (path.endsWith('/upload-slot')) {
      assert.deepEqual({ ...body }, { bridgeId: 'bridge', assignmentId: 'queued' });
      return { accepted: true, status: busy-- > 0 ? 'BUSY' : 'GRANTED' };
    }
    return { accepted: true, status: 'LEASE_EXTENDED' };
  }, { handlesUploadSlot: true });
  await report({}, { bridgeId: 'bridge', assignmentId: 'queued' });
  assert.deepEqual(calls, ['translated', 'upload-slot', 'upload-slot', 'upload-slot', 'upload-slot', 'complete']);
  assert.equal(waits.length, 3);
  assert.ok(waits.every(ms => ms >= 1000 && ms < 2000));
});

test('the granted slot id rides along with the upload so the server returns exactly that slot', async () => {
  let completeBody;
  const payload = { bridgeId: 'bridge', assignmentId: 'slotted', resultImageBase64: 'img' };
  const { report } = reportHarness(async (_service, path, body) => {
    if (path.endsWith('/upload-slot')) return { accepted: true, status: 'GRANTED', uploadSlotId: 'slot-1' };
    if (path.endsWith('/complete')) completeBody = body;
    return { accepted: true, status: 'LEASE_EXTENDED' };
  }, { handlesUploadSlot: true });
  await report({}, payload);
  assert.deepEqual({ ...completeBody }, { ...payload, uploadSlotId: 'slot-1' });
  assert.equal(payload.uploadSlotId, undefined);
});

test('an old server without the upload-slot endpoint is probed once, then uploads go straight through', async () => {
  const calls = [];
  const { report, logs } = reportHarness(async (_service, path) => {
    calls.push(path.split('/').pop());
    if (path.endsWith('/upload-slot')) throw new Error('HTTP 404: Not Found');
    return { accepted: true };
  }, { handlesUploadSlot: true });
  const service = { baseUrl: 'https://old-server' };
  await report(service, { assignmentId: 'legacy-1' });
  await report(service, { assignmentId: 'legacy-2' });
  assert.deepEqual(calls, ['translated', 'upload-slot', 'complete', 'translated', 'complete']);
  assert.equal(logs.filter(([, message]) => message.includes('without server limit')).length, 1);
});

test('an overloaded slot endpoint costs a report retry instead of bypassing the limit', async () => {
  const calls = [];
  let slotRequests = 0;
  const { report, waits } = reportHarness(async (_service, path) => {
    calls.push(path.split('/').pop());
    if (path.endsWith('/upload-slot')) {
      if (++slotRequests === 1) throw new Error('signal is aborted without reason');
      return { accepted: true, status: 'GRANTED', uploadSlotId: 'slot-2' };
    }
    return { accepted: true, status: 'LEASE_EXTENDED' };
  }, { handlesUploadSlot: true });
  await report({ baseUrl: 'https://busy-server' }, { assignmentId: 'overloaded' });
  assert.deepEqual(calls, ['translated', 'upload-slot', 'translated', 'upload-slot', 'complete']);
  assert.deepEqual(waits, [1000]);
});

test('heartbeats keep the lease alive for the whole report and stop when it ends', async () => {
  const heartbeats = [];
  let releaseUpload;
  const { report, heartbeat } = reportHarness(async (_service, path, body) => {
    if (path.endsWith('/translated')) {
      heartbeats.push({ ...body });
      return { accepted: true, status: 'LEASE_EXTENDED' };
    }
    await new Promise(resolve => { releaseUpload = resolve; });
  });
  const reporting = report({}, { bridgeId: 'bridge', assignmentId: 'slow-upload', resultImageBase64: 'img' });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(heartbeat.ticks.length, 1);
  assert.equal(heartbeat.ticks[0][1], 60000);
  const [tick] = heartbeat.ticks[0];
  tick();
  tick(); // 上一拍还没返回：跳过，不堆积请求
  await new Promise(resolve => setImmediate(resolve));
  assert.deepEqual(heartbeats, Array(2).fill({ bridgeId: 'bridge', assignmentId: 'slow-upload' }));
  assert.equal(heartbeat.cleared, 0);
  releaseUpload();
  await reporting;
  assert.equal(heartbeat.cleared, 1);
});

test('retry skips the image upload when the server already stored an earlier attempt', async () => {
  const calls = [];
  const { report, waits } = reportHarness(async (_service, path) => {
    calls.push(path);
    if (path.endsWith('/translated')) {
      return calls.length === 1 ? { accepted: true, status: 'LEASE_EXTENDED' } : { accepted: true, status: 'COMPLETED' };
    }
    throw new Error('signal is aborted without reason');
  });
  await report({}, { assignmentId: 'slow' });
  assert.deepEqual(calls, ['/turboflow-bridge/tasks/translated', '/turboflow-bridge/tasks/complete',
    '/turboflow-bridge/tasks/translated']);
  assert.deepEqual(waits, [1000]);
});

test('retry waits instead of re-uploading while the server is still processing the previous upload', async () => {
  const notices = ['LEASE_EXTENDED', 'COMPLETING', 'COMPLETING', 'LEASE_EXTENDED'];
  let uploads = 0;
  const { report, waits } = reportHarness(async (_service, path) => {
    if (path.endsWith('/translated')) return { accepted: true, status: notices.shift() };
    // 第一次在插件侧超时；服务端处理完却失败了（租约仍在），第二次真正重投
    if (++uploads === 1) throw new Error('signal is aborted without reason');
  });
  await report({}, { assignmentId: 'busy' });
  assert.equal(uploads, 2);
  assert.deepEqual(waits, [1000, 2000, 4000]);
});

test('concurrent completion uploads are capped while notices still go out immediately', async () => {
  let active = 0;
  let peak = 0;
  let notices = 0;
  const releases = [];
  const { report } = reportHarness(async (_service, path) => {
    if (path.endsWith('/translated')) {
      notices++;
      return { accepted: true, status: 'LEASE_EXTENDED' };
    }
    active++;
    peak = Math.max(peak, active);
    await new Promise(resolve => releases.push(resolve));
    active--;
  }, { uploadConcurrency: 2 });
  const reports = Array.from({ length: 5 }, (_, i) => report({}, { assignmentId: `a${i}` }));
  const settle = () => new Promise(resolve => setImmediate(resolve));
  await settle();
  assert.equal(notices, 5);
  assert.equal(active, 2);
  while (releases.length) {
    releases.shift()();
    await settle();
  }
  await Promise.all(reports);
  assert.equal(peak, 2);
});

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
    ['downloading_source', 'standby', '获取中'],
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
  assert.ok(!panel.includes('fetching:'));
});

test('polling stays hidden until a task response starts downloading', async () => {
  let onResponse;
  let finish;
  const context = vm.createContext({
    pollPaused: false, bridgeId: 'bridge', VERSION: 'test', currentTasks: [],
    broadcastTasksChanged() {}, addLog() {},
    postJson: (_service, _path, _body, options) => {
      onResponse = options.onResponse;
      return new Promise(resolve => { finish = resolve; });
    },
  });
  vm.runInContext(source.slice(source.indexOf('async function pollTask('),
    source.indexOf('async function executeTask(')), context);
  const polling = context.pollTask({ baseUrl: 'server' }, {}, false);
  assert.equal(context.currentTasks.length, 0);
  onResponse();
  assert.equal(context.currentTasks.length, 1);
  assert.equal(context.currentTasks[0].phase, 'downloading_source');
  finish({ hasTask: true });
  await polling;
  assert.equal(context.currentTasks.length, 0);
});
