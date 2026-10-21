// TurboFlow 2.3.8's project-page verification route needs replaceable bindings.
// This must run in MAIN at document_start, before Flow defines these properties.
(() => {
  if (location.hostname !== 'flow.google.com' || window.__bridge238VerificationBootstrap) return;
  window.__bridge238VerificationBootstrap = true;
  const defineProperty = Object.defineProperty;
  const defineProperties = Object.defineProperties;
  function descriptor(target, key, value) {
    if (!value) return value;
    if ((target === window && key === 'grecaptcha')
      || (key === 'enterprise' && typeof value.value?.execute === 'function')
      || (key === 'execute' && typeof value.value === 'function')) {
      const updated = { ...value, configurable: true };
      // Accessor descriptors cannot also have a writable flag.
      if ('value' in value || 'writable' in value) updated.writable = true;
      if (key === 'enterprise') updated.value = { ...value.value };
      return updated;
    }
    return value;
  }
  Object.defineProperty = function (target, key, value) {
    return defineProperty.call(this, target, key, descriptor(target, key, value));
  };
  Object.defineProperties = function (target, values) {
    const updated = {};
    for (const key of Reflect.ownKeys(values)) {
      if (Object.prototype.propertyIsEnumerable.call(values, key)) {
        defineProperty(updated, key, { value: descriptor(target, key, values[key]), enumerable: true });
      }
    }
    return defineProperties.call(this, target, updated);
  };
})();
