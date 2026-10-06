import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import * as policy from '../task-error-policy.js';
import { parseBatchexecuteResponse } from '../flow-modern-api.js';
import { generateWithReference, resolveFlowImageUrl, uploadImageToFlow } from '../flow-api.js';

function backgroundStopHarness() {
  const source = readFileSync(new URL('../background.js', import.meta.url), 'utf8');
  const saved = {};
  const events = [];
  const releases = [];
  const context = vm.createContext({
    ...policy,
    releaseApi2351: async () => { events.push({ type: 'VERIFICATION_RELEASE' }); },
    releasePrefetchedTask: reason => releases.push(reason),
    recoveryState: { consecutiveFailures: 0, consecutiveFlowDisconnects: 0 },
    pollPaused: false, pauseReason: null, pauseReasonCode: null, pausedAt: 0,
    nextPollAt: 1000, timerId: 1, lastStatus: {},
    STOP_STATE_STORAGE_KEY: 'bridgeStopState',
    persistRecoveryState() {}, clearTimeout() {}, safeAction() {},
    broadcast: event => events.push(event), addLog() {},
    chrome: { storage: { local: {
      set: async data => Object.assign(saved, data), remove: async () => {},
    } } },
  });
  vm.runInContext(source.slice(source.indexOf('function applyFailureStreak('),
    source.indexOf('function resumePoll(')), context);
  return { context, saved, events, releases };
}

test('entering the stopped state hands the standby task back to the server', () => {
  const { context, releases, events } = backgroundStopHarness();
  context.applyFailureStreak('increment', { errorCode: 'FLOW_AUTHENTICATION_FAILED' });
  assert.equal(context.pollPaused, true);
  assert.deepEqual(releases, ['bridge stopped']);
  assert.ok(events.some(event => event.type === 'VERIFICATION_RELEASE'));
});

test('the first RPC 8 persists a quota stop and later concurrent failures cannot overwrite it', () => {
  const { context, saved, events } = backgroundStopHarness();
  let error;
  try {
    parseBatchexecuteResponse(JSON.stringify([['wrb.fr', 'ogiZ0b', null, null, null, [8]]]), 'ogiZ0b');
  } catch (caught) { error = caught; }
  context.applyFailureStreak('increment', { errorCode: policy.classifyErrorCode(error), errorMessage: error.message });
  assert.equal(context.recoveryState.consecutiveFailures, 1);
  assert.equal(context.pollPaused, true);
  assert.equal(context.timerId, null);
  assert.equal(context.nextPollAt, 0);
  assert.equal(saved.bridgeStopState.pauseReasonCode, 'FLOW_RESOURCE_EXHAUSTED');
  assert.match(context.pauseReason, /RESOURCE_EXHAUSTED/);
  assert.doesNotMatch(context.pauseReason, /consecutive task failures/);
  const reason = context.pauseReason;
  context.applyFailureStreak('increment', { errorCode: 'FLOW_AUTHENTICATION_FAILED' });
  assert.equal(saved.bridgeStopState.pauseReason, reason);
  assert.equal(events.filter(e => e.type === 'BRIDGE_PAUSED').length, 1);
});

test('RPC rejection names the actual error and a later quota failure updates the panel', () => {
  const { context, saved, events } = backgroundStopHarness();
  context.applyFailureStreak('increment', { errorCode: 'FLOW_RPC_REJECTED', errorMessage: 'RPC status 3: INVALID_ARGUMENT' });
  assert.equal(saved.bridgeStopState.pauseReasonCode, 'FLOW_RPC_REJECTED');
  assert.match(context.pauseReason, /INVALID_ARGUMENT/);
  assert.doesNotMatch(context.pauseReason, /limit 5/);
  context.applyFailureStreak('increment', { errorCode: 'DAILY_QUOTA_REACHED' });
  assert.equal(saved.bridgeStopState.pauseReasonCode, 'DAILY_QUOTA_REACHED');
  assert.equal(events.filter(e => e.type === 'BRIDGE_PAUSED').length, 2);
  assert.equal(context.lastStatus.message, context.pauseReason);
});

test('per-model daily quota persists the daily-limit stop reason on the first failure', () => {
  const { context, saved } = backgroundStopHarness();
  let error;
  try {
    parseBatchexecuteResponse(JSON.stringify([['wrb.fr', 'ogiZ0b', null, null, null,
      [8, null, [['type.googleapis.com/google.rpc.ErrorInfo', ['PUBLIC_ERROR_PER_MODEL_DAILY_QUOTA_REACHED']]]]]]), 'ogiZ0b');
  } catch (caught) { error = caught; }
  context.applyFailureStreak('increment', { errorCode: policy.classifyErrorCode(error), errorMessage: error.message });
  assert.equal(context.pollPaused, true);
  assert.equal(saved.bridgeStopState.pauseReasonCode, 'DAILY_QUOTA_REACHED');
  assert.match(context.pauseReason, /daily quota reached/);
});

function mockModernResponse(response) {
  let calls = 0;
  globalThis.window = {
    WIZ_global_data: { SNlM0e: 'fixture-token' },
    location: { origin: 'https://flow.google.com', pathname: '/project/test' },
  };
  globalThis.document = { documentElement: { lang: 'en' } };
  globalThis.fetch = async () => { calls++; return response; };
  globalThis.chrome = {
    tabs: { get: async () => ({ url: 'https://flow.google.com/project/test' }) },
    scripting: { executeScript: async ({ func, args = [] }) => [{
      result: args.length === 2 ? 'fixture-captcha' : await func(...args),
    }] },
  };
  return () => calls;
}

test('HTTP 429 upload stops without retry and HTTP 403 is permission denial, not authentication', async () => {
  for (const status of [429, 403, 401]) {
    const calls = mockModernResponse({ ok: false, status, text: async () => 'request rejected' });
    await assert.rejects(uploadImageToFlow(1, {
      base64: 'YWJj', fileName: 'test.png', mimeType: 'image/png', pid: 'test',
    }), error => {
      assert.equal(error.code, status === 429 ? 'FLOW_RESOURCE_EXHAUSTED'
        : status === 401 ? 'FLOW_AUTHENTICATION_FAILED' : 'FLOW_RPC_REJECTED');
      assert.equal(error.httpStatus, status);
      if (status === 403) assert.equal(error.rpcStatusName, 'PERMISSION_DENIED');
      return true;
    });
    assert.equal(calls(), 1);
  }
});

test('media URL resolution propagates RPC 8 immediately without readiness retries', async () => {
  const calls = mockModernResponse({
    ok: true, text: async () => JSON.stringify([['wrb.fr', 'uurnC', null, null, null, [8]]]),
  });
  await assert.rejects(resolveFlowImageUrl(1, { mediaId: 'media/test' }, 'https://flow.google.com/project/test'),
    { code: 'FLOW_RESOURCE_EXHAUSTED', rpcStatus: 8 });
  assert.equal(calls(), 1);
});

test('media URL resolution also propagates explicit daily quota without readiness retries', async () => {
  const calls = mockModernResponse({
    ok: true, text: async () => JSON.stringify([['wrb.fr', 'uurnC', null, null, null,
      [8, 'PUBLIC_ERROR_PER_MODEL_DAILY_QUOTA_REACHED']]]),
  });
  await assert.rejects(resolveFlowImageUrl(1, { mediaId: 'media/test' }, 'https://flow.google.com/project/test'),
    { code: 'DAILY_QUOTA_REACHED', rpcStatus: 8 });
  assert.equal(calls(), 1);
});

for (const modern of [true, false]) {
  test(`${modern ? 'BOQ' : 'legacy'} checks the submission gate after awaiting verification`, async () => {
    const calls = mockModernResponse({ ok: true,
      text: async () => JSON.stringify([['wrb.fr', 'nzlxg', '[1050,1,2,2,null,1050]']]),
    });
    chrome.tabs.get = async () => ({ url: modern
      ? 'https://flow.google.com/project/test' : 'https://labs.google/fx/tools/flow/project/test' });
    let paused = false;
    const execute = chrome.scripting.executeScript;
    chrome.scripting.executeScript = async options => {
      const result = await execute(options);
      if (options.args?.length === 2) paused = true;
      return result;
    };
    await assert.rejects(generateWithReference(1, {
      prompt: 'translate', referenceMediaId: 'media/test', pid: 'test',
      beforeSubmit: () => {
        if (paused) throw Object.assign(new Error('paused'), { code: 'FLOW_SUBMISSION_PAUSED' });
      },
    }), { code: 'FLOW_SUBMISSION_PAUSED' });
    assert.equal(calls(), modern ? 1 : 0); // Only the credit preflight may have run.
  });
}

test('a quota stop prevents any further backend task polling', async () => {
  const { context } = backgroundStopHarness();
  const source = readFileSync(new URL('../background.js', import.meta.url), 'utf8');
  context.postJson = () => assert.fail('must not fetch another backend task');
  vm.runInContext(source.slice(source.indexOf('async function pollTask('), source.indexOf('async function executeTask(')), context);
  context.applyFailureStreak('increment', { errorCode: 'FLOW_RESOURCE_EXHAUSTED' });
  assert.equal(await context.pollTask({}, {}, false), null);
});

test('a submitted task reports completion after another task causes a quota stop', async () => {
  const { context, saved } = backgroundStopHarness();
  const source = readFileSync(new URL('../background.js', import.meta.url), 'utf8');
  let finish;
  const completions = [];
  const history = [];
  const released = [];
  Object.assign(context, {
    currentTasks: [], reuseSummary: { count: 0 }, bridgeId: 'bridge',
    TRANSLATE_TIMEOUT_MS: 300000, POLL_INTERVAL_MS: 500, STAT_SUCCESS: 'success',
    buildPrompt: () => 'translate', ensureDataUrl: value => value,
    broadcastTasksChanged() {}, createThumbnail: async () => null,
    imageDigest: async () => 'digest', findImagePolicyFallback: async () => null,
    translateImage: () => new Promise(resolve => { finish = resolve; }),
    runWithTimeout: promise => promise,
    releaseFlowSlot: id => released.push(id),
    postCompletionWithRetry: async (_service, completion) => completions.push(completion),
    reportFailWithRetry: () => assert.fail('submitted task must not be failed by another task quota error'),
    recordStat: outcome => assert.equal(outcome, 'success'),
    addTaskHistory: item => history.push(item),
    removeCurrentTask: id => { context.currentTasks = context.currentTasks.filter(t => t.assignmentId !== id); },
    scheduleLoop: () => assert.equal(context.pollPaused, true),
  });
  vm.runInContext(source.slice(source.indexOf('async function executeTask('), source.indexOf('function runWithTimeout(')), context);
  const pending = context.executeTask({ baseUrl: 'service' }, {
    assignmentId: 'active', taskId: 'task', subTaskId: 'sub', imageBase64: 'source',
  }, {});
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(typeof finish, 'function');
  context.applyFailureStreak('increment', { errorCode: 'FLOW_RESOURCE_EXHAUSTED' });
  assert.equal(completions.length, 0);
  finish({ resultDataUrl: 'translated', resultUrl: 'https://flow-content.google/result' });
  await pending;
  assert.equal(completions.length, 1);
  assert.equal(completions[0].resultImageBase64, 'translated');
  assert.equal(history[0].status, 'completed');
  assert.ok(released.includes('active'));
  assert.equal(context.currentTasks.length, 0);
  assert.equal(context.pollPaused, true);
  assert.equal(saved.bridgeStopState.pauseReasonCode, 'FLOW_RESOURCE_EXHAUSTED');
});

test('a failed translation releases the standby before reporting its own failure', async () => {
  const { context } = backgroundStopHarness();
  const source = readFileSync(new URL('../background.js', import.meta.url), 'utf8');
  const order = [];
  Object.assign(context, {
    currentTasks: [], reuseSummary: { count: 0 }, bridgeId: 'bridge',
    TRANSLATE_TIMEOUT_MS: 300000, POLL_INTERVAL_MS: 500, STAT_FAILED: 'failed',
    buildPrompt: () => 'translate', ensureDataUrl: value => value,
    broadcastTasksChanged() {}, createThumbnail: async () => null,
    imageDigest: async () => 'digest', findImagePolicyFallback: async () => null,
    translateImage: async () => { throw new Error('translate timeout (300s)'); },
    runWithTimeout: promise => promise,
    releaseFlowSlot() {}, recordStat() {}, addTaskHistory() {}, removeCurrentTask() {}, scheduleLoop() {},
    friendlyErrorMessage: (_code, message) => message,
    releasePrefetchedTask: reason => order.push(`release:${reason}`),
    reportFailWithRetry: async (_service, payload) => order.push(`fail:${payload.assignmentId}:${payload.errorCode}`),
  });
  vm.runInContext(source.slice(source.indexOf('async function executeTask('), source.indexOf('function runWithTimeout(')), context);
  await context.executeTask({ baseUrl: 'service' }, {
    assignmentId: 'active', taskId: 'task', subTaskId: 'sub', imageBase64: 'source',
  }, {});
  assert.deepEqual(order, ['release:task sub failed [TIMEOUT]', 'fail:active:TIMEOUT']);
});
