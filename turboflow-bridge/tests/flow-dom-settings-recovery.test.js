import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const source = readFileSync(new URL('../flow-dom-method.js', import.meta.url), 'utf8');
const section = (start, end) => source.slice(source.indexOf(start), source.indexOf(end));

function button({ label = '', text = '', icons = [], hidden = false, disabled = false,
  popup = false, overlay = false, inPanel = false, onClick = () => {} } = {}) {
  return {
    isConnected: true, disabled, textContent: icons.join('') + text,
    getClientRects: () => hidden ? [] : [{}],
    getAttribute: (name) => ({ 'aria-label': label, 'aria-haspopup': popup ? 'menu' : null })[name] ?? null,
    closest: (selector) => selector.includes('.cdk-overlay-pane') && inPanel ? {} : null,
    querySelectorAll: () => icons.map((textContent) => ({ textContent })),
    querySelector: () => overlay ? {} : null,
    scrollIntoView() {}, click: onClick,
  };
}

function harness({ buttons = [], promptButtons = [], panel = () => null } = {}) {
  let now = 0;
  let escapes = 0;
  const warnings = [];
  const read = (value) => typeof value === 'function' ? value(now) : value;
  const context = vm.createContext({
    Date: { now: () => now },
    sleep: async (ms) => { now += ms; },
    pressEscape: () => { escapes++; },
    console: { warn: (message) => warnings.push(message) },
    getComputedStyle: () => ({ visibility: 'visible' }),
    document: {
      querySelectorAll(selector) {
        if (selector === 'flow-base-prompt-box, .base-prompt-box') {
          return [{ querySelectorAll: () => read(promptButtons) }];
        }
        if (selector === 'button[aria-label="Settings trigger"]') {
          return read(buttons).filter((b) => b.getAttribute('aria-label') === 'Settings trigger');
        }
        if (selector === 'button[aria-haspopup="menu"]') {
          return read(buttons).filter((b) => b.getAttribute('aria-haspopup') === 'menu');
        }
        throw new Error(`Unexpected selector: ${selector}`);
      },
    },
  });
  vm.runInContext([
    section('const ASPECT_CONFIG =', 'function randomBetween('),
    section('async function waitFor(', 'function stealthClick('),
    section('function controlLabel(', 'function findAgentModeChip('),
    section('function isVisibleSettingsElement(', 'async function applySettings('),
  ].join('\n'), context);
  return {
    find: () => context.findSettingsTrigger(),
    open: () => context.openSettingsPanel(() => panel(now)),
    warnings, get escapes() { return escapes; }, get elapsed() { return now; },
  };
}

test('reuses an already open panel without clicking a toggle', async () => {
  const panel = {};
  const trigger = button({ label: 'Settings trigger', onClick: () => assert.fail('must not toggle') });
  const h = harness({ buttons: [trigger], panel: () => panel });
  assert.equal(await h.open(), panel);
  assert.equal(h.elapsed, 0);
});

test('retries when the settings trigger renders after the first timeout', async () => {
  let clicked = 0;
  const panel = {};
  const trigger = button({ label: 'Settings trigger', onClick: () => { clicked++; } });
  const h = harness({ buttons: (now) => now >= 5000 ? [trigger] : [], panel: () => clicked ? panel : null });
  assert.equal(await h.open(), panel);
  assert.equal(clicked, 1);
  assert.equal(h.warnings.length, 1);
  assert.ok(h.elapsed >= 5000);
});

test('missing triggers receive all three attempts and report zero clicks', async () => {
  const h = harness();
  await assert.rejects(h.open(), /after 3 attempts \(trigger=not-found, clicks=0\)/);
  assert.equal(h.warnings.length, 3);
  assert.ok(h.elapsed >= 12000);
});

test('reacquires the trigger after the first click rerenders the prompt box', async () => {
  let clicks = 0;
  const panel = {};
  const first = button({ label: 'Settings trigger', onClick: () => { clicks++; first.isConnected = false; } });
  const second = button({ label: 'Settings trigger', onClick: () => { clicks++; } });
  const h = harness({ buttons: () => clicks ? [second] : [first], panel: () => clicks === 2 ? panel : null });
  assert.equal(await h.open(), panel);
  assert.equal(clicks, 2);
});

test('a late panel is reused before another toggle click', async () => {
  let clicks = 0;
  const panel = {};
  const trigger = button({ label: 'Settings trigger', onClick: () => { clicks++; } });
  const h = harness({ buttons: [trigger], panel: (now) => now >= 4200 ? panel : null });
  assert.equal(await h.open(), panel);
  assert.equal(clicks, 1);
});

test('reports click failure separately from a missing trigger', async () => {
  let clicks = 0;
  const trigger = button({ label: 'Settings trigger', onClick: () => { clicks++; } });
  const h = harness({ buttons: [trigger] });
  await assert.rejects(h.open(), /trigger=missing, clicks=3/);
  assert.equal(clicks, 3);
});

test('ignores hidden, detached and disabled named triggers', () => {
  const hidden = button({ label: 'Settings trigger', hidden: true });
  const disabled = button({ label: 'Settings trigger', disabled: true });
  const detached = button({ label: 'Settings trigger' });
  detached.isConnected = false;
  const visible = button({ label: 'Settings trigger' });
  assert.equal(harness({ buttons: [hidden, disabled, detached, visible] }).find(), visible);
});

test('finds icon-only settings inside the prompt box', () => {
  const trigger = button({ icons: ['tune'] });
  assert.equal(harness({ promptButtons: [trigger] }).find(), trigger);
  assert.equal(harness({ buttons: [trigger] }).find(), null);
});

test('finds generation summary buttons without the fixed English aria label', () => {
  const trigger = button({ text: 'Nano Banana 2 x1', icons: ['crop_16_9'] });
  assert.equal(harness({ promptButtons: [trigger] }).find(), trigger);
});

test('supports legacy nested-label summaries but rejects unrelated menus and aspect options', () => {
  const unrelated = button({ popup: true, overlay: true, text: 'Project menu' });
  const option = button({ text: '16:9', icons: ['crop_16_9'], inPanel: true });
  const legacy = button({ popup: true, overlay: true, text: 'Nano Banana 2', icons: ['crop_square'] });
  assert.equal(harness({ buttons: [unrelated, legacy], promptButtons: [option] }).find(), legacy);
  assert.equal(harness({ buttons: [unrelated], promptButtons: [option] }).find(), null);
});
