import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import { openFreshFlowProject } from '../flow-api.js';

function flowHarness({ projects = [], malformedList = false, failedDelete = null, createError = false } = {}) {
  const events = [];
  const listeners = new Set();
  const tab = { id: 42, status: 'complete', url: 'https://flow.google.com/' };
  const state = { projects: [...projects], pendingDelete: null };
  const makeCard = (id, malformed = false) => {
    const link = { getAttribute: () => malformed ? '/not-a-project' : `/project/${id}` };
    const title = { childNodes: [{ textContent: id }], textContent: id };
    const icon = { textContent: 'delete' };
    const deleteButton = {
      querySelectorAll: selector => selector === 'mat-icon' && id !== failedDelete ? [icon] : [],
      click: () => { events.push(`delete:${id}`); state.pendingDelete = id; },
    };
    return {
      querySelector: selector => selector.startsWith('a[') ? link
        : selector === '.project-title-label' ? title : null,
      querySelectorAll: selector => selector === 'button' ? [deleteButton] : [],
    };
  };
  const createButton = {
    disabled: false,
    click: () => {
      events.push('create');
      if (createError) throw new Error('Flow create failed');
      tab.url = 'https://flow.google.com/project/fresh-project';
    },
  };
  const dialog = {
    querySelectorAll: selector => selector === 'mat-dialog-actions button' ? [
      { click() {} },
      { click: () => {
        state.projects = state.projects.filter(id => id !== state.pendingDelete);
        state.pendingDelete = null;
      } },
    ] : [],
  };
  globalThis.document = {
    querySelector: selector => selector === 'button.new-project-button' ? createButton
      : selector === 'mat-dialog-container[role="dialog"]' && state.pendingDelete ? dialog : null,
    querySelectorAll: selector => selector === 'flow-project-card'
      ? (malformedList ? [makeCard('bad', true)] : state.projects.map(id => makeCard(id))) : [],
  };
  globalThis.window = { location: { href: tab.url } };
  globalThis.chrome = {
    tabs: {
      create: async ({ url }) => { events.push('open'); tab.url = url; return tab; },
      get: async () => tab,
      update: async (_id, { url }) => { tab.url = url; return tab; },
      onUpdated: {
        addListener: listener => listeners.add(listener),
        removeListener: listener => listeners.delete(listener),
      },
    },
    scripting: { executeScript: async ({ func, args = [] }) => {
      if (func.name === 'deleteModernFlowProject') events.push(`delete-attempt:${args[0]}`);
      return [{ result: await func(...args) }];
    } },
  };
  return { events, tab, listeners, state };
}

test('Open Flow lists all pages, deletes every old project, then creates and opens one new project', async () => {
  const { events, tab, listeners } = flowHarness({ projects: ['old-a', 'old-b'] });
  const result = await openFreshFlowProject();
  assert.deepEqual(events, [
    'open', 'delete-attempt:old-a', 'delete:old-a',
    'delete-attempt:old-b', 'delete:old-b', 'create',
  ]);
  assert.deepEqual(result, { tabId: 42, projectId: 'fresh-project' });
  assert.equal(tab.url, 'https://flow.google.com/project/fresh-project');
  assert.equal(listeners.size, 0);
});

test('an empty account still gets exactly one new project', async () => {
  const { events } = flowHarness();
  await openFreshFlowProject();
  assert.deepEqual(events, ['open', 'create']);
});

test('a malformed or failed project listing never creates a project', async () => {
  const { events } = flowHarness({ malformedList: true });
  await assert.rejects(openFreshFlowProject(), /Invalid Flow project card/);
  assert.deepEqual(events, ['open']);
});

test('partial deletion failure finishes cleanup but prevents creation', async () => {
  const { events } = flowHarness({ projects: ['old-a', 'old-b'], failedDelete: 'old-a' });
  await assert.rejects(openFreshFlowProject(), /Failed to delete 1/);
  assert.deepEqual(events, [
    'open', 'delete-attempt:old-a',
    'delete-attempt:old-b', 'delete:old-b',
  ]);
});

test('creation errors are surfaced after cleanup', async () => {
  flowHarness({ createError: true });
  await assert.rejects(openFreshFlowProject(), /Flow create failed/);
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
