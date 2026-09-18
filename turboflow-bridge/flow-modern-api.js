// flow-modern-api.js — flow.google.com BOQ/batchexecute adapter
//
// The new Flow frontend no longer exposes the legacy /fx/api/trpc session API.
// It sends protobuf-as-JSON arrays through the same-origin Google BOQ transport.

export const MODERN_FLOW_ORIGIN = 'https://flow.google.com';
export const RPC_UPLOAD_IMAGE = 'maseQ';
export const RPC_BATCH_GENERATE_IMAGES = 'ogiZ0b';
export const RPC_GET_MEDIA_URL = 'uurnC';
export const RPC_GET_PROJECT_CONTENTS = 'Zzl0ze';

const ASPECT_RATIO_ENUM = Object.freeze({
  IMAGE_ASPECT_RATIO_SQUARE: 1,
  IMAGE_ASPECT_RATIO_PORTRAIT: 2,
  IMAGE_ASPECT_RATIO_LANDSCAPE: 3,
  IMAGE_ASPECT_RATIO_PORTRAIT_THREE_FOUR: 4,
  IMAGE_ASPECT_RATIO_LANDSCAPE_FOUR_THREE: 5,
});

const IMAGE_MODEL_ENUM = Object.freeze({
  GEM_PIX_2: 25,
  NARWHAL: 29,
  IMAGEN_3_5: 21,
});

function sparseMessage(lastField) {
  return new Array(lastField).fill(null);
}

export function modernAspectRatioEnum(value) {
  return ASPECT_RATIO_ENUM[value] || ASPECT_RATIO_ENUM.IMAGE_ASPECT_RATIO_LANDSCAPE;
}

export function modernImageModelEnum(value) {
  return IMAGE_MODEL_ENUM[value] || IMAGE_MODEL_ENUM.NARWHAL;
}

export function buildModernClientContext(projectId, recaptchaToken) {
  const context = sparseMessage(11);
  context[1] = 22; // Tool.PINHOLE
  context[5] = projectId;
  context[10] = [recaptchaToken, 1]; // token, RECAPTCHA_APPLICATION_TYPE_WEB
  return context;
}

export function buildModernUploadRequest({
  base64,
  fileName,
  mimeType,
  projectId,
  recaptchaToken,
}) {
  const request = sparseMessage(9);
  request[0] = buildModernClientContext(projectId, recaptchaToken);
  request[1] = base64;
  request[2] = mimeType;
  request[3] = 1; // isUserUploaded
  request[8] = fileName;
  return request;
}

export function buildModernGenerateRequest({
  prompt,
  referenceMediaId,
  aspectRatio,
  projectId,
  recaptchaToken,
  model = 'NARWHAL',
  batchId,
  seed,
  requestId = crypto.randomUUID(),
  clientMediaId = crypto.randomUUID(),
}) {
  const context = buildModernClientContext(projectId, recaptchaToken);

  const imageInput = sparseMessage(5);
  imageInput[0] = referenceMediaId;
  imageInput[4] = 1; // IMAGE_INPUT_TYPE_REFERENCE

  const request = sparseMessage(14);
  request[2] = [imageInput];
  request[3] = seed;
  request[4] = modernAspectRatioEnum(aspectRatio);
  // Requests use the symbolic name; numeric model enums occur in responses.
  request[5] = Object.hasOwn(IMAGE_MODEL_ENUM, model) ? model : 'NARWHAL';
  request[7] = context;
  request[8] = [[[prompt]]]; // StructuredPrompt.parts[].text
  request[12] = requestId;
  request[13] = clientMediaId;

  const outer = sparseMessage(5);
  outer[1] = [request];
  outer[2] = 1; // useNewMedia
  outer[3] = context;
  outer[4] = [batchId];
  return outer;
}

export function buildBatchexecuteRequest(rpcId, payload) {
  const request = [[[rpcId, JSON.stringify(payload), null, 'generic']]];
  return JSON.stringify(request);
}

export function buildModernGetMediaUrlRequest(mediaName) {
  return [normalizeModernMediaName(mediaName)];
}

export function buildModernGetProjectContentsRequest(projectId) {
  return [projectId];
}

function visitArrays(value, visitor) {
  if (!Array.isArray(value)) return false;
  if (visitor(value)) return true;
  for (const item of value) {
    if (visitArrays(item, visitor)) return true;
  }
  return false;
}

export function parseBatchexecuteResponse(text, rpcId) {
  const clean = String(text || '').replace(/^\)\]\}'\s*/, '');
  const jsonLines = clean
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.startsWith('['));

  let rpcPayload;
  let rpcError;
  for (const line of jsonLines) {
    let chunk;
    try {
      chunk = JSON.parse(line);
    } catch {
      continue;
    }
    visitArrays(chunk, (entry) => {
      if (entry[0] === 'wrb.fr' && entry[1] === rpcId) {
        rpcPayload = entry[2];
        if (rpcPayload == null) rpcError = entry;
        return true;
      }
      if (entry[0] === 'er' && (!entry[1] || entry[1] === rpcId)) {
        rpcError = entry;
      }
      return false;
    });
    if (rpcPayload !== undefined) break;
  }

  if (rpcPayload == null) {
    const status = rpcError?.[0] === 'wrb.fr' ? rpcError?.[5]?.[0] : rpcError?.[2];
    const error = new Error('Flow RPC ' + rpcId + ' failed (RPC status ' + (status ?? 'unknown') + ')');
    error.code = status === 16 ? 'FLOW_AUTHENTICATION_FAILED' : 'FLOW_RPC_REJECTED';
    error.rpcId = rpcId;
    error.rpcStatus = status;
    throw error;
  }
  if (typeof rpcPayload !== 'string') return rpcPayload;
  try {
    return JSON.parse(rpcPayload);
  } catch {
    throw new Error('Flow RPC ' + rpcId + ' returned malformed JSON');
  }
}

function findString(value, predicate) {
  if (typeof value === 'string') return predicate(value) ? value : null;
  if (!Array.isArray(value)) return null;
  for (const item of value) {
    const found = findString(item, predicate);
    if (found) return found;
  }
  return null;
}

function isMediaName(value) {
  return /(^|\/)media\//i.test(value) || /^media[-_:]/i.test(value);
}

export function normalizeModernMediaName(value) {
  const name = String(value || '').trim();
  if (!name) return '';
  if (/^(?:projects\/[^/]+\/)?media\//i.test(name)) return name;
  return 'media/' + name;
}

function mediaNamesEqual(left, right) {
  if (!left || !right) return false;
  const normalize = (value) => String(value).replace(/^projects\/[^/]+\//i, '').replace(/^media\//i, '');
  return normalize(left) === normalize(right);
}

function isGeneratedImageUrl(value) {
  if (!/^https:\/\//i.test(value)) return false;
  return /flow-content\.google|googleusercontent\.com|googleapis\.com|gstatic\.com|ggpht\.com/i.test(value);
}

export function extractUploadedMediaName(payload) {
  const direct = payload?.[0]?.[0];
  if (typeof direct === 'string' && direct) return direct;
  return findString(payload, isMediaName);
}

export function extractModernGenerationResult(payload) {
  const mediaList = Array.isArray(payload?.[0]) ? payload[0] : [];
  const workflowList = Array.isArray(payload?.[1]) ? payload[1] : [];
  const workflow = workflowList.find((item) => typeof item?.[0] === 'string') || null;
  const primaryMediaId = typeof workflow?.[3]?.[4] === 'string'
    ? workflow[3][4]
    : typeof workflow?.[3]?.[0] === 'string' ? workflow[3][0] : null;
  const matchedMedia = primaryMediaId
    ? mediaList.find((media) => mediaNamesEqual(media?.[0], primaryMediaId))
    : null;
  const directMedia = matchedMedia
    || mediaList.find((media) => typeof media?.[0] === 'string')
    || null;
  return {
    fifeUrl: findString(payload, isGeneratedImageUrl),
    mediaId: directMedia?.[0] || null,
    primaryMediaId,
    workflowId: workflow?.[0] || null,
  };
}

export function extractProjectGenerationResult(payload, workflowId) {
  const mediaList = Array.isArray(payload?.[2]) ? payload[2] : [];
  const workflowList = Array.isArray(payload?.[1]) ? payload[1] : [];
  const workflow = workflowList.find((item) => item?.[0] === workflowId);
  const primaryMediaId = typeof workflow?.[3]?.[4] === 'string'
    ? workflow[3][4]
    : typeof workflow?.[3]?.[0] === 'string' ? workflow[3][0] : null;
  const media = primaryMediaId
    ? mediaList.find((item) => mediaNamesEqual(item?.[0], primaryMediaId))
    : null;
  return {
    mediaId: media?.[0] || null,
    primaryMediaId,
    workflowId: workflow?.[0] || workflowId || null,
  };
}

export function extractModernMediaUrl(payload) {
  const direct = findString(payload, isGeneratedImageUrl);
  if (direct) return direct;
  if (
    Array.isArray(payload)
    && typeof payload[0] === 'string'
    && /^image\/[a-z0-9.+-]+$/i.test(payload[0])
    && typeof payload[1] === 'string'
  ) {
    return 'data:' + payload[0] + ';base64,' + payload[1];
  }

  if (typeof globalThis.atob !== 'function') return null;
  let decodedUrl = null;
  findString(payload, (value) => {
    if (!/^[A-Za-z0-9+/]+={0,2}$/.test(value)) return false;
    try {
      const decoded = globalThis.atob(value);
      if (isGeneratedImageUrl(decoded)) {
        decodedUrl = decoded;
        return true;
      }
    } catch {}
    return false;
  });
  return decodedUrl;
}
