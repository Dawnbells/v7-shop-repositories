import test from 'node:test';
import assert from 'node:assert/strict';

import {
  clearPendingPolicyCompletion,
  listPendingPolicyCompletions,
} from '../policy-fallback-state.js';

function storageWithLegacyReport() {
  const values = {
    'pendingPolicyFallbackReport:shared-digest': {
      imageHash: 'shared-digest',
      serviceBaseUrl: 'https://service.example',
      payload: { assignmentId: 'legacy-assignment', imageHash: 'shared-digest' },
    },
  };
  return {
    async get(keys) {
      if (keys === null) return { ...values };
      const result = {};
      for (const key of keys) result[key] = values[key];
      return result;
    },
    async remove(keys) {
      for (const key of keys) delete values[key];
    },
  };
}

test('legacy image-keyed pending report is removed after acknowledgement', async () => {
  const storage = storageWithLegacyReport();
  const [pending] = await listPendingPolicyCompletions(storage);

  assert.equal(pending.pendingKey, 'https://service.example::legacy-assignment');
  await clearPendingPolicyCompletion(storage, pending);

  assert.deepEqual(await listPendingPolicyCompletions(storage), []);
});
