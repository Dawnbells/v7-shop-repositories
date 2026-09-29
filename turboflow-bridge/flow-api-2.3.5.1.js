// Image wire flow from TurboFlow 2.3.5.1, using local protocol constants only.
import { fetchImageAsBase64, getRecaptchaToken } from './flow-api.js';
import { buildFlowMediaRedirectUrl, isModernFlowUrl } from './flow-sites.js';
import {
  RPC_UPLOAD_IMAGE, RPC_BATCH_GENERATE_IMAGES, RPC_GET_MEDIA_URL,
  buildModernUploadRequest, buildModernGenerateRequest, buildBatchexecuteRequest,
  parseBatchexecuteResponse, createFlowRpcError,
} from './flow-modern-api.js';
import { createVerificationSession } from './flow-verification-session.js';

const API_BASE = 'https://aisandbox-pa.googleapis.com/v1';
const uuid = () => crypto.randomUUID().toUpperCase();

export function createApi2351({ browser = globalThis.chrome, verification = createVerificationSession(browser),
  legacyVerification = getRecaptchaToken, download = fetchImageAsBase64, now = Date.now } = {}) {
  const tokens = new Map();
  let lastSessionContext = null;
  browser.tabs.onUpdated?.addListener((_tabId, change) => {
    if (change.status === 'loading' || change.url) tokens.clear();
  });
  browser.tabs.onRemoved?.addListener(() => tokens.clear());

  async function getSessionToken(tabId) {
    const tab = await browser.tabs.get(tabId);
    const key = `${tabId}:${tab.url}`;
    if (key !== lastSessionContext) tokens.clear();
    lastSessionContext = key;
    const cached = tokens.get(key);
    if (cached && now() - cached.at < 300000) return cached.token;
    const results = await browser.scripting.executeScript({
      target: { tabId }, world: 'MAIN',
      func: async () => {
        const response = await fetch('/fx/api/auth/session', { credentials: 'include', signal: AbortSignal.timeout(30000) });
        if (!response.ok) return null;
        return (await response.json()).access_token || null;
      },
    });
    const token = results?.[0]?.result;
    if (!token) throw Object.assign(new Error('Flow session expired; log into Flow again'), { code: 'FLOW_AUTHENTICATION_FAILED' });
    tokens.set(key, { token, at: now() });
    return token;
  }

  async function modern(tabId) {
    return isModernFlowUrl((await browser.tabs.get(tabId)).url);
  }

  // The first injection reports dispatch, not completion. Each request owns
  // a separate pending response so the existing scheduler can overlap results.
  async function request(tabId, { rpcId, url, payload, token, onSubmitted, beforeSubmit }) {
    beforeSubmit?.();
    const results = await browser.scripting.executeScript({
      target: { tabId }, world: 'MAIN',
      func: async (rpc, restUrl, body, bearer, track) => {
        try {
          let url = restUrl;
          let headers;
          if (rpc) {
            const wiz = window.WIZ_global_data || {};
            if (!wiz.SNlM0e || !wiz.cfb2h) return { error: 'Flow session is not ready; refresh the project page', status: 401 };
            const account = window.location.pathname.match(/^\/u\/(\d+)\//);
            url = new URL(`${account ? '/u/' + account[1] : ''}/_/AiSandboxAngularFrontend/data/batchexecute`, window.location.origin);
            url.search = new URLSearchParams({ rpcids: rpc, 'source-path': window.location.pathname,
              bl: wiz.cfb2h, 'f.sid': wiz.FdrFJe || '', hl: 'en',
              _reqid: String(Math.floor(900000 * Math.random())), rt: 'c' }).toString();
            body = new URLSearchParams({ 'f.req': body, at: wiz.SNlM0e }).toString();
            headers = { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8', 'X-Same-Domain': '1' };
          } else {
            headers = { 'Content-Type': 'text/plain;charset=UTF-8', Authorization: 'Bearer ' + bearer };
          }
          const pending = fetch(String(url), { method: 'POST', headers, body,
            ...(rpc ? { credentials: 'include' } : {}), signal: AbortSignal.timeout(120000) });
          const submittedAt = Date.now();
          const completion = pending.then(async response => ({
            status: response.status, ok: response.ok, text: await response.text(),
          })).catch(error => ({ error: error.message }));
          if (!track) return await completion;
          const requestKey = crypto.randomUUID();
          window.__turboFlow2351Requests ||= new Map();
          window.__turboFlow2351Requests.set(requestKey, completion);
          return { requestKey, submittedAt };
        } catch (error) { return { error: error.message }; }
      },
      args: [rpcId || null, url || null, rpcId ? buildBatchexecuteRequest(rpcId, payload) : JSON.stringify(payload), token || null, !!onSubmitted],
    });
    let result = results?.[0]?.result;
    if (result?.requestKey) {
      // Always drain the page request, even if a scheduler callback throws.
      let callbackError;
      try { onSubmitted({ submittedAt: result.submittedAt }); } catch (error) { callbackError = error; }
      const completed = await browser.scripting.executeScript({
        target: { tabId }, world: 'MAIN',
        func: async key => {
          const pending = window.__turboFlow2351Requests;
          if (!pending?.has(key)) return { error: 'Flow request was lost after page navigation' };
          try { return await pending.get(key); }
          finally { pending.delete(key); }
        },
        args: [result.requestKey],
      });
      if (callbackError) throw callbackError;
      result = completed?.[0]?.result;
    }
    if (!result) throw new Error('Flow script execution failed');
    if (result.error || !result.ok) {
      let message = result.error || result.text || 'Flow request failed';
      let reason = '';
      let apiStatus = null;
      if (!rpcId && result.text) {
        try {
          const data = JSON.parse(result.text).error;
          apiStatus = data?.status;
          reason = data?.details?.find(item => item?.['@type'] === 'type.googleapis.com/google.rpc.ErrorInfo')?.reason || '';
          message = `${data?.message || message}${reason ? ' [' + reason + ']' : ''}`;
        } catch {}
      }
      if (!rpcId && url?.endsWith('/flow/uploadImage') && apiStatus === 'INVALID_ARGUMENT') {
        throw Object.assign(new Error('Upload rejected by content policy: ' + message), {
          code: 'FLOW_UPLOAD_POLICY_REJECTED', apiStatus, reason: reason || apiStatus, httpStatus: result.status,
        });
      }
      if (result.status === 401) {
        tokens.clear();
        throw Object.assign(new Error(message), { code: 'FLOW_AUTHENTICATION_FAILED', httpStatus: 401 });
      }
      if (rpcId && [403, 429].includes(result.status)) {
        throw Object.assign(createFlowRpcError(rpcId, result.status === 429 ? 8 : 7, message), { httpStatus: result.status });
      }
      const code = result.status === 429 ? (reason === 'DAILY_QUOTA_REACHED' ? 'DAILY_QUOTA_REACHED' : 'FLOW_RESOURCE_EXHAUSTED')
        : result.status === 403 ? (/recaptcha|captcha|unusual.activity/i.test(message) ? 'RECAPTCHA_BLOCKED' : 'GOOGLE_BLOCKED') : undefined;
      const prefix = code === 'RECAPTCHA_BLOCKED' ? 'reCAPTCHA blocked'
        : code === 'GOOGLE_BLOCKED' ? 'Blocked by Google (403)' : `HTTP ${result.status || 'network'}`;
      throw Object.assign(new Error(`${prefix}: ${message}`), { ...(code ? { code } : {}), httpStatus: result.status });
    }
    return rpcId ? parseBatchexecuteResponse(result.text, rpcId) : JSON.parse(result.text);
  }

  async function uploadImageToFlow(tabId, options) {
    options.beforeSubmit?.();
    if (await modern(tabId)) {
      const recaptchaToken = await verification.getToken();
      const payload = buildModernUploadRequest({ base64: options.base64, fileName: options.fileName,
        mimeType: options.mimeType, projectId: options.pid, recaptchaToken,
        workflowIdSeed: uuid(), mediaIdSeed: uuid() });
      const data = await request(tabId, { rpcId: RPC_UPLOAD_IMAGE, payload, beforeSubmit: options.beforeSubmit });
      const mediaId = data?.[0]?.[0];
      if (!mediaId) throw new Error('No mediaId in Flow upload response');
      return mediaId;
    }
    const token = await getSessionToken(tabId);
    const data = await request(tabId, { url: `${API_BASE}/flow/uploadImage`, token,
      beforeSubmit: options.beforeSubmit,
      payload: { clientContext: { projectId: options.pid, tool: 'PINHOLE' }, fileName: options.fileName,
        imageBytes: options.base64, isHidden: false, isUserUploaded: true, mimeType: options.mimeType } });
    if (!data?.media?.name) throw new Error('No mediaId in Flow upload response');
    return data.media.name;
  }

  async function generateWithReference(tabId, options) {
    options.beforeSubmit?.();
    const seed = Math.floor(Math.random() * 300000);
    const batchId = uuid();
    if (await modern(tabId)) {
      const recaptchaToken = await verification.getToken();
      const payload = buildModernGenerateRequest({ ...options, projectId: options.pid,
        recaptchaToken, seed, batchId, requestId: uuid(), clientMediaId: uuid() });
      const data = await request(tabId, { rpcId: RPC_BATCH_GENERATE_IMAGES, payload,
        onSubmitted: options.onSubmitted, beforeSubmit: options.beforeSubmit });
      const media = data?.[0]?.[0];
      if (!media?.[0]) throw new Error('No media in Flow generation response');
      return { mediaId: media[0], workflowId: media[2] || null, fifeUrl: media[6]?.[0]?.[13] || null };
    }
    const token = await getSessionToken(tabId);
    const recaptchaToken = await legacyVerification(tabId, 'IMAGE_GENERATION');
    if (!recaptchaToken) throw Object.assign(new Error('Flow verification is not ready'), { code: 'FLOW_VERIFICATION_REQUIRED' });
    const context = { projectId: options.pid, tool: 'PINHOLE', sessionId: ';' + now() + Math.random(),
      recaptchaContext: { applicationType: 'RECAPTCHA_APPLICATION_TYPE_WEB', token: recaptchaToken } };
    const payload = { clientContext: context, mediaGenerationContext: { batchId }, useNewMedia: true,
      requests: [{ clientContext: context, imageModelName: options.model || 'NARWHAL',
        imageAspectRatio: options.aspectRatio || 'IMAGE_ASPECT_RATIO_LANDSCAPE', seed,
        imageInputs: [{ imageInputType: 'IMAGE_INPUT_TYPE_REFERENCE', name: options.referenceMediaId }],
        structuredPrompt: { parts: [{ text: options.prompt }] } }] };
    const data = await request(tabId, { url: `${API_BASE}/projects/${encodeURIComponent(options.pid)}/flowMedia:batchGenerateImages`,
      token, payload, onSubmitted: options.onSubmitted, beforeSubmit: options.beforeSubmit });
    const workflow = data?.workflows?.find(item => item?.metadata?.primaryMediaId);
    const mediaId = workflow?.metadata?.primaryMediaId || data?.media?.[0]?.name;
    const media = data?.media?.find(item => item.name === mediaId) || data?.media?.[0];
    if (!mediaId) throw new Error('No mediaId in Flow generation response');
    return { mediaId, workflowId: workflow?.name || null, fifeUrl: media?.image?.generatedImage?.fifeUrl || null };
  }

  async function resolveFlowImageUrl(tabId, generation, flowUrl) {
    if (generation.fifeUrl) return generation.fifeUrl;
    if (!await modern(tabId)) return buildFlowMediaRedirectUrl(flowUrl, generation.mediaId);
    const media = await request(tabId, { rpcId: RPC_GET_MEDIA_URL, payload: [generation.mediaId] });
    const url = media?.[5]?.[10] || media?.[6]?.[0]?.[13];
    return typeof url === 'string' && url.startsWith('https://') ? url : null;
  }

  return { getSessionToken, uploadImageToFlow, generateWithReference, resolveFlowImageUrl,
    fetchImageAsBase64: download, withTranslation: verification.withLease, release: verification.release };
}

let adapter;
export function getApi2351() { return adapter ||= createApi2351(); }
export function releaseApi2351() { return getApi2351().release(); }
