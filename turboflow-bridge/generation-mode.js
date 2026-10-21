export const API_238 = 'api-2.3.8';

export function normalizeGenerationMode(value) {
  if (value === 'api-2.3.5.1') return API_238;
  return ['ui', 'api', API_238].includes(value) ? value : API_238;
}

export function generationModeLabel(value) {
  const mode = normalizeGenerationMode(value);
  return mode === 'ui' ? 'Flow UI' : mode === API_238 ? 'API-2.3.8' : 'API';
}
