// Own only the helper tab created by this extension, never a user's about tab.
const STORAGE_KEY = 'flowApi2351VerificationTab';
const ABOUT_URL = 'https://flow.google.com/about';
const SITE_KEY = '6LdsFiUsAAAAAIjVDZcuLhaHiDn5nnHVXVRQGeMV';
const BOOTSTRAP_VERSION = 2;

// Self-contained because Chrome serializes this function into the MAIN world.
export async function requestVerificationToken(siteKey) {
  return new Promise(resolve => {
    let done = false;
    let poll;
    let stage = 'script-load';
    let ownedScript;
    const allowedScript = value => /^https:\/\/www\.(google|gstatic)\.com\/recaptcha\//.test(String(value));
    const finish = value => {
      if (done) return;
      done = true;
      clearTimeout(timeout);
      clearTimeout(poll);
      window.removeEventListener?.('error', scriptError, true);
      document.removeEventListener?.('securitypolicyviolation', policyError);
      if (value.error) ownedScript?.remove();
      resolve(value);
    };
    const fail = reason => finish({ error: `Flow verification ${reason} (stage=${stage})` });
    const scriptError = event => {
      // Capture failures in both enterprise.js and its secondary gstatic bundle.
      const source = event.target?.src || event.filename;
      if (source && allowedScript(source)) fail(`script failed: ${event.message || 'resource could not load'}`);
    };
    const policyError = event => {
      if (allowedScript(event.blockedURI) || /trusted-types/.test(event.effectiveDirective || '')) {
        fail(`blocked by page policy: ${event.effectiveDirective || 'script-src'}`);
      }
    };
    const timeout = setTimeout(() => fail('timed out'), 30000);
    window.addEventListener?.('error', scriptError, true);
    document.addEventListener?.('securitypolicyviolation', policyError);
    try {
      if (!window.grecaptcha?.enterprise?.execute && !document.querySelector('script[data-tf-2351-rc]')) {
        let scriptUrl = 'https://www.google.com/recaptcha/enterprise.js?render=' + encodeURIComponent(siteKey);
        try {
          if (window.trustedTypes?.createPolicy) {
            window.__tf2351ScriptPolicy ||= window.trustedTypes.createPolicy('default', {
              createScriptURL(value) {
                // enterprise.js subsequently loads www.gstatic.com/recaptcha/
                // releases/.../recaptcha__<locale>.js through this policy.
                if (!allowedScript(value)) throw new Error('Unexpected verification script');
                return value;
              },
            });
            scriptUrl = window.__tf2351ScriptPolicy.createScriptURL(scriptUrl);
          }
        } catch {
          // If the page already owns the default policy, src assignment below
          // must still pass that policy; never override its restrictions.
        }
        ownedScript = document.createElement('script');
        const nonce = document.querySelector('script[nonce]')?.nonce;
        if (nonce) ownedScript.nonce = nonce;
        ownedScript.src = scriptUrl;
        ownedScript.async = true;
        ownedScript.setAttribute('data-tf-2351-rc', '1');
        ownedScript.onerror = () => fail('entry script could not load');
        (document.head || document.documentElement).appendChild(ownedScript);
      }
      const check = () => {
        if (done) return;
        const rc = window.grecaptcha?.enterprise;
        if (!rc?.execute || typeof rc.ready !== 'function') { poll = setTimeout(check, 250); return; }
        stage = 'ready';
        try {
          rc.ready(() => {
            if (done) return;
            stage = 'execute';
            Promise.resolve().then(() => rc.execute(siteKey, { action: 'IMAGE_GENERATION' }))
              .then(token => token ? finish({ token }) : fail('returned no token'),
                error => fail(`execution failed: ${error.message || String(error)}`));
          });
        } catch (error) { fail(`initialization failed: ${error.message}`); }
      };
      check();
    } catch (error) { fail(`initialization failed: ${error.message}`); }
  });
}

export function createVerificationSession(browser = globalThis.chrome) {
  let tokenQueue = Promise.resolve();
  let lifecycle = Promise.resolve();
  let active = 0;
  let releaseRequested = false;

  function exclusive(fn) {
    const next = lifecycle.then(fn);
    lifecycle = next.catch(() => {});
    return next;
  }

  async function ownedTab() {
    const saved = (await browser.storage.session.get(STORAGE_KEY))[STORAGE_KEY];
    const tabId = typeof saved === 'number' ? saved : saved?.tabId;
    if (!Number.isInteger(tabId)) return null;
    try {
      const tab = await browser.tabs.get(tabId);
      if ((tab.pendingUrl || tab.url) === ABOUT_URL) return { ...tab, bootstrapVersion: saved?.bootstrapVersion || 0 };
    } catch {}
    await browser.storage.session.remove(STORAGE_KEY);
    return null;
  }

  async function ensureTab() {
    return exclusive(async () => {
      let tab = await ownedTab();
      if (tab && tab.bootstrapVersion !== BOOTSTRAP_VERSION) {
        // Trusted Types policies cannot be replaced in a loaded document.
        // Retire only our recorded helper, retaining any user-owned tabs.
        await browser.tabs.remove(tab.id).catch(() => {});
        await browser.storage.session.remove(STORAGE_KEY);
        tab = null;
      }
      if (!tab) {
        tab = await browser.tabs.create({ url: ABOUT_URL, active: false });
        try { await browser.storage.session.set({ [STORAGE_KEY]: { tabId: tab.id, bootstrapVersion: BOOTSTRAP_VERSION } }); }
        catch (error) {
          await browser.tabs.remove(tab.id).catch(() => {});
          throw error;
        }
      }
      // Register before reading status so completion cannot fall between them.
      await new Promise((resolve, reject) => {
        const finish = (error) => {
          clearTimeout(timer);
          browser.tabs.onUpdated.removeListener(updated);
          browser.tabs.onRemoved.removeListener(removed);
          error ? reject(error) : resolve();
        };
        const updated = (id, change) => {
          if (id === tab.id && change.status === 'complete') finish();
        };
        const removed = (id) => {
          if (id === tab.id) finish(new Error('Flow verification tab was closed'));
        };
        const timer = setTimeout(() => finish(new Error('Flow verification page timed out')), 15000);
        browser.tabs.onUpdated.addListener(updated);
        browser.tabs.onRemoved.addListener(removed);
        browser.tabs.get(tab.id).then(current => {
          if ((current.pendingUrl || current.url) !== ABOUT_URL) finish(new Error('Flow verification page navigated away'));
          else if (current.status === 'complete') finish();
        }).catch(finish);
      });
      await browser.tabs.update(tab.id, { autoDiscardable: false }).catch(() => {});
      return tab.id;
    });
  }

  async function closeIfIdle() {
    return exclusive(async () => {
      if (!releaseRequested || active) return;
      const tab = await ownedTab();
      if (tab) await browser.tabs.remove(tab.id).catch(() => {});
      await browser.storage.session.remove(STORAGE_KEY);
      releaseRequested = false;
    });
  }

  async function withLease(run) {
    active++;
    try { return await run(); }
    finally {
      active--;
      // Cleanup must not turn a completed translation into a failed task.
      await closeIfIdle().catch(() => {});
    }
  }

  return {
    withLease,
    release() {
      releaseRequested = true;
      return closeIfIdle();
    },
    getToken() {
      // Include queued requests in the lease count, not just executing ones.
      return withLease(() => {
        const next = tokenQueue.then(async () => {
          try {
            const tabId = await ensureTab();
            const results = await browser.scripting.executeScript({
              target: { tabId }, world: 'MAIN',
              func: requestVerificationToken,
              args: [SITE_KEY],
            });
            const result = results?.[0]?.result;
            if (!result?.token) throw new Error(result?.error || 'Flow verification returned no token');
            return result.token;
          } catch (error) {
            throw Object.assign(new Error(error.message), { code: 'FLOW_VERIFICATION_REQUIRED' });
          }
        });
        tokenQueue = next.catch(() => {});
        return next;
      });
    },
  };
}
