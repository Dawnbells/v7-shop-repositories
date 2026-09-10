// Shared Google Flow site routing helpers.
// Google is migrating the legacy labs.google/fx app to flow.google.com, so every
// caller must treat both origins as the same Flow surface.

export const FLOW_HOME_URL = 'https://flow.google.com/';

export const FLOW_TAB_URL_PATTERNS = [
  'https://flow.google.com/*',
  'https://labs.google/fx/*',
];

const LEGACY_FLOW_PATH_RE = /^\/fx(?:\/[a-z]{2}(?:-[a-z]{2})?)?\/tools\/flow(?:\/|$)/i;
const PROJECT_PATH_RE = /(?:^|\/)project\/([a-z0-9-]+)/i;

export function isFlowUrl(value) {
  try {
    const url = new URL(value);
    if (url.protocol !== 'https:') return false;
    if (url.hostname === 'flow.google.com') return true;
    return url.hostname === 'labs.google' && LEGACY_FLOW_PATH_RE.test(url.pathname);
  } catch {
    return false;
  }
}

export function getFlowOrigin(value) {
  if (!isFlowUrl(value)) return null;
  return new URL(value).origin;
}

export function isModernFlowUrl(value) {
  return getFlowOrigin(value) === 'https://flow.google.com';
}

export function getProjectIdFromFlowUrl(value) {
  if (!isFlowUrl(value)) return null;
  const url = new URL(value);
  const match = (url.pathname + url.hash).match(PROJECT_PATH_RE);
  return match?.[1] || null;
}

export function buildFlowProjectUrl(value, projectId) {
  const origin = getFlowOrigin(value);
  if (!origin || !projectId) return null;
  const encodedProjectId = encodeURIComponent(projectId);
  if (origin === 'https://flow.google.com') {
    return origin + '/project/' + encodedProjectId;
  }
  return origin + '/fx/tools/flow/project/' + encodedProjectId;
}

export function buildFlowMediaRedirectUrl(value, mediaId) {
  const origin = getFlowOrigin(value);
  if (!origin || !mediaId) return null;
  // flow.google.com removed the legacy TRPC redirect endpoint. Its BOQ
  // generation response normally contains the downloadable image URL.
  if (origin === 'https://flow.google.com') return null;
  return origin + '/fx/api/trpc/media.getMediaUrlRedirect?name=' + encodeURIComponent(mediaId);
}
