import test from 'node:test';
import assert from 'node:assert/strict';

import {
  RPC_BATCH_GENERATE_IMAGES,
  RPC_UPLOAD_IMAGE,
  buildBatchexecuteRequest,
  buildModernGenerateRequest,
  buildModernGetProjectContentsRequest,
  buildModernUploadRequest,
  extractModernGenerationResult,
  extractModernMediaUrl,
  extractProjectGenerationResult,
  extractUploadedMediaName,
  modernAspectRatioEnum,
  modernImageModelEnum,
  normalizeModernMediaName,
  parseBatchexecuteResponse,
} from '../flow-modern-api.js';

test('rejects an HTTP-200 envelope whose RPC payload is null', () => {
  const response = ")]}'\n" + JSON.stringify([['wrb.fr', RPC_BATCH_GENERATE_IMAGES, null, null, null, [7]]]);
  assert.throws(() => parseBatchexecuteResponse(response, RPC_BATCH_GENERATE_IMAGES), /ogiZ0b/);
});

test('decodes all canonical RPC failures with symbolic names and server messages', () => {
  const names = ['CANCELLED', 'UNKNOWN', 'INVALID_ARGUMENT', 'DEADLINE_EXCEEDED',
    'NOT_FOUND', 'ALREADY_EXISTS', 'PERMISSION_DENIED', 'RESOURCE_EXHAUSTED',
    'FAILED_PRECONDITION', 'ABORTED', 'OUT_OF_RANGE', 'UNIMPLEMENTED',
    'INTERNAL', 'UNAVAILABLE', 'DATA_LOSS', 'UNAUTHENTICATED'];
  for (const [index, name] of names.entries()) {
    const status = index + 1;
    for (const rpcId of [RPC_UPLOAD_IMAGE, RPC_BATCH_GENERATE_IMAGES]) {
      for (const entry of [
        ['wrb.fr', rpcId, null, null, null, [status, 'server explanation']],
        ['er', rpcId, String(status), 'server explanation'],
      ]) {
        assert.throws(() => parseBatchexecuteResponse(JSON.stringify([entry]), rpcId), error => {
          assert.equal(error.rpcStatus, status);
          assert.equal(error.rpcStatusName, name);
          assert.equal(error.rpcId, rpcId);
          assert.equal(error.code, status === 8 ? 'FLOW_RESOURCE_EXHAUSTED'
            : status === 16 ? 'FLOW_AUTHENTICATION_FAILED' : 'FLOW_RPC_REJECTED');
          assert.ok(error.message.includes(name));
          assert.ok(error.message.includes('server explanation'));
          return true;
        });
      }
    }
  }
});

test('missing status is not treated as success or quota exhaustion', () => {
  assert.throws(() => parseBatchexecuteResponse('[]', RPC_UPLOAD_IMAGE), error => {
    assert.equal(error.rpcStatus, undefined);
    assert.equal(error.code, 'FLOW_RPC_REJECTED');
    assert.match(error.message, /unknown: UNRECOGNIZED_STATUS/);
    return true;
  });
});

test('builds the protobuf JSON upload request used by flow.google.com', () => {
  const request = buildModernUploadRequest({
    base64: 'YWJj',
    fileName: 'source.png',
    mimeType: 'image/png',
    projectId: 'project-123',
    recaptchaToken: 'captcha-token',
  });

  assert.equal(
    JSON.stringify(request),
    '[[null,22,null,null,null,"project-123",null,null,null,null,["captcha-token",1]],"YWJj","image/png",1,null,null,null,null,"source.png"]',
  );
});

test('builds the protobuf JSON batch generation request used by flow.google.com', () => {
  const request = buildModernGenerateRequest({
    prompt: 'translate this',
    referenceMediaId: 'media/abc',
    aspectRatio: 'IMAGE_ASPECT_RATIO_LANDSCAPE',
    projectId: 'project-123',
    recaptchaToken: 'captcha-token',
    model: 'NARWHAL',
    batchId: 'batch-123',
    seed: 12345,
    requestId: 'request-123',
    clientMediaId: 'client-media-123',
  });

  assert.equal(
    JSON.stringify(request),
    '[null,[[null,null,[["media/abc",null,null,null,1]],12345,3,"NARWHAL",null,[null,22,null,null,null,"project-123",null,null,null,null,["captcha-token",1]],[[["translate this"]]],null,null,null,"request-123","client-media-123"]],1,[null,22,null,null,null,"project-123",null,null,null,null,["captcha-token",1]],["batch-123"]]',
  );
  assert.equal(modernImageModelEnum('GEM_PIX_2'), 25);
  assert.equal(modernImageModelEnum('NARWHAL'), 29);
  assert.equal(modernImageModelEnum('IMAGEN_3_5'), 21);
});

test('maps every supported image aspect ratio to the Flow enum', () => {
  assert.equal(modernAspectRatioEnum('IMAGE_ASPECT_RATIO_SQUARE'), 1);
  assert.equal(modernAspectRatioEnum('IMAGE_ASPECT_RATIO_PORTRAIT'), 2);
  assert.equal(modernAspectRatioEnum('IMAGE_ASPECT_RATIO_LANDSCAPE'), 3);
  assert.equal(modernAspectRatioEnum('IMAGE_ASPECT_RATIO_PORTRAIT_THREE_FOUR'), 4);
  assert.equal(modernAspectRatioEnum('IMAGE_ASPECT_RATIO_LANDSCAPE_FOUR_THREE'), 5);
  assert.equal(modernAspectRatioEnum('unknown'), 3);
});

test('builds and parses a BOQ batchexecute envelope', () => {
  const payload = [[['media/abc']]];
  const envelope = JSON.parse(buildBatchexecuteRequest(RPC_UPLOAD_IMAGE, payload));
  assert.equal(envelope[0][0][0], RPC_UPLOAD_IMAGE);
  assert.deepEqual(JSON.parse(envelope[0][0][1]), payload);

  const response = ")]}'\n\n123\n" + JSON.stringify([
    [['wrb.fr', RPC_UPLOAD_IMAGE, JSON.stringify(payload), null, null, null, 'generic']],
  ]);
  assert.deepEqual(parseBatchexecuteResponse(response, RPC_UPLOAD_IMAGE), payload);
});

test('extracts upload and generated media fields from protobuf arrays', () => {
  assert.equal(extractUploadedMediaName([['media/uploaded']]), 'media/uploaded');
  assert.equal(
    extractUploadedMediaName([
      ['d0a9bd9e-034f-49e8-b54d-6326adfa945a', 'project-id', 'workflow-id'],
      ['workflow-id', null, null, ['source.png', null, null, null, 'd0a9bd9e-034f-49e8-b54d-6326adfa945a']],
    ]),
    'd0a9bd9e-034f-49e8-b54d-6326adfa945a',
  );

  const generated = extractModernGenerationResult([
    [['media/generated', null, ['https://lh3.googleusercontent.com/result.png']]],
    [],
  ]);
  assert.equal(generated.mediaId, 'media/generated');
  assert.equal(generated.fifeUrl, 'https://lh3.googleusercontent.com/result.png');
  assert.equal(buildModernGetProjectContentsRequest('project-123')[0], 'project-123');

  const nanoBananaPro = extractModernGenerationResult([
    [[
      'generated-media-id',
      null,
      'workflow-id',
      null,
      null,
      null,
      [[null, 123, null, null, null, null, 1, 'Translate into English', 25, null, null,
        'workflow-id', null,
        'https://flow-content.google/image/generated-media-id?Expires=123&KeyName=test&Signature=test']],
    ]],
    [['workflow-id', null, null, ['generated-media-id']]],
  ]);
  assert.equal(
    nanoBananaPro.fifeUrl,
    'https://flow-content.google/image/generated-media-id?Expires=123&KeyName=test&Signature=test',
  );

  const workflowOnly = extractModernGenerationResult([
    [],
    [['workflow/abc', null, null, ['raw-primary-media-id']]],
  ]);
  assert.equal(workflowOnly.workflowId, 'workflow/abc');
  assert.equal(workflowOnly.primaryMediaId, 'raw-primary-media-id');
  assert.equal(workflowOnly.mediaId, null);

  const projectResult = extractProjectGenerationResult([
    null,
    [['workflow/abc', null, null, ['completed-media-id']]],
    [['media/completed-media-id']],
  ], 'workflow/abc');
  assert.equal(projectResult.mediaId, 'media/completed-media-id');
  assert.equal(normalizeModernMediaName('completed-media-id'), 'media/completed-media-id');
  assert.equal(normalizeModernMediaName('media/completed-media-id'), 'media/completed-media-id');
  assert.equal(
    extractModernMediaUrl([null, btoa('https://lh3.googleusercontent.com/signed.png')]),
    'https://lh3.googleusercontent.com/signed.png',
  );
  assert.equal(
    extractModernMediaUrl(['image/png', 'iVBORw0KGgo=']),
    'data:image/png;base64,iVBORw0KGgo=',
  );
  assert.equal(RPC_BATCH_GENERATE_IMAGES, 'ogiZ0b');
});
