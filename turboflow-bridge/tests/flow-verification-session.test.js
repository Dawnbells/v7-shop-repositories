import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { createVerificationSession, requestVerificationToken } from '../flow-verification-session.js';
import { isFlowUrl } from '../flow-sites.js';

const tick = () => new Promise(resolve => setImmediate(resolve));
function event() {
  const listeners = new Set();
  return { addListener: fn => listeners.add(fn), removeListener: fn => listeners.delete(fn),
    emit: (...args) => { for (const listener of [...listeners]) listener(...args); } };
}

function harness(execute = async () => 'token') {
  const stored = {};
  const tabs = new Map([[1, { id: 1, url: 'https://flow.google.com/about', status: 'complete' }]]);
  const created = [];
  const removed = [];
  const actions = [];
  let id = 10;
  const onUpdated = event();
  const onRemoved = event();
  const page = vm.createContext({ setTimeout, clearTimeout,
    document: {},
    window: { grecaptcha: { enterprise: {
      ready: fn => fn(), execute: (key, options) => { actions.push({ key, ...options }); return execute(); },
    } } },
  });
  const browser = {
    storage: { session: { get: async key => ({ [key]: stored[key] }),
      set: async value => Object.assign(stored, value), remove: async key => { delete stored[key]; } } },
    tabs: {
      onUpdated, onRemoved,
      get: async id => { if (!tabs.has(id)) throw new Error('closed'); return tabs.get(id); },
      create: async options => { created.push(options); const tab = { id: id++, status: 'complete', ...options }; tabs.set(tab.id, tab); return tab; },
      update: async (id, options) => Object.assign(tabs.get(id), options),
      remove: async id => { removed.push(id); tabs.delete(id); onRemoved.emit(id); },
    },
    scripting: { executeScript: async ({ func, args }) => [{ result: await vm.runInContext('(' + func.toString() + ')', page)(...args) }] },
  };
  return { browser, stored, tabs, created, removed, actions, page };
}

test('concurrent verification creates one inactive helper and serializes fresh IMAGE_GENERATION tokens', async () => {
  const pending = [];
  const h = harness(() => new Promise(resolve => pending.push(resolve)));
  const session = createVerificationSession(h.browser);
  const requests = [session.getToken(), session.getToken(), session.getToken()];
  await tick();
  assert.equal(h.created.length, 1);
  assert.equal(h.created[0].active, false);
  assert.equal(h.tabs.get(10).autoDiscardable, false);
  assert.equal(pending.length, 1);
  pending[0]('first');
  await tick();
  assert.equal(pending.length, 2);
  pending[1]('second');
  await tick();
  pending[2]('third');
  assert.deepEqual(await Promise.all(requests), ['first', 'second', 'third']);
  assert.equal(h.created.length, 1);
  assert.deepEqual(h.actions.map(action => action.action), ['IMAGE_GENERATION', 'IMAGE_GENERATION', 'IMAGE_GENERATION']);
  await session.release();
  assert.deepEqual(h.removed, [10]);
  assert.ok(h.tabs.has(1), 'the user about tab remains open');
});

test('helper identity survives background restart and a closed helper is recreated', async () => {
  const h = harness();
  await createVerificationSession(h.browser).getToken();
  const restarted = createVerificationSession(h.browser);
  await restarted.getToken();
  assert.equal(h.created.length, 1);
  await h.browser.tabs.remove(10);
  await restarted.getToken();
  assert.equal(h.created.length, 2);
  assert.equal(h.stored.flowApi2351VerificationTab.tabId, 11);
  await restarted.release();
});

test('stop or mode switch waits for the entire active translation, including download', async () => {
  const h = harness();
  const session = createVerificationSession(h.browser);
  let finish;
  const translation = session.withLease(async () => {
    await session.getToken();
    await new Promise(resolve => { finish = resolve; });
    return 'download-complete';
  });
  await tick();
  await session.release();
  assert.deepEqual(h.removed, []);
  finish();
  assert.equal(await translation, 'download-complete');
  assert.deepEqual(h.removed, [10]);
});

test('release waits for queued token requests and does not close a helper mid-execute', async () => {
  const pending = [];
  const h = harness(() => new Promise(resolve => pending.push(resolve)));
  const session = createVerificationSession(h.browser);
  const requests = [session.getToken(), session.getToken()];
  await tick();
  await session.release();
  assert.equal(h.removed.length, 0);
  pending[0]('first');
  await tick();
  assert.equal(h.removed.length, 0);
  pending[1]('second');
  await Promise.all(requests);
  assert.deepEqual(h.removed, [10]);
});

test('navigating an owned helper to a project relinquishes ownership without closing it', async () => {
  const h = harness();
  const session = createVerificationSession(h.browser);
  await session.getToken();
  h.tabs.get(10).url = 'https://flow.google.com/project/user-project';
  await session.release();
  assert.deepEqual(h.removed, []);
  assert.ok(h.tabs.has(10));
  await session.getToken();
  assert.equal(h.created.length, 2);
  await session.release();
  assert.deepEqual(h.removed, [11]);
});

test('failed verification is structured and does not poison the token queue', async () => {
  let attempts = 0;
  const h = harness(async () => { if (!attempts++) throw new Error('verification failed'); return 'next'; });
  const session = createVerificationSession(h.browser);
  await assert.rejects(session.getToken(), { code: 'FLOW_VERIFICATION_REQUIRED' });
  assert.equal(await session.getToken(), 'next');
  await session.release();
});

test('helper about URLs are excluded while working project and home URLs remain discoverable', () => {
  assert.equal(isFlowUrl('https://flow.google.com/about'), false);
  assert.equal(isFlowUrl('https://flow.google.com/u/2/about/'), false);
  assert.equal(isFlowUrl('https://flow.google.com/project/abc'), true);
  assert.equal(isFlowUrl('https://flow.google.com/'), true);
  assert.equal(isFlowUrl('https://labs.google/fx/tools/flow/project/abc'), true);
});

test('helper loads the verification script with the page nonce when an existing default policy owns Trusted Types', async () => {
  const h = harness();
  const rc = h.page.window.grecaptcha;
  delete h.page.window.grecaptcha;
  h.page.window.trustedTypes = { createPolicy() { throw new Error('default policy already exists'); } };
  let appended;
  h.page.document = {
    querySelector: selector => selector === 'script[nonce]' ? { nonce: 'page-nonce' } : null,
    createElement: () => ({ setAttribute(name, value) { this[name] = value; } }),
    head: { appendChild(script) { appended = script; h.page.window.grecaptcha = rc; } },
  };
  const session = createVerificationSession(h.browser);
  assert.equal(await session.getToken(), 'token');
  assert.equal(appended.nonce, 'page-nonce');
  assert.match(appended.src, /^https:\/\/www\.google\.com\/recaptcha\/enterprise\.js\?render=/);
  assert.equal(appended['data-tf-2351-rc'], '1');
  await session.release();
});

test('verification bootstrap permits the secondary gstatic bundle while rejecting unrelated scripts', async () => {
  const h = harness();
  const readyRc = h.page.window.grecaptcha;
  delete h.page.window.grecaptcha;
  let policy;
  const loaded = [];
  h.page.window.trustedTypes = { createPolicy(_name, rules) { policy = rules; return rules; } };
  h.page.document = {
    querySelector: () => null,
    createElement: () => ({ setAttribute() {}, remove() {} }),
    head: { appendChild(script) {
      loaded.push(policy.createScriptURL(script.src));
      // Match enterprise.js: a ready stub exists before its secondary bundle.
      h.page.window.grecaptcha = { enterprise: { execute() {}, ready() {} } };
      loaded.push(policy.createScriptURL('https://www.gstatic.com/recaptcha/releases/test/recaptcha__en.js'));
      h.page.window.grecaptcha = readyRc;
    } },
  };
  const session = createVerificationSession(h.browser);
  assert.equal(await session.getToken(), 'token');
  assert.equal(loaded.length, 2);
  assert.throws(() => policy.createScriptURL('https://attacker.example/recaptcha/evil.js'));
  assert.throws(() => policy.createScriptURL('https://www.gstatic.com/unrelated.js'));
  assert.throws(() => policy.createScriptURL('http://www.gstatic.com/recaptcha/release.js'));
  await session.release();
});

test('pre-fix helper is replaced on upgrade because the old default policy cannot be overwritten', async () => {
  const h = harness();
  h.tabs.set(9, { id: 9, url: 'https://flow.google.com/about', status: 'complete' });
  h.stored.flowApi2351VerificationTab = 9; // 1.5.7 session storage format
  const session = createVerificationSession(h.browser);
  assert.equal(await session.getToken(), 'token');
  assert.deepEqual(h.removed, [9]);
  assert.equal(h.created.length, 1);
  assert.equal(h.stored.flowApi2351VerificationTab.bootstrapVersion, 2);
  assert.ok(h.tabs.has(1), 'user about tab is retained');
  await session.release();
});

function tokenHarness(enterprise) {
  const timers = new Map();
  const listeners = new Map();
  let timerId = 0;
  const addEventListener = (name, callback) => listeners.set(name, callback);
  const removeEventListener = name => listeners.delete(name);
  const context = vm.createContext({
    setTimeout: (callback, ms) => { const id = ++timerId; timers.set(id, { callback, ms }); return id; },
    clearTimeout: id => timers.delete(id),
    window: { grecaptcha: { enterprise }, addEventListener, removeEventListener },
    document: { addEventListener, removeEventListener, querySelector: () => ({}) },
  });
  const run = () => vm.runInContext('(' + requestVerificationToken.toString() + ')', context)('site-key');
  const expire = () => [...timers.values()].find(timer => timer.ms === 30000).callback();
  return { context, run, expire, timers, listeners };
}

for (const stage of ['script-load', 'ready', 'execute']) {
  test(`verification timeout identifies ${stage} and clears timers/listeners`, async () => {
    const enterprise = stage === 'script-load' ? undefined
      : { execute: () => new Promise(() => {}), ready: callback => { if (stage === 'execute') callback(); } };
    const h = tokenHarness(enterprise);
    const pending = h.run();
    h.expire();
    const result = await pending;
    assert.equal(result.error, `Flow verification timed out (stage=${stage})`);
    assert.equal(h.timers.size, 0);
    assert.equal(h.listeners.size, 0);
  });
}

test('failed secondary script reports immediately instead of waiting for timeout', async () => {
  const h = tokenHarness({ execute() {}, ready() {} });
  const pending = h.run();
  h.listeners.get('error')({ target: { src: 'https://www.gstatic.com/recaptcha/releases/test/recaptcha__en.js' } });
  const result = await pending;
  assert.match(result.error, /resource could not load.*stage=ready/);
  assert.equal(h.timers.size, 0);
  assert.equal(h.listeners.size, 0);
});

test('page CSP rejection reports its directive and does not execute verification', async () => {
  let executions = 0;
  const h = tokenHarness({ execute() { executions++; }, ready() {} });
  const pending = h.run();
  h.listeners.get('securitypolicyviolation')({ blockedURI: 'https://www.gstatic.com/recaptcha/releases/test/recaptcha__en.js', effectiveDirective: 'script-src-elem' });
  const result = await pending;
  assert.match(result.error, /blocked by page policy: script-src-elem.*stage=ready/);
  assert.equal(executions, 0);
  assert.equal(h.timers.size, 0);
});
