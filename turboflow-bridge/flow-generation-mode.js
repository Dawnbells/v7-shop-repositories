import { API_238, normalizeGenerationMode } from './generation-mode.js';
import { getApi238 } from './flow-api-2.3.8.js';
import { translateImageViaApi } from './flow-image-translation.js';

// Both automatic tasks and the test action use this same mode dispatch.
export function translateImageForMode(mode, conn, task, runUi) {
  const selected = normalizeGenerationMode(mode);
  if (selected === 'ui') return runUi(conn, task);
  return translateImageViaApi(conn, task, selected === API_238 ? getApi238() : undefined);
}
