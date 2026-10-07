import { API_2351 } from './generation-mode.js';

// The private preset is packaged for this installation but never committed.
export async function initializeDefaultConfig(storage, readPreset) {
  const stored = await storage.get(['services', 'generationMode', 'flowConcurrency']);
  const defaults = {};
  if (!Object.hasOwn(stored, 'services')) {
    const preset = await readPreset();
    if (preset?.baseUrl !== 'https://api.xyzdwd.com' || !String(preset?.token || '').trim()) {
      throw new Error('Private service preset is missing or invalid');
    }
    defaults.services = [{ baseUrl: preset.baseUrl, token: preset.token.trim(), enabled: true }];
  }
  if (!Object.hasOwn(stored, 'generationMode')) defaults.generationMode = API_2351;
  if (!Object.hasOwn(stored, 'flowConcurrency')) defaults.flowConcurrency = 4;
  if (Object.keys(defaults).length) await storage.set(defaults);
  return { ...stored, ...defaults };
}
