import test from 'node:test';
import assert from 'node:assert/strict';

// 模拟 Flow 页面：MAIN world 的 executeScript 直接在本进程执行，XHR 为最小替身。
function installPage({ promptText = '' } = {}) {
  class FakeXhr {
    constructor() { this.listeners = {}; }
    open(method, url) { this.url = url; }
    send(body) { this.body = body; FakeXhr.sent.push(this); }
    addEventListener(type, fn) { (this.listeners[type] ||= []).push(fn); }
    respond(status, text) {
      this.status = status;
      this.responseText = text;
      (this.listeners.loadend || []).forEach((fn) => fn());
    }
  }
  FakeXhr.sent = [];
  globalThis.XMLHttpRequest = FakeXhr;
  globalThis.window = { fetch: async () => ({ status: 200, clone: () => ({ text: async () => '' }) }) };
  globalThis.location = { href: 'https://flow.google.com/project/p1' };
  globalThis.document = {
    querySelector: () => ({ textContent: promptText }),
  };
  globalThis.chrome = {
    tabs: { get: async () => ({ url: 'https://flow.google.com/project/p1' }) },
    scripting: { executeScript: async ({ func, args = [] }) => [{ result: await func(...args) }] },
  };
  return FakeXhr;
}

// Flow 真实请求体是 f.req=<URL 编码的 JSON>&at=...，参考图 media id 出现在其中。
function sendPageRequest(rpcId, referenceMediaId = '') {
  const xhr = new XMLHttpRequest();
  xhr.open('POST', `/_/AiSandboxAngularFrontend/data/batchexecute?rpcids=${rpcId}&rt=c`);
  const inner = JSON.stringify([null, [[null, null, [[referenceMediaId, null, null, null, 1]]]]]);
  xhr.send(`f.req=${encodeURIComponent(JSON.stringify([[[rpcId, inner, null, 'generic']]]))}&at=xsrf`);
  return xhr;
}

const REJECTED = ")]}'\n\n192\n" + JSON.stringify([['wrb.fr', 'ogiZ0b', null, null, null,
  [7, null, [['type.googleapis.com/google.rpc.ErrorInfo', ['PUBLIC_ERROR_UNUSUAL_ACTIVITY']]]], 'generic']]);

test('claims only the page ogiZ0b request issued after arming and surfaces its RPC rejection', async () => {
  installPage();
  const api = await import(`../flow-api.js?monitor=${Date.now()}`);
  const token = await api.armModernGenerateMonitor(1, { referenceMediaIds: ['media-a'] });

  const early = await api.waitModernGenerateSent(1, token, 20);
  assert.deepEqual(early, { sent: false, promptCleared: true });

  sendPageRequest('nzlxg');                            // 额度查询 RPC 不能被认领
  const generate = sendPageRequest('ogiZ0b', 'media-a');
  assert.equal((await api.waitModernGenerateSent(1, token, 20)).sent, true);

  const pending = api.resolveModernGenerateResponse(1, token, { projectId: 'p1', timeoutMs: 1000 });
  generate.respond(200, REJECTED);
  await assert.rejects(pending, (error) => {
    assert.equal(error.rpcStatus, 7);
    assert.match(error.message, /PUBLIC_ERROR_UNUSUAL_ACTIVITY/);
    return true;
  });
});

test('a late request for a previous source image is never claimed by the next task', async () => {
  installPage({ promptText: 'still here' });
  const api = await import(`../flow-api.js?late=${Date.now()}`);
  const next = await api.armModernGenerateMonitor(1, { referenceMediaIds: ['media-b'] });

  // 上一张（media-a）的请求迟到发出：不能被 media-b 的 token 认领。
  sendPageRequest('ogiZ0b', 'media-a');
  assert.deepEqual(await api.waitModernGenerateSent(1, next, 20), { sent: false, promptCleared: false });

  sendPageRequest('ogiZ0b', 'media-b');
  assert.equal((await api.waitModernGenerateSent(1, next, 20)).sent, true);
});

test('each concurrent generation reads the response of its own request regardless of finish order', async () => {
  installPage();
  const api = await import(`../flow-api.js?order=${Date.now()}`);
  const first = await api.armModernGenerateMonitor(1, { referenceMediaIds: ['media-1'] });
  const firstXhr = sendPageRequest('ogiZ0b', 'media-1');
  assert.equal((await api.waitModernGenerateSent(1, first, 20)).sent, true);
  const second = await api.armModernGenerateMonitor(1, { referenceMediaIds: ['media-2'] });
  const secondXhr = sendPageRequest('ogiZ0b', 'media-2');
  assert.equal((await api.waitModernGenerateSent(1, second, 20)).sent, true);

  const firstResult = api.resolveModernGenerateResponse(1, first, { projectId: 'p1', timeoutMs: 1000 });
  const secondResult = api.resolveModernGenerateResponse(1, second, { projectId: 'p1', timeoutMs: 1000 });
  secondXhr.respond(429, '');                          // 后提交的先结束
  firstXhr.respond(401, '');
  await assert.rejects(secondResult, (error) => error.httpStatus === 429);
  await assert.rejects(firstResult, (error) => error.httpStatus === 401);
});

test('a stale armed token is discarded so the next click owns the next ogiZ0b', async () => {
  installPage({ promptText: 'still here' });
  const api = await import(`../flow-api.js?stale=${Date.now()}`);
  const stale = await api.armModernGenerateMonitor(1);
  const fresh = await api.armModernGenerateMonitor(1);

  sendPageRequest('ogiZ0b');
  assert.equal((await api.waitModernGenerateSent(1, fresh, 20)).sent, true);
  assert.equal((await api.waitModernGenerateSent(1, stale, 20)).lost, true);
});
