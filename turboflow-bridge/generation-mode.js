export const API_2351 = 'api-2.3.5.1';

export function normalizeGenerationMode(value) {
  return ['ui', 'api', API_2351].includes(value) ? value : API_2351;
}

export function generationModeLabel(value) {
  const mode = normalizeGenerationMode(value);
  return mode === 'ui' ? 'Flow UI' : mode === API_2351 ? 'API-2.3.5.1' : 'API';
}
