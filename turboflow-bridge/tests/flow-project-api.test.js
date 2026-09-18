import test from 'node:test';
import assert from 'node:assert/strict';
import {
  inspectModernFlowProjectPage,
  clickModernFlowNewProject,
  deleteModernFlowProject,
} from '../flow-project-dom.js';

function pageHarness(projectIds = []) {
  const state = { projects: [...projectIds], pendingDelete: null, created: 0 };
  const makeCard = (id) => {
    const link = { getAttribute: () => `/project/${id}` };
    const title = { childNodes: [{ textContent: `Title ${id}` }], textContent: `Title ${id}` };
    const icon = { textContent: 'delete' };
    const deleteButton = {
      querySelectorAll: selector => selector === 'mat-icon' ? [icon] : [],
      click: () => { state.pendingDelete = id; },
    };
    return {
      querySelector: selector => selector.startsWith('a[') ? link
        : selector === '.project-title-label' ? title : null,
      querySelectorAll: selector => selector === 'button' ? [deleteButton] : [],
    };
  };
  const createButton = { disabled: false, click: () => { state.created++; } };
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
      ? state.projects.map(id => makeCard(id)) : [],
  };
  return { state, createButton };
}

test('reads all project ids and titles from the current Flow project cards', () => {
  pageHarness(['a', 'b']);
  assert.deepEqual(inspectModernFlowProjectPage(), {
    ready: true,
    canCreate: true,
    projects: [
      { projectId: 'a', title: 'Title a' },
      { projectId: 'b', title: 'Title b' },
    ],
  });
});

test('an empty account is ready when New project is enabled', () => {
  pageHarness();
  assert.deepEqual(inspectModernFlowProjectPage(), {
    ready: true,
    canCreate: true,
    projects: [],
  });
});

test('loading waits for the New project button to become enabled', () => {
  const { createButton } = pageHarness();
  createButton.disabled = true;
  assert.equal(inspectModernFlowProjectPage().ready, false);
});

test('malformed project cards fail closed instead of being treated as an empty account', () => {
  pageHarness();
  globalThis.document.querySelectorAll = selector => selector === 'flow-project-card' ? [{
    querySelector: () => null,
    querySelectorAll: () => [],
  }] : [];
  assert.match(inspectModernFlowProjectPage().error, /Invalid Flow project card/);
});

test('New project uses the current frontend button', () => {
  const { state } = pageHarness();
  assert.deepEqual(clickModernFlowNewProject(), { clicked: true });
  assert.equal(state.created, 1);
});

test('deletion opens the card dialog, confirms, and waits for that card to disappear', async () => {
  const { state } = pageHarness(['keep', 'delete-me']);
  assert.deepEqual(await deleteModernFlowProject('delete-me', 1000, 1), {
    deleted: true,
    projectId: 'delete-me',
  });
  assert.deepEqual(state.projects, ['keep']);
});

test('deletion reports a missing project without touching another card', async () => {
  const { state } = pageHarness(['keep']);
  assert.match((await deleteModernFlowProject('missing', 1000, 1)).error, /not found/);
  assert.deepEqual(state.projects, ['keep']);
});
