import test from 'node:test';
import assert from 'node:assert/strict';

import {
  findImagePolicyFallback,
  rememberImagePolicyFallback,
  reportPolicyFallback,
} from '../policy-fallback-state.js';

function memoryStorage() {
  const values = {};
  return {
    async get(keys) {
      const result = {};
      for (const key of keys) result[key] = values[key];
      return result;
    },
    async set(update) {
      Object.assign(values, update);
    },
  };
}
function concurrentSnapshotStorage() {
  const values = {};
  let initialReads = 0;
  let releaseInitialReads;
  const initialReadGate = new Promise((resolve) => {
    releaseInitialReads = resolve;
  });
  return {
    async get(keys) {
      const snapshot = {};
      for (const key of keys) snapshot[key] = values[key];
      if (initialReads < 2) {
        initialReads++;
        if (initialReads === 1) setTimeout(releaseInitialReads, 0);
        if (initialReads === 2) releaseInitialReads();
        await initialReadGate;
      }
      return snapshot;
    },
    async set(update) {
      Object.assign(values, update);
    },
  };
}


test('policy fallback reporting rejects after retry exhaustion', async () => {
  let attempts = 0;

  await assert.rejects(
    reportPolicyFallback({
      post: async () => {
        attempts++;
        throw new Error('server unavailable');
      },
      payload: { assignmentId: 'assignment-a' },
      maxRetries: 2,
      retryBaseMs: 1,
      sleep: async () => {},
    }),
    /server unavailable/,
  );

  assert.equal(attempts, 3);
});

test('policy fallback cache is image-only and retains the first reason', async () => {
  const storage = memoryStorage();
  const image = 'data:image/png;base64,aW1hZ2U=';

  const first = await rememberImagePolicyFallback(storage, image, {
    apiStatus: 'INVALID_ARGUMENT',
    reason: 'PUBLIC_ERROR_SEXUAL_UPLOAD',
  });
  const second = await rememberImagePolicyFallback(storage, image, {
    apiStatus: 'INVALID_ARGUMENT',
    reason: 'SOME_LATER_REASON',
  });
  const germanTaskHit = await findImagePolicyFallback(storage, image);

  assert.equal(first.reason, 'PUBLIC_ERROR_SEXUAL_UPLOAD');
  assert.equal(second.reason, 'PUBLIC_ERROR_SEXUAL_UPLOAD');
  assert.deepEqual(germanTaskHit, first);
});
test('concurrent policy writes for different images cannot overwrite each other', async () => {
  const storage = concurrentSnapshotStorage();
  const firstImage = 'data:image/png;base64,Zmlyc3Q=';
  const secondImage = 'data:image/png;base64,c2Vjb25k';

  await Promise.all([
    rememberImagePolicyFallback(storage, firstImage, {
      apiStatus: 'INVALID_ARGUMENT',
      reason: 'FIRST_REASON',
    }),
    rememberImagePolicyFallback(storage, secondImage, {
      apiStatus: 'INVALID_ARGUMENT',
      reason: 'SECOND_REASON',
    }),
  ]);

  assert.equal((await findImagePolicyFallback(storage, firstImage)).reason, 'FIRST_REASON');
  assert.equal((await findImagePolicyFallback(storage, secondImage)).reason, 'SECOND_REASON');
});

test('concurrent policy writes for the same image retain the first reason', async () => {
  const storage = concurrentSnapshotStorage();
  const image = 'data:image/png;base64,c2FtZQ==';

  const [first, second] = await Promise.all([
    rememberImagePolicyFallback(storage, image, {
      apiStatus: 'INVALID_ARGUMENT',
      reason: 'FIRST_REASON',
    }),
    rememberImagePolicyFallback(storage, image, {
      apiStatus: 'INVALID_ARGUMENT',
      reason: 'SECOND_REASON',
    }),
  ]);

  assert.equal(first.reason, 'FIRST_REASON');
  assert.equal(second.reason, 'FIRST_REASON');
  assert.equal((await findImagePolicyFallback(storage, image)).reason, 'FIRST_REASON');
});
test('storage failure retains an in-memory policy entry', async () => {
  const storage = {
    async get() {
      throw new Error('storage unavailable');
    },
    async set() {
      throw new Error('storage unavailable');
    },
  };
  const image = 'data:image/png;base64,bWVtb3J5';

  const remembered = await rememberImagePolicyFallback(storage, image, {
    apiStatus: 'INVALID_ARGUMENT',
    reason: 'MEMORY_REASON',
  });

  assert.equal(remembered.reason, 'MEMORY_REASON');
  assert.equal((await findImagePolicyFallback(storage, image)).reason, 'MEMORY_REASON');
});
