(function () {
  console.log('[WebUI][INFO] application initialized');

  // Debug overlay visibility: enabled by ?debug=1 or localStorage.jeveDebug='1'
  try {
    const q = new URLSearchParams(location.search);
    if (q.get('debug') === '1' || localStorage.getItem('jeveDebug') === '1') {
      document.body.classList.add('jeve-debug');
    }
  } catch (e) {}

  // ---- WindowDiag overlay (dev) ----
  const dbg = document.createElement('div');
  dbg.id = 'window-diag';
  dbg.style.cssText = 'position:fixed;right:4px;bottom:4px;width:240px;max-height:140px;overflow:hidden;font-size:10px;background:rgba(255,255,255,0.92);border:1px solid var(--jeve-border,#ddd);padding:4px;z-index:99999;pointer-events:none;font-family:monospace;line-height:1.3;border-radius:8px;';
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
    const minBtn = e.target.closest('#header-minimize');
    if (!minBtn) return;
    if (!window.desktopBridge) return;
    try {
      if (typeof window.desktopBridge.minimizeWindow === 'function') window.desktopBridge.minimizeWindow();
    } catch (err) {}
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

  // ===== Header dynamic action: 采集 toggle vs 重新读取 =====
  window.__jeveSourceType = 'database';
  function updateHeaderAction() {
    const st = window.__jeveSourceType || 'database';
    const cap = document.getElementById('capture-toggle');
    const rs = document.getElementById('header-resync');
    const page = window.__jevePage || 'home';
    // capture toggle: only on home when OCR mode
    if (cap) cap.style.display = (st === 'ocr' && page === 'home') ? '' : 'none';
    // header resync: only on chat-history page (database mode)
    if (rs) rs.style.display = (st === 'database' && page === 'chat-history') ? '' : 'none';
  }
  window.updateHeaderAction = updateHeaderAction;

  // Load current sourceType and update header
  fetch('http://127.0.0.1:18080/api/dbsource/config').then(r=>r.json()).then(d=>{
    if (d && d.success) window.__jeveSourceType = d.sourceType || 'database';
    updateHeaderAction();
  }).catch(()=>{ updateHeaderAction(); });

  // Wire header-resync button
  document.addEventListener('click', (e) => {
    const t = e.target.closest('#header-resync');
    if (!t) return;
    if (t.disabled) return;
    t.disabled = true;
    const orig = t.textContent;
    t.classList.add('rs-spinning');
    t.textContent = '';
    fetch('http://127.0.0.1:18080/api/dbsource/sync', {method:'POST',headers:{'Content-Type':'application/json'},body:'{}'})
      .then(r=>r.json())
      .then(d => {
        // Wait a bit for background sync to start, then refresh chat history if on chat page
        setTimeout(()=>{
          t.classList.remove('rs-spinning');
          t.textContent = orig;
          t.disabled = false;
          if (typeof JeveRouter !== 'undefined' && JeveRouter.currentPage === 'chat-history') {
            try { JeveRouter.navigate('chat-history'); } catch(e){}
          }
        }, 1500);
      })
      .catch(err => {
        t.classList.remove('rs-spinning');
        t.textContent = orig;
        t.disabled = false;
      });
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

  // Edge docking: send mouse position to bridge for real-time reveal/hide
  document.addEventListener('mousemove', (e) => {
    if (!window.desktopBridge || typeof window.desktopBridge.onMousePosition !== 'function') return;
    try { window.desktopBridge.onMousePosition(e.screenX, e.screenY, true); } catch (err) {}
  });

  JeveRouter.navigate('home');
})();
