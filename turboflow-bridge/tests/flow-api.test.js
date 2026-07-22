import test from 'node:test';
import assert from 'node:assert/strict';

import { uploadImageToFlow } from '../flow-api.js';

test('upload INVALID_ARGUMENT becomes a policy error with the Google reason', async () => {
  const responseBody = JSON.stringify({
    error: {
      code: 400,
      message: 'Request contains an invalid argument.',
      status: 'INVALID_ARGUMENT',
      details: [{
        '@type': 'type.googleapis.com/google.rpc.ErrorInfo',
        reason: 'PUBLIC_ERROR_SEXUAL_UPLOAD',
      }],
    },
  });

  globalThis.fetch = async () => ({
    ok: false,
    status: 400,
    text: async () => responseBody,
  });
  globalThis.chrome = {
    scripting: {
      executeScript: async ({ func, args }) => [{ result: await func(...args) }],
    },
  };

  await assert.rejects(
    uploadImageToFlow(1, {
      base64: 'aW1hZ2U=',
      fileName: 'source.png',
      mimeType: 'image/png',
      pid: 'project-id',
      token: 'token',
    }),
    (error) => {
      assert.equal(error.code, 'FLOW_UPLOAD_POLICY_REJECTED');
      assert.equal(error.apiStatus, 'INVALID_ARGUMENT');
      assert.equal(error.reason, 'PUBLIC_ERROR_SEXUAL_UPLOAD');
      return true;
    },
  );
});
