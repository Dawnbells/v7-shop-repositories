import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const source = readFileSync(new URL('../flow-dom-method.js', import.meta.url), 'utf8');

test('attaches the source image before writing and submitting the translation prompt', () => {
  const start = source.indexOf('await attachAllImages(images, task);');
  const end = source.indexOf("if (!await submitPrompt(task)) throw new Error('Submit failed');", start);
  assert.notEqual(start, -1);
  assert.notEqual(end, -1);

  const pipeline = source.slice(start, end + 80);
  assert.ok(pipeline.indexOf('await requireAttachedReferences(images.length);') > 0);
  assert.ok(pipeline.indexOf("await injectPrompt(task.prompt || '');") > 0);
  assert.ok(pipeline.indexOf('await submitPrompt(task)') > pipeline.indexOf('await injectPrompt'));
});

test('does not add artificial waits between Add to prompt and generation submit', () => {
  const start = source.indexOf('await attachAllImages(images, task);');
  const end = source.indexOf("if (!await submitPrompt(task)) throw new Error('Submit failed');", start);
  const pipeline = source.slice(start, end);

  assert.doesNotMatch(pipeline, /randomDelay\s*\(/);
  assert.doesNotMatch(pipeline, /await sleep\s*\(/);
});

test('continues after Add to prompt committed instead of searching for the image again', () => {
  const start = source.indexOf('async function attachAllImages(images, task)');
  const end = source.indexOf('async function applySettings(task)', start);
  const attachmentPipeline = source.slice(start, end);

  assert.match(attachmentPipeline, /attachedReferenceCount\(\) >= targetReferenceCount/);
  assert.match(attachmentPipeline, /Attachment already committed; continuing without retry/);
  assert.doesNotMatch(attachmentPipeline, /Search result for .* not found/);
});
