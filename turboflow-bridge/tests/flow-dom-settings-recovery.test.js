import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const source = readFileSync(new URL('../flow-dom-method.js', import.meta.url), 'utf8');
const start = source.indexOf('async function applySettings(task)');
const end = source.indexOf('function getEditor()', start);
const applySettingsSource = source.slice(start, end);

test('reuses an already-open Flow settings panel before toggling the trigger', () => {
  const reuse = applySettingsSource.indexOf('let settingsPanel = findSettingsPanel();');
  const click = applySettingsSource.indexOf('clickDom(trigger);');

  assert.ok(reuse >= 0);
  assert.ok(click > reuse);
});

test('reacquires and retries the settings trigger after stale UI state', () => {
  assert.match(applySettingsSource, /for \(let attempt = 1; !settingsPanel && attempt <= 3; attempt\+\+\)/);
  assert.match(applySettingsSource, /await waitFor\(findSettingsTrigger, 4000, 100\)/);
  assert.match(applySettingsSource, /Settings panel open retry/);
});
