import test from 'node:test';
import assert from 'node:assert/strict';
import { ensureFlowProjectOpen } from '../flow-api.js';

test('creating a project waits for the editor to render, then settles 2 s before returning', async () => {
  const state = { url: 'https://flow.google.com/u/0/', clicked: false, editorPolls: 0 };
  globalThis.window = {
    location: { get href() { return state.url; } },
    WIZ_global_data: { SNlM0e: 'xsrf', eptZe: '/_/AiSandboxAngularFrontend/' },
  };
  globalThis.location = { get pathname() { return new URL(state.url).pathname; }, hash: '' };
  globalThis.document = {
    querySelector: selector => {
      if (selector === 'button.new-project-button') {
        return { disabled: false, click: () => { state.clicked = true; state.url = 'https://flow.google.com/u/0/project/fresh'; } };
      }
      // 编辑器在 URL 切换后第 3 次轮询才渲染出来
      if (selector.startsWith('flow-base-prompt-box') || selector.startsWith('.ProseMirror')) {
        return state.editorPolls >= 3 ? {} : null;
      }
      return null;
    },
    querySelectorAll: () => [],
  };
  globalThis.chrome = {
    tabs: {
      get: async () => ({ id: 1, url: state.url, status: 'complete' }),
      onUpdated: { addListener() {}, removeListener() {} },
    },
    scripting: {
      executeScript: async ({ func, args = [] }) => {
        if (func.name === 'inspectModernFlowProjectEditor') state.editorPolls++;
        return [{ result: await func(...args) }];
      },
    },
  };
  const started = Date.now();
  assert.equal(await ensureFlowProjectOpen(1), 'fresh');
  assert.equal(state.clicked, true);
  assert.equal(state.editorPolls, 3, 'editor readiness is polled until the third poll renders it');
  assert.ok(Date.now() - started >= 2000, 'a 2 s settle follows editor readiness');
});
