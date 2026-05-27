(function () {
  'use strict';

  const VERSION = 3;
  const previous = window.__turboFlowDomMethod;
  if (previous?.version === VERSION) return;
  if (previous?.listener) {
    try { chrome.runtime.onMessage.removeListener(previous.listener); } catch {}
  }

  const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
  const FILE_INJECT_GAP_MS = 500;
  const UPLOAD_SETTLE_MS = 3000;
  const PICKER_TIMEOUT_MS = 8000;
  const SEARCH_TIMEOUT_MS = 15000;
  const PICKER_CLOSE_TIMEOUT_MS = 8000;
  const RESULT_TIMEOUT_MS = 180000;
  const RESULT_SCAN_MS = 1000;
  const STEALTH_SHORT_PROMPT_LIMIT = 120;

  const MODEL_LABELS = {
    GEM_PIX_2: 'Nano Banana Pro',
    NARWHAL: 'Nano Banana 2',
    IMAGEN_3_5: 'Imagen 4',
    nano_banana_pro: 'Nano Banana Pro',
    nano_banana2: 'Nano Banana 2',
    imagen4: 'Imagen 4',
  };

  const ASPECT_CONFIG = {
    IMAGE_ASPECT_RATIO_LANDSCAPE: { icon: 'crop_16_9', label: 'Landscape' },
    IMAGE_ASPECT_RATIO_LANDSCAPE_FOUR_THREE: { icon: 'crop_landscape', label: 'Widescreen' },
    IMAGE_ASPECT_RATIO_SQUARE: { icon: 'crop_square', label: 'Square' },
    IMAGE_ASPECT_RATIO_PORTRAIT_THREE_FOUR: { icon: 'crop_portrait', label: 'Tall' },
    IMAGE_ASPECT_RATIO_PORTRAIT: { icon: 'crop_9_16', label: 'Portrait' },
    landscape: { icon: 'crop_16_9', label: 'Landscape' },
    widescreen: { icon: 'crop_landscape', label: 'Widescreen' },
    square: { icon: 'crop_square', label: 'Square' },
    tallscreen: { icon: 'crop_portrait', label: 'Tall' },
    portrait: { icon: 'crop_9_16', label: 'Portrait' },
  };

  const TYPO_NEIGHBORS = {
    a: ['q', 'w', 's', 'z'], b: ['v', 'g', 'h', 'n'], c: ['x', 'd', 'f', 'v'],
    d: ['s', 'e', 'r', 'f', 'c'], e: ['w', 'r', 'd'], f: ['d', 'r', 't', 'g', 'v'],
    g: ['f', 't', 'y', 'h', 'b'], h: ['g', 'y', 'u', 'j', 'n'], i: ['u', 'o', 'k'],
    j: ['h', 'u', 'i', 'k', 'n'], k: ['j', 'i', 'o', 'l'], l: ['k', 'o', 'p'],
    m: ['n', 'j', 'k'], n: ['b', 'h', 'j', 'm'], o: ['i', 'p', 'l', 'k'],
    p: ['o', 'l'], q: ['w', 'a'], r: ['e', 't', 'f'], s: ['a', 'w', 'e', 'd', 'z'],
    t: ['r', 'y', 'g'], u: ['y', 'i', 'h', 'j'], v: ['c', 'f', 'g', 'b'],
    w: ['q', 'e', 's'], x: ['z', 's', 'd', 'c'], y: ['t', 'u', 'g', 'h'], z: ['a', 's'],
  };
  const FAST_BIGRAMS = new Set(['th', 'he', 'in', 'er', 'an', 're', 'on', 'en', 'at', 'es', 'ti', 'or']);

  function randomBetween(min, max) {
    const a = Math.min(Number(min) || 0, Number(max) || 0);
    const b = Math.max(Number(min) || 0, Number(max) || 0);
    return a + Math.random() * (b - a);
  }

  async function randomDelay(task, label) {
    const min = Math.max(0, Number(task.delayMin || 0));
    const max = Math.max(0, Number(task.delayMax || min));
    if (max <= 0) return;
    const ms = Math.round(randomBetween(min, max) * 1000);
    console.log(`[TurboFlow DOM] Random delay before ${label}: ${ms}ms`);
    await sleep(ms);
  }

  function xPath(path) {
    try {
      return document.evaluate(path, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue;
    } catch (error) {
      console.warn('[TurboFlow DOM] XPath error:', path, error);
      return null;
    }
  }

  function visible(el) {
    if (!el) return false;
    const style = getComputedStyle(el);
    const rect = el.getBoundingClientRect();
    return style.display !== 'none' && style.visibility !== 'hidden' && style.opacity !== '0' && rect.width > 6 && rect.height > 6;
  }

  async function waitFor(fn, timeoutMs, intervalMs = 150) {
    const started = Date.now();
    while (Date.now() - started < timeoutMs) {
      const value = await fn();
      if (value) return value;
      await sleep(intervalMs);
    }
    return null;
  }

  function center(el) {
    const rect = el.getBoundingClientRect();
    return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 };
  }

  function clickDom(el) {
    if (!el) return false;
    el.scrollIntoView({ block: 'center', inline: 'center' });
    const { x, y } = center(el);
    const mouse = { bubbles: true, cancelable: true, clientX: x, clientY: y, button: 0 };
    el.dispatchEvent(new MouseEvent('mousedown', mouse));
    el.dispatchEvent(new MouseEvent('mouseup', mouse));
    el.dispatchEvent(new MouseEvent('click', mouse));
    return true;
  }

  function stealthClick(el) {
    if (!el) return false;
    el.scrollIntoView({ block: 'center', inline: 'center' });
    const rect = el.getBoundingClientRect();
    const x = rect.left + rect.width / 2 + (Math.random() - 0.5) * rect.width * 0.6;
    const y = rect.top + rect.height / 2 + (Math.random() - 0.5) * rect.height * 0.6;
    const base = { bubbles: true, cancelable: true, view: window, clientX: x, clientY: y, screenX: window.screenX + x, screenY: window.screenY + y, button: 0 };
    el.dispatchEvent(new PointerEvent('pointerdown', { ...base, pointerId: 1, pointerType: 'mouse', isPrimary: true, buttons: 1 }));
    el.dispatchEvent(new MouseEvent('mousedown', { ...base, buttons: 1 }));
    el.dispatchEvent(new PointerEvent('pointerup', { ...base, pointerId: 1, pointerType: 'mouse', isPrimary: true, buttons: 0 }));
    el.dispatchEvent(new MouseEvent('mouseup', { ...base, buttons: 0 }));
    el.dispatchEvent(new PointerEvent('click', { ...base, pointerId: 1, pointerType: 'mouse', isPrimary: true }));
    el.dispatchEvent(new MouseEvent('click', base));
    return true;
  }

  function click(el, task = {}) {
    return task.stealthMode ? stealthClick(el) : clickDom(el);
  }

  function pressEscape() {
    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', keyCode: 27, bubbles: true, cancelable: true, composed: true }));
  }

  function setNativeInputValue(input, value) {
    const descriptor = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value');
    if (descriptor?.set) descriptor.set.call(input, value);
    else input.value = value;
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.dispatchEvent(new Event('change', { bubbles: true }));
  }

  function dataUrlToFile(dataUrl, fileName, mimeType) {
    if (!dataUrl || typeof dataUrl !== 'string') return null;
    const parts = dataUrl.split(',');
    if (parts.length < 2) return null;
    const meta = parts[0];
    const base64 = parts[1];
    const mime = mimeType || meta.match(/:(.*?);/)?.[1] || 'image/png';
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    return new File([bytes], fileName || `upload_${Date.now()}.png`, { type: mime });
  }

  function findFileInput() {
    return document.querySelector('input[type="file"][accept*="image"]');
  }

  function findAddReferenceTrigger() {
    return xPath("//button[.//i[normalize-space(text())='add_2']]");
  }

  function findPickerDialog() {
    return Array.from(document.querySelectorAll('[role="dialog"][data-state="open"]'))
      .find((dialog) => dialog.querySelector('input[type="text"]')) || null;
  }

  async function waitForPickerDialog() {
    return await waitFor(findPickerDialog, PICKER_TIMEOUT_MS, 150);
  }

  async function waitForPickerClosed() {
    return !!(await waitFor(() => !findPickerDialog(), PICKER_CLOSE_TIMEOUT_MS, 200));
  }

  function findPickerImage(fileName) {
    const target = String(fileName || '').trim().toLowerCase();
    const images = Array.from(document.querySelectorAll('[data-testid="virtuoso-item-list"] img[alt]'));
    return images.find((img) => String(img.getAttribute('alt') || '').trim().toLowerCase() === target) || null;
  }

  async function waitForPickerImage(fileName) {
    return await waitFor(() => findPickerImage(fileName), SEARCH_TIMEOUT_MS, 300);
  }

  function closestClickable(el) {
    return el?.closest('button, [role="button"]') || el?.parentElement || el;
  }

  async function searchPicker(dialog, fileName, task) {
    const input = dialog.querySelector('input[type="text"]');
    if (!input) throw new Error('Image picker search input not found');
    if (task.stealthMode) await sleep(100 + Math.random() * 300);
    input.focus();
    setNativeInputValue(input, fileName || '');
    return await waitForPickerImage(fileName);
  }

  async function checkImagesInLibrary(fileNames, task) {
    const found = new Set();
    const trigger = findAddReferenceTrigger();
    if (!trigger) return found;

    click(trigger, task);
    const dialog = await waitForPickerDialog();
    if (!dialog) return found;

    for (const name of fileNames) {
      const input = dialog.querySelector('input[type="text"]');
      if (!input) break;
      setNativeInputValue(input, '');
      await sleep(120);
      const result = await searchPicker(dialog, name, task);
      if (result) found.add(name);
      await sleep(200);
    }

    pressEscape();
    await sleep(400);
    return found;
  }

  async function clearAttachedReferences(task) {
    const buttons = Array.from(document.querySelectorAll('button'));
    const clearButton = buttons.find((button) => {
      const icon = button.querySelector('i.google-symbols, i, mat-icon');
      return icon?.textContent?.trim() === 'close' && button.querySelector('span');
    });
    if (!clearButton) {
      console.log('[TurboFlow DOM] Reference area already clean');
      return false;
    }
    click(clearButton, task);
    await sleep(300);
    return true;
  }

  async function uploadAllImages(images, task) {
    if (!images.length) return true;
    const names = images.map((image, index) => image.name || `reference_${index + 1}.png`);
    const alreadyInLibrary = await checkImagesInLibrary(names, task);
    const missingCount = images.length - alreadyInLibrary.size;
    let injected = 0;

    for (let i = 0; i < images.length; i++) {
      const image = images[i];
      const name = names[i];
      if (alreadyInLibrary.has(name)) continue;
      const input = findFileInput();
      if (!input) throw new Error(`Image upload input not found for ${name}`);
      const file = dataUrlToFile(image.data, name, image.mimeType || 'image/png');
      if (!file) throw new Error(`Failed to prepare upload file: ${name}`);
      const dt = new DataTransfer();
      dt.items.add(file);
      input.files = dt.files;
      input.dispatchEvent(new Event('change', { bubbles: true }));
      injected++;
      if (injected < missingCount) await sleep(FILE_INJECT_GAP_MS);
    }

    if (injected > 0) await sleep(UPLOAD_SETTLE_MS);
    return true;
  }

  async function attachOneImage(fileName, task) {
    const trigger = findAddReferenceTrigger();
    if (!trigger) throw new Error('Image picker add_2 trigger not found');
    click(trigger, task);

    const dialog = await waitForPickerDialog();
    if (!dialog) throw new Error('Image picker did not open');
    await sleep(task.stealthMode ? 400 * (0.7 + Math.random() * 0.6) : 400);

    const result = await searchPicker(dialog, fileName, task);
    if (!result) {
      pressEscape();
      throw new Error(`Search result for ${fileName} not found`);
    }

    const row = closestClickable(result);
    if (!row) {
      pressEscape();
      throw new Error(`Search result row for ${fileName} not found`);
    }

    if (task.stealthMode) await sleep(150 + Math.random() * 200);
    click(row, task);
    if (!await waitForPickerClosed()) {
      pressEscape();
      await sleep(300);
    }
    await sleep(500);
  }

  async function attachAllImages(images, task) {
    for (let i = 0; i < images.length; i++) {
      const name = images[i].name || `reference_${i + 1}.png`;
      let lastError = null;
      for (let attempt = 1; attempt <= 4; attempt++) {
        try {
          await attachOneImage(name, task);
          lastError = null;
          break;
        } catch (error) {
          lastError = error;
          console.warn(`[TurboFlow DOM] Attach retry ${attempt}/4 for ${name}: ${error.message}`);
          pressEscape();
          await sleep(2500);
        }
      }
      if (lastError) throw lastError;
    }
  }

  async function applySettings(task) {
    const settings = {
      count: '1',
      model: task.model || 'NARWHAL',
      aspectRatio: task.aspectRatio || 'IMAGE_ASPECT_RATIO_LANDSCAPE',
    };

    const trigger = xPath("//button[@aria-haspopup='menu' and .//div[@data-type='button-overlay'] and text()[normalize-space() != '']]");
    if (!trigger) {
      console.warn('[TurboFlow DOM] Main settings trigger not found; continuing with current Flow settings');
      return false;
    }
    clickDom(trigger);
    await sleep(600);

    const imageTab = xPath("//button[@role='tab' and contains(@class,'flow_tab_slider_trigger') and .//i[normalize-space(text())='image']]");
    if (imageTab && imageTab.getAttribute('data-state') !== 'active') {
      clickDom(imageTab);
      await sleep(400);
    }

    const aspect = ASPECT_CONFIG[settings.aspectRatio] || ASPECT_CONFIG.IMAGE_ASPECT_RATIO_LANDSCAPE;
    const aspectTab = xPath(`//button[@role='tab' and contains(@class,'flow_tab_slider_trigger') and .//i[normalize-space(text())='${aspect.icon}']]`);
    if (aspectTab && aspectTab.getAttribute('data-state') !== 'active') {
      clickDom(aspectTab);
      await sleep(300);
    }

    const countTab = xPath("//button[@role='tab' and contains(@class,'flow_tab_slider_trigger') and normalize-space(text())='x1']");
    if (countTab && countTab.getAttribute('data-state') !== 'active') {
      clickDom(countTab);
      await sleep(300);
    }

    const modelLabel = MODEL_LABELS[settings.model] || MODEL_LABELS.NARWHAL;
    const modelTrigger = xPath("//div[@role='menu' and @data-state='open']//button[@aria-haspopup='menu' and .//div[@data-type='button-overlay']]");
    if (modelTrigger) {
      clickDom(modelTrigger);
      await sleep(500);
      const modelOption = xPath(`//div[@role='menuitem']//button[.//span[contains(normalize-space(text()),'${modelLabel}')]]`);
      if (modelOption) {
        clickDom(modelOption);
        await sleep(400);
      } else {
        console.warn(`[TurboFlow DOM] Model option not found: ${modelLabel}`);
        pressEscape();
        await sleep(300);
      }
    }

    pressEscape();
    await sleep(600);
    return true;
  }

  function getEditor() {
    return document.querySelector('[data-slate-editor="true"]');
  }

  async function executeInMainWorld(funcBody, args = []) {
    return await new Promise((resolve) => {
      chrome.runtime.sendMessage({ action: 'executeInMainWorld', funcBody, args }, (response) => {
        if (chrome.runtime.lastError) {
          resolve({ success: false, error: chrome.runtime.lastError.message });
          return;
        }
        resolve(response || { success: false, error: 'No response' });
      });
    });
  }

  async function fastInjectPrompt(prompt) {
    const editor = getEditor();
    if (!editor) return false;
    editor.click();
    editor.focus();
    await sleep(150);
    editor.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true, cancelable: true, key: 'a', code: 'KeyA', ctrlKey: true, keyCode: 65 }));
    await sleep(80);
    editor.dispatchEvent(new InputEvent('beforeinput', { bubbles: true, cancelable: true, inputType: 'insertText', data: prompt }));
    await sleep(400);
    const text = editor.textContent.trim();
    return text === prompt || text.includes(prompt.substring(0, 20));
  }

  async function stealthPastePrompt(prompt) {
    const editor = getEditor();
    if (!editor) return false;
    await sleep(300 + Math.random() * 600);
    editor.click();
    editor.focus();
    await sleep(150 + Math.random() * 100);
    editor.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true, cancelable: true, key: 'a', code: 'KeyA', ctrlKey: true, keyCode: 65 }));
    await sleep(80 + Math.random() * 80);
    editor.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true, cancelable: true, key: 'v', code: 'KeyV', ctrlKey: true, keyCode: 86 }));
    await sleep(50 + Math.random() * 50);
    editor.dispatchEvent(new InputEvent('beforeinput', { bubbles: true, cancelable: true, inputType: 'insertText', data: prompt }));
    await sleep(300 + Math.random() * 200);
    const text = editor.textContent.trim();
    return text === prompt.trim() || text.includes(prompt.substring(0, 30)) || await fastInjectPrompt(prompt);
  }

  async function stealthTypePrompt(prompt) {
    const init = await executeInMainWorld(`
      const el = document.querySelector('[data-slate-editor="true"]');
      if (!el) return 'error:Editor not found';
      const fiberKey = Object.keys(el).find(k => k.startsWith('__reactFiber') || k.startsWith('__reactInternalInstance'));
      if (!fiberKey) return 'error:React fiber not found on editor element';
      let node = el[fiberKey];
      let editor = null;
      for (let i = 0; i < 100; i++) {
        if (!node) break;
        const p = node.memoizedProps;
        if (p && p.editor && typeof p.editor.apply === 'function' && p.editor.children) {
          editor = p.editor;
          break;
        }
        node = node.return;
      }
      if (!editor) return 'error:Slate editor not found in fiber tree';
      window.__flowSlateEditor = editor;
      const existing = editor.children[0]?.children[0]?.text || '';
      if (existing.length > 0) {
        editor.apply({ type: 'remove_text', path: [0, 0], offset: 0, text: existing });
      }
      return 'ok';
    `);
    if (!init.success || String(init.result || '').startsWith('error:')) {
      console.warn('[TurboFlow DOM] Stealth typing init failed; falling back to paste:', init.error || init.result);
      return await stealthPastePrompt(prompt);
    }

    let previous = '';
    for (let i = 0; i < prompt.length; i++) {
      const ch = prompt[i];
      const lower = ch.toLowerCase();
      if (/[a-z]/.test(lower) && Math.random() < 0.03) {
        const typo = (TYPO_NEIGHBORS[lower] || [lower])[Math.floor(Math.random() * (TYPO_NEIGHBORS[lower] || [lower]).length)];
        await executeInMainWorld(`
          const editor = window.__flowSlateEditor;
          if (editor) {
            const offset = editor.children[0]?.children[0]?.text?.length || 0;
            editor.apply({ type: 'insert_text', path: [0, 0], offset, text: args[0] });
          }
        `, [typo]);
        await sleep(230 + Math.random() * 310);
        await executeInMainWorld(`
          const editor = window.__flowSlateEditor;
          if (editor) {
            const text = editor.children[0]?.children[0]?.text || '';
            if (text.length > 0) {
              editor.apply({ type: 'remove_text', path: [0, 0], offset: text.length - 1, text: text[text.length - 1] });
            }
          }
        `);
        await sleep(60 + Math.random() * 50);
      }

      await executeInMainWorld(`
        const editor = window.__flowSlateEditor;
        if (editor) {
          const offset = editor.children[0]?.children[0]?.text?.length || 0;
          editor.apply({ type: 'insert_text', path: [0, 0], offset, text: args[0] });
        }
      `, [ch]);

      const bigram = previous + lower;
      let delay;
      if (FAST_BIGRAMS.has(bigram)) delay = 50 + Math.random() * 40;
      else if (ch === ' ') delay = 120 + Math.random() * 150;
      else if (ch === ',' || ch === '.') delay = 150 + Math.random() * 200;
      else delay = 80 + Math.random() * 120;
      const sinceSpace = i - prompt.lastIndexOf(' ', i);
      if (sinceSpace > 5) delay += sinceSpace * 2;
      if (Math.random() < 0.03) delay += 400 + Math.random() * 800;
      previous = lower;
      await sleep(delay);
    }

    await sleep(400);
    return true;
  }

  async function injectPrompt(prompt, task) {
    if (!getEditor()) throw new Error('Flow prompt editor not found');
    if (!task.stealthMode) {
      if (!await fastInjectPrompt(prompt)) throw new Error('Prompt injection failed');
      return;
    }
    const ok = prompt.length > STEALTH_SHORT_PROMPT_LIMIT
      ? await stealthPastePrompt(prompt)
      : await stealthTypePrompt(prompt);
    if (!ok) throw new Error('Prompt injection failed');
  }

  async function submitPrompt(task) {
    const result = await executeInMainWorld(`
      const buttons = Array.from(document.querySelectorAll('button'));
      const submitBtn = buttons.find(btn => {
        const hasArrowForward = btn.querySelector('i')?.textContent.trim() === 'arrow_forward';
        const hasSpanText = btn.querySelector('span')?.textContent.trim().length > 0;
        return hasArrowForward && hasSpanText && !btn.disabled && btn.getAttribute('aria-disabled') !== 'true';
      }) || buttons.find(btn => btn.querySelector('i')?.textContent.trim() === 'arrow_forward');
      if (!submitBtn) return 'error:Submit button not found';
      const fiberKey = Object.keys(submitBtn).find(k => k.startsWith('__reactFiber') || k.startsWith('__reactInternalInstance'));
      if (!fiberKey) return 'error:React fiber not found on submit button';
      let node = submitBtn[fiberKey];
      let onClick = null;
      for (let i = 0; i < 50; i++) {
        if (!node) break;
        const p = node.memoizedProps;
        if (p && typeof p.onClick === 'function') {
          onClick = p.onClick;
          break;
        }
        node = node.return;
      }
      if (!onClick) return 'error:onClick prop not found in fiber tree';
      document.querySelector('[data-slate-editor="true"]')?.focus();
      onClick({
        isTrusted: true,
        type: 'click',
        bubbles: true,
        cancelable: true,
        target: submitBtn,
        currentTarget: submitBtn,
        nativeEvent: { isTrusted: true, type: 'click', target: submitBtn },
        isDefaultPrevented: () => false,
        isPropagationStopped: () => false,
        preventDefault: () => {},
        stopPropagation: () => {},
      });
      return 'ok';
    `);

    if (result.success && !String(result.result || '').startsWith('error:')) return true;
    console.warn('[TurboFlow DOM] React submit failed; falling back to DOM click:', result.error || result.result);
    const button = xPath("(//button[.//i[normalize-space()='arrow_forward'] and not(@disabled) and not(@aria-disabled='true')])[last()]")
      || xPath("(//button[.//i[normalize-space()='arrow_forward']])[last()]");
    if (!button) return false;
    click(button, task);
    return true;
  }

  function snapshotTileIds() {
    const ids = new Set();
    document.querySelectorAll('[data-tile-id]').forEach((tile) => {
      const id = tile.getAttribute('data-tile-id');
      if (id) ids.add(id);
    });
    return ids;
  }

  function findTileError(tile) {
    const icons = Array.from(tile.querySelectorAll('i')).map((icon) => icon.textContent.trim());
    if (!icons.includes('warning')) return null;
    if (Array.from(tile.querySelectorAll('a[href]')).some((a) => {
      const href = a.getAttribute('href') || '';
      return href.includes('/faq') || href.includes('/policies') || href.includes('policy');
    })) return 'Prompt flagged by content policy';
    if (icons.includes('refresh')) return 'Generation failed - Flow encountered an error';
    return 'Generation error detected';
  }

  async function imageToDataUrl(src) {
    return await new Promise((resolve) => {
      const timeout = setTimeout(() => resolve(null), 30000);
      const img = new Image();
      img.crossOrigin = 'anonymous';
      img.onload = () => {
        try {
          const canvas = document.createElement('canvas');
          canvas.width = img.naturalWidth;
          canvas.height = img.naturalHeight;
          canvas.getContext('2d').drawImage(img, 0, 0);
          clearTimeout(timeout);
          resolve(canvas.toDataURL('image/png'));
        } catch {
          clearTimeout(timeout);
          resolve(null);
        }
      };
      img.onerror = () => {
        clearTimeout(timeout);
        resolve(null);
      };
      img.src = src;
    });
  }

  async function waitForGeneratedImage(preSubmitTileIds) {
    const started = Date.now();
    while (Date.now() - started < RESULT_TIMEOUT_MS) {
      for (const tile of document.querySelectorAll('[data-tile-id]')) {
        const id = tile.getAttribute('data-tile-id');
        if (!id || preSubmitTileIds.has(id)) continue;
        const error = findTileError(tile);
        if (error) throw new Error(error);
        const img = tile.querySelector('img[src*="media.getMediaUrlRedirect"]');
        if (img?.src) {
          const resultDataUrl = await imageToDataUrl(img.src);
          return { resultUrl: img.src, resultDataUrl };
        }
      }
      await sleep(RESULT_SCAN_MS);
    }
    throw new Error('Timed out waiting for generated image tile');
  }

  async function runDomTranslate(task) {
    const imageName = task.fileName || 'reference_1.png';
    const images = [{
      data: task.imageBase64,
      name: imageName,
      mimeType: task.mimeType || 'image/png',
    }];

    await applySettings(task);
    await clearAttachedReferences(task);
    await randomDelay(task, 'reference upload');
    await uploadAllImages(images, task);
    await attachAllImages(images, task);
    await sleep(500);
    await randomDelay(task, 'prompt input');
    await injectPrompt(task.prompt || '', task);
    await sleep(1000);
    const preSubmitTileIds = snapshotTileIds();
    await randomDelay(task, 'submit');
    if (!await submitPrompt(task)) throw new Error('Submit failed');
    return await waitForGeneratedImage(preSubmitTileIds);
  }

  const listener = (msg, _sender, sendResponse) => {
    if (msg.type !== 'RUN_DOM_TRANSLATE_V3') return false;
    runDomTranslate(msg.task || {})
      .then((result) => sendResponse({ ok: true, ...result }))
      .catch((error) => sendResponse({ ok: false, error: error.message || String(error) }));
    return true;
  };

  chrome.runtime.onMessage.addListener(listener);
  window.__turboFlowDomMethod = { version: VERSION, listener };
})();
