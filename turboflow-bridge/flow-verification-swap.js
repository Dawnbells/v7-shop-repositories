import { createVerificationSession } from './flow-verification-session.js';
import { isModernFlowUrl, getProjectIdFromFlowUrl } from './flow-sites.js';

const SITE_KEY = '6LdsFiUsAAAAAIjVDZcuLhaHiDn5nnHVXVRQGeMV';

// Self-contained: Chrome serializes this into the selected Flow project's MAIN world.
// The packaged Google library is the same release bundled by TurboFlow 2.3.8.
export async function requestSwapVerificationToken(siteKey, libraryUrl) {
  const state = window.__bridge238Verification ||= { execute: null, loading: null };
  const currentExecute = () => window.grecaptcha?.enterprise?.execute;
  const valid = () => typeof state.execute === 'function' && currentExecute() === state.execute;
  let timer;
  const timeout = new Promise(resolve => {
    timer = setTimeout(() => resolve({ error: 'Flow verification timed out; refresh the Flow page' }), 30000);
  });
  const run = async () => {
    if (!valid()) {
      if (!state.loading) {
        state.loading = new Promise((resolve, reject) => {
          const previous = currentExecute();
          let script;
          let poll;
          let finished = false;
          const finish = error => {
            if (finished) return;
            finished = true;
            clearTimeout(poll);
            clearTimeout(loadTimeout);
            if (error) { script?.remove(); reject(error); }
            else resolve();
          };
          const loadTimeout = setTimeout(() => finish(new Error(
            'Flow verification library timed out; refresh the Flow page',
          )), 12000);
          try {
            state.execute = null;
            delete window.grecaptcha;
            delete window.___grecaptcha_cfg;
            for (const element of document.querySelectorAll('iframe[src*="recaptcha"], script[src*="recaptcha"], script[data-bridge-238-rc]')) {
              element.remove();
            }
            if (currentExecute() === previous && previous) {
              throw new Error('Flow verification is locked; refresh the Flow page after reloading the extension');
            }
            let src = libraryUrl;
            if (window.trustedTypes?.createPolicy) {
              try {
                state.policy ||= window.trustedTypes.createPolicy('default', {
                  createScriptURL(value) {
                    if (value !== libraryUrl) throw new Error('Unexpected verification script');
                    return value;
                  },
                  // Required by the bundled reCAPTCHA renderer, as in 2.3.8.
                  createScript: value => value,
                  createHTML: value => value,
                });
                src = state.policy.createScriptURL(libraryUrl);
              } catch {
                // Let an existing page policy validate the assignment below.
              }
            }
            const config = window.___grecaptcha_cfg = {};
            window.grecaptcha = { enterprise: { ready(callback) { (config.fns ||= []).push(callback); } } };
            window.__recaptcha_api = 'https://www.google.com/recaptcha/enterprise/';
            config.enterprise = [true];
            config.render = [siteKey];
            config['anchor-ms'] = [20000];
            config['execute-ms'] = [30000];
            window.__google_recaptcha_client = true;
            script = document.createElement('script');
            const nonce = document.querySelector('script[nonce]')?.nonce;
            if (nonce) script.nonce = nonce;
            script.src = src;
            script.async = true;
            script.setAttribute('data-bridge-238-rc', '1');
            script.onerror = () => finish(new Error('Flow verification bundled library failed to load'));
            (document.head || document.documentElement).appendChild(script);
            const check = () => {
              if (finished) return;
              const execute = currentExecute();
              if (typeof execute === 'function' && execute !== previous) {
                state.execute = execute;
                finish();
              } else poll = setTimeout(check, 200);
            };
            check();
          } catch (error) { finish(error); }
        }).finally(() => { state.loading = null; });
      }
      await state.loading;
    }
    if (!valid()) throw new Error('Flow verification changed; refresh the Flow page');
    return new Promise((resolve, reject) => {
      window.grecaptcha.enterprise.ready(() => {
        Promise.resolve().then(() => window.grecaptcha.enterprise.execute(siteKey, { action: 'IMAGE_GENERATION' }))
          .then(token => token ? resolve({ token }) : reject(new Error('Flow verification returned no token')), reject);
      });
    });
  };
  try { return await Promise.race([run(), timeout]); }
  catch (error) { return { error: error.message || String(error) }; }
  finally { clearTimeout(timer); }
}

export function createSwapVerificationSession(browser = globalThis.chrome) {
  let queue = Promise.resolve();
  // Retire only a helper owned by the previous version; never close the project.
  const legacy = createVerificationSession(browser);
  let cleaned = false;
  return {
    withLease: run => run(),
    release: () => legacy.release(),
    getToken(tabId) {
      const next = queue.then(async () => {
        try {
          if (!cleaned) { await legacy.release(); cleaned = true; }
          const tab = await browser.tabs.get(tabId);
          const url = tab.pendingUrl || tab.url;
          if (!isModernFlowUrl(url) || !getProjectIdFromFlowUrl(url)) {
            throw new Error('Open a Google Flow project before translating');
          }
          const results = await browser.scripting.executeScript({
            target: { tabId }, world: 'MAIN', func: requestSwapVerificationToken,
            args: [SITE_KEY, browser.runtime.getURL('rc-lib.js')],
          });
          const result = results?.[0]?.result;
          if (!result?.token) throw new Error(result?.error || 'Flow verification returned no token');
          return result.token;
        } catch (error) {
          throw Object.assign(new Error(error.message), { code: 'FLOW_VERIFICATION_REQUIRED' });
        }
      });
      queue = next.catch(() => {});
      return next;
    },
  };
}
