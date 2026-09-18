import test from 'node:test';
import assert from 'node:assert/strict';
import { translateImageViaApi } from '../flow-image-translation.js';

const conn = { connected: true, tabId: 7, projectId: 'project', transport: 'boq', flowUrl: 'https://flow.google.com/project/project' };

test('two interleaved translations retain their own upload, workflow and result', async () => {
  const calls = [];
  const api = {
    getSessionToken: () => assert.fail('BOQ uses the page session, not legacy bearer auth'),
    uploadImageToFlow: async (tab, options) => {
      assert.equal(tab, 7);
      assert.equal(options.mimeType, 'image/jpeg');
      assert.equal(options.pid, 'project');
      assert.ok(!options.base64.startsWith('data:'));
      return 'uploaded-' + options.base64;
    },
    generateWithReference: async (tab, options) => {
      calls.push(options);
      await new Promise(resolve => setTimeout(resolve, options.prompt === 'first' ? 20 : 0));
      return { mediaId: options.referenceMediaId + '-result', workflowId: options.prompt };
    },
    resolveFlowImageUrl: async (tab, result, url) => {
      assert.equal(url, conn.flowUrl);
      return 'https://flow-content.google/image/' + result.mediaId;
    },
    fetchImageAsBase64: async (tab, url) => ({ dataUrl: 'download:' + url }),
  };
  const results = await Promise.all(['first', 'second'].map(prompt => translateImageViaApi(conn, {
    imageBase64: 'data:image/jpeg;base64,' + prompt,
    prompt,
    model: 'GEM_PIX_2',
    aspectRatio: 'IMAGE_ASPECT_RATIO_SQUARE',
  }, api)));
  for (const [index, prompt] of ['first', 'second'].entries()) {
    assert.equal(results[index].referenceMediaId, 'uploaded-' + prompt);
    assert.equal(results[index].workflowId, prompt);
    assert.ok(results[index].resultDataUrl.endsWith('uploaded-' + prompt + '-result'));
    assert.equal(calls[index].prompt, prompt);
    assert.equal(calls[index].model, 'GEM_PIX_2');
  }
});

test('a failed upload never submits generation', async () => {
  const error = Object.assign(new Error('verification required'), { code: 'FLOW_AUTHENTICATION_FAILED' });
  await assert.rejects(translateImageViaApi(conn, { imageBase64: 'YWJj' }, {
    uploadImageToFlow: async () => { throw error; },
    generateWithReference: () => assert.fail('must not generate'),
  }), value => value === error);
});

test('quota pause blocks a task still uploading but lets submitted generation finish and download', async () => {
  let paused = false;
  let finishGeneration;
  let finishUpload;
  const submissions = [];
  const downloads = [];
  const beforeSubmit = () => {
    if (paused) throw Object.assign(new Error('paused'), { code: 'FLOW_SUBMISSION_PAUSED' });
  };
  const api = {
    uploadImageToFlow: async (_tab, options) => options.base64 === 'waiting'
      ? new Promise(resolve => { finishUpload = resolve; }) : 'active-media',
    generateWithReference: async (_tab, options) => {
      submissions.push(options.referenceMediaId);
      return new Promise(resolve => { finishGeneration = resolve; });
    },
    resolveFlowImageUrl: async () => 'https://flow-content.google/image/completed',
    fetchImageAsBase64: async (_tab, url) => {
      downloads.push(url);
      return { dataUrl: 'data:image/png;base64,done' };
    },
  };
  const active = translateImageViaApi(conn, { imageBase64: 'active', beforeSubmit }, api);
  await new Promise(resolve => setImmediate(resolve));
  const waiting = translateImageViaApi(conn, { imageBase64: 'waiting', beforeSubmit }, api);
  const rejected = assert.rejects(waiting, { code: 'FLOW_SUBMISSION_PAUSED' });
  paused = true;
  finishUpload('waiting-media');
  finishGeneration({ mediaId: 'active-result' });
  await rejected;
  assert.equal((await active).mediaId, 'active-result');
  assert.deepEqual(submissions, ['active-media']);
  assert.equal(downloads.length, 1);
});
