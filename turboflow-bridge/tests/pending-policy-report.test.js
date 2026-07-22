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

test('pending policy completion survives enumeration until acknowledged', async () => {
  const storage = memoryStorage();
  const policy = {
    imageHash: 'digest-a',
    apiStatus: 'INVALID_ARGUMENT',
    reason: 'PUBLIC_ERROR_SEXUAL_UPLOAD',
  };
  const pending = await rememberPendingPolicyCompletion(storage, policy, {
    serviceBaseUrl: 'https://service.example',
    payload: { assignmentId: 'assignment-a', imageHash: 'digest-a' },
  });

  assert.equal((await listPendingPolicyCompletions(storage))[0].payload.assignmentId, 'assignment-a');

  await clearPendingPolicyCompletion(storage, pending.pendingKey);
  assert.deepEqual(await listPendingPolicyCompletions(storage), []);
});
