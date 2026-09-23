import test from 'node:test';
import assert from 'node:assert/strict';

// 模拟 Flow 页面：MAIN world 的 executeScript 直接在本进程执行，XHR 为最小替身。
function installPage({ promptText = '' } = {}) {
  class FakeXhr {
    constructor() { this.listeners = {}; }
    open(method, url) { this.url = url; }
    send() { FakeXhr.sent.push(this); }
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

function sendPageRequest(FakeXhr, rpcId) {
  const xhr = new XMLHttpRequest();
  xhr.open('POST', `/_/AiSandboxAngularFrontend/data/batchexecute?rpcids=${rpcId}&rt=c`);
  xhr.send('f.req=...');
  return xhr;
}

test('claims only the page ogiZ0b request issued after arming and surfaces its RPC rejection', async () => {
  const FakeXhr = installPage();
  const api = await import(`../flow-api.js?monitor=${Date.now()}`);
  const token = await api.armModernGenerateMonitor(1);

  const early = await api.waitModernGenerateSent(1, token, 20);
  assert.deepEqual(early, { sent: false, promptCleared: true });

  sendPageRequest(FakeXhr, 'nzlxg');                    // 聚焦 RPC 不能被认领
  const generate = sendPageRequest(FakeXhr, 'ogiZ0b');
  const sent = await api.waitModernGenerateSent(1, token, 20);
  assert.equal(sent.sent, true);

  const pending = api.resolveModernGenerateResponse(1, token, { projectId: 'p1', timeoutMs: 1000 });
  generate.respond(200, ")]}'\n\n192\n" + JSON.stringify([['wrb.fr', 'ogiZ0b', null, null, null,
    [7, null, [['type.googleapis.com/google.rpc.ErrorInfo', ['PUBLIC_ERROR_UNUSUAL_ACTIVITY']]]], 'generic']]));
  await assert.rejects(pending, (error) => {
    assert.equal(error.rpcStatus, 7);
    assert.match(error.message, /PUBLIC_ERROR_UNUSUAL_ACTIVITY/);
    return true;
  });
});

test('a stale armed token is discarded so the next click owns the next ogiZ0b', async () => {
  const FakeXhr = installPage({ promptText: 'still here' });
  const api = await import(`../flow-api.js?stale=${Date.now()}`);
  const stale = await api.armModernGenerateMonitor(1);
  const fresh = await api.armModernGenerateMonitor(1);

  const notSent = await api.waitModernGenerateSent(1, fresh, 20);
  assert.deepEqual(notSent, { sent: false, promptCleared: false });

  sendPageRequest(FakeXhr, 'ogiZ0b');
  assert.equal((await api.waitModernGenerateSent(1, fresh, 20)).sent, true);
  assert.equal((await api.waitModernGenerateSent(1, stale, 20)).lost, true);
});
