import {
  getSessionToken,
  uploadImageToFlow,
  generateWithReference,
  resolveFlowImageUrl,
  fetchImageAsBase64,
} from './flow-api.js';

const defaultApi = { getSessionToken, uploadImageToFlow, generateWithReference, resolveFlowImageUrl, fetchImageAsBase64 };

// Keep every upload and generation result scoped to its own task. No DOM tile
// matching or shared "latest image" state is involved in the API pipeline.
export async function translateImageViaApi(conn, task, api = defaultApi) {
  if (!conn?.connected || !conn.tabId || !conn.projectId) throw new Error('Flow is not connected');
  const input = String(task.imageBase64 || '');
  const match = input.match(/^data:(image\/[\w.+-]+);base64,([\s\S]+)$/i);
  const base64 = match ? match[2] : input;
  if (!base64 || (input.startsWith('data:') && !match)) throw new Error('A base64 source image is required');
  const token = conn.transport === 'boq' ? null : await api.getSessionToken(conn.tabId);
  task.beforeSubmit?.();
  const referenceMediaId = await api.uploadImageToFlow(conn.tabId, {
    base64,
    mimeType: match?.[1] || task.mimeType || 'image/png',
    fileName: task.fileName || 'source.png',
    pid: conn.projectId,
    token,
  });
  task.beforeSubmit?.();
  const generation = await api.generateWithReference(conn.tabId, {
    prompt: task.prompt,
    referenceMediaId,
    onSubmitted: task.onSubmitted,
    beforeSubmit: task.beforeSubmit,
    aspectRatio: task.aspectRatio,
    model: task.model,
    pid: conn.projectId,
    token,
  });
  // Once submitted, always drain generation, download and reporting even when
  // another task has stopped new submissions due to quota exhaustion.
  task.onPhase?.('downloading_result');
  const resultUrl = await api.resolveFlowImageUrl(conn.tabId, generation, conn.flowUrl);
  if (!resultUrl) throw new Error('Flow API returned no downloadable image');
  const image = await api.fetchImageAsBase64(conn.tabId, resultUrl);
  if (!image?.dataUrl) throw new Error('[DOWNLOAD_FAILED] Flow API image could not be read');
  return { resultDataUrl: image.dataUrl, resultUrl, referenceMediaId, mediaId: generation.mediaId, workflowId: generation.workflowId };
}
