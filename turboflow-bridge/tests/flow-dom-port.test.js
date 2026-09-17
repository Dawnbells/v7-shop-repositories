import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const background = readFileSync(new URL('../background.js', import.meta.url), 'utf8');
const pageAutomation = readFileSync(new URL('../flow-dom-method.js', import.meta.url), 'utf8');

test('uses a dedicated port instead of one long-lived sendMessage response', () => {
  const start = background.indexOf('async function runFlowDomTranslation(conn, task)');
  const end = background.indexOf('function buildDomUploadFileName', start);
  const pipeline = background.slice(start, end);

  assert.match(pipeline, /chrome\.tabs\.connect\(conn\.tabId, \{ name: DOM_TRANSLATE_PORT \}\)/);
  assert.match(pipeline, /FLOW_DOM_TRANSLATION_ACCEPTED/);
  assert.match(pipeline, /FLOW_DOM_TRANSLATION_RESULT/);
  assert.doesNotMatch(pipeline, /chrome\.tabs\.sendMessage/);
});

test('page automation acknowledges first and reports detailed completion over the port', () => {
  assert.match(pageAutomation, /chrome\.runtime\.onConnect\.addListener\(portListener\)/);
  assert.match(pageAutomation, /FLOW_DOM_TRANSLATION_ACCEPTED/);
  assert.match(pageAutomation, /FLOW_DOM_TRANSLATION_RESULT/);
  assert.match(pageAutomation, /Unknown Flow page automation error/);
});
