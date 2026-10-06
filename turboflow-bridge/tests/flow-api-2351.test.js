import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { createApi2351 } from '../flow-api-2.3.5.1.js';
import { translateImageViaApi } from '../flow-image-translation.js';
import { classifyErrorCode } from '../task-error-policy.js';

const tick = () => new Promise(resolve => setImmediate(resolve));
const response = data => ({ ok: true, status: 200, text: async () => JSON.stringify(data), json: async () => data });
const rpcResponse = (rpc, data) => response([['wrb.fr', rpc, JSON.stringify(data)]]);
const media = (id, url) => {
  const value = [id, 'project', 'workflow-' + id];
  value[6] = [[]];
  value[6][0][13] = url;
  return value;
};

function harness({ modern = true, respond, verification, legacyVerification, now, download } = {}) {
  const calls = [];
  let tokenCount = 0;
  const pageUrl = modern ? 'https://flow.google.com/u/2/project/test' : 'https://labs.google/fx/tools/flow/project/test';
  const tab = { id: 7, url: pageUrl, status: 'complete' };
  const listeners = [];
  const page = vm.createContext({
    URL, URLSearchParams, AbortSignal, crypto, Date, Math,
    window: { location: new URL(pageUrl), WIZ_global_data: { SNlM0e: 'xsrf', cfb2h: 'build', FdrFJe: 'session' } },
    fetch: async (url, options) => {
      const rpc = url.startsWith('https://flow.google.com') ? new URL(url).searchParams.get('rpcids') : null;
      const form = rpc ? new URLSearchParams(options.body) : null;
      const payload = rpc ? JSON.parse(JSON.parse(form.get('f.req'))[0][0][1]) : options.body ? JSON.parse(options.body) : null;
      const call = { url, options, rpc, form, payload };
      calls.push(call);
      if (respond) return respond(call, calls);
      if (rpc === 'maseQ') return rpcResponse(rpc, [['source']]);
      if (rpc === 'ogiZ0b') return rpcResponse(rpc, [[media('result', 'https://flow-content.google/result')]]);
      if (rpc === 'uurnC') return rpcResponse(rpc, media(payload[0], 'https://flow-content.google/' + payload[0]));
      if (url === '/fx/api/auth/session') return response({ access_token: 'session-token' });
      if (url.endsWith('/flow/uploadImage')) return response({ media: { name: 'source' } });
      return response({ workflows: [{ name: 'workflow', metadata: { primaryMediaId: 'result' } }],
        media: [{ name: 'result', image: { generatedImage: { fifeUrl: 'https://flow-content.google/result' } } }] });
    },
  });
  const browser = {
    tabs: { get: async () => tab, onUpdated: { addListener: fn => listeners.push(fn) } },
    scripting: { executeScript: async ({ func, args = [] }) => {
      // Compile in the page context, rather than calling the extension closure.
      const callable = vm.runInContext('(' + func.toString() + ')', page);
      return [{ result: await callable(...args) }];
    } },
  };
  const api = createApi2351({ browser, now, download: download || (async (_tab, url) => ({ dataUrl: 'download:' + url })),
    verification: verification || { getToken: async () => 'fresh-' + ++tokenCount, withLease: run => run(), release: async () => {} },
    legacyVerification: legacyVerification || (async (_tab, action) => { assert.equal(action, 'IMAGE_GENERATION'); return 'legacy-fresh'; }) });
  return { api, calls, page, tab, listeners,
    conn: { connected: true, tabId: 7, projectId: 'test', flowUrl: pageUrl, transport: modern ? 'boq' : 'legacy-rest' } };
}

const task = { imageBase64: 'data:image/jpeg;base64,YWJj', fileName: 'source.jpg', prompt: 'Translate',
  model: 'GEM_PIX_2', aspectRatio: 'IMAGE_ASPECT_RATIO_SQUARE' };

test('2351 modern translation uses upload/generate only, account path, fresh tokens and uppercase seeds', async () => {
  const { api, calls, conn } = harness();
  const result = await translateImageViaApi(conn, task, api);
  assert.deepEqual(calls.map(call => call.rpc), ['maseQ', 'ogiZ0b']);
  assert.equal(result.mediaId, 'result');
  assert.equal(result.workflowId, 'workflow-result');
  assert.equal(result.resultDataUrl, 'download:https://flow-content.google/result');
  for (const call of calls) {
    const url = new URL(call.url);
    assert.equal(url.pathname, '/u/2/_/AiSandboxAngularFrontend/data/batchexecute');
    assert.equal(url.searchParams.get('bl'), 'build');
    assert.equal(url.searchParams.get('f.sid'), 'session');
    assert.equal(call.form.get('at'), 'xsrf');
    assert.equal(call.options.credentials, 'include');
    assert.equal(call.options.headers['X-Same-Domain'], '1');
    assert.equal(call.options.headers.Authorization, undefined);
  }
  const upload = calls[0].payload;
  assert.deepEqual(upload.slice(1, 4), ['YWJj', 'image/jpeg', 1]);
  assert.equal(upload[8], 'source.jpg');
  assert.deepEqual(upload[0][10], ['fresh-1', 1]);
  assert.match(upload[10], /^[A-F0-9-]{36}$/);
  assert.match(upload[11], /^[A-F0-9-]{36}$/);
  const generate = calls[1].payload;
  assert.deepEqual(generate[3][10], ['fresh-2', 1]);
  assert.equal(generate[1][0][4], 1);
  assert.equal(generate[1][0][5], 'GEM_PIX_2');
  assert.deepEqual(generate[1][0][2], [['source', null, null, null, 1]]);
  assert.deepEqual(generate[1][0][8], [[['Translate']]]);
  for (const id of [generate[1][0][12], generate[1][0][13], generate[4][0]]) assert.match(id, /^[A-F0-9-]{36}$/);
});

test('2351 media lookup sends the exact generation id, without a latest-project fallback', async () => {
  const { api, calls } = harness();
  assert.equal(await api.resolveFlowImageUrl(7, { mediaId: 'raw-id' }), 'https://flow-content.google/raw-id');
  assert.deepEqual(calls.map(call => [call.rpc, call.payload]), [['uurnC', ['raw-id']]]);
});

test('2351 legacy translates with REST context and verifies only generation', async () => {
  const { api, calls, conn } = harness({ modern: false });
  const result = await translateImageViaApi(conn, task, api);
  assert.equal(result.referenceMediaId, 'source');
  assert.equal(result.mediaId, 'result');
  assert.equal(calls.length, 3);
  assert.equal(calls[0].url, '/fx/api/auth/session');
  assert.equal(calls[1].url, 'https://aisandbox-pa.googleapis.com/v1/flow/uploadImage');
  assert.equal(calls[1].payload.clientContext.recaptchaContext, undefined);
  const generation = calls[2];
  assert.equal(generation.url, 'https://aisandbox-pa.googleapis.com/v1/projects/test/flowMedia:batchGenerateImages');
  assert.equal(generation.options.headers.Authorization, 'Bearer session-token');
  assert.equal(generation.options.headers['Content-Type'], 'text/plain;charset=UTF-8');
  assert.equal(generation.payload.clientContext.recaptchaContext.token, 'legacy-fresh');
  assert.deepEqual(generation.payload.requests[0].clientContext, generation.payload.clientContext);
  assert.equal(generation.payload.requests[0].imageInputs[0].name, 'source');
  assert.equal(await api.resolveFlowImageUrl(7, { mediaId: 'id' }, conn.flowUrl),
    'https://labs.google/fx/api/trpc/media.getMediaUrlRedirect?name=id');
});

test('legacy session expires in five minutes and invalidates on URL changes and reloads', async () => {
  let clock = 0;
  const { api, calls, tab, listeners } = harness({ modern: false, now: () => clock });
  await api.getSessionToken(7);
  clock = 299999;
  await api.getSessionToken(7);
  assert.equal(calls.length, 1);
  clock = 300000;
  await api.getSessionToken(7);
  assert.equal(calls.length, 2);
  tab.url += '?authuser=1';
  await api.getSessionToken(7);
  assert.equal(calls.length, 3);
  listeners[0](7, { status: 'loading' });
  await api.getSessionToken(7);
  assert.equal(calls.length, 4);
});

for (const modern of [true, false]) {
  test(`2351 ${modern ? 'RPC' : 'REST'} signals submission before response and preserves interleaved results`, async () => {
    const pending = [];
    const submitted = [];
    const { api, page } = harness({ modern, respond: call => {
      if (call.url === '/fx/api/auth/session') return response({ access_token: 'token' });
      return new Promise(resolve => pending.push({ call, resolve }));
    } });
    const requests = [0, 1, 2].map(i => api.generateWithReference(7, { ...task, pid: 'test', referenceMediaId: 'source-' + i,
      onSubmitted: info => submitted.push({ i, ...info }) }));
    await tick();
    assert.equal(submitted.length, 3);
    assert.equal(pending.length, 3);
    assert.equal(page.window.__turboFlow2351Requests.size, 3);
    for (const i of [2, 0, 1]) {
      const { call, resolve } = pending[i];
      resolve(modern ? rpcResponse(call.rpc, [[media('result-' + i, 'https://flow-content.google/' + i)]])
        : response({ workflows: [{ metadata: { primaryMediaId: 'result-' + i } }], media: [{ name: 'result-' + i }] }));
    }
    const results = await Promise.all(requests);
    assert.deepEqual(results.map(result => result.mediaId), ['result-0', 'result-1', 'result-2']);
    assert.equal(submitted.length, 3);
    assert.equal(page.window.__turboFlow2351Requests.size, 0);
  });
}

for (const operation of ['uploadImageToFlow', 'generateWithReference']) {
  test(`pause during verification prevents ${operation} submission`, async () => {
    let paused = false;
    const { api, calls } = harness({ verification: { getToken: async () => { paused = true; return 'fresh'; } } });
    await assert.rejects(api[operation](7, { pid: 'test', ...task, beforeSubmit() {
      if (paused) throw Object.assign(new Error('paused'), { code: 'FLOW_SUBMISSION_PAUSED' });
    } }), { code: 'FLOW_SUBMISSION_PAUSED' });
    assert.equal(calls.length, 0);
  });
}

for (const [status, expected] of [[8, 'FLOW_RESOURCE_EXHAUSTED'], [16, 'FLOW_AUTHENTICATION_FAILED'], [3, 'FLOW_RPC_REJECTED'], [7, 'RECAPTCHA_BLOCKED']]) {
  test(`2351 RPC ${status} preserves current error policy without retrying`, async () => {
    const { api, calls, conn } = harness({ respond: call => response([['wrb.fr', call.rpc, null, null, null,
      [status, null, [['type.googleapis.com/google.rpc.ErrorInfo', [status === 7 ? 'PUBLIC_ERROR_UNUSUAL_ACTIVITY' : 'REJECTED']]]]]]) });
    await assert.rejects(translateImageViaApi(conn, task, api), error => classifyErrorCode(error) === expected);
    assert.equal(calls.length, 1);
  });
}

test('2351 missing BOQ session rejects before network dispatch', async () => {
  const { api, calls, page } = harness();
  delete page.window.WIZ_global_data.cfb2h;
  await assert.rejects(api.generateWithReference(7, { pid: 'test', ...task }), { code: 'FLOW_AUTHENTICATION_FAILED' });
  assert.equal(calls.length, 0);
});

test('2351 per-model daily quota is not flattened into generic resource exhaustion', async () => {
  const { api, calls } = harness({ respond: call => response([['wrb.fr', call.rpc, null, null, null,
    [8, null, [['type.googleapis.com/google.rpc.ErrorInfo', ['PUBLIC_ERROR_PER_MODEL_DAILY_QUOTA_REACHED']]]]]]) });
  await assert.rejects(api.generateWithReference(7, { pid: 'test', ...task, referenceMediaId: 'source' }), error => {
    assert.equal(error.code, 'DAILY_QUOTA_REACHED');
    assert.equal(classifyErrorCode(error), 'DAILY_QUOTA_REACHED');
    return true;
  });
  assert.equal(calls.length, 1);
});

test('legacy upload policy errors retain the original-image fallback contract', async () => {
  const { api, conn } = harness({ modern: false, respond: call => call.url === '/fx/api/auth/session'
    ? response({ access_token: 'token' }) : { ok: false, status: 400, text: async () => JSON.stringify({ error: {
      status: 'INVALID_ARGUMENT', details: [{ '@type': 'type.googleapis.com/google.rpc.ErrorInfo', reason: 'POLICY' }],
    } }) } });
  await assert.rejects(translateImageViaApi(conn, task, api), { code: 'FLOW_UPLOAD_POLICY_REJECTED', reason: 'POLICY' });
});

test('a stopped translation still downloads its already submitted result', async () => {
  let paused = false;
  const { api, conn, calls } = harness();
  const result = await translateImageViaApi(conn, { ...task,
    beforeSubmit() { if (paused) throw new Error('paused'); },
    onSubmitted() { paused = true; },
  }, api);
  assert.equal(result.resultDataUrl, 'download:https://flow-content.google/result');
  assert.equal(calls.length, 2);
});

for (const [status, message, expected] of [
  [401, 'UNAUTHENTICATED', 'FLOW_AUTHENTICATION_FAILED'],
  [403, 'reCAPTCHA verification failed', 'RECAPTCHA_BLOCKED'],
  [403, 'permission denied', 'GOOGLE_BLOCKED'],
  [429, 'resource exhausted', 'FLOW_RESOURCE_EXHAUSTED'],
  [429, 'PUBLIC_ERROR_PER_MODEL_DAILY_QUOTA_REACHED', 'DAILY_QUOTA_REACHED'],
]) {
  test(`legacy HTTP ${status} ${message} retains the bridge error classification`, async () => {
    const { api, calls } = harness({ modern: false, respond: call => call.url === '/fx/api/auth/session'
      ? response({ access_token: 'token' })
      : { ok: false, status, text: async () => JSON.stringify({ error: { message } }) } });
    await assert.rejects(api.generateWithReference(7, { ...task, pid: 'test', referenceMediaId: 'source' }),
      error => classifyErrorCode(error) === expected);
    assert.equal(calls.length, 2, 'generation is never resubmitted after rejection');
  });
}
