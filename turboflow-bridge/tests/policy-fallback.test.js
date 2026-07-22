import test from 'node:test';
import assert from 'node:assert/strict';

import * as flowApi from '../flow-api.js';

test('policy fallback completion keeps the original image without sending image bytes', () => {
  assert.equal(typeof flowApi.buildPolicyFallbackCompletion, 'function');

  const payload = flowApi.buildPolicyFallbackCompletion({
    bridgeId: 'bridge-a',
    assignmentId: 'assignment-a',
    apiStatus: 'INVALID_ARGUMENT',
    reason: 'PUBLIC_ERROR_SEXUAL_UPLOAD',
    elapsedMs: 1234,
  });

  assert.deepEqual(payload, {
    bridgeId: 'bridge-a',
    assignmentId: 'assignment-a',
    policyFallback: true,
    policyFallbackStatus: 'INVALID_ARGUMENT',
    policyFallbackReason: 'PUBLIC_ERROR_SEXUAL_UPLOAD',
    elapsedMs: 1234,
  });
  assert.equal('resultImageBase64' in payload, false);
});
