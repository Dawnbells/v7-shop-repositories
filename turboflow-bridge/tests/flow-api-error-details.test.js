import test from 'node:test';
import assert from 'node:assert/strict';

import { clearTokenCache, generateWithReference, uploadImageToFlow } from '../flow-api.js';

async function rejectedUpload(error) {
  globalThis.fetch = async () => ({
    ok: false,
    status: error.code,
    text: async () => JSON.stringify({ error }),
    json: async () => ({}),
  });
  globalThis.chrome = {
    scripting: {
      executeScript: async ({ func, args = [] }) => [{ result: await func(...args) }],
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

test('a 401 from the generate stage also exposes a structured authentication failure', async () => {
  // 以前这条路径只抛 `HTTP 401: ...`，被 classifyErrorCode 兜成 FLOW_EXECUTION_FAILED 后无限重试
  clearTokenCache();
  const body = JSON.stringify({ error: { code: 401, status: 'UNAUTHENTICATED' } });
  globalThis.fetch = async () => ({
    ok: false,
    status: 401,
    text: async () => body,
    // getSessionToken 会拿这个 —— 返回空 body 让它拿不到新 token，不再重试
    json: async () => ({}),
  });
  globalThis.chrome = {
    scripting: {
      executeScript: async ({ func, args }) => {
        // getRecaptchaToken 传 [siteKey, action]，callFlowApi 传 [url, body, token]，
        // getSessionToken 不传 args
        if (Array.isArray(args) && args.length === 2) {
          return [{ result: 'recaptcha-token' }];
        }
        return [{ result: await func(...(args || [])) }];
      },
    },
  };

  await assert.rejects(
    generateWithReference(1, {
      prompt: 'translate',
      referenceMediaId: 'media-1',
      aspectRatio: 'IMAGE_ASPECT_RATIO_SQUARE',
      pid: 'project-id',
      token: 'stale-token',
      model: 'NARWHAL',
    }),
    (error) => {
      assert.equal(error.name, 'FlowAuthenticationError');
      assert.equal(error.code, 'FLOW_AUTHENTICATION_FAILED');
      assert.equal(error.httpStatus, 401);
      return true;
    },
  );
});

test('an unauthenticated upload exposes a structured authentication failure', async () => {
  await assert.rejects(
    rejectedUpload({
      code: 401,
      message: 'Request had invalid authentication credentials.',
      status: 'UNAUTHENTICATED',
    }),
    (error) => {
      assert.equal(error.name, 'FlowAuthenticationError');
      assert.equal(error.code, 'FLOW_AUTHENTICATION_FAILED');
      assert.equal(error.apiStatus, 'UNAUTHENTICATED');
      assert.equal(error.httpStatus, 401);
      return true;
    },
  );
});
