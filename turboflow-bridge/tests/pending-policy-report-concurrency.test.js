import test from 'node:test';
import assert from 'node:assert/strict';

import {
  clearPendingPolicyCompletion,
  listPendingPolicyCompletions,
  rememberPendingPolicyCompletion,
} from '../policy-fallback-state.js';

function memoryStorage() {
  const values = {};
  return {
    async get(keys) {
      if (keys === null) return { ...values };
      const result = {};
      for (const key of keys) result[key] = values[key];
      return result;
    },
    async set(update) {
      Object.assign(values, update);
    },
    async remove(keys) {
      for (const key of keys) delete values[key];
    },
  };
}

test('same-image assignments retain and acknowledge independent pending reports', async () => {
  const storage = memoryStorage();
  const policy = { imageHash: 'shared-digest' };

  const first = await rememberPendingPolicyCompletion(storage, policy, {
    serviceBaseUrl: 'https://service.example',
    payload: { assignmentId: 'assignment-a', imageHash: 'shared-digest' },
  });
  const second = await rememberPendingPolicyCompletion(storage, policy, {
    serviceBaseUrl: 'https://service.example',
    payload: { assignmentId: 'assignment-b', imageHash: 'shared-digest' },
  });

  assert.deepEqual(
    (await listPendingPolicyCompletions(storage)).map((item) => item.payload.assignmentId).sort(),
    ['assignment-a', 'assignment-b'],
  );

  await clearPendingPolicyCompletion(storage, first.pendingKey);
  assert.deepEqual(
    (await listPendingPolicyCompletions(storage)).map((item) => item.payload.assignmentId),
    ['assignment-b'],
  );
  await clearPendingPolicyCompletion(storage, second.pendingKey);
});
