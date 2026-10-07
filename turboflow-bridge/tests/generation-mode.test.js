import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import { normalizeGenerationMode, generationModeLabel } from '../generation-mode.js';
import { translateImageForMode } from '../flow-generation-mode.js';

test('three modes round-trip; absent or invalid settings use API-2.3.5.1', async () => {
  const source = readFileSync(new URL('../background.js', import.meta.url), 'utf8');
  const stored = {};
  let cleanup = 0;
  const context = vm.createContext({
    normalizeGenerationMode, normalizeFlowConcurrency: value => value || 1, normalizeBaseUrl: value => value,
    generationMode: 'api', flowConcurrency: 1, flowTasks: { setLimit() {} },
    MAX_FLOW_CONCURRENCY: 10,
    GENERATION_MODE_STORAGE_KEY: 'generationMode', FLOW_CONCURRENCY_STORAGE_KEY: 'flowConcurrency',
    ensureBridgeId: async () => {}, bridgeId: 'test',
    chrome: { storage: { local: { set: async data => Object.assign(stored, data), get: async () => stored } } },
    releaseApi2351: async () => { cleanup++; }, addLog() {},
  });
  vm.runInContext(source.slice(source.indexOf('async function saveConfig('), source.indexOf('function normalizeFlowConcurrency(')), context);
  const start = source.indexOf('async function loadConfig(');
  vm.runInContext(source.slice(start, source.indexOf('\n}', start) + 2), context);
  for (const value of ['api', 'ui', 'api-2.3.5.1', undefined, 'invalid']) {
    await context.saveConfig({ generationMode: value, services: [], flowConcurrency: 3 });
    const expected = normalizeGenerationMode(value);
    assert.equal(context.generationMode, expected);
    assert.equal((await context.loadConfig()).generationMode, expected);
    assert.equal(stored.flowConcurrency, 3);
  }
  assert.equal(cleanup, 2);
  assert.equal(generationModeLabel('ui'), 'Flow UI');
  assert.equal(generationModeLabel('api-2.3.5.1'), 'API-2.3.5.1');
});

test('shared mode dispatch retains the UI adapter and snapshots its selected mode', async () => {
  let mode = 'ui';
  let finish;
  const conn = {};
  const task = {};
  const running = translateImageForMode(mode, conn, task, (selectedConn, selectedTask) => {
    assert.equal(selectedConn, conn);
    assert.equal(selectedTask, task);
    return new Promise(resolve => { finish = resolve; });
  });
  mode = 'api-2.3.5.1';
  finish('ui-result');
  assert.equal(await running, 'ui-result');
});

test('test and automatic entrypoints both dispatch through the same mode selector', () => {
  const source = readFileSync(new URL('../background.js', import.meta.url), 'utf8');
  const testAction = source.slice(source.indexOf('async function executeTestTranslation('), source.indexOf('async function cleanupStatus('));
  const automatic = source.slice(source.indexOf('async function translateImage(task, conn)'), source.indexOf('async function runFlowDomTranslation'));
  for (const entry of [testAction, automatic]) {
    assert.match(entry, /const mode = (?:uiOnly \? 'ui' : )?generationMode/);
    assert.match(entry, /translateImageForMode\(mode, conn,/);
  }
});
