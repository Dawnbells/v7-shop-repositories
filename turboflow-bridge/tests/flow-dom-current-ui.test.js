import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

import { classifyErrorCode } from '../task-error-policy.js';

// 以下断言对应 2026-09 Flow 前端（boq_labs-ai-sandbox-frontend_20260922）的真实 DOM。
const source = readFileSync(new URL('../flow-dom-method.js', import.meta.url), 'utf8');
const background = readFileSync(new URL('../background.js', import.meta.url), 'utf8');

function extractFunction(name) {
  const start = source.indexOf(`function ${name}(`);
  assert.notEqual(start, -1, `${name} not found`);
  const bodyStart = source.indexOf('{', source.indexOf(')', start));
  let depth = 0;
  for (let i = bodyStart; i < source.length; i++) {
    if (source[i] === '{') depth++;
    if (source[i] === '}' && --depth === 0) {
      return new Function(`return (${source.slice(start, i + 1)});`)();
    }
  }
  throw new Error(`${name} is not balanced`);
}

function fakeElement({ text = '', icons = [], links = [] } = {}) {
  return {
    textContent: text,
    querySelectorAll(selector) {
      if (selector === 'a[href]') return links.map((href) => ({ getAttribute: () => href }));
      return icons.map((icon) => ({ textContent: icon }));
    },
  };
}

test('reads control labels without the icon ligature or emoji prefix', () => {
  const controlLabel = extractFunction('controlLabel');
  assert.equal(controlLabel(fakeElement({ text: 'imageImage', icons: ['image'] })), 'Image');
  assert.equal(controlLabel(fakeElement({ text: 'crop_9_169:16', icons: ['crop_9_16'] })), '9:16');
  assert.equal(controlLabel(fakeElement({ text: '🍌 Nano Banana 2' })), 'Nano Banana 2');
  assert.equal(controlLabel(fakeElement({ text: '🍌 Nano Banana 2 Lite' })), 'Nano Banana 2 Lite');
});

test('selects the model by exact label so Nano Banana 2 never picks the Lite variant', () => {
  const settings = source.slice(source.indexOf('async function applySettings(task)'), source.indexOf('function getEditor()'));
  assert.match(settings, /controlLabel\(option\) === modelLabel/);
  assert.doesNotMatch(settings, /textContent \|\| ''\)\.includes\(modelLabel\)/);
  assert.match(settings, /controlLabel\(button\)\.toLowerCase\(\) === 'image'/);
  assert.doesNotMatch(source, /:\s*'Imagen 4'/);
});

test('counts prompt references by Ingredient chips, not by the Clear prompt close icon', () => {
  const chipsSource = source.slice(source.indexOf('function findAttachedReferenceChips'), source.indexOf('function attachedReferenceCount'));
  assert.match(chipsSource, /flow-image-ingredient-chip button/);
  assert.match(chipsSource, /button\[aria-label="Ingredient"\]/);
  assert.doesNotMatch(source, /=== 'close'/);
});

test('turns Flow Agent mode off before configuring a direct generation', () => {
  const run = source.slice(source.indexOf('async function runDomTranslate'));
  const agent = run.indexOf('await ensureAgentModeOff();');
  const settings = run.indexOf('await applySettings(task);');
  assert.ok(agent > 0);
  assert.ok(settings > agent);
});

test('uploads unique task images without a blocking library pre-search', () => {
  assert.doesNotMatch(source, /checkImagesInLibrary/);
  assert.doesNotMatch(source, /SEARCH_TIMEOUT_MS/);
});

test('dispatches Escape where the CDK overlay listens for it', () => {
  const escape = source.slice(source.indexOf('function pressEscape()'), source.indexOf('function controlLabel'));
  assert.match(escape, /\(document\.activeElement \|\| document\.body\)\.dispatchEvent\(event\)/);
});

test('classifies the modern API unusual-activity RPC rejection as a reCAPTCHA block', () => {
  const error = new Error('Flow RPC ogiZ0b failed (RPC status 7: PERMISSION_DENIED): PUBLIC_ERROR_UNUSUAL_ACTIVITY');
  error.code = 'FLOW_RPC_REJECTED';
  error.rpcStatus = 7;
  assert.equal(classifyErrorCode(error), 'RECAPTCHA_BLOCKED');
  assert.equal(classifyErrorCode('Flow RPC ogiZ0b failed (RPC status 7: PERMISSION_DENIED)'), 'FLOW_RPC_REJECTED');
});

test('accepts the first-upload rights dialog and keeps the page script version in sync', () => {
  assert.match(background, /rights to use this image/i);
  assert.match(background, /\^i agree\$/i);
  const version = source.match(/const VERSION = (\d+);/)[1];
  assert.match(source, new RegExp(`TURBOFLOW_DOM_V${version}'`));
  assert.match(source, new RegExp(`RUN_DOM_TRANSLATE_V${version}'`));
  assert.match(background, new RegExp(`DOM_TRANSLATE_MESSAGE = 'RUN_DOM_TRANSLATE_V${version}'`));
  assert.match(background, new RegExp(`DOM_TRANSLATE_PORT = 'TURBOFLOW_DOM_V${version}'`));
  assert.doesNotMatch(background, /'IMAGEN_3_5'\]/);
});

function extractFrom(text, name) {
  const start = text.indexOf(`function ${name}(`);
  assert.notEqual(start, -1, `${name} not found`);
  const bodyStart = text.indexOf('{', text.indexOf(')', start));
  let depth = 0;
  for (let i = bodyStart; i < text.length; i++) {
    if (text[i] === '{') depth++;
    if (text[i] === '}' && --depth === 0) return text.slice(start, i + 1);
  }
  throw new Error(`${name} is not balanced`);
}

test('picks the nearest Flow aspect ratio for every source image size', () => {
  const aspectRatioFor = new Function(`${extractFrom(background, 'aspectRatioFor')}; return aspectRatioFor;`)();
  const cases = [
    [1920, 1080, 'IMAGE_ASPECT_RATIO_LANDSCAPE'],
    [3000, 1000, 'IMAGE_ASPECT_RATIO_LANDSCAPE'],
    [1500, 1000, 'IMAGE_ASPECT_RATIO_LANDSCAPE_FOUR_THREE'],
    [800, 600, 'IMAGE_ASPECT_RATIO_LANDSCAPE_FOUR_THREE'],
    [1000, 1000, 'IMAGE_ASPECT_RATIO_SQUARE'],
    [1100, 1000, 'IMAGE_ASPECT_RATIO_SQUARE'],
    [750, 1000, 'IMAGE_ASPECT_RATIO_PORTRAIT_THREE_FOUR'],
    [1000, 1500, 'IMAGE_ASPECT_RATIO_PORTRAIT_THREE_FOUR'],
    [1080, 1920, 'IMAGE_ASPECT_RATIO_PORTRAIT'],
    [790, 1500, 'IMAGE_ASPECT_RATIO_PORTRAIT'],
    [null, 1000, 'IMAGE_ASPECT_RATIO_LANDSCAPE'],
  ];
  for (const [width, height, expected] of cases) {
    assert.equal(aspectRatioFor(width, height), expected, `${width}x${height}`);
  }
});

test('measures each task image before choosing its aspect ratio', () => {
  const translate = extractFrom(background, 'translateImage');
  assert.match(translate, /await readImageSize\(task\.imageBase64\)/);
  assert.match(translate, /task\.sourceWidth, height: task\.sourceHeight/);
  assert.match(translate, /aspectRatioFor\(size\.width, size\.height\)/);
});

test('selects aspect ratio on the exact radio and verifies the applied trigger icon', () => {
  const settings = source.slice(source.indexOf('async function applySettings(task)'), source.indexOf('function getEditor()'));
  assert.match(settings, /controlLabel\(control\) === aspect\.label/);
  assert.match(settings, /await waitFor\(\(\) => isSelected\(findAspectTab\(\)\), 3000, 100\)/);
  assert.doesNotMatch(settings, /exposesSelectionState/);
  assert.match(settings, /controlIcons\(appliedTrigger\)\.includes\(aspect\.icon\)/);
});

test('submits generation with a trusted click and confirms it by the real ogiZ0b request', () => {
  const submit = extractFrom(source, 'submitPrompt');
  assert.match(submit, /type: 'FLOW_TRUSTED_SUBMIT', selector: START_GENERATION_SELECTOR/);
  assert.match(submit, /return response\.generateToken/);
  assert.doesNotMatch(submit, /click\(button/);

  const manifest = JSON.parse(readFileSync(new URL('../manifest.json', import.meta.url), 'utf8'));
  assert.ok(manifest.permissions.includes('debugger'));

  const trusted = extractFrom(background, 'dispatchTrustedClickNow');
  assert.match(trusted, /chrome\.debugger\.attach\(target, '1\.3'\)/);
  for (const type of ['mouseMoved', 'mousePressed', 'mouseReleased']) {
    assert.match(trusted, new RegExp(`type: '${type}'`));
  }
  assert.match(trusted, /finally \{\s*if \(attached\) await chrome\.debugger\.detach\(target\)/);
  assert.match(trusted, /next\.vh === point\.vh/);
  assert.match(background, /msg\.type === 'FLOW_TRUSTED_SUBMIT'[\s\S]{0,200}isFlowUrl\(_sender\.tab\.url/);

  // 监听先于点击挂好；只有 prompt 仍在时才重点，已清空时只延长等待，避免重复生成。
  const flow = extractFrom(background, 'submitFlowGeneration');
  assert.ok(flow.indexOf('armModernGenerateMonitor(tabId)') < flow.indexOf('dispatchTrustedClick(tabId, selector)'));
  assert.match(flow, /if \(state\.sent\) return token;/);
  assert.match(flow, /if \(state\.promptCleared\) \{[\s\S]*?SUBMIT_CLEARED_GRACE_MS[\s\S]*?throw new Error/);
});

test('maps the UI result from the ogiZ0b response instead of guessing DOM tiles', () => {
  assert.doesNotMatch(source, /claimGenerationTile|unclaimed generation tiles|findTileError/);
  const run = source.slice(source.indexOf('async function runDomTranslate'));
  assert.match(run, /return \{ generateToken \};/);

  const dom = extractFrom(background, 'runFlowDomTranslation');
  assert.match(dom, /resolveModernGenerateResponse\(conn\.tabId, result\.generateToken, \{ projectId: conn\.projectId \}\)/);
});
