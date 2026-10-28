import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFileSync } from 'node:fs';
import { createGenerationDecisions, createGenerationDecisionTimeout, isUnsafeGeneration } from '../generation-decision-state.js';
import { renderGenerationDecisionPanel } from '../generation-decision-panel.js';

const source = readFileSync(new URL('../background.js', import.meta.url), 'utf8');
const unsafe = 'Flow RPC ogiZ0b failed (RPC status 3: INVALID_ARGUMENT): PUBLIC_ERROR_UNSAFE_GENERATION';
const tick = () => new Promise(resolve => setImmediate(resolve));
function storageHarness() {
  const data = {};
  return { data, storage: {
    get: async () => structuredClone(data),
    set: async patch => Object.assign(data, structuredClone(patch)),
    remove: async key => { delete data[key]; },
  } };
}
const record = (id = 'one', returned = false) => ({
  id, serviceBaseUrl: 'https://service.test', returned, error: unsafe,
  task: { assignmentId: id, subTaskId: 'sub-' + id, taskId: 'task', imageBase64: 'source-' + id },
  context: { startedAt: 100, sourceThumb: 'thumb', sourceImage: 'source', targetLang: 'French' },
});

test('only explicit unsafe generation errors offer the decision', () => {
  assert.ok(isUnsafeGeneration({ message: unsafe }));
  assert.ok(isUnsafeGeneration({ reason: 'PUBLIC_ERROR_UNSAFE_GENERATION' }));
  for (const message of ['RPC status 3 INVALID_ARGUMENT', 'PUBLIC_ERROR_UNSAFE_IMAGE_UPLOAD',
    'PUBLIC_ERROR_UNUSUAL_ACTIVITY', 'PUBLIC_ERROR_UNSAFE_GENERATION_OTHER', 'DAILY_QUOTA_REACHED']) {
    assert.equal(isUnsafeGeneration({ message }), false);
  }
});

test('parallel decisions survive restart independently and summaries do not expose full source images', async () => {
  const { storage } = storageHarness();
  const decisions = createGenerationDecisions(storage);
  await Promise.all([decisions.save(record()), decisions.save(record('two'))]);
  const restarted = createGenerationDecisions(storage);
  await restarted.restore();
  assert.equal(restarted.list().length, 2);
  assert.equal(restarted.summaries()[0].sourceImage, undefined);
  assert.equal(restarted.summaries()[0].task, undefined);
  await restarted.remove('one');
  const again = createGenerationDecisions(storage);
  await again.restore();
  assert.deepEqual(again.list().map(value => value.id), ['two']);
});

function resolverHarness() {
  const { storage, data } = storageHarness();
  const decisions = createGenerationDecisions(storage);
  const events = [];
  const context = vm.createContext({
    generationDecisionTimeout: { schedule() {} },
    generationDecisions: decisions, generationDecisionBusy: false, generationLeaseRefresh: null,
    loadPersistedState: async () => {},
    roundRecovery: { busy: false, snapshot: () => ({ phase: 'stopped' }),
      restore: async () => events.push('round-resumed') },
    deletingProjects: false, openingFlowPromise: null,
    loadConfig: async () => ({ services: [{ baseUrl: 'https://service.test', token: 'configured-token' }] }),
    normalizeBaseUrl: value => value, bridgeId: 'bridge',
    chrome: { storage: { local: storage } },
    broadcast() {}, addLog: (_level, message) => events.push(message),
    rememberImagePolicyFallback: async (_storage, image, policy) => {
      events.push(['policy', image, policy.reason]); return { imageHash: 'hash', ...policy };
    },
    completePolicyFallbackTask: async (_service, task, _context, policy) => {
      events.push(['original', task.assignmentId, policy.reason]); return true;
    },
    reportFailWithRetry: async (_service, payload) => events.push(['retry', payload.assignmentId, payload.retryable]),
    recordStat: value => events.push(['stat', value]), STAT_FAILED: 'failed', addTaskHistory() {},
    pauseReasonCode: 'FLOW_UNSAFE_GENERATION', resumePoll: () => events.push('resume'),
    scheduleLoop: () => events.push('schedule'),
    postJson: async (_service, path, payload) => events.push(['lease', path, payload.assignmentId]),
  });
  vm.runInContext(source.slice(source.indexOf('function broadcastGenerationDecisions('),
    source.indexOf('async function loadConfig(')), context);
  return { context, decisions, data, events };
}

test('original choice uses the existing policy completion and resumes after the last choice', async () => {
  const h = resolverHarness();
  await h.decisions.save(record());
  await h.context.resolveGenerationDecision('one', 'original');
  assert.deepEqual(h.events.slice(0, 2), [
    ['policy', 'source-one', 'PUBLIC_ERROR_UNSAFE_GENERATION'],
    ['original', 'one', 'PUBLIC_ERROR_UNSAFE_GENERATION'],
  ]);
  assert.deepEqual(h.events.slice(-3), ['round-resumed', 'resume', 'schedule']);
  assert.equal(h.decisions.list().length, 0);
  assert.equal(h.context.generationDecisionBusy, false);
});

test('retry returns the same assignment, does not cache policy, and does not resume other pending choices', async () => {
  const h = resolverHarness();
  await h.decisions.save(record());
  await h.decisions.save(record('two'));
  await h.context.resolveGenerationDecision('one', 'retry');
  assert.deepEqual(h.events, [['retry', 'one', true], ['stat', 'failed']]);
  assert.deepEqual(h.decisions.list().map(value => value.id), ['two']);
});

test('an independent quota stop remains stopped after resolving the content decision', async () => {
  const h = resolverHarness();
  h.context.pauseReasonCode = 'DAILY_QUOTA_REACHED';
  await h.decisions.save(record());
  await h.context.resolveGenerationDecision('one', 'original');
  assert.equal(h.events.includes('resume'), false);
});

test('duplicate clicks are rejected while reporting and cannot overwrite a chosen action', async () => {
  const h = resolverHarness();
  let finish;
  h.context.reportFailWithRetry = () => new Promise(resolve => { finish = resolve; });
  await h.decisions.save(record());
  const pending = h.context.resolveGenerationDecision('one', 'retry');
  await tick();
  await assert.rejects(h.context.resolveGenerationDecision('one', 'original'), /稍候/);
  finish();
  await pending;
  await assert.rejects(h.context.resolveGenerationDecision('one', 'original'), /已处理/);
});

test('report failure retains a persisted choice and allows retrying that operation', async () => {
  const h = resolverHarness();
  await h.decisions.save(record());
  h.context.reportFailWithRetry = async () => { throw new Error('offline'); };
  await assert.rejects(h.context.resolveGenerationDecision('one', 'retry'), /offline/);
  assert.equal(h.context.generationDecisionBusy, false);
  assert.equal(h.decisions.get('one').action, 'retry');
  assert.equal(Object.values(h.data)[0].action, 'retry');
  await assert.rejects(h.context.resolveGenerationDecision('one', 'original'), /已提交此选择/);
  h.context.reportFailWithRetry = async () => {};
  await h.context.resolveGenerationDecision('one', 'retry');
  assert.equal(h.decisions.list().length, 0);
});

test('an already returned assignment sets the original-image cache without completing a stale assignment', async () => {
  const h = resolverHarness();
  await h.decisions.save(record('one', true));
  await h.context.resolveGenerationDecision('one', 'original');
  assert.deepEqual(h.events[0], ['policy', 'source-one', 'PUBLIC_ERROR_UNSAFE_GENERATION']);
  assert.equal(h.events.some(item => Array.isArray(item) && ['original', 'retry'].includes(item[0])), false);
});

test('leases extend while awaiting choice and expiry switches to the returned-task path', async () => {
  const h = resolverHarness();
  await h.decisions.save(record());
  await h.context.refreshGenerationDecisionLeases();
  assert.deepEqual(h.events, [['lease', '/turboflow-bridge/tasks/translated', 'one']]);
  h.context.postJson = async () => { throw new Error('assignment not found or expired'); };
  await h.context.refreshGenerationDecisionLeases();
  assert.equal(h.decisions.get('one').returned, true);
  await h.context.resolveGenerationDecision('one', 'original');
  assert.equal(h.events.some(item => Array.isArray(item) && item[0] === 'original'), false);
});

test('a new concurrent rejection during resume keeps polling paused', async () => {
  const h = resolverHarness();
  await h.decisions.save(record());
  h.context.roundRecovery.restore = async () => { await h.decisions.save(record('late')); };
  await h.context.resolveGenerationDecision('one', 'retry');
  assert.equal(h.events.includes('resume'), false);
});

test('unsafe generation holds the task before any fail report and frees its Flow slot', async () => {
  const h = resolverHarness();
  Object.assign(h.context, {
    isUnsafeGeneration, currentTasks: [], reuseSummary: { count: 0 },
    roundRecovery: { state: { roundId: 1 } },
    buildPrompt: () => 'prompt', ensureDataUrl: value => value,
    createThumbnail: async () => 'thumb', broadcastTasksChanged() {},
    imageDigest: async () => 'hash', findImagePolicyFallback: async () => null,
    observeTaskForUploadRejection: async () => ({ sequence: 1, forgotten: [] }),
    translateImage: async () => { throw new Error(unsafe); }, trackOperation: promise => promise,
    runWithTimeout: promise => promise, TRANSLATE_TIMEOUT_MS: 300000,
    pollPaused: false, pauseReasonCode: null,
    pausePoll: (_reason, options) => { h.context.pollPaused = true; h.context.pauseReasonCode = options.code; },
    removeCurrentTask: id => { h.context.currentTasks = h.context.currentTasks.filter(task => task.assignmentId !== id); },
    releaseFlowSlot: id => h.events.push(['released', id]),
    reportFailWithRetry: () => assert.fail('must wait for user decision'),
  });
  vm.runInContext(source.slice(source.indexOf('async function executeTask('), source.indexOf('function runWithTimeout(')), h.context);
  await h.context.executeTask({ baseUrl: 'https://service.test' }, record().task, {});
  assert.equal(h.context.pollPaused, true);
  assert.equal(h.context.pauseReasonCode, 'FLOW_UNSAFE_GENERATION');
  assert.equal(h.decisions.list().length, 1);
  assert.equal(h.context.currentTasks.length, 0);
  assert.ok(h.events.some(item => Array.isArray(item) && item[0] === 'released'));
});

test('decision panel renders both choices, binds the correct task and disables duplicate actions', () => {
  const doc = { createElement: () => ({ children: [], events: {}, classList: { toggle() {} },
    appendChild(child) { this.children.push(child); }, append(...children) { this.children.push(...children); },
    replaceChildren() { this.children = []; }, addEventListener(name, handler) { this.events[name] = handler; } }) };
  const container = doc.createElement();
  container.ownerDocument = doc;
  const chosen = [];
  renderGenerationDecisionPanel(container, [{ id: 'one', subTaskId: '<b>unsafe</b>', deadlineAt: 11000 }], false,
    (...args) => chosen.push(args), 1000);
  const body = container.children[0].children[0];
  assert.match(body.children[1].textContent, /<b>unsafe<\/b>/);
  assert.match(body.children[1].textContent, /10 秒内未选择将自动使用原图/);
  const buttons = body.children[2].children;
  assert.deepEqual(buttons.map(button => button.textContent), ['重试', '使用原图']);
  buttons[1].events.click();
  assert.deepEqual(chosen, [['one', 'original']]);
  renderGenerationDecisionPanel(container, [{ id: 'one', action: 'original' }], true, () => {});
  assert.ok(container.children[0].children[0].children[2].children.every(button => button.disabled));
});

test('ten-second deadline persists across updates and restarts; older records get a fresh window', async () => {
  const { storage, data } = storageHarness();
  let now = 1000;
  const first = createGenerationDecisions(storage, () => now);
  await first.save(record());
  assert.equal(first.get('one').deadlineAt, 11000);
  now = 6000;
  await first.save({ ...record(), returned: true });
  assert.equal(first.summaries()[0].deadlineAt, 11000);
  data['unsafeGenerationDecision:old'] = record('old');
  const restarted = createGenerationDecisions(storage, () => now);
  await restarted.restore();
  assert.equal(restarted.get('one').deadlineAt, 11000);
  assert.equal(restarted.get('old').deadlineAt, 16000);
  assert.equal(data['unsafeGenerationDecision:old'].deadlineAt, 16000);
});

function timeoutHarness(records, resolve) {
  let now = 1000;
  let timer;
  const calls = [];
  const alarms = [];
  const errors = [];
  const controller = createGenerationDecisionTimeout({
    list: () => records, now: () => now,
    setTimer: (fn, delay) => { timer = { fn, delay }; return 1; }, clearTimer: () => { timer = null; },
    armAlarm: when => alarms.push(when), clearAlarm: () => alarms.push('clear'),
    onError: error => errors.push(error.message),
    resolve: resolve || (async (id, action, options) => {
      calls.push([id, action, options.automatic]); records.find(item => item.id === id).action = action;
    }),
  });
  return { controller, calls, alarms, errors, setNow: value => { now = value; }, getTimer: () => timer };
}

test('background timeout defaults to original at ten seconds without a panel, never before', async () => {
  const h = timeoutHarness([{ ...record(), deadlineAt: 11000 }]);
  h.controller.schedule();
  assert.equal(h.getTimer().delay, 10000);
  assert.equal(h.alarms[0], 11000);
  h.setNow(10999);
  await h.controller.run();
  assert.equal(h.calls.length, 0);
  assert.equal(h.getTimer().delay, 1);
  h.setNow(11000);
  await h.getTimer().fn();
  assert.deepEqual(h.calls, [['one', 'original', true]]);
  assert.equal(h.getTimer(), null);
  assert.equal(h.alarms.at(-1), 'clear');
});

test('restart executes overdue decisions; manual retry cancels default and deadlines are per task', async () => {
  const records = [{ ...record(), deadlineAt: 1000, action: 'retry' },
    { ...record('two'), deadlineAt: 500 }, { ...record('three'), deadlineAt: 11000 }];
  const h = timeoutHarness(records);
  h.controller.schedule();
  assert.equal(h.getTimer().delay, 0);
  await h.controller.run();
  assert.deepEqual(h.calls, [['two', 'original', true]]);
  assert.equal(h.getTimer().delay, 10000);
  records[2].action = 'original';
  h.controller.schedule();
  assert.equal(h.getTimer(), null);
});

test('busy handling retries with a delay and overlapping timer/alarm callbacks cannot duplicate work', async () => {
  const records = [{ ...record(), deadlineAt: 1000 }];
  let finish;
  let attempts = 0;
  const h = timeoutHarness(records, async () => {
    attempts++;
    if (attempts === 1) throw new Error('busy');
    await new Promise(resolve => { finish = resolve; });
    records[0].action = 'original';
  });
  await h.controller.run();
  assert.equal(h.getTimer().delay, 1000);
  h.setNow(2000);
  const pending = h.controller.run();
  await tick();
  await h.controller.run();
  assert.equal(attempts, 2);
  finish();
  await pending;
  assert.equal(h.getTimer(), null);
});

test('automatic resolver does not overwrite a manual choice or act before its persisted deadline', async () => {
  const h = resolverHarness();
  await h.decisions.save({ ...record(), action: 'retry', deadlineAt: 1 });
  await h.context.resolveGenerationDecision('one', 'original', { automatic: true });
  assert.equal(h.decisions.get('one').action, 'retry');
  await h.decisions.save({ ...record('two'), deadlineAt: Date.now() + 10000 });
  await h.context.resolveGenerationDecision('two', 'original', { automatic: true });
  assert.deepEqual(h.events, []);
});
