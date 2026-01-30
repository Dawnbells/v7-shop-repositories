export function useMainDomain() {
  if (typeof globalThis !== 'undefined' && globalThis.location) {
    return globalThis.location.origin
  }
  if (typeof globalThis !== 'undefined' && globalThis.location) {
    if (globalThis.location.origin) {
      return globalThis.location.origin
    }
    return `${globalThis.location.protocol}//${globalThis.location.host}`
  }
  return ''
}
