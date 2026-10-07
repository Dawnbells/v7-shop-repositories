import test from 'node:test';
import assert from 'node:assert/strict';
import { restartFlowProject } from '../flow-project-lifecycle.js';

const FLOW_TABS = [
  { id: 1, url: 'https://flow.google.com/u/0/project/old' },
  { id: 2, url: 'https://labs.google/fx/tools/flow' },
  { id: 3, url: 'https://example.com/' },
];

function harness({ homeReady = async () => ({ ready: true }), tabs = FLOW_TABS, clearStorage = async () => {} } = {}) {
  const events = [];
  const logs = [];
  let nextTabId = 42;
  const browser = { tabs: {
    query: async () => tabs,
    get: async id => ({ id }),
    remove: async id => { events.push(`close:${id}`); },
  } };
  const api = {
    openFlowHome: async ({ url }) => { events.push(`open:${url}`); return { tabId: nextTabId++ }; },
    waitForFlowHomeReady: async tabId => { events.push(`home-ready:${tabId}`); return homeReady(tabId); },
    ensureFlowProjectOpen: async tabId => { events.push(`create:${tabId}`); return 'new-project'; },
    clearFlowStorage: async tabId => { events.push(`clear-storage:${tabId}`); await clearStorage(tabId); },
    clearTokenCache: () => events.push('clear-token'),
    clearProjectIdCache: () => events.push('clear-project'),
  };
  const phase = async name => { events.push(`phase:${name}`); };
  const log = (level, message) => logs.push(`${level}:${message}`);
  const run = target => restartFlowProject(target, phase, browser, api, log);
  return { events, logs, browser, api, phase, log, run };
}

test('recovery clears storage in every Flow tab, closes them, waits for the home page, then creates a project', async () => {
  const h = harness();
  const result = await h.run({ homeUrl: 'https://flow.google.com/u/0/project/old', windowId: 7 });
  assert.deepEqual(result, { tabId: 42, projectId: 'new-project' });
  assert.deepEqual(h.events, [
    'phase:clearing', 'clear-storage:1', 'clear-storage:2',
    'phase:closing', 'close:1', 'close:2', 'clear-token', 'clear-project',
    'phase:opening', 'open:https://flow.google.com/u/0/', 'home-ready:42',
    'phase:creating', 'create:42',
  ]);
  assert.ok(h.logs.includes('info:cleared local/session storage in 2/2 Flow tab(s)'));
});

test('a Flow tab whose storage cannot be cleared is logged and does not abort the recovery', async () => {
  const h = harness({ clearStorage: async tabId => { if (tabId === 1) throw new Error('tab discarded'); } });
  const result = await h.run(null);
  assert.deepEqual(result, { tabId: 42, projectId: 'new-project' });
  assert.deepEqual(h.events.slice(0, 5),
    ['phase:clearing', 'clear-storage:1', 'clear-storage:2', 'phase:closing', 'close:1']);
  // One tab was cleared, so the fresh home tab is used directly without a second open.
  assert.equal(h.events.filter(event => event.startsWith('open:')).length, 1);
  assert.equal(h.events.includes('clear-storage:42'), false);
  assert.ok(h.logs.some(line => line.startsWith('warn:could not clear storage in Flow tab 1 ')));
  assert.ok(h.logs.includes('info:cleared local/session storage in 1/2 Flow tab(s)'));
});

test('without any Flow tab to clear, the fresh home tab is cleared and reopened before the project is created', async () => {
  const h = harness({ tabs: [{ id: 3, url: 'https://example.com/' }] });
  const result = await h.run({ homeUrl: 'https://flow.google.com/', windowId: 7 });
  assert.deepEqual(result, { tabId: 43, projectId: 'new-project' });
  assert.deepEqual(h.events, [
    'phase:clearing',
    'phase:closing', 'clear-token', 'clear-project',
    'phase:opening', 'open:https://flow.google.com/', 'home-ready:42',
    'phase:clearing', 'clear-storage:42',
    'phase:closing', 'close:42',
    'phase:opening', 'open:https://flow.google.com/', 'home-ready:43',
    'phase:creating', 'create:43',
  ]);
  assert.ok(h.logs.some(line => line.startsWith('warn:no Flow tab could be cleared before closing')));
});

test('when every Flow tab fails to clear, the fresh home tab is cleared and reopened', async () => {
  const h = harness({ clearStorage: async tabId => { if (tabId < 42) throw new Error('tab discarded'); } });
  const result = await h.run(null);
  assert.deepEqual(result, { tabId: 43, projectId: 'new-project' });
  assert.ok(h.events.includes('clear-storage:42'));
  assert.ok(h.events.includes('close:42'));
  assert.equal(h.events.filter(event => event.startsWith('open:')).length, 2);
  assert.equal(h.events.at(-1), 'create:43');
});

test('a home page that never becomes usable fails the recovery before any project is created', async () => {
  const h = harness({ homeReady: async () => {
    throw new Error('Flow home opened a non-Flow page (accounts.google.com); sign in to Google Flow, then click Run Now');
  } });
  await assert.rejects(h.run(null), /non-Flow page/);
  assert.equal(h.events.includes('phase:creating'), false);
  assert.equal(h.events.some(event => event.startsWith('create:')), false);
  // Storage was still cleared before the tabs were closed.
  assert.ok(h.events.indexOf('clear-storage:2') < h.events.indexOf('close:1'));
});
