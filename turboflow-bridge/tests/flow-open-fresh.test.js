import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import { openFlowHome } from '../flow-api.js';

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
