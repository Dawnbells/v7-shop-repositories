export const API_2351 = 'api-2.3.5.1';

export function normalizeGenerationMode(value) {
  return value === 'ui' || value === API_2351 ? value : 'api';
}

export function generationModeLabel(value) {
  const mode = normalizeGenerationMode(value);
  return mode === 'ui' ? 'Flow UI' : mode === API_2351 ? API_2351 : 'API';
}
