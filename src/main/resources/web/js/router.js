(function (global) {
  const PAGE_TITLES = {
    'home': 'AI 快速回复',
    'chat-history': '核对记录',
    'detail-analysis': '详细分析',
    'settings': '设置',
    'ai-observation': 'AI 观察',
    'ai-config': 'AI 配置'
  };
  const BACK_PAGES = { 'chat-history': true, 'detail-analysis': true, 'ai-observation': true, 'settings': true, 'ai-config': true };

  const pages = {
    'home': renderHome,
    'ai-observation': renderDeepObservation,
    'settings': renderSettings,
    'ai-config': renderAiConfig,
    'chat-history': renderChatHistory,
    'detail-analysis': renderDetailAnalysis
  };

  function normalizeStrategy(s) {
    if (!s) return 'NATURAL';
    const k = String(s).trim();
    if (k === 'NATURAL' || k === '自然') return 'NATURAL';
    if (k === 'PROACTIVE' || k === 'ACTIVE' || k === '主动') return 'PROACTIVE';
    if (k === 'LIGHT_FLIRT' || k === 'SLIGHTLY_FLIRTATIOUS' || k === '轻微暧昧') return 'LIGHT_FLIRT';
    return k;
  }
  const STRATEGY_LABEL = { 'NATURAL':'自然', 'PROACTIVE':'主动', 'LIGHT_FLIRT':'轻微暧昧' };
  const STRATEGY_ORDER = ['NATURAL','PROACTIVE','LIGHT_FLIRT'];
  let quickReplyState = { generated: false, byStrategy: {} };

  /* ===== Header / scroll collapse ===== */
  let lastScrollTop = 0;
  let currentScrollEl = null;
  const scrollbarTimers = new WeakMap();
  function pokeScrollbar(el) {
    if (!el) return;
    el.classList.add('scrollbar-active');
    clearTimeout(scrollbarTimers.get(el));
    scrollbarTimers.set(el, setTimeout(() => el.classList.remove('scrollbar-active'), 800));
  }
  function updateScrollThumb(list) {
    const sb = document.getElementById('ch-scrollbar');
    const thumb = document.getElementById('ch-thumb');
    if (!sb || !thumb || !list) return;
    const sh = list.scrollHeight, ch = list.clientHeight, st = list.scrollTop;
    if (sh <= ch) { sb.classList.remove('visible'); return; }
    const thumbH = Math.max(24, ch * ch / sh);
    const maxTop = ch - thumbH;
    const maxScroll = sh - ch;
    const thumbT = maxScroll > 0 ? st / maxScroll * maxTop : 0;
    thumb.style.height = thumbH + 'px';
    thumb.style.top = thumbT + 'px';
    sb.classList.add('visible');
    console.log('[ScrollbarDebug][THUMB] thumbHeight=', thumbH.toFixed(0), 'thumbTop=', thumbT.toFixed(0), 'visible=true');
    clearTimeout(scrollbarTimers.get(sb));
    scrollbarTimers.set(sb, setTimeout(() => sb.classList.remove('visible'), 800));
  }
  function bindHeaderScroll(scrollEl) {
    const header = document.getElementById('jeve-header');
    if (!header) return;
    currentScrollEl = scrollEl;
    console.log('[HeaderDebug] bind scroll element=', scrollEl ? (scrollEl.id || scrollEl.className) : 'none');
    if (!scrollEl) return;
    scrollEl.onscroll = () => {
      const st = scrollEl.scrollTop;
      if (st > lastScrollTop + 8 && st > 60) {
        header.classList.add('hidden');
      } else if (st < lastScrollTop - 4) {
        header.classList.remove('hidden');
      }
      lastScrollTop = st;
      console.log('[HeaderDebug] scrollTop=', st, 'headerHidden=', header.classList.contains('hidden'));
      pokeScrollbar(scrollEl);
    };
  }

  function updateHeader(page) {
    document.getElementById('header-title').textContent = PAGE_TITLES[page] || 'JEVE';
    const back = document.getElementById('header-back');
    back.classList.toggle('visible', !!BACK_PAGES[page]);
    const settingsBtn = document.getElementById('header-settings');
    if (settingsBtn) settingsBtn.classList.toggle('hidden', page === 'settings');
    const bottomNav = document.getElementById('bottom-nav');
    if (bottomNav) bottomNav.style.display = (page === 'chat-history') ? 'none' : '';
    lastScrollTop = 0;
    document.getElementById('jeve-header').classList.remove('hidden');
  }

  /* ===== Pages ===== */
  function renderHome() {
    const initial = !quickReplyState.generated;
    return `
      <div class="home-page ${initial ? 'initial' : ''}" id="home-page">
        <div class="select-row">
          <div class="jeve-dropdown" id="contact-select" data-value="current">
            <button type="button" class="jeve-dropdown-trigger"><span>T高改芸</span><span class="jeve-dropdown-arrow">˅</span></button>
            <div class="jeve-dropdown-menu">
              <button type="button" class="jeve-dropdown-option selected" data-value="current">T高改芸</button>
            </div>
          </div>
          <div class="jeve-dropdown" id="skill-select" data-value="QUICK_REPLY">
            <button type="button" class="jeve-dropdown-trigger"><span>快速回复</span><span class="jeve-dropdown-arrow">˅</span></button>
            <div class="jeve-dropdown-menu">
              <button type="button" class="jeve-dropdown-option selected" data-value="QUICK_REPLY">快速回复</button>
            </div>
          </div>
        </div>
        <div class="qr-toolbar">
          <button id="qr-generate" class="primary qr-generate-big">生成回复</button>
          <span id="qr-status" class="muted"></span>
        </div>
        <div id="qr-grid" class="qr-grid"></div>
      </div>`;
  }

  function renderSettings() {
    return `<div class="settings-list">
      <div class="qr-card settings-item" data-page="ai-config">
        <div class="settings-item-main"><h3>AI 配置</h3><p class="muted">服务商、API Key、Base URL、模型</p></div>
        <span class="settings-arrow">›</span>
      </div>
      <div class="qr-card"><h3>实时采集上下文</h3>
        <div class="jeve-dropdown" id="ctx-len" data-value="10">
          <button type="button" class="jeve-dropdown-trigger"><span>10 条</span><span class="jeve-dropdown-arrow">˅</span></button>
          <div class="jeve-dropdown-menu">
            <button type="button" class="jeve-dropdown-option selected" data-value="10">10 条</button>
            <button type="button" class="jeve-dropdown-option" data-value="20">20 条</button>
            <button type="button" class="jeve-dropdown-option" data-value="50">50 条</button>
          </div>
        </div></div>
      <div class="qr-card settings-item" id="settings-profile">
        <div class="settings-item-main"><h3>AI 档案</h3><p class="muted">管理我的档案与对方档案</p></div>
        <span class="settings-arrow">›</span>
      </div>
      <div class="qr-card"><h3>AI 观察</h3><button class="primary" data-page="ai-observation">进入深度观察</button></div>
      <div class="qr-card"><h3>Skill 管理</h3><p class="muted">当前 Skill：快速回复。</p></div>
      <div class="qr-card"><h3>关于</h3><p class="muted">JEVE · Web UI V1。</p></div>
    </div>`;
  }

  function renderAiConfig() {
    return `
      <div class="ai-config-page">
        <div class="modal-row">
          <label>AI 服务商</label>
          <div class="jeve-dropdown" id="ai-provider" data-value="DEEPSEEK">
            <button type="button" class="jeve-dropdown-trigger"><span>DeepSeek</span><span class="jeve-dropdown-arrow">˅</span></button>
            <div class="jeve-dropdown-menu">
              <button type="button" class="jeve-dropdown-option selected" data-value="DEEPSEEK">DeepSeek</button>
            </div>
          </div>
        </div>
        <div class="modal-row">
          <label>API Key</label>
          <input type="password" id="ai-api-key" placeholder="已配置，留空则保持不变" />
          <span class="muted" id="ai-key-status"></span>
        </div>
        <div class="modal-row">
          <label>Base URL</label>
          <input id="ai-base-url" value="https://api.deepseek.com" />
        </div>
        <div class="modal-row">
          <label>模型</label>
          <div class="jeve-dropdown" id="ai-model" data-value="deepseek-chat">
            <button type="button" class="jeve-dropdown-trigger"><span>deepseek-chat</span><span class="jeve-dropdown-arrow">˅</span></button>
            <div class="jeve-dropdown-menu">
              <button type="button" class="jeve-dropdown-option selected" data-value="deepseek-chat">deepseek-chat</button>
            </div>
          </div>
        </div>
        <div class="ai-config-actions">
          <button id="ai-test" class="ghost">测试连接</button>
          <button id="ai-save" class="primary">保存</button>
        </div>
        <div id="ai-config-status" class="muted"></div>
      </div>`;
  }

  function renderChatHistory() {
    return `<div class="chat-page">
      <div id="ch-status" class="page-placeholder">正在加载…</div>
      <div class="chat-scroll-viewport">
        <div id="ch-list" class="chat-stream"></div>
      </div>
      <div id="ch-scrollbar" class="custom-scrollbar"><div id="ch-thumb" class="custom-scrollbar-thumb"></div></div>
      <div class="ch-inputbar">
        <button id="ch-add" class="ch-add" title="添加历史消息">＋</button>
        <input id="ch-input" placeholder="输入消息……" />
        <button id="ch-send" class="primary">发送</button>
      </div>
    </div>`;
  }

  function renderDeepObservation() {
    setTimeout(() => {
      fetch('http://127.0.0.1:18080/api/deep-observation/data-range').then(r=>r.json()).then(d => {
        const el = document.getElementById('do-range');
        if (!el) return;
        el.textContent = d.hasData ? (d.first + ' ~ ' + d.last) : '暂无聊天记录';
        if (!d.hasData) { const b = document.getElementById('do-start'); if (b) b.disabled = true; }
      }).catch(() => { const el = document.getElementById('do-range'); if (el) el.textContent = '加载失败'; });
    }, 0);
    return `<p class="muted">这段关系一路发生了什么？</p>
      <div class="qr-grid">
        <div class="qr-card">
          <label><input type="radio" name="do-range" value="ALL" checked> 全部聊天记录</label><br>
          <label><input type="radio" name="do-range" value="RECENT_YEAR"> 最近一年</label><br>
          <label><input type="radio" name="do-range" value="RECENT_3_MONTHS"> 最近三个月</label><br>
          <label><input type="radio" name="do-range" value="CUSTOM"> 自定义</label>
        </div>
        <div class="qr-card"><span class="muted">当前聊天数据：</span><span id="do-range">加载中…</span></div>
        <button id="do-start" class="primary">开始深度观察</button>
        <div id="do-status" class="muted"></div>
        <div id="do-report"></div>
      </div>`;
  }

  function renderDetailAnalysis() {
    setTimeout(() => runDetailAnalysis(), 0);
    return `<div id="da-status" class="page-placeholder">正在分析最近 7 天的互动……</div>
      <div id="da-result"></div>`;
  }

  /* ===== Detail analysis ===== */
  async function runDetailAnalysis() {
    try {
      const res = await fetch('http://127.0.0.1:18080/api/detail-analysis', {
        method: 'POST', headers: {'Content-Type':'application/json'}, body: '{}'
      });
      const data = await res.json();
      const status = document.getElementById('da-status');
      if (!data.success) { status.textContent = '暂时无法完成分析：' + (data.status || data.error || ''); return; }
      if (data.status === 'NO_DATA') { status.textContent = data.message || '当前没有可分析的聊天记录。'; return; }
      status.textContent = '';
      const r = data.result || {};
      const poss = r.possibilities || [];
      const conf = poss[0] && poss[0].estimatedProbability != null ? Math.round(poss[0].estimatedProbability * 100) : null;
      const obs = r.observations || [];
      const recs = r.recommendations || [];
      const emotions = (r.emotions || []).slice(0, 4).map(e => ({
        label: e.emotion || '其他',
        pct: Math.round((e.estimatedProbability || 0) * 100)
      }));

      document.getElementById('da-result').innerHTML = `
        <div class="da-section">
          <h3>对方可能意图</h3>
          <div class="da-card">${escapeHtml(poss[0] ? poss[0].content : '暂无判断')}</div>
        </div>
        ${conf != null ? `<div class="da-section"><h3>判断把握</h3><div class="da-card da-confidence">判断把握 · ${conf}%</div></div>` : ''}
        <div class="da-section">
          <h3>当前状态</h3>
          <div class="da-card">${donutHtml(emotions)}</div>
        </div>
        <div class="da-section">
          <h3>判断依据</h3>
          <div class="da-card">${obs.map(o => '• ' + escapeHtml(o.content)).join('<br>') || '暂无依据'}</div>
        </div>
        <div class="da-section">
          <h3>AI 建议</h3>
          <div class="da-card">${escapeHtml(recs[0] ? recs[0].content : '暂无建议')}</div>
        </div>`;
    } catch (e) {
      document.getElementById('da-status').textContent = '请求失败：' + e.message;
    }
  }

  function donutHtml(items) {
    if (!items.length) return '<span class="muted">暂无情绪数据</span>';
    const colors = ['#6d5bd0', '#e8a838', '#4fb286', '#b8b8c2'];
    const total = items.reduce((s, x) => s + (x.pct || 0), 0) || 1;
    let acc = 0;
    const R = 30, C = 2 * Math.PI * R;
    const arcs = items.map((it, i) => {
      const frac = it.pct / total;
      const len = frac * C;
      const dash = `${len} ${C - len}`;
      const offset = -acc * C;
      acc += frac;
      return `<circle r="${R}" cx="36" cy="36" fill="none" stroke="${colors[i % colors.length]}" stroke-width="8" stroke-dasharray="${dash}" stroke-dashoffset="${offset}"/>`;
    }).join('');
    const top = items[0];
    return `<div class="donut-wrap">
      <div class="donut">
        <svg width="72" height="72">${arcs}</svg>
        <div class="donut-center"><span class="pct">${top.pct}%</span><span class="lbl">${escapeHtml(top.label)}</span></div>
      </div>
      <div class="donut-legend">
        ${items.map((it, i) => `<div class="row"><span class="dot" style="background:${colors[i%colors.length]}"></span>${escapeHtml(it.label)}<span class="pct">${it.pct}%</span></div>`).join('')}
      </div>
    </div>`;
  }

  /* ===== Navigate ===== */
  let currentPage = 'home';
  function navigate(page) {
    console.log('[RouteDebug] navigate from=' + currentPage + ' to=' + page);
    console.log('[WebUI][INFO] navigate:', page);
    const fn = pages[page];
    if (!fn) { console.log('[RouteDebug] MISSING page render fn for=' + page); return; }
    currentPage = page;
    console.log('[PageDebug] currentPage=' + currentPage);
    updateHeader(page);
    const main = document.getElementById('main-content');
    main.innerHTML = fn();
    main.classList.toggle('no-scroll', page === 'chat-history');
    lastScrollTop = 0;
    if (page === 'chat-history') {
      bindHeaderScroll(null);
    } else {
      bindHeaderScroll(main);
    }
    document.querySelectorAll('[data-page]').forEach(b => {
      b.classList.toggle('active', b.dataset.page === page);
    });
    document.getElementById('session-back')?.remove();
    const backBtn = document.getElementById('header-back');
    backBtn.onclick = () => {
      const resolved = (page === 'ai-config' || page === 'ai-observation') ? 'settings' : 'home';
      console.log('[BackDebug] router.js backBtn click currentPage=' + page + ' resolvedTarget=' + resolved);
      if (page !== 'ai-config' && page !== 'ai-observation') console.log('[BackDebug] FALLBACK_HOME triggered by router');
      navigate(resolved);
    };
    if (page === 'home') bindHome();
    if (page === 'ai-observation') bindDeepObs();
    if (page === 'chat-history') loadChatHistory();
    if (page === 'ai-config') bindAiConfig();
    initJeveDropdowns();
    if (page === 'settings') bindSettings();
  }

  /* ===== Chat history ===== */
  let chState = { loaded: new Set(), hasMore: true, loading: false, beforeTime: null, beforeId: null };

  async function loadChatHistory() {
    const status = document.getElementById('ch-status');
    const list = document.getElementById('ch-list');
    chState = { loaded: new Set(), hasMore: true, loading: false, beforeTime: null, beforeId: null };
    try {
      const res = await fetch('http://127.0.0.1:18080/api/chat-history?size=50');
      const d = await res.json();
      if (!d.success) { status.textContent = '聊天记录加载失败。'; return; }
      let msgs = (d.messages || []).slice().reverse();
      if (!msgs.length) { status.textContent = '暂无聊天记录'; } else { status.textContent = ''; }
      list.innerHTML = msgs.map(m => { chState.loaded.add(m.id); return bubble(m); }).join('');
      chState.hasMore = d.hasMore;
      if (msgs.length) {
        const oldest = msgs[0];
        chState.beforeTime = oldest.time; chState.beforeId = oldest.id;
      }
      requestAnimationFrame(() => { list.scrollTop = list.scrollHeight; updateScrollThumb(list); });
      bindChatActions(list);
      console.log('[HeaderDebug] chat-history header fixed');
      list.onscroll = async () => {
        updateScrollThumb(list);
        if (list.scrollTop <= 30 && !chState.loading && chState.hasMore && chState.beforeTime) {
          chState.loading = true;
          const oldH = list.scrollHeight, oldT = list.scrollTop;
          try {
            const r = await fetch(`http://127.0.0.1:18080/api/chat-history?size=50&beforeTime=${encodeURIComponent(chState.beforeTime)}&beforeId=${chState.beforeId}`);
            const j = await r.json();
            let older = (j.messages || []).slice().reverse();
            const fresh = older.filter(m => !chState.loaded.has(m.id));
            fresh.forEach(m => chState.loaded.add(m.id));
            if (fresh.length) {
              list.insertAdjacentHTML('afterbegin', fresh.map(bubble).join(''));
              chState.hasMore = j.hasMore;
              const o = fresh[0]; chState.beforeTime = o.time; chState.beforeId = o.id;
              requestAnimationFrame(() => { list.scrollTop = oldT + (list.scrollHeight - oldH); });
              bindChatActions(list);
            }
          } finally { chState.loading = false; }
        }
      };
      const doSend = async () => {
        const input = document.getElementById('ch-input');
        const text = (input.value||'').trim();
        if (!text) return;
        console.log('[ChatHistory] sending message');
        await fetch('http://127.0.0.1:18080/api/chat-history', {method:'POST',headers:{'Content-Type':'application/json'},
          body: JSON.stringify({speaker:'ME', content:text})});
        input.value = '';
        input.focus();
        loadChatHistory();
      };
      const send = document.getElementById('ch-send');
      if (send) send.onclick = doSend;
      const input = document.getElementById('ch-input');
      if (input) input.onkeydown = (e) => { if (e.key === 'Enter') { e.preventDefault(); doSend(); } };
      document.getElementById('ch-add').onclick = () => openMsgModal('ME', null);
    } catch (e) {
      status.textContent = '聊天记录加载失败，请稍后重试。';
    }
  }

  function bubble(m) {
    const me = m.speaker === 'ME';
    return `<div class="bubble-row ${me?'me':'other'}" data-id="${m.id}" data-time="${escapeHtml(m.time||'')}">
      <div class="bubble ${me?'bubble-me':'bubble-other'}">
        <div class="muted">${me?'我':'对方'} · ${escapeHtml((m.time||'').replace('T',' ').slice(0,16))}</div>
        <div>${escapeHtml(m.content||'')}</div>
      </div>
    </div>`;
  }

  /* ===== Right-click context menu (unified document handler) ===== */
  let ctxTargetId = null;
  function bindChatActions(root) { /* contextmenu now handled globally */ }
  function closeCtxMenu() {
    const menu = document.getElementById('ctx-menu');
    if (menu && menu.style.display === 'block') menu.style.display = 'none';
  }
  let editTargetId = null;
  document.addEventListener('contextmenu', (e) => {
    e.preventDefault();
    const menu = document.getElementById('ctx-menu');
    if (e.target.closest && e.target.closest('#ctx-menu')) {
      console.log('[ContextMenuDebug] target=ctx-menu nativePrevented=true reason=custom-menu');
      return;
    }
    const bubble = e.target.closest ? e.target.closest('.bubble') : null;
    if (!bubble) {
      console.log('[ContextMenuDebug] target=', e.target.tagName, 'isBubble=false menuShown=false');
      closeCtxMenu();
      return;
    }
    const row = bubble.closest('.bubble-row');
    ctxTargetId = row ? row.dataset.id : null;
    menu.style.display = 'block';
    let left = e.clientX, top = e.clientY;
    const r = menu.getBoundingClientRect();
    const vw = window.innerWidth, vh = window.innerHeight;
    if (left + r.width > vw) left = vw - r.width - 8;
    if (top + r.height > vh) top = vh - r.height - 8;
    left = Math.max(8, left); top = Math.max(8, top);
    menu.style.left = left + 'px';
    menu.style.top = top + 'px';
    console.log('[ContextMenuDebug] isBubble=true messageId=', ctxTargetId, 'menuShown=true');
  });
  document.addEventListener('click', closeCtxMenu);
  document.addEventListener('scroll', closeCtxMenu, true);
  document.addEventListener('click', (e) => {
    const menu = document.getElementById('ctx-menu');
    if (menu && menu.style.display === 'block' && !menu.contains(e.target)) menu.style.display = 'none';
  });
  document.getElementById('ctx-menu').addEventListener('click', async (e) => {
    const act = e.target.dataset.act;
    document.getElementById('ctx-menu').style.display = 'none';
    if (!ctxTargetId) return;
    if (act === 'edit') {
      openEditModal(ctxTargetId);
    } else if (act === 'add') {
      const row = document.querySelector(`.bubble-row[data-id="${ctxTargetId}"]`);
      openMsgModal('OTHER', row ? row.dataset.time : null);
    }
  });

  /* ===== Add message modal ===== */
  function openMsgModal(defaultSpeaker, presetTime) {
    document.getElementById('msg-modal').style.display = 'flex';
    document.querySelector(`input[name="m-speaker"][value="${defaultSpeaker}"]`).checked = true;
    const dateEl = document.getElementById('m-date');
    const timeEl = document.getElementById('m-time');
    let base;
    if (presetTime) base = presetTime;
    else base = new Date(Date.now() - new Date().getTimezoneOffset()*60000).toISOString();
    dateEl.value = base.slice(0,10);
    timeEl.value = (base.slice(11,16)) || '12:00';
    document.getElementById('m-content').value = '';
    document.getElementById('m-content').focus();
  }
  document.getElementById('m-cancel').onclick = () => { document.getElementById('msg-modal').style.display = 'none'; };
  document.getElementById('msg-modal').addEventListener('click', (e) => {
    if (e.target.id === 'msg-modal') e.target.style.display = 'none';
  });
  document.getElementById('m-save').onclick = async () => {
    const speaker = document.querySelector('input[name="m-speaker"]:checked').value;
    const date = document.getElementById('m-date').value;
    const time = document.getElementById('m-time').value;
    const combinedTime = date + 'T' + (time || '12:00') + ':00';
    const content = document.getElementById('m-content').value.trim();
    if (!content) return;
    console.log('[AddMessageDebug] speaker=', speaker, 'date=', date, 'time=', time, 'combinedTime=', combinedTime);
    await fetch('http://127.0.0.1:18080/api/chat-history', {method:'POST',headers:{'Content-Type':'application/json'},
      body: JSON.stringify({speaker, time: combinedTime, content})});
    document.getElementById('msg-modal').style.display = 'none';
    loadChatHistory();
  };

  /* ===== Edit message modal ===== */
  function openEditModal(id) {
    editTargetId = id;
    const row = document.querySelector(`.bubble-row[data-id="${id}"]`);
    if (!row) return;
    const isMe = row.classList.contains('me');
    const timeRaw = row.dataset.time || '';
    const content = row.querySelector('.bubble > div:last-child').textContent || '';
    console.log('[EditMessageDebug][TARGET] id=', id, 'speaker=', isMe ? 'ME' : 'OTHER', 'time=', timeRaw, 'contentLen=', content.length);
    document.querySelector(`input[name="e-speaker"][value="${isMe ? 'ME' : 'OTHER'}"]`).checked = true;
    const d = document.getElementById('e-date');
    const t = document.getElementById('e-time');
    if (timeRaw) {
      d.value = timeRaw.slice(0,10);
      t.value = timeRaw.slice(11,16) || '12:00';
    } else {
      d.value = ''; t.value = '12:00';
    }
    document.getElementById('e-content').value = content;
    document.getElementById('edit-msg-modal').style.display = 'flex';
    console.log('[EditMessageDebug][OPEN] id=', id, 'date=', d.value, 'time=', t.value);
  }
  document.getElementById('e-cancel').onclick = () => {
    document.getElementById('edit-msg-modal').style.display = 'none';
    editTargetId = null;
  };
  document.getElementById('edit-msg-modal').addEventListener('click', (e) => {
    if (e.target.id === 'edit-msg-modal') { e.target.style.display = 'none'; editTargetId = null; }
  });
  document.getElementById('e-save').onclick = async () => {
    if (!editTargetId) return;
    const speaker = document.querySelector('input[name="e-speaker"]:checked').value;
    const date = document.getElementById('e-date').value;
    const time = document.getElementById('e-time').value;
    const content = document.getElementById('e-content').value.trim();
    const combinedTime = date + 'T' + (time || '12:00') + ':00';
    console.log('[EditMessageDebug][SAVE] id=', editTargetId, 'speaker=', speaker, 'combinedTime=', combinedTime, 'contentLen=', content.length);
    await fetch('http://127.0.0.1:18080/api/chat-history/' + editTargetId, {
      method: 'PUT', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ speaker, time: combinedTime, content })
    });
    console.log('[EditMessageDebug][SUCCESS] id=', editTargetId);
    document.getElementById('edit-msg-modal').style.display = 'none';
    editTargetId = null;
    ctxTargetId = null;
    loadChatHistory();
  };

  /* ===== Deep observation ===== */
  function bindDeepObs() {
    const btn = document.getElementById('do-start');
    if (!btn) return;
    btn.addEventListener('click', async () => {
      btn.disabled = true;
      document.getElementById('do-status').textContent = '正在进行深度观察……';
      const rangeEl = document.querySelector('input[name=do-range]:checked');
      const range = rangeEl ? rangeEl.value : 'ALL';
      try {
        const res = await fetch('http://127.0.0.1:18080/api/deep-observation', {
          method: 'POST', headers: {'Content-Type':'application/json'},
          body: JSON.stringify({range})
        });
        const d = await res.json();
        if (!d.success) { document.getElementById('do-status').textContent = '失败：' + d.status; btn.disabled = false; return; }
        document.getElementById('do-status').textContent = '报告已生成';
        document.getElementById('do-report').innerHTML = `
          <div class="qr-card">
            <button id="do-save">保存报告</button>
            <iframe sandbox src="http://127.0.0.1:18080/api/deep-observation/report?reportId=${d.reportId}" style="width:100%;height:800px;border:none;border-radius:8px;background:#fff"></iframe>
          </div>`;
        document.getElementById('do-save').addEventListener('click', async () => {
          await fetch('http://127.0.0.1:18080/api/deep-observation/save', {
            method: 'POST', headers: {'Content-Type':'application/json'},
            body: JSON.stringify({reportId: d.reportId})
          });
        });
      } catch (e) {
        document.getElementById('do-status').textContent = '请求失败：' + e.message;
        btn.disabled = false;
      }
    });
  }

  /* ===== Quick reply ===== */
  function bindHome() {
    const btn = document.getElementById('qr-generate');
    if (btn) btn.addEventListener('click', generate);
    const cs = document.getElementById('contact-select');
    if (cs) cs.addEventListener('change', () => console.log('[ContactDebug] selected=', cs.value));
    const ss = document.getElementById('skill-select');
    if (ss) ss.addEventListener('change', () => console.log('[SkillDebug] selected=', ss.value));
    if (quickReplyState.generated) renderQuickCards(quickReplyState.byStrategy);
  }
  function renderQuickCards(byStrategy) {
    const grid = document.getElementById('qr-grid');
    if (!grid) return;
    grid.innerHTML = STRATEGY_ORDER.map(s => `
      <div class="qr-card">
        <div class="qr-head">
          <h3>${STRATEGY_LABEL[s]}</h3>
          <button class="qr-refresh" data-strategy="${s}" title="刷新">↻</button>
        </div>
        <div class="qr-text">${escapeHtml(byStrategy[s] || '（无候选）')}</div>
        <div class="qr-actions"><button data-use="${escapeHtml(byStrategy[s] || '')}">使用</button></div>
      </div>`).join('');
    grid.querySelectorAll('[data-use]').forEach(b => {
      b.addEventListener('click', async () => {
        await fetch('http://127.0.0.1:18080/api/clipboard', {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({text:b.dataset.use})});
        b.textContent='已复制';
      });
    });
    grid.querySelectorAll('.qr-refresh').forEach(b => {
      b.addEventListener('click', async (e) => {
        e.stopPropagation();
        const s = b.dataset.strategy;
        b.disabled = true;
        try {
          const r = await fetch('http://127.0.0.1:18080/api/quick-reply/refresh', {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({strategy:s})});
          const d = await r.json();
          if (d.success && d.candidate) {
            quickReplyState.byStrategy[s] = d.candidate.replyText;
            renderQuickCards(quickReplyState.byStrategy);
          }
        } finally { b.disabled = false; }
      });
    });
  }

  async function generate() {
    const status = document.getElementById('qr-status');
    document.getElementById('qr-generate').disabled = true;
    status.textContent = '正在生成回复……';
    try {
      const res = await fetch('http://127.0.0.1:18080/api/quick-reply', {
        method: 'POST', headers: {'Content-Type':'application/json'}, body: '{}'
      });
      const data = await res.json();
      if (!data.success) throw new Error(data.error || data.status || 'failed');
      const byStrategy = {};
      (data.candidates || []).forEach(c => {
        byStrategy[normalizeStrategy(c.strategy)] = c.replyText;
      });
      quickReplyState = { generated: true, byStrategy };
      renderQuickCards(byStrategy);
      const hp = document.getElementById('home-page');
      if (hp) hp.classList.remove('initial');
      status.textContent = '完成';
    } catch (e) {
      status.textContent = '生成失败：' + e.message;
    } finally {
      document.getElementById('qr-generate').disabled = false;
    }
  }

  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  }

  global.JeveRouter = { navigate };
})(window);
  function bindSettings() {
    const el = document.getElementById('settings-profile');
    if (el) el.onclick = () => {
      console.log('[AIProfileUI] open-profile');
      if (window.desktopBridge && typeof window.desktopBridge.openProfilePage === 'function') {
        window.desktopBridge.openProfilePage();
        console.log('[AIProfileUI] external-browser-requested');
      } else {
        console.error('[AIProfileUI] openProfilePage bridge unavailable');
      }
    };
  }

  function bindAiConfig() {
    console.log('[AIConfigUI] open');
    const status = document.getElementById('ai-config-status');
    document.getElementById('ai-save').onclick = () => {
      const provider = document.getElementById('ai-provider').value;
      const baseUrl = document.getElementById('ai-base-url').value;
      const model = document.getElementById('ai-model').value;
      const apiKey = document.getElementById('ai-api-key').value;
      console.log('[AIConfigUI] save provider=', provider, 'model=', model);
      status.textContent = '配置已保存（TODO：后端 Web API 未暴露，仅前端记录）';
    };
    document.getElementById('ai-test').onclick = () => {
      console.log('[AIConfigUI] test-start');
      status.textContent = '测试中……（TODO：后端 Web API 未暴露）';
    };
  }
  /* ===== JEVE Dropdown controller ===== */
  function closeAllDropdowns(except) {
    document.querySelectorAll('.jeve-dropdown.open').forEach(d => {
      if (d !== except) {
        d.classList.remove('open', 'menu-up');
        console.log('[DropdownUI] close id=', d.id);
      }
    });
  }
  function initJeveDropdowns() {
    const dropdowns = document.querySelectorAll('.jeve-dropdown');
    console.log('[DropdownUI] init count=', dropdowns.length);
    dropdowns.forEach(dd => {
      if (dd.dataset.wired) return;
      dd.dataset.wired = '1';
      const trigger = dd.querySelector('.jeve-dropdown-trigger');
      trigger.addEventListener('click', (e) => {
        e.stopPropagation();
        const willOpen = !dd.classList.contains('open');
        closeAllDropdowns(dd);
        if (willOpen) {
          dd.classList.add('open');
          const rect = dd.getBoundingClientRect();
          const menuH = dd.querySelector('.jeve-dropdown-menu').offsetHeight;
          if (rect.bottom + menuH + 8 > window.innerHeight) dd.classList.add('menu-up');
          console.log('[DropdownUI] open id=', dd.id);
        } else {
          dd.classList.remove('open', 'menu-up');
          console.log('[DropdownUI] close id=', dd.id);
        }
      });
      dd.querySelectorAll('.jeve-dropdown-option').forEach(opt => {
        opt.addEventListener('click', (e) => {
          e.stopPropagation();
          const val = opt.dataset.value;
          const label = opt.textContent.trim();
          dd.dataset.value = val;
          dd.querySelector('.jeve-dropdown-trigger span').textContent = label;
          dd.querySelectorAll('.jeve-dropdown-option').forEach(o => o.classList.remove('selected'));
          opt.classList.add('selected');
          dd.classList.remove('open', 'menu-up');
          console.log('[DropdownUI] select id=', dd.id, 'value=', val);
          if (dd.id === 'contact-select') console.log('[ContactDebug] change value=', val);
          if (dd.id === 'skill-select') console.log('[SkillDebug] change value=', val);
          if (dd.id === 'ctx-len') console.log('[ContextLengthDebug] change value=', val);
        });
      });
    });
    document.onclick = () => closeAllDropdowns(null);
    document.onkeydown = (e) => { if (e.key === 'Escape') closeAllDropdowns(null); };
    window.onscroll = () => closeAllDropdowns(null);
  }