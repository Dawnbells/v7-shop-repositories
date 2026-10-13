import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import { openFlowHome } from '../flow-api.js';
import { restartFlowProject } from '../flow-project-lifecycle.js';

function openingBrowser(create, createWindow = async () => {
  assert.fail('unexpected window creation');
}) {
  globalThis.chrome = {
    tabs: {
      create,
      get: async id => ({ id, url: 'https://flow.google.com/u/0/', status: 'complete' }),
      onUpdated: { addListener() {}, removeListener() {} },
    },
    windows: { create: createWindow },
  };
  return globalThis.chrome;
}

test('Open Flow keeps a valid requested window', async () => {
  const calls = [];
  openingBrowser(async options => { calls.push(options); return { id: 42 }; });
  assert.deepEqual(await openFlowHome({ windowId: 7 }), { tabId: 42 });
  assert.deepEqual(calls, [{ url: 'https://flow.google.com/', windowId: 7 }]);
});

test('Open Flow retries a closed window in the current window and preserves the account URL', async () => {
  const calls = [];
  const url = 'https://flow.google.com/u/2/';
  openingBrowser(async options => {
    calls.push(options);
    if (options.windowId === 1095711031) throw new Error('No window with id: 1095711031.');
    return { id: 42 };
  });
  assert.deepEqual(await openFlowHome({ url, windowId: 1095711031 }), { tabId: 42 });
  assert.deepEqual(calls, [{ url, windowId: 1095711031 }, { url }]);
});

for (const windowId of [undefined, 7]) {
  test(`Open Flow creates a window when none remain (target ${windowId})`, async () => {
    const calls = [];
    const url = 'https://flow.google.com/u/2/';
    openingBrowser(async options => {
      calls.push(options);
      throw new Error(options.windowId ? `No window with id: ${options.windowId}.` : 'No current window');
    }, async options => {
      assert.deepEqual(options, { url });
      return { tabs: [{ id: 42 }] };
    });
    assert.deepEqual(await openFlowHome({ url, windowId }), { tabId: 42 });
    assert.equal(calls.length, windowId === undefined ? 1 : 2);
  });
}

for (const closedTarget of [false, true]) {
  test(`Open Flow propagates unrelated creation failures (closed target ${closedTarget})`, async () => {
    let calls = 0;
    const failure = new Error('Tabs cannot be edited right now');
    openingBrowser(async options => {
      calls++;
      if (closedTarget && options.windowId) throw new Error('No window with id: 7.');
      throw failure;
    });
    await assert.rejects(openFlowHome({ windowId: 7 }), error => error === failure);
    assert.equal(calls, closedTarget ? 2 : 1);
  });
}

test('recovery completes when closing Flow tabs removes the target window, including the storage fallback', async () => {
  let liveTab = { id: 1, windowId: 7, url: 'https://flow.google.com/u/2/project/old', status: 'complete' };
  let nextId = 42;
  let windowsCreated = 0;
  const browser = openingBrowser(async ({ windowId }) => {
    throw new Error(windowId ? `No window with id: ${windowId}.` : 'No current window');
  }, async ({ url }) => {
    windowsCreated++;
    liveTab = { id: nextId++, windowId: windowsCreated + 7, url, status: 'complete' };
    return { tabs: [liveTab] };
  });
  browser.tabs.query = async () => liveTab ? [liveTab] : [];
  browser.tabs.get = async id => {
    assert.equal(id, liveTab?.id);
    return liveTab;
  };
  browser.tabs.remove = async id => { assert.equal(id, liveTab.id); liveTab = null; };
  const createdProjects = [];
  const result = await restartFlowProject({ windowId: 7, homeUrl: liveTab.url }, async () => {}, browser, {
    openFlowHome,
    clearFlowStorage: async id => { if (id === 1) throw new Error('tab discarded'); },
    clearTokenCache() {}, clearProjectIdCache() {},
    waitForFlowHomeReady: async id => { assert.equal(id, liveTab.id); },
    ensureFlowProjectOpen: async id => { createdProjects.push(id); return 'new-project'; },
  });
  assert.deepEqual(result, { tabId: 43, projectId: 'new-project' });
  assert.deepEqual(createdProjects, [43]);
  assert.equal(windowsCreated, 2);
  assert.equal(liveTab.url, 'https://flow.google.com/u/2/');
});

test('Open Flow only opens the home page and leaves projects for manual management', async () => {
  const events = [];
  const listeners = new Set();
  globalThis.chrome = {
    tabs: {
      create: async ({ url }) => {
        events.push({ action: 'open', url });
        return { id: 42, url, status: 'complete' };
      },
      get: async () => ({ id: 42, url: 'https://flow.google.com/', status: 'complete' }),
      onUpdated: {
        addListener: listener => listeners.add(listener),
        removeListener: listener => listeners.delete(listener),
      },
    },
  };

  const result = await openFlowHome();
  assert.deepEqual(result, { tabId: 42 });
  assert.deepEqual(events, [{ action: 'open', url: 'https://flow.google.com/' }]);
  assert.equal(listeners.size, 0);
});

test('Open Flow tolerates a redirect to the account-scoped home instead of timing out', async () => {
  globalThis.chrome = {
    tabs: {
      create: async () => ({ id: 43, url: '', status: 'loading' }),
      get: async () => ({ id: 43, url: 'https://flow.google.com/u/0/', status: 'complete' }),
      onUpdated: { addListener() {}, removeListener() {} },
    },
  };
  assert.deepEqual(await openFlowHome(), { tabId: 43 });
});

test('concurrent Open Flow requests share one open and leave the bridge disconnected', async () => {
  const source = readFileSync(new URL('../background.js', import.meta.url), 'utf8');
  let complete;
  let opens = 0;
  let scheduled = 0;
  let releases = 0;
  const context = vm.createContext({
    openingFlowPromise: null, timerId: null, nextPollAt: 0, lastStatus: {},
    running: false, currentTasks: [], recoveryPromise: null, deletingProjects: false,
    roundRecovery: { busy: false }, flowTasks: { inUse: 0 }, activeOperations: new Set(),
    prefetchedTask: { conn: { projectId: 'old-project' } }, flowTabAvailable: false,
    broadcast() {}, addLog() {}, clearTimeout, setTimeout,
    scheduleLoop() { scheduled++; },
    releasePrefetchedTask() { releases++; context.prefetchedTask = null; },
    openFlowHome: () => { opens++; return new Promise(resolve => { complete = resolve; }); },
  });
  vm.runInContext(source.slice(source.indexOf('function openFlowForManualProject('),
    source.indexOf('chrome.sidePanel.setPanelBehavior(')), context);
  const first = context.openFlowForManualProject();
  const second = context.openFlowForManualProject();
  assert.equal(first, second);
  assert.equal(opens, 1);
  assert.equal(releases, 1);
  assert.equal(scheduled, 0);
  complete({ tabId: 42 });
  assert.equal((await first).ok, true);
  assert.equal(context.prefetchedTask, null);
  assert.equal(context.lastStatus.connected, false);
  assert.equal(context.lastStatus.projectId, null);
  assert.equal(context.openingFlowPromise, null);
  assert.equal(scheduled, 1);
});
