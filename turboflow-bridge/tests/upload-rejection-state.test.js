import test from 'node:test';
import assert from 'node:assert/strict';

import {
  UPLOAD_REJECTION_FORGET_AFTER_TASKS,
  UPLOAD_REJECTION_POLICY_THRESHOLD,
  UPLOAD_REJECTION_STORAGE_KEY,
  forgetUploadRejection,
  listUploadRejections,
  observeTaskForUploadRejection,
  rememberUploadRejection,
  resetUploadRejectionMemory,
  shouldFallBackToPolicy,
} from '../upload-rejection-state.js';

function fakeStorage(initial = {}) {
  const data = structuredClone(initial);
  const writes = [];
  return {
    data,
    writes,
    async get(keys) {
      if (keys == null) return structuredClone(data);
      const out = {};
      for (const key of Array.isArray(keys) ? keys : [keys]) if (key in data) out[key] = structuredClone(data[key]);
      return out;
    },
    async set(values) { writes.push(structuredClone(values)); Object.assign(data, structuredClone(values)); },
    async remove() {},
  };
}

const rejection = {
  rpcId: 'maseQ', rpcStatus: 3, apiStatus: 'INVALID_ARGUMENT', reason: null,
  errorMessage: 'Flow RPC maseQ failed (RPC status 3: INVALID_ARGUMENT)',
};

test.beforeEach(() => resetUploadRejectionMemory());

test('an unmarked image advances the sequence without touching storage', async () => {
  const storage = fakeStorage();
  const first = await observeTaskForUploadRejection(storage, 'img-a');
  const second = await observeTaskForUploadRejection(storage, 'img-b');
  assert.equal(first.record, null);
  assert.equal(first.sequence, 1);
  assert.equal(second.sequence, 2);
  assert.equal(storage.writes.length, 0);
});

test('the first real rejection is receipt one; the next three dispatches reach the policy threshold', async () => {
  assert.equal(UPLOAD_REJECTION_POLICY_THRESHOLD, 4);
  const storage = fakeStorage();
  const { sequence } = await observeTaskForUploadRejection(storage, 'img-a');
  const record = await rememberUploadRejection(storage, 'img-a', { ...rejection, sequence });
  assert.equal(record.receipts, 1);
  assert.equal(record.reason, 'INVALID_ARGUMENT');
  assert.equal(record.errorMessage, rejection.errorMessage);
  assert.equal(record.lastSequence, sequence);
  assert.equal(shouldFallBackToPolicy(record), false);

  const receipts = [];
  for (let i = 0; i < UPLOAD_REJECTION_POLICY_THRESHOLD - 1; i++) {
    await observeTaskForUploadRejection(storage, 'img-other');
    const { record: seen } = await observeTaskForUploadRejection(storage, 'img-a');
    receipts.push(seen.receipts);
  }
  assert.deepEqual(receipts, [2, 3, 4]);
  assert.equal(shouldFallBackToPolicy({ receipts: 3 }), false);
  assert.equal(shouldFallBackToPolicy({ receipts: 4 }), true);
});

test('the mark is forgotten after twenty other tasks, but a return within the window still counts', async () => {
  assert.equal(UPLOAD_REJECTION_FORGET_AFTER_TASKS, 20);
  const storage = fakeStorage();
  const { sequence } = await observeTaskForUploadRejection(storage, 'img-a');
  await rememberUploadRejection(storage, 'img-a', { ...rejection, sequence });

  for (let i = 0; i < UPLOAD_REJECTION_FORGET_AFTER_TASKS - 1; i++) {
    const seen = await observeTaskForUploadRejection(storage, `other-${i}`);
    assert.deepEqual(seen.forgotten, []);
  }
  // 19 条其他任务之后再派回：仍算一次收到，并重新开始数 20 条
  const returned = await observeTaskForUploadRejection(storage, 'img-a');
  assert.equal(returned.record.receipts, 2);

  for (let i = 0; i < UPLOAD_REJECTION_FORGET_AFTER_TASKS - 1; i++) {
    await observeTaskForUploadRejection(storage, `later-${i}`);
  }
  assert.equal((await listUploadRejections(storage)).length, 1);
  const twentieth = await observeTaskForUploadRejection(storage, 'last-other');
  assert.equal(twentieth.forgotten.length, 1);
  assert.equal(twentieth.forgotten[0].imageHash, 'img-a');
  assert.deepEqual(await listUploadRejections(storage), []);
  // 之后同一张图再来：重新从零开始
  const fresh = await observeTaskForUploadRejection(storage, 'img-a');
  assert.equal(fresh.record, null);
});

test('marks and the task sequence survive a service worker restart', async () => {
  const storage = fakeStorage();
  const { sequence } = await observeTaskForUploadRejection(storage, 'img-a');
  await rememberUploadRejection(storage, 'img-a', { ...rejection, sequence });
  await observeTaskForUploadRejection(storage, 'other-1');

  resetUploadRejectionMemory();
  const seen = await observeTaskForUploadRejection(storage, 'img-a');
  assert.equal(seen.sequence, 3);
  assert.equal(seen.record.receipts, 2);
  assert.equal(storage.data[UPLOAD_REJECTION_STORAGE_KEY].sequence, 3);
});

test('concurrent real rejections of the same image each count as a receipt', async () => {
  const storage = fakeStorage();
  const [a, b] = await Promise.all([
    observeTaskForUploadRejection(storage, 'img-a'),
    observeTaskForUploadRejection(storage, 'img-a'),
  ]);
  assert.equal(a.record, null);
  assert.equal(b.record, null);
  const [first, second] = await Promise.all([
    rememberUploadRejection(storage, 'img-a', { ...rejection, sequence: a.sequence }),
    rememberUploadRejection(storage, 'img-a', { ...rejection, sequence: b.sequence, reason: 'PUBLIC_ERROR_X' }),
  ]);
  assert.equal(first.receipts, 1);
  assert.equal(second.receipts, 2);
  assert.equal(second.reason, 'PUBLIC_ERROR_X');
  assert.equal(second.lastSequence, 2);
});

test('forgetting removes the mark from memory and storage; storage failures keep the in-memory mark', async () => {
  const storage = fakeStorage();
  await rememberUploadRejection(storage, 'img-a', rejection);
  assert.equal((await forgetUploadRejection(storage, 'img-a')).imageHash, 'img-a');
  assert.equal(await forgetUploadRejection(storage, 'img-a'), null);
  assert.deepEqual(storage.data[UPLOAD_REJECTION_STORAGE_KEY].records, {});

  resetUploadRejectionMemory();
  const broken = { ...fakeStorage(), set: async () => { throw new Error('quota'); } };
  const record = await rememberUploadRejection(broken, 'img-b', rejection);
  assert.equal(record.storageError, 'quota');
  const seen = await observeTaskForUploadRejection(broken, 'img-b');
  assert.equal(seen.record.receipts, 2);
  assert.equal(seen.storageError, 'quota');
});

test('corrupted persisted state is ignored', async () => {
  const storage = fakeStorage({ [UPLOAD_REJECTION_STORAGE_KEY]: {
    sequence: 'x', records: { 'img-a': { imageHash: 'other' }, 5: null },
  } });
  const seen = await observeTaskForUploadRejection(storage, 'img-a');
  assert.equal(seen.sequence, 1);
  assert.equal(seen.record, null);
});
