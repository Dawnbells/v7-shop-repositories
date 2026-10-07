import test from 'node:test';
import assert from 'node:assert/strict';
import { restartFlowProject } from '../flow-project-lifecycle.js';

function harness({ homeReady = async () => ({ ready: true }) } = {}) {
  const events = [];
  const browser = { tabs: {
    query: async () => [
      { id: 1, url: 'https://flow.google.com/u/0/project/old' },
      { id: 2, url: 'https://labs.google/fx/tools/flow' },
      { id: 3, url: 'https://example.com/' },
    ],
    get: async id => ({ id }),
    remove: async id => { events.push(`close:${id}`); },
  } };
  const api = {
    openFlowHome: async ({ url }) => { events.push(`open:${url}`); return { tabId: 42 }; },
    waitForFlowHomeReady: async tabId => { events.push(`home-ready:${tabId}`); return homeReady(tabId); },
    ensureFlowProjectOpen: async tabId => { events.push(`create:${tabId}`); return 'new-project'; },
    clearTokenCache: () => events.push('clear-token'),
    clearProjectIdCache: () => events.push('clear-project'),
  };
  const phase = async name => { events.push(`phase:${name}`); };
  return { events, browser, api, phase };
}

test('recovery closes Flow tabs, waits for the home page to load, then creates a project', async () => {
  const h = harness();
  const result = await restartFlowProject(
    { homeUrl: 'https://flow.google.com/u/0/project/old', windowId: 7 }, h.phase, h.browser, h.api);
  assert.deepEqual(result, { tabId: 42, projectId: 'new-project' });
  assert.deepEqual(h.events, [
    'phase:closing', 'close:1', 'close:2', 'clear-token', 'clear-project',
    'phase:opening', 'open:https://flow.google.com/u/0/', 'home-ready:42',
    'phase:creating', 'create:42',
  ]);
});

test('a home page that never becomes usable fails the recovery before any project is created', async () => {
  const h = harness({ homeReady: async () => {
    throw new Error('Flow home opened a non-Flow page (accounts.google.com); sign in to Google Flow, then click Run Now');
  } });
  await assert.rejects(restartFlowProject(null, h.phase, h.browser, h.api), /non-Flow page/);
  assert.equal(h.events.includes('phase:creating'), false);
  assert.equal(h.events.some(event => event.startsWith('create:')), false);
});
