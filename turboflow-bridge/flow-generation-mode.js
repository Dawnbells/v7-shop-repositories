import { API_2351, normalizeGenerationMode } from './generation-mode.js';
import { getApi2351 } from './flow-api-2.3.5.1.js';
import { translateImageViaApi } from './flow-image-translation.js';

// Both automatic tasks and the test action use this same mode dispatch.
export function translateImageForMode(mode, conn, task, runUi) {
  const selected = normalizeGenerationMode(mode);
  if (selected === 'ui') return runUi(conn, task);
  return translateImageViaApi(conn, task, selected === API_2351 ? getApi2351() : undefined);
}
