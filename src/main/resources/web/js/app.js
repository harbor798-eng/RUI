(function () {
  console.log('[WebUI][INFO] application initialized');

  // ---- WindowDiag overlay (dev) ----
  const dbg = document.createElement('div');
  dbg.id = 'window-diag';
  dbg.style.cssText = 'position:fixed;right:4px;bottom:4px;width:240px;max-height:140px;overflow:hidden;font-size:10px;background:rgba(255,255,255,0.85);border:1px solid #aaa;padding:4px;z-index:99999;pointer-events:none;font-family:monospace;line-height:1.3';
  if (document.body) document.body.appendChild(dbg);
  const lines = [];
  function dlog(msg) {
    lines.push(msg);
    if (lines.length > 16) lines.shift();
    dbg.textContent = 'Window Debug\n' + lines.join('\n');
  }

  if (window.__jeveDesktopEventsBound) { dlog('already bound'); return; }
  window.__jeveDesktopEventsBound = true;
  dlog('events bound');

  document.addEventListener('click', (e) => {
    const closeBtn = e.target.closest('[data-page="close"]');
    if (!closeBtn) return;
    e.preventDefault();
    dlog('close clicked bridge=' + !!window.desktopBridge);
    if (!window.desktopBridge) return;
    try { window.desktopBridge.closeWindow(); } catch (err) { dlog('close err'); }
  });

  document.addEventListener('click', (e) => {
    const btn = e.target.closest('[data-page]');
    if (!btn || btn.dataset.page === 'close') return;
    JeveRouter.navigate(btn.dataset.page);
  });

  /* ===== Collector toggle state machine ===== */
  const captureBtn = document.getElementById('capture-toggle');
  function setCollectorState(state) {
    if (!captureBtn) return;
    captureBtn.dataset.state = state;
    captureBtn.disabled = (state === 'DISABLED');
    console.log('[COLLECTOR] state=' + state);
    dlog('collector ' + state);
    if (window.desktopBridge && typeof window.desktopBridge.setCollectorState === 'function') {
      try { window.desktopBridge.setCollectorState(state); } catch (err) { console.warn('[COLLECTOR] bridge call failed', err); }
    }
  }
  setCollectorState('OFF');
  document.addEventListener('click', (e) => {
    const t = e.target.closest('#capture-toggle');
    if (!t) return;
    if (t.disabled) return;
    const cur = t.dataset.state;
    const next = (cur === 'ON') ? 'OFF' : 'ON';
    setCollectorState(next);
  });

  let dragging = false;
  document.addEventListener('mousedown', (e) => {
    if (e.button !== 0) return;
    if (!e.target.closest('.jeve-header')) return;
    if (e.target.closest('button, a, input, select, textarea, [data-no-drag]')) return;
    if (!window.desktopBridge) return;
    try { window.desktopBridge.startDrag(e.screenX, e.screenY); dragging = true; } catch (err) {}
  });
  document.addEventListener('mousemove', (e) => {
    if (!dragging || !window.desktopBridge) return;
    try { window.desktopBridge.dragTo(e.screenX, e.screenY); } catch (err) {}
  });
  document.addEventListener('mouseup', () => {
    if (!dragging) return;
    dragging = false;
    if (window.desktopBridge) { try { window.desktopBridge.endDrag(); } catch (err) {} }
  });

  JeveRouter.navigate('home');
})();
