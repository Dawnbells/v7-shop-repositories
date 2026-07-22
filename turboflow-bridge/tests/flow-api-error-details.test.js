import test from 'node:test';
import assert from 'node:assert/strict';

import { uploadImageToFlow } from '../flow-api.js';

async function rejectedUpload(error) {
  globalThis.fetch = async () => ({
    ok: false,
    status: error.code,
    text: async () => JSON.stringify({ error }),
  });
  globalThis.chrome = {
    scripting: {
      executeScript: async ({ func, args }) => [{ result: await func(...args) }],
    },
  };
  return uploadImageToFlow(1, {
    base64: 'aW1hZ2U=',
    fileName: 'source.png',
    mimeType: 'image/png',
    pid: 'project-id',
    token: 'token',
  });
}

test('only google.rpc.ErrorInfo supplies the policy reason', async () => {
  await assert.rejects(
    rejectedUpload({
      code: 400,
      status: 'INVALID_ARGUMENT',
      details: [
        { '@type': 'example.com/UnrelatedDetail', reason: 'WRONG_REASON' },
        {
          '@type': 'type.googleapis.com/google.rpc.ErrorInfo',
          reason: 'PUBLIC_ERROR_SEXUAL_UPLOAD',
        },
      ],
    }),
    (error) => {
      assert.equal(error.code, 'FLOW_UPLOAD_POLICY_REJECTED');
      assert.equal(error.reason, 'PUBLIC_ERROR_SEXUAL_UPLOAD');
      return true;
    },
  );
});

test('a policy-like reason without INVALID_ARGUMENT is an ordinary upload failure', async () => {
  await assert.rejects(
    rejectedUpload({
      code: 422,
      status: 'FAILED_PRECONDITION',
      details: [{
        '@type': 'type.googleapis.com/google.rpc.ErrorInfo',
        reason: 'PUBLIC_ERROR_SEXUAL_UPLOAD',
      }],
    }),
    (error) => {
      assert.equal(error.code, undefined);
      assert.match(error.message, /^Upload failed:/);
      return true;
    },
  );
});
