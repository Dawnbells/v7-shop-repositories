import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const source = readFileSync(new URL('../background.js', import.meta.url), 'utf8');

function harness() {
  const calls = [];
  const context = vm.createContext({
    UPLOAD_REJECTION_POLICY_THRESHOLD: 4,
    POLL_INTERVAL_MS: 500,
    STAT_FAILED: 'failed',
    bridgeId: 'bridge-a',
    chrome: { storage: { local: {} } },
    reportFailWithRetry: async (service, payload) => calls.push(['fail', payload]),
    addLog: (level, message) => calls.push(['log', level, message]),
    recordStat: stat => calls.push(['stat', stat]),
    addTaskHistory: entry => calls.push(['history', entry]),
    removeCurrentTask: id => calls.push(['remove', id]),
    scheduleLoop: ms => calls.push(['schedule', ms]),
    applyFailureStreak: () => calls.push(['streak']),
    releasePrefetchedTask: () => calls.push(['release-prefetch']),
    rememberImagePolicyFallback: async (storage, image, policy) => {
      calls.push(['remember-policy', policy]);
      return { imageHash: 'hash-a', ...policy };
    },
    forgetUploadRejection: async (storage, hash) => calls.push(['forget', hash]),
    completePolicyFallbackTask: async (service, task, ctx, policy) => calls.push(['policy-complete', policy]),
  });
  vm.runInContext(source.slice(source.indexOf('async function failUploadRejectedTask('),
    source.indexOf('async function completePolicyFallbackTask(')), context);
  return { context, calls };
}

const service = { baseUrl: 'https://svc' };
const task = { taskId: 't1', subTaskId: 's1', assignmentId: 'a1', imageBase64: 'aW1hZ2U=' };
const context = { startedAt: Date.now() - 50, sourceThumb: 'thumb', sourceImage: 'image', targetLang: 'en' };
const record = {
  imageHash: 'hash-a', receipts: 2, apiStatus: 'INVALID_ARGUMENT', reason: 'INVALID_ARGUMENT',
  errorMessage: 'Flow RPC maseQ failed (RPC status 3: INVALID_ARGUMENT)',
};

test('a re-dispatched rejected image is returned without upload, retryable, and neutral to the failure streak', async () => {
  const { context: ctx, calls } = harness();
  await ctx.failUploadRejectedTask(service, task, context, record);
  const fail = calls.find(call => call[0] === 'fail')[1];
  assert.equal(fail.bridgeId, 'bridge-a');
  assert.equal(fail.assignmentId, 'a1');
  assert.equal(fail.errorCode, 'FLOW_UPLOAD_REJECTED');
  assert.equal(fail.retryable, true);
  assert.match(fail.message, /2\/4/);
  assert.match(fail.message, /RPC status 3: INVALID_ARGUMENT/);
  assert.ok(calls.some(call => call[0] === 'stat' && call[1] === 'failed'));
  assert.equal(calls.find(call => call[0] === 'history')[1].status, 'failed');
  assert.ok(calls.some(call => call[0] === 'remove' && call[1] === 'a1'));
  assert.ok(calls.some(call => call[0] === 'schedule'));
  assert.equal(calls.some(call => call[0] === 'streak' || call[0] === 'release-prefetch'), false);
});

test('the fourth dispatch takes the legacy policy fallback and clears the mark', async () => {
  const { context: ctx, calls } = harness();
  await ctx.completeUploadRejectionAsPolicy(service, task, context, { ...record, receipts: 4, reason: 'PUBLIC_ERROR_X' });
  // vm 上下文里创建的对象原型不同，逐字段比较而不是 deepEqual
  const remembered = calls.find(call => call[0] === 'remember-policy')[1];
  assert.equal(remembered.apiStatus, 'INVALID_ARGUMENT');
  assert.equal(remembered.reason, 'PUBLIC_ERROR_X');
  assert.ok(calls.some(call => call[0] === 'forget' && call[1] === 'hash-a'));
  assert.equal(calls.find(call => call[0] === 'policy-complete')[1].reason, 'PUBLIC_ERROR_X');
  assert.equal(calls.some(call => call[0] === 'fail'), false);
});

test('executeTask consults the ledger after the policy cache and before uploading, and remembers the first RPC 3', () => {
  const execute = source.slice(source.indexOf('async function executeTask('), source.indexOf('function runWithTimeout('));
  const policyCache = execute.indexOf('findImagePolicyFallback(');
  const ledger = execute.indexOf('observeTaskForUploadRejection(');
  const translate = execute.indexOf('translateImage(task, conn)');
  assert.ok(policyCache > 0 && ledger > policyCache && translate > ledger);
  assert.match(execute, /shouldFallBackToPolicy\(observed\.record\)/);
  assert.match(execute, /errorCode === 'FLOW_UPLOAD_REJECTED' && imageHash/);
  const remember = execute.indexOf('rememberUploadRejection(');
  const report = execute.indexOf('await reportFailWithRetry(service, {', remember);
  assert.ok(remember > translate && report > remember);
  assert.match(execute.slice(report, report + 400), /retryable: true/);
});
