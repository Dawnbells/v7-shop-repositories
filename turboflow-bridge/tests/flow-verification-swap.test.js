import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFileSync } from 'node:fs';
import { createSwapVerificationSession, requestSwapVerificationToken } from '../flow-verification-swap.js';

const tick = () => new Promise(resolve => setImmediate(resolve));
const libraryUrl = 'chrome-extension://bridge/rc-lib.js';

function pageHarness({ execute, loadError = false, locked = false, load = true } = {}) {
  const actions = [];
  const scripts = [];
  const removed = [];
  const timers = new Map();
  let tokenCount = 0;
  let timerId = 0;
  const oldExecute = () => assert.fail('must replace the page verification implementation');
  const window = { grecaptcha: { enterprise: { execute: oldExecute } } };
  if (locked) Object.defineProperty(window, 'grecaptcha', { configurable: false });
  const context = vm.createContext({ window,
    setTimeout(fn, ms) { const id = ++timerId; timers.set(id, { fn, ms }); return id; },
    clearTimeout(id) { timers.delete(id); },
    document: {
      querySelectorAll: () => [{ remove: () => removed.push('old') }],
      querySelector: () => ({ nonce: 'page-nonce' }),
      createElement: () => ({ setAttribute(name, value) { this[name] = value; }, remove() { removed.push(this); } }),
      head: { appendChild(script) {
        scripts.push(script);
        if (loadError) { script.onerror(); return; }
        if (!load) return;
        window.grecaptcha.enterprise = {
          ready: callback => callback(),
          execute: (key, options) => {
            actions.push({ key, ...options });
            return execute ? execute() : Promise.resolve('fresh-' + ++tokenCount);
          },
        };
      } },
    },
  });
  const request = () => vm.runInContext('(' + requestSwapVerificationToken.toString() + ')', context)('site-key', libraryUrl);
  return { context, window, scripts, actions, timers, removed, request };
}

test('238 replaces the page library, initializes enterprise config and reuses it with fresh tokens', async () => {
  const h = pageHarness();
  assert.equal((await h.request()).token, 'fresh-1');
  assert.equal((await h.request()).token, 'fresh-2');
  assert.equal(h.scripts.length, 1);
  assert.equal(h.scripts[0].src, libraryUrl);
  assert.equal(h.scripts[0].nonce, 'page-nonce');
  assert.equal(h.scripts[0]['data-bridge-238-rc'], '1');
  assert.deepEqual(h.actions, Array(2).fill({ key: 'site-key', action: 'IMAGE_GENERATION' }));
  const cfg = JSON.parse(JSON.stringify(h.window.___grecaptcha_cfg));
  assert.deepEqual(cfg, { enterprise: [true], render: ['site-key'], 'anchor-ms': [20000], 'execute-ms': [30000] });
  assert.equal(h.timers.size, 0);
});

test('238 detects page library replacement and bootstraps again', async () => {
  const h = pageHarness();
  await h.request();
  h.window.grecaptcha.enterprise.execute = () => assert.fail('stale library');
  assert.equal((await h.request()).token, 'fresh-2');
  assert.equal(h.scripts.length, 2);
});

test('238 reports a locked page and library failure without accepting the old token', async () => {
  for (const options of [{ locked: true }, { loadError: true }]) {
    const h = pageHarness(options);
    const result = await h.request();
    assert.match(result.error, /refresh|failed to load|Cannot delete/);
    assert.equal(h.actions.length, 0);
    assert.equal(h.timers.size, 0);
  }
});

test('238 times out a missing library and permits another bootstrap attempt', async () => {
  const h = pageHarness({ load: false });
  const pending = h.request();
  [...h.timers.values()].find(timer => timer.ms === 12000).fn();
  assert.match((await pending).error, /library timed out/);
  assert.equal(h.window.__bridge238Verification.loading, null);
  assert.equal(h.timers.size, 0);
  assert.ok(h.removed.includes(h.scripts[0]));
});

test('238 bounds a stalled execute and propagates execute rejection', async () => {
  const stalled = pageHarness({ execute: () => new Promise(() => {}) });
  const pending = stalled.request();
  await tick();
  [...stalled.timers.values()].find(timer => timer.ms === 30000).fn();
  assert.match((await pending).error, /timed out/);
  const failed = pageHarness({ execute: async () => { throw new Error('verification rejected'); } });
  assert.equal((await failed.request()).error, 'verification rejected');
});

function sessionHarness() {
  const pending = [];
  const calls = [];
  const removed = [];
  const stored = { flowApi2351VerificationTab: { tabId: 10, bootstrapVersion: 2 } };
  const tabs = new Map([
    [7, { url: 'https://flow.google.com/u/2/project/test' }],
    [8, { url: 'https://flow.google.com/project/other' }],
    [9, { url: 'https://flow.google.com/about' }],
    [10, { url: 'https://flow.google.com/about' }],
  ]);
  const browser = {
    runtime: { getURL: file => 'chrome-extension://bridge/' + file },
    storage: { session: {
      get: async key => ({ [key]: stored[key] }), remove: async key => { delete stored[key]; },
    } },
    tabs: {
      get: async id => { if (!tabs.has(id)) throw new Error('tab closed'); return { id, ...tabs.get(id) }; },
      remove: async id => { removed.push(id); tabs.delete(id); },
      create: () => assert.fail('238 must not create a helper tab'),
    },
    scripting: { executeScript: options => {
      calls.push(options);
      return new Promise(resolve => pending.push(resolve));
    } },
  };
  return { browser, pending, calls, removed, stored, tabs, session: createSwapVerificationSession(browser) };
}

test('238 serializes verification across project tabs and cleans only the recorded old helper', async () => {
  const h = sessionHarness();
  const first = h.session.getToken(7);
  const second = h.session.getToken(8);
  await tick();
  assert.equal(h.calls.length, 1);
  assert.equal(h.calls[0].target.tabId, 7);
  assert.equal(h.calls[0].world, 'MAIN');
  assert.equal(h.calls[0].args[1], libraryUrl);
  h.pending[0]([{ result: { token: 'first' } }]);
  await tick();
  assert.equal(h.calls[1].target.tabId, 8);
  h.pending[1]([{ result: { token: 'second' } }]);
  assert.deepEqual(await Promise.all([first, second]), ['first', 'second']);
  await h.session.release();
  assert.deepEqual(h.removed, [10]);
  assert.deepEqual([...h.tabs.keys()], [7, 8, 9]);
});

test('238 verification failures keep the queue usable; invalid targets never inject', async () => {
  const h = sessionHarness();
  const first = h.session.getToken(7);
  const rejected = assert.rejects(first, { code: 'FLOW_VERIFICATION_REQUIRED', message: 'failed' });
  const second = h.session.getToken(8);
  await tick();
  h.pending[0]([{ result: { error: 'failed' } }]);
  await rejected;
  await tick();
  h.pending[1]([{ result: { token: 'second' } }]);
  assert.equal(await second, 'second');
  for (const id of [9, 404]) await assert.rejects(h.session.getToken(id), { code: 'FLOW_VERIFICATION_REQUIRED' });
  h.tabs.set(7, { url: 'https://example.com/project/test' });
  await assert.rejects(h.session.getToken(7), { code: 'FLOW_VERIFICATION_REQUIRED' });
  assert.equal(h.calls.length, 2);
});

test('238 verification accepts the existing hash project route', async () => {
  const h = sessionHarness();
  h.tabs.set(7, { url: 'https://flow.google.com/#/project/test' });
  const pending = h.session.getToken(7);
  await tick();
  assert.equal(h.calls.length, 1);
  h.pending[0]([{ result: { token: 'hash-project-token' } }]);
  assert.equal(await pending, 'hash-project-token');
});

test('238 Trusted Types policy accepts only its packaged script and reuses the page nonce', async () => {
  const h = pageHarness();
  let policy;
  h.window.trustedTypes = { createPolicy(name, value) {
    assert.equal(name, 'default');
    policy = value;
    return value;
  } };
  assert.equal((await h.request()).token, 'fresh-1');
  assert.equal(policy.createScriptURL(libraryUrl), libraryUrl);
  for (const url of ['https://example.com/script.js', 'chrome-extension://other/script.js']) {
    assert.throws(() => policy.createScriptURL(url), /Unexpected verification script/);
  }
  assert.equal(h.scripts[0].nonce, 'page-nonce');
});

test('document-start bootstrap unlocks verification properties while preserving unrelated descriptors', () => {
  const bootstrap = readFileSync(new URL('../flow-verification-bootstrap.js', import.meta.url), 'utf8');
  const context = vm.createContext({ location: { hostname: 'flow.google.com' } });
  vm.runInContext('window = globalThis', context);
  vm.runInContext(bootstrap, context);
  const result = vm.runInContext(`(() => {
    Object.defineProperty(window, 'grecaptcha', { value: {} });
    Object.defineProperties(grecaptcha, { enterprise: { value: { execute() {} } } });
    Object.defineProperty(grecaptcha.enterprise, 'execute', { value() {} });
    const plain = {};
    Object.defineProperty(plain, 'unchanged', { value: 1 });
    return [Object.getOwnPropertyDescriptor(window, 'grecaptcha').configurable,
      Object.getOwnPropertyDescriptor(grecaptcha, 'enterprise').writable,
      Object.getOwnPropertyDescriptor(grecaptcha.enterprise, 'execute').configurable,
      Object.getOwnPropertyDescriptor(plain, 'unchanged').configurable];
  })()`, context);
  assert.deepEqual(Array.from(result), [true, true, true, false]);
});
