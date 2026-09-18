import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import { openFreshFlowProject } from '../flow-api.js';

function flowHarness({ pages = [[]], listError = false, failedDelete = null, createError = false } = {}) {
  const events = [];
  const listeners = new Set();
  let page = 0;
  const tab = { id: 42, status: 'complete', url: 'https://flow.google.com/' };
  globalThis.window = { location: {
    set href(url) {
      tab.url = url;
      // Complete before executeScript resolves to exercise the load-event race.
      for (const listener of listeners) listener(tab.id, { status: 'complete' }, tab);
    },
  } };
  globalThis.chrome = {
    tabs: {
      create: async ({ url }) => { events.push('open'); tab.url = url; return tab; },
      get: async () => tab,
      onUpdated: {
        addListener: listener => listeners.add(listener),
        removeListener: listener => listeners.delete(listener),
      },
    },
    scripting: { executeScript: async ({ func, args = [] }) => [{ result: await func(...args) }] },
  };
  globalThis.fetch = async (path, options) => {
    let data;
    let ok = true;
    if (path.includes('searchUserProjects')) {
      events.push(`list:${page}`);
      data = listError ? { error: { message: 'not signed in' } } : { result: { data: { json: { result: {
        projects: pages[page].map(projectId => ({ projectId })),
        nextPageToken: page + 1 < pages.length ? `page-${page + 1}` : null,
      } } } } };
      page++;
    } else if (path.includes('deleteProject')) {
      const id = JSON.parse(options.body).json.projectToDeleteId;
      events.push(`delete:${id}`);
      ok = id !== failedDelete;
    } else if (path.includes('createProject')) {
      events.push('create');
      ok = !createError;
      data = { result: { data: { json: { result: { projectId: 'fresh-project' } } } } };
    } else {
      assert.fail(`Unexpected request: ${path}`);
    }
    return { ok, status: ok ? 200 : 500, json: async () => data };
  };
  return { events, tab, listeners };
}

test('Open Flow lists all pages, deletes every old project, then creates and opens one new project', async () => {
  const { events, tab, listeners } = flowHarness({ pages: [['old-a'], ['old-b']] });
  const result = await openFreshFlowProject();
  assert.deepEqual(events, ['open', 'list:0', 'list:1', 'delete:old-a', 'delete:old-b', 'create']);
  assert.deepEqual(result, { tabId: 42, projectId: 'fresh-project' });
  assert.equal(tab.url, 'https://flow.google.com/project/fresh-project');
  assert.equal(listeners.size, 0);
});

test('an empty account still gets exactly one new project', async () => {
  const { events } = flowHarness();
  await openFreshFlowProject();
  assert.deepEqual(events, ['open', 'list:0', 'create']);
});

test('a malformed or failed project listing never creates a project', async () => {
  const { events } = flowHarness({ listError: true });
  await assert.rejects(openFreshFlowProject(), /Invalid Flow project list response/);
  assert.deepEqual(events, ['open', 'list:0']);
});

test('partial deletion failure finishes cleanup but prevents creation', async () => {
  const { events } = flowHarness({ pages: [['old-a', 'old-b']], failedDelete: 'old-a' });
  await assert.rejects(openFreshFlowProject(), /Failed to delete 1/);
  assert.deepEqual(events, ['open', 'list:0', 'delete:old-a', 'delete:old-b']);
});

test('creation errors are surfaced after cleanup', async () => {
  flowHarness({ createError: true });
  await assert.rejects(openFreshFlowProject(), /HTTP 500/);
});

test('concurrent Open Flow requests share one reset and refresh the standby project', async () => {
  const source = readFileSync(new URL('../background.js', import.meta.url), 'utf8');
  let complete;
  let resets = 0;
  let scheduled = 0;
  const context = vm.createContext({
    openingFlowPromise: null, timerId: null, nextPollAt: 0, lastStatus: {},
    running: false, currentTasks: [], recoveryPromise: null, deletingProjects: false,
    prefetchedTask: { conn: { projectId: 'old-project' } }, flowTabAvailable: false,
    broadcast() {}, addLog() {}, clearTimeout, setTimeout,
    scheduleLoop() { scheduled++; },
    openFreshFlowProject: () => { resets++; return new Promise(resolve => { complete = resolve; }); },
  });
  vm.runInContext(source.slice(source.indexOf('function openFlowWithFreshProject('),
    source.indexOf('chrome.sidePanel.setPanelBehavior(')), context);
  const first = context.openFlowWithFreshProject();
  const second = context.openFlowWithFreshProject();
  assert.equal(first, second);
  assert.equal(resets, 1);
  assert.equal(scheduled, 0);
  complete({ tabId: 42, projectId: 'fresh-project' });
  assert.equal((await first).ok, true);
  assert.equal(context.prefetchedTask.conn.projectId, 'fresh-project');
  assert.equal(context.openingFlowPromise, null);
  assert.equal(scheduled, 1);
});
