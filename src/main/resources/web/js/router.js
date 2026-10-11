(function (global) {
  // Stage UI polish: faster mouse wheel scroll on main content
  document.addEventListener('wheel', function(e) {
    const container = e.target.closest('.page-content') || e.target.closest('#app');
    if (!container) return;
    e.preventDefault();
    container.scrollTop += e.deltaY * 1.8;
  }, {passive: false});
  const PAGE_TITLES = {
    'home': 'RUI',
    'chat-history': '核对记录',
    'detail-analysis': '详细分析',
    'settings': '设置',
    'ai-observation': 'AI 观察',
    'ai-config': 'AI 配置',
    'collect-settings': '采集设置',
    'wechat-history': '微信聊天记录',
    'wechat-read': '从微信读取',
    'skill-management': 'Skill 管理',
    'theme-settings': '外观主题',
    'distill-identity': '人物蒸馏',
    'distill': '前任蒸馏'
  };
  const BACK_PAGES = { 'chat-history': true, 'detail-analysis': true, 'ai-observation': true, 'settings': true, 'ai-config': true, 'collect-settings': true, 'wechat-history': true, 'wechat-read': true, 'skill-management': true, 'theme-settings': true, 'distill-identity': true, 'distill': true };

  // Settings context: all pages under settings hierarchy — hide bottom nav & resync button
  const SETTINGS_PAGES = new Set(['settings', 'ai-config', 'collect-settings', 'wechat-history', 'wechat-read', 'ai-observation', 'skill-management', 'theme-settings', 'distill-identity', 'distill']);
  // Pages where bottom nav should also be hidden (already the target page)
  const BOTTOM_NAV_HIDDEN = new Set(['settings', 'ai-config', 'collect-settings', 'wechat-history', 'wechat-read', 'ai-observation', 'skill-management', 'theme-settings', 'distill-identity', 'distill', 'chat-history', 'detail-analysis']);
  // Parent hierarchy for back navigation
  const PAGE_PARENT = {
    'settings': 'home',
    'ai-config': 'settings',
    'collect-settings': 'settings',
    'wechat-history': 'settings',
    'wechat-read': 'wechat-history',
    'ai-observation': 'settings',
    'skill-management': 'settings',
    'theme-settings': 'settings',
    'distill-identity': 'skill-management',
    'distill': 'distill-identity',
    'chat-history': 'home',
    'detail-analysis': 'home'
  };

  const pages = {
    'home': renderHome,
    'ai-observation': renderDeepObservation,
    'settings': renderSettings,
    'ai-config': renderAiConfig,
    'collect-settings': renderCollectSettings,
    'wechat-history': renderWechatHistory,
    'wechat-read': renderWechatRead,
    'chat-history': renderChatHistory,
    'detail-analysis': renderDetailAnalysis,
    'skill-management': renderSkillManagement,
    'theme-settings': renderThemeSettings,
    'distill-identity': renderDistillIdentity,
    'distill': renderDistill
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
  let quickReplyState = { generated: false, byStrategy: {} };
  let currentRelationshipId = null;
  let currentSkillName = '';
  try { currentSkillName = localStorage.getItem('ruiActiveSkill') || ''; } catch(e) {}

  /* ===== Theme: Classic vs Desktop Glass ===== */
  function getTheme() {
    try { return localStorage.getItem('ruiTheme') || 'classic'; } catch(e) { return 'classic'; }
  }
  function applyTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    const glass = theme === 'desktop-glass';
    // Use setProperty with 'important' to override liquid-glass.css !important rules.
    if (glass) {
      document.documentElement.style.setProperty('background', 'transparent', 'important');
      document.documentElement.style.setProperty('background-image', 'none', 'important');
      document.body.style.setProperty('background', 'transparent', 'important');
      document.body.style.setProperty('background-image', 'none', 'important');
      document.querySelectorAll('.jeve-shell').forEach(el => {
        el.style.setProperty('background', 'rgba(22, 18, 48, 0.78)', 'important');
        el.style.setProperty('background-image', 'none', 'important');
      });
    } else {
      document.documentElement.style.removeProperty('background');
      document.documentElement.style.removeProperty('background-image');
      document.body.style.removeProperty('background');
      document.body.style.removeProperty('background-image');
      document.querySelectorAll('.jeve-shell').forEach(el => {
        el.style.removeProperty('background');
        el.style.removeProperty('background-image');
      });
    }
    if (window.desktopBridge && window.desktopBridge.diagTheme) {
      try {
        const htmlBg = getComputedStyle(document.documentElement).backgroundColor;
        const bodyBg = getComputedStyle(document.body).backgroundColor;
        const shell = document.querySelector('.jeve-shell');
        const shellBg = shell ? getComputedStyle(shell).backgroundColor : 'no-shell';
        const shellImg = shell ? getComputedStyle(shell).backgroundImage : 'no-shell';
        window.desktopBridge.diagTheme('applyTheme theme=' + theme +
          ' dataset=' + document.documentElement.dataset.theme +
          ' htmlBg=' + htmlBg +
          ' bodyBg=' + bodyBg +
          ' shellBg=' + shellBg +
          ' shellImg=' + shellImg);
      } catch(e) {}
    }
    if (window.desktopBridge && window.desktopBridge.setGlassTheme) {
      try { window.desktopBridge.setGlassTheme(glass); } catch(e) {}
    }
    if (window.desktopBridge && window.desktopBridge.diagScene) {
      try { window.desktopBridge.diagScene(); } catch(e) {}
    }
  }
  applyTheme(getTheme());
  // Bridge is injected after WebEngine loads; retry until it appears.
  let themeRetries = 0;
  const themeTimer = setInterval(() => {
    themeRetries++;
    if (window.desktopBridge && window.desktopBridge.setGlassTheme) {
      clearInterval(themeTimer);
      applyTheme(getTheme());
    } else if (themeRetries > 50) {
      clearInterval(themeTimer);
    }
  }, 200);
  const daCache = {}; // relationshipId -> {data}
  const qrCache = {}; // `${relId}|${skillName}` -> {byStrategy, single, singleSkillLabel}

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
  function updateCustomScrollbar(scrollEl, sbId, thumbId) {
    if (!scrollEl) return;
    const sb = document.getElementById(sbId);
    const thumb = document.getElementById(thumbId);
    if (!sb || !thumb) return;
    const sh = scrollEl.scrollHeight, ch = scrollEl.clientHeight, st = scrollEl.scrollTop;
    if (sh <= ch) { sb.classList.remove('visible'); return; }
    const thumbH = Math.max(24, ch * ch / sh);
    const maxTop = ch - thumbH;
    const maxScroll = sh - ch;
    const thumbT = maxScroll > 0 ? st / maxScroll * maxTop : 0;
    thumb.style.height = thumbH + 'px';
    thumb.style.top = thumbT + 'px';
    sb.classList.add('visible');
    clearTimeout(scrollbarTimers.get(sb));
    scrollbarTimers.set(sb, setTimeout(() => sb.classList.remove('visible'), 800));
  }
  function updateScrollThumb(list) {
    updateCustomScrollbar(list, 'ch-scrollbar', 'ch-thumb');
  }
  function updateMainScrollbar() {
    const main = document.getElementById('main-content');
    if (main && main.classList.contains('no-scroll')) return;
    updateCustomScrollbar(main, 'main-scrollbar', 'main-thumb');
  }
  function bindHeaderScroll(scrollEl) {
    const header = document.getElementById('jeve-header');
    if (!header) return;
    if (currentScrollEl === scrollEl && scrollEl) return; // already bound
    currentScrollEl = scrollEl;
    console.log('[HeaderDebug] bind scroll element=', scrollEl ? (scrollEl.id || scrollEl.className) : 'none');
    if (!scrollEl) return;
    scrollEl.addEventListener('scroll', () => {
      const st = scrollEl.scrollTop;
      if (st > lastScrollTop + 8 && st > 60) {
        header.classList.add('hidden');
      } else if (st < lastScrollTop - 4) {
        header.classList.remove('hidden');
      }
      lastScrollTop = st;
      console.log('[HeaderDebug] scrollTop=', st, 'headerHidden=', header.classList.contains('hidden'));
      pokeScrollbar(scrollEl);
      if (scrollEl.id === 'main-content') updateMainScrollbar();
    }, { passive: true });
  }

  function updateHeader(page) {
    document.getElementById('header-title').textContent = PAGE_TITLES[page] || 'JEVE';
    const back = document.getElementById('header-back');
    back.classList.toggle('visible', !!BACK_PAGES[page]);
    const settingsBtn = document.getElementById('header-settings');
    if (settingsBtn) settingsBtn.classList.toggle('hidden', page === 'settings');
    // Bottom nav: hidden on all settings-context pages
    const bottomNav = document.getElementById('bottom-nav');
    if (bottomNav) bottomNav.style.display = BOTTOM_NAV_HIDDEN.has(page) ? 'none' : '';
    // Resync button: show only on home and chat-history, hide on settings context
    const rsBtn = document.getElementById('header-resync');
    if (rsBtn) {
      if (SETTINGS_PAGES.has(page)) {
        rsBtn.style.display = 'none';
      } else if (typeof window.updateHeaderAction === 'function') {
        window.updateHeaderAction();
      }
    }
    lastScrollTop = 0;
    document.getElementById('jeve-header').classList.remove('hidden');
  }

  /* ===== Pages ===== */
  function renderHome() {
    const initial = !quickReplyState.generated;
    setTimeout(() => { loadContactSelector(); loadSkillSelector(); }, 0);
    return `
      <div class="home-page ${initial ? 'initial' : ''}" id="home-page">
        <div class="select-row">
          <div class="jeve-dropdown" id="contact-select" data-value="current">
            <button type="button" class="jeve-dropdown-trigger"><span id="contact-current">加载联系人…</span><span class="jeve-dropdown-arrow">˅</span></button>
            <div class="jeve-dropdown-menu">
              <div class="jeve-dropdown-menu-inner" id="contact-menu"></div>
              <div class="custom-scrollbar" id="contact-scrollbar"><div class="custom-scrollbar-thumb" id="contact-thumb"></div></div>
            </div>
          </div>
          <div class="jeve-dropdown" id="skill-select" data-value="">
            <button type="button" class="jeve-dropdown-trigger"><span id="skill-current">默认三策略</span><span class="jeve-dropdown-arrow">˅</span></button>
            <div class="jeve-dropdown-menu">
              <div class="jeve-dropdown-menu-inner" id="skill-menu">
                <button type="button" class="jeve-dropdown-option selected" data-value="">默认三策略</button>
              </div>
              <div class="custom-scrollbar" id="skill-scrollbar"><div class="custom-scrollbar-thumb" id="skill-thumb"></div></div>
            </div>
          </div>
        </div>
        <div class="qr-toolbar">
          <button id="qr-generate" class="primary qr-generate-big">生成回复</button>
          <span id="qr-status" class="muted"></span>
        </div>
        <div id="qr-grid" class="qr-grid"></div>
        <div class="chat-preview-wrap">
          <div class="chat-preview-headrow">
            <button id="chat-preview-toggle" class="chat-preview-toggle">聊天记录预览 <span id="chat-preview-arrow">˅</span></button>
            <button id="chat-preview-resync" class="chat-preview-resync" title="重新获取并刷新" style="display:none">↻</button>
          </div>
          <div id="chat-preview-panel" class="chat-preview-panel" style="display:none">
            <div class="chat-preview-viewport">
              <div id="chat-preview-list" class="chat-preview-list"></div>
              <div id="cp-scrollbar" class="custom-scrollbar"><div id="cp-thumb" class="custom-scrollbar-thumb"></div></div>
            </div>
          </div>
        </div>
      </div>`;
  }

  async function loadContactSelector() {
    const menu = document.getElementById('contact-menu');
    const cur = document.getElementById('contact-current');
    if (!menu || !cur) return;
    try {
      const res = await fetch('http://127.0.0.1:18080/api/relationships');
      const data = await res.json();
      const list = (data.relationships || []).slice(0, 10);
      if (!list.length) { cur.textContent = '暂无联系人'; return; }
      // Only auto-pick first contact on the very first load.
      // On later page re-renders, preserve currentRelationshipId to avoid wiping Quick Reply cache.
      if (currentRelationshipId == null) {
        selectContact(list.find(r => r.id === currentRelationshipId) || list[0]);
      } else {
        const curSel = list.find(r => r.id === currentRelationshipId);
        if (curSel) {
          if (cur) cur.textContent = curSel.name || ('联系人#' + curSel.id);
          const dd = document.getElementById('contact-select');
          if (dd) dd.dataset.value = curSel.id;
        }
      }
      menu.innerHTML = list.map(r =>
        `<button type="button" class="jeve-dropdown-option" data-value="${r.id}" data-name="${escapeHtml(r.name||'')}">${escapeHtml(r.name||('联系人#'+r.id))}</button>`
      ).join('');
      menu.querySelectorAll('.jeve-dropdown-option').forEach(btn => {
        btn.addEventListener('click', () => {
          const opt = list.find(x => String(x.id) === btn.dataset.value);
          if (opt) selectContact(opt);
        });
      });
    } catch (e) {
      cur.textContent = '联系人加载失败';
    }
  }

  function selectContact(r) {
    const cur = document.getElementById('contact-current');
    const dd = document.getElementById('contact-select');
    if (cur) cur.textContent = r.name || ('联系人#' + r.id);
    if (dd) dd.dataset.value = r.id;
    currentRelationshipId = r.id;
    // notify backend of current relationship id
    fetch('http://127.0.0.1:18080/api/use-relationship', {
      method: 'POST', headers: {'Content-Type':'application/json'},
      body: JSON.stringify({relationshipId: r.id})
    }).catch(() => {});
    // Switching contact: reset current view, but qrCache[oldRel|skill] stays for later restore.
    quickReplyState = { generated: false, byStrategy: {} };
    const grid = document.getElementById('qr-grid');
    if (grid) grid.innerHTML = '';
    const status = document.getElementById('qr-status');
    if (status) status.textContent = '';
    // Restore from cache for (new contact, current skill) if present.
    const key = r.id + '|' + currentSkillName;
    if (qrCache[key]) {
      quickReplyState = { generated: true, byStrategy: qrCache[key].byStrategy || {}, candidates: qrCache[key].candidates || [], single: !!qrCache[key].single, singleSkillLabel: qrCache[key].singleSkillLabel || '' };
      renderQuickCards(quickReplyState);
      const tb = document.querySelector('.qr-toolbar'); if (tb) tb.style.display = 'none';
    } else {
      const tb = document.querySelector('.qr-toolbar'); if (tb) tb.style.display = '';
    }
  }

  async function loadSkillSelector() {
    const menu = document.getElementById('skill-menu');
    const cur = document.getElementById('skill-current');
    if (!menu || !cur) return;
    try {
      const res = await fetch('http://127.0.0.1:18080/api/skills');
      const data = await res.json();
      const list = (data.skills || [])
        .filter(s => !['quick-reply','direct-reply'].includes(s.name))
        .filter(s => s.kind !== 'SYSTEM')
        .filter(s => s.enabled !== false);
      // If current selection was disabled, fall back to default
      if (currentSkillName && !list.find(s => s.name === currentSkillName)) {
        currentSkillName = '';
        try { localStorage.setItem('ruiActiveSkill', ''); } catch(e) {}
      }
      const opts = ['<button type="button" class="jeve-dropdown-option' + (currentSkillName === '' ? ' selected' : '') + '" data-value="">默认三策略</button>'];
      list.forEach(s => {
        opts.push('<button type="button" class="jeve-dropdown-option' + (currentSkillName === s.name ? ' selected' : '') + '" data-value="' + escapeHtml(s.name) + '">' + escapeHtml(s.name) + '</button>');
      });
      menu.innerHTML = opts.join('');
      // Sync trigger label with persisted currentSkillName (after returning to home page).
      cur.textContent = currentSkillName || '默认三策略';
      menu.querySelectorAll('.jeve-dropdown-option').forEach(btn => {
        btn.addEventListener('click', () => {
          currentSkillName = btn.dataset.value || '';
          try { localStorage.setItem('ruiActiveSkill', currentSkillName); } catch(e) {}
          cur.textContent = currentSkillName || '默认三策略';
          menu.querySelectorAll('.jeve-dropdown-option').forEach(b => b.classList.toggle('selected', b === btn));
          // Switching skill: reset current view; restore from (contact, skill) cache if present.
          quickReplyState = { generated: false, byStrategy: {} };
          const grid = document.getElementById('qr-grid');
          if (grid) grid.innerHTML = '';
          const st = document.getElementById('qr-status');
          if (st) st.textContent = '';
          const key = (currentRelationshipId || '?') + '|' + currentSkillName;
          if (qrCache[key]) {
            quickReplyState = { generated: true, byStrategy: qrCache[key].byStrategy || {}, candidates: qrCache[key].candidates || [], single: !!qrCache[key].single, singleSkillLabel: qrCache[key].singleSkillLabel || '' };
            renderQuickCards(quickReplyState);
            const tb = document.querySelector('.qr-toolbar'); if (tb) tb.style.display = 'none';
          } else {
            const tb = document.querySelector('.qr-toolbar'); if (tb) tb.style.display = '';
          }
        });
      });
    } catch (e) {
      cur.textContent = 'Skill 加载失败';
    }
  }

  function renderSettings() {
    const cur = getTheme();
    return `<div class="settings-list">
      <div class="qr-card settings-item" data-page="ai-config">
        <div class="settings-item-main"><h3>AI 配置</h3><p class="muted">服务商、API Key、Base URL、模型</p></div>
        <span class="settings-arrow">›</span>
      </div>
      <div class="qr-card settings-item" data-page="wechat-history">
        <div class="settings-item-main"><h3>微信聊天记录</h3><p class="muted">从微信读取或导入已有数据库</p></div>
        <span class="settings-arrow">›</span>
      </div>
      <div class="qr-card settings-item" data-page="collect-settings">
        <div class="settings-item-main"><h3>采集方式</h3><p class="muted">数据库读取 / 屏幕 OCR、每次读取数量</p></div>
        <span class="settings-arrow">›</span>
      </div>
      <div class="qr-card settings-item" data-page="ai-observation">
        <div class="settings-item-main"><h3>AI 观察</h3><p class="muted">深度观察与 AI 档案</p></div>
        <span class="settings-arrow">›</span>
      </div>
      <div class="qr-card settings-item" data-page="skill-management">
        <div class="settings-item-main"><h3>Skill 管理</h3><p class="muted" id="settings-current-skill">当前 Skill：<span id="settings-current-skill-name"></span></p></div>
        <span class="settings-arrow">›</span>
      </div>
      <div class="qr-card settings-item" data-page="theme-settings">
        <div class="settings-item-main"><h3>外观主题</h3><p class="muted">切换应用外观与玻璃效果</p></div>
        <span class="settings-arrow">›</span>
      </div>
      <div class="qr-card settings-item" id="settings-about" style="cursor:pointer;">
        <div class="settings-item-main"><h3>关于</h3><p class="muted">JEVE · Web UI V1。</p></div>
        <span class="settings-arrow">›</span>
      </div>
    </div>`;
  }

  function renderThemeSettings() {
    const cur = getTheme();
    return `<div class="settings-list" style="padding:12px;">
      <div class="qr-card theme-option-card ${cur==='classic'?'active':''}" data-theme-val="classic" style="cursor:pointer;padding:14px;">
        <h3 style="margin:0 0 4px;font-size:14px;">Classic</h3>
        <p class="muted" style="margin:0;">暖白稳定主题</p>
      </div>
      <div class="qr-card theme-option-card ${cur==='desktop-glass'?'active':''}" data-theme-val="desktop-glass" style="cursor:pointer;padding:14px;margin-top:10px;">
        <h3 style="margin:0 0 4px;font-size:14px;">Desktop Glass</h3>
        <p class="muted" style="margin:0;">紫色玻璃质感，桌面毛玻璃效果</p>
      </div>
    </div>`;
  }

  function renderDistillIdentity() {
    const cards = [
      { id: 'ex', title: '前任', desc: '基于聊天记录蒸馏前任的说话风格与共同记忆', enabled: true },
      { id: 'self', title: '自己', desc: '蒸馏本人的说话风格', enabled: false },
      { id: 'generic', title: '通用', desc: '通用人物画像 Skill', enabled: false }
    ];
    return `<div class="settings-list" style="padding:12px;">
      <div class="qr-card" style="padding:14px;">
        <h3 style="margin:0 0 6px;font-size:15px;">人物蒸馏</h3>
        <p class="muted" style="margin:0 0 12px;font-size:12px;line-height:1.5;">选择要蒸馏的人物身份，后续可扩展。</p>
        <div style="display:flex;flex-direction:column;gap:8px;">
          ${cards.map(c => `
            <div class="qr-card settings-item" data-identity="${c.id}" style="${c.enabled ? 'cursor:pointer;' : 'opacity:0.55;'}">
              <div class="settings-item-main">
                <h3 style="font-size:14px;margin:0 0 2px;">${c.title}${c.enabled ? '' : ' <span style="font-size:10px;color:#888;font-weight:normal;">· 暂未开放</span>'}</h3>
                <p class="muted" style="font-size:11px;margin:0;">${c.desc}</p>
              </div>
              ${c.enabled ? '<span class="settings-arrow">›</span>' : ''}
            </div>`).join('')}
        </div>
      </div>
    </div>`;
  }

  function bindDistillIdentity() {
    document.querySelectorAll('[data-identity]').forEach(card => {
      card.addEventListener('click', () => {
        const id = card.dataset.identity;
        if (id === 'ex') {
          navigate('distill');
        } else {
          const status = document.getElementById('distill-status-identity');
          // show a small toast-like message inline
          const hint = document.createElement('div');
          hint.style.cssText = 'margin-top:10px;font-size:12px;color:#ffb86b;';
          hint.textContent = '「' + card.querySelector('h3').childNodes[0].textContent.trim() + '」暂未开放，敬请期待。';
          // avoid duplicates
          const old = document.getElementById('distill-status-identity');
          if (old) old.remove();
          hint.id = 'distill-status-identity';
          card.parentElement.parentElement.appendChild(hint);
        }
      });
    });
  }

  function renderDistill() {
    return `<div class="settings-list" style="padding:12px;">
      <div class="qr-card" style="padding:14px;">
        <h3 style="margin:0 0 6px;font-size:15px;">前任 Skill 蒸馏</h3>
        <p class="muted" style="margin:0 0 10px;font-size:12px;line-height:1.5;">从聊天记录生成共同记忆 + Persona，可组合多种材料来源。</p>
        <div style="display:flex;flex-direction:column;gap:6px;">
          <input id="ex-nickname" placeholder="她的昵称/代号（如：小美）" style="padding:8px;border-radius:6px;background:rgba(255,255,255,0.08);border:1px solid rgba(139,92,246,0.3);color:var(--jeve-text,#eee);font-size:12px;">
          <input id="ex-relationship" placeholder="关系描述（如：在一起三年 大学同学）" style="padding:8px;border-radius:6px;background:rgba(255,255,255,0.08);border:1px solid rgba(139,92,246,0.3);color:var(--jeve-text,#eee);font-size:12px;">
          <input id="ex-tags" placeholder="性格标签（如：ENFP 焦虑型 爱撒娇）" style="padding:8px;border-radius:6px;background:rgba(255,255,255,0.08);border:1px solid rgba(139,92,246,0.3);color:var(--jeve-text,#eee);font-size:12px;">
        </div>
        <div style="margin-top:12px;">
          <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:6px;">
            <span style="font-size:12px;color:#a78bfa;font-weight:600;">材料来源</span>
            <span style="font-size:10px;color:#888;">支持多选，按顺序拼接</span>
          </div>
          <label style="display:flex;align-items:center;gap:8px;padding:8px;border-radius:6px;background:rgba(139,92,246,0.08);border:1px solid rgba(139,92,246,0.25);font-size:12px;color:var(--jeve-text,#eee);cursor:pointer;margin-bottom:6px;">
            <input type="checkbox" id="ex-use-db" checked style="accent-color:#7c3aed;">
            <span>使用 RUI 数据库聊天记录<span style="color:#888;font-size:10px;">（当前选中联系人）</span></span>
          </label>
          <div id="ex-materials" style="display:flex;flex-direction:column;gap:6px;"></div>
          <div style="display:flex;gap:6px;margin-top:6px;position:relative;">
            <div id="ex-add-type-wrap" style="flex:1;position:relative;">
              <button type="button" id="ex-add-type" data-value="WECHAT_FILE"
                style="width:100%;padding:6px;border-radius:6px;background:rgba(255,255,255,0.08);border:1px solid rgba(139,92,246,0.3);color:var(--jeve-text,#eee);font-size:12px;text-align:left;cursor:pointer;">微信导出文件</button>
              <div id="ex-add-type-panel" style="display:none;position:absolute;top:100%;left:0;right:0;margin-top:4px;background:rgba(32,27,65,0.96);border:1px solid rgba(167,139,250,0.4);border-radius:8px;z-index:50;box-shadow:0 8px 24px rgba(0,0,0,0.4);overflow:hidden;">
                <div data-val="WECHAT_FILE" class="ex-dd-item">微信导出文件</div>
                <div data-val="QQ_FILE" class="ex-dd-item">QQ 导出文件</div>
                <div data-val="SMS_FILE" class="ex-dd-item">短信导出（需 Python）</div>
                <div data-val="SOCIAL_FILE" class="ex-dd-item">社交媒体导出（需 Python）</div>
                <div data-val="PHOTO_DIR" class="ex-dd-item">照片目录（需 Python）</div>
              </div>
            </div>
            <button id="ex-add" style="flex-shrink:0;padding:6px 12px;font-size:12px;border:1px solid rgba(139,92,246,0.4);background:rgba(139,92,246,0.1);color:#a78bfa;border-radius:6px;cursor:pointer;">添加</button>
          </div>
          <div class="muted" style="font-size:10px;margin-top:4px;color:#888;">注：短信/社交媒体/照片目录需本机 Python。iMessage 仅支持 macOS，未接入。</div>
        </div>
        <button id="ex-start" style="margin-top:12px;padding:10px;border:none;border-radius:8px;background:linear-gradient(135deg,#7c3aed,#6366f1);color:#fff;cursor:pointer;font-size:13px;width:100%;">蒸馏前任（约 60-120 秒）</button>
        <div id="distill-status" class="muted" style="margin-top:10px;font-size:12px;"></div>
      </div>
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
        <div class="modal-row">
          <label>AI 上下文条数</label>
          <div class="jeve-dropdown" id="ctx-len" data-value="10">
            <button type="button" class="jeve-dropdown-trigger"><span>10 条</span><span class="jeve-dropdown-arrow">˅</span></button>
            <div class="jeve-dropdown-menu">
              <button type="button" class="jeve-dropdown-option selected" data-value="10">10 条</button>
              <button type="button" class="jeve-dropdown-option" data-value="20">20 条</button>
              <button type="button" class="jeve-dropdown-option" data-value="50">50 条</button>
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

  function renderCollectSettings() {
    return `
      <div class="settings-list">
        <div class="qr-card"><h3>采集方式</h3>
          <div class="jeve-dropdown" id="source-type" data-value="database">
            <button type="button" class="jeve-dropdown-trigger"><span id="source-type-label">数据库读取</span><span class="jeve-dropdown-arrow">˅</span></button>
            <div class="jeve-dropdown-menu">
              <button type="button" class="jeve-dropdown-option selected" data-value="database">数据库读取</button>
              <button type="button" class="jeve-dropdown-option" data-value="ocr">屏幕 OCR</button>
            </div>
          </div></div>
        <div class="qr-card"><h3>每次读取数量</h3>
          <div class="jeve-dropdown" id="batch-size" data-value="50">
            <button type="button" class="jeve-dropdown-trigger"><span id="batch-size-label">50 条</span><span class="jeve-dropdown-arrow">˅</span></button>
            <div class="jeve-dropdown-menu">
              <button type="button" class="jeve-dropdown-option selected" data-value="20">20 条</button>
              <button type="button" class="jeve-dropdown-option" data-value="50">50 条</button>
              <button type="button" class="jeve-dropdown-option" data-value="100">100 条</button>
              <button type="button" class="jeve-dropdown-option" data-value="200">200 条</button>
              <button type="button" class="jeve-dropdown-option" data-value="500">500 条</button>
            </div>
          </div></div>
        <div class="qr-card"><p class="muted">采集方式切换时自动停止当前运行的采集。数据库模式主界面显示 ↻ 按钮，OCR 模式显示采集开关。</p></div>
      </div>`;
  }

  function renderWechatHistory() {
    return `<div class="settings-list">
      <div class="qr-card settings-item" data-page="wechat-read" style="cursor:pointer">
        <div class="settings-item-main"><h3>从微信读取</h3><p class="muted">直接读取当前电脑上已登录的微信聊天记录，无需准备文件</p></div>
        <span class="settings-arrow">›</span>
      </div>
      <div class="qr-card settings-item" id="wh-import-existing" style="cursor:pointer">
        <div class="settings-item-main"><h3>导入已有数据库</h3><p class="muted">使用你自己提供的聊天数据库文件进行导入</p></div>
        <span class="settings-arrow">›</span>
      </div>
    </div>`;
  }

  function renderWechatRead() {
    return `<div class="settings-list" id="wechat-read-root" style="display:flex;flex-direction:column;min-height:calc(100vh - 140px);">
      <div class="qr-card" id="wr-status-card">
        <p class="muted">正在检查微信环境…</p>
      </div>
      <div class="qr-card" id="wr-accounts" style="display:none"></div>
      <div class="qr-card" id="wr-contacts" style="display:none"></div>
      <div style="flex:1"></div>
      <p style="text-align:center;color:var(--jeve-text-secondary,#43534F);font-size:12px;margin:16px auto 8px;line-height:1.6;width:84%;max-width:420px;">微信需要安装并登录。RUI 会自动检测微信本地聊天数据，无需手动填写数据库路径或微信号。</p>
    </div>`;
  }

  function renderChatHistory() {
    return `<div class="chat-page">
      <div id="ch-toolbar" class="qr-toolbar" style="padding:8px 12px;">
        <span id="ch-resync-status" class="muted"></span>
      </div>
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
    return `<div class="qr-card" style="margin-bottom:12px;">
      <h3>AI 档案</h3>
      <p class="muted" style="font-size:12px;">管理我的档案与对方档案</p>
      <button class="primary" style="margin-top:8px;" id="do-open-profile">进入 AI 档案</button>
    </div>
    <p class="muted">这段关系一路发生了什么？</p>
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

  function daKey() {
    return (currentRelationshipId || '?') + '|' + (currentSkillName || 'default');
  }

  function renderDetailAnalysis() {
    const key = daKey();
    const cached = currentRelationshipId != null ? daCache[key] : null;
    if (cached) {
      setTimeout(() => {
        renderDetailAnalysisResult(cached);
        const st = document.getElementById('da-status');
        if (st) { st.textContent = ''; st.style.display = 'none'; }
      }, 0);
      return `<div id="da-status" class="page-placeholder" style="display:none"></div>
        <div id="da-toolbar" class="qr-toolbar"></div>
        <div id="da-result"></div>`;
    }
    // No cached result: show idle CTA, do NOT auto-call AI.
    setTimeout(() => {
      const st = document.getElementById('da-status');
      if (st) {
        st.style.display = '';
        const skillLabel = currentSkillName === '' ? '默认三策略' : (currentSkillName || '默认三策略');
        let hint = '当前使用 Skill：' + skillLabel;
        if (currentSkillName === '') hint += '。默认三策略的详细分析使用"狗头军师"的分析能力。';
        st.innerHTML = '<div style="text-align:center;padding:32px 12px;">'
          + '<div class="muted" style="margin-bottom:10px">还没有当前联系人的分析结果</div>'
          + '<div class="muted" style="font-size:11px;margin-bottom:14px;opacity:0.7;">' + escapeHtml(hint) + '</div>'
          + '<button id="da-generate" class="primary" style="min-width:140px">生成分析</button>'
          + '</div>';
        const g = document.getElementById('da-generate');
        if (g) g.onclick = () => runDetailAnalysis(true);
      }
    }, 0);
    return `<div id="da-status" class="page-placeholder"></div>
      <div id="da-toolbar" class="qr-toolbar"></div>
      <div id="da-result"></div>`;
  }

  /* ===== Detail analysis (Phase 22: progressive disclosure) ===== */
  async function runDetailAnalysis(force) {
    const status = document.getElementById('da-status');
    const key = daKey();
    if (!force && currentRelationshipId != null && daCache[key]) {
      renderDetailAnalysisResult(daCache[key]);
      return;
    }
    try {
      if (status) status.textContent = '正在分析最近 7 天的互动……';
      const res = await fetch('http://127.0.0.1:18080/api/detail-analysis-v2', {
        method: 'POST', headers: {'Content-Type':'application/json'},
        body: JSON.stringify({skillName: currentSkillName || '', selectedDocumentIds: getCurrentSkillSelectedDocs()})
      });
      const data = await res.json();
      const st = document.getElementById('da-status');
      if (!data.success) { if (st) st.textContent = '暂时无法完成分析：' + (data.status || data.error || data.code || ''); return; }
      if (data.status === 'NO_DATA') { if (st) st.textContent = data.message || '当前没有可分析的聊天记录。'; return; }
      if (st) st.textContent = '';
      if (currentRelationshipId != null) daCache[key] = data;
      renderDetailAnalysisResult(data);
    } catch (e) {
      const st = document.getElementById('da-status');
      if (st) st.textContent = '请求失败：' + e.message;
    }
  }

  function renderDetailAnalysisResult(data) {
      const summary = data.summary || '';
      const meE = (data.emotions && data.emotions.me) || [];
      const otherE = (data.emotions && data.emotions.other) || [];
      const facts = data.facts || [];
      const inferences = data.inferences || [];
      const possibilities = data.possibilities || [];
      const rec = data.recommendation || {action:'', reason:''};
      const selfCare = data.selfCare || {show:false, reason:''};
      const report = data.reportMarkdown || '';
      const reportKind = data.reportKind || '';
      if (data.notice) showToast(data.notice);

      // Stage 5-2: SHORT_ANALYSIS 分支 —— 不渲染 emotions/facts 长 schema，直接给一段短评。
      if (reportKind === 'SHORT_ANALYSIS') {
        document.getElementById('da-result').innerHTML =
          '<div class="da-card"><div style="padding:12px;white-space:pre-wrap;line-height:1.7;">' + escapeHtml(report || '（暂无内容）') + '</div></div>';
        return;
      }

      const confLabel = {high:'较高', medium:'中等', low:'较低'};

      const sections = [];
      // 1. summary
      if (summary) {
        sections.push(disclosureCard('核心结论', escapeHtml(oneLinePreview(summary, 40)), renderMd(summary), true));
      }
      // 2. emotions
      if (meE.length || otherE.length) {
        const body = emotionBlock('我', meE) + emotionBlock('对方', otherE);
        const teaser = (meE[0] ? meE[0].label : '') + (otherE[0] ? ' · 对方：' + otherE[0].label : '');
        sections.push(disclosureCard('当前情绪', teaser, body, true));
      }
      // 3. recommendation
      if (rec.action || rec.reason) {
        const recText = rec.action + (rec.reason ? '：' + rec.reason : '');
        sections.push(disclosureCard('军师建议', escapeHtml(oneLinePreview(recText, 40)),
          '<div class="da-rec">' + renderMd(rec.reason || '') + '</div>', true));
      }
      // 4. self-care
      if (selfCare.show && selfCare.reason) {
        sections.push(disclosureCard('照顾一下你自己的情绪', escapeHtml(oneLinePreview(selfCare.reason, 40)), renderMd(selfCare.reason), true));
      }
      // 5. possibilities
      if (possibilities.length) {
        const renderOne = (p) => '<div class="da-poss"><span class="da-conf da-conf-' + escapeHtml(p.confidence||'medium') + '">' +
          (confLabel[p.confidence] || p.confidence || '') + '</span> ' + escapeHtml(p.content) + '</div>';
        const full = possibilities.map(renderOne).join('');
        const topN = possibilities.slice(0, Math.min(1, possibilities.length)).map(renderOne).join('');
        sections.push(disclosureCard('对方可能意图', topN, full, true));
      }
      // 6. facts
      if (facts.length) {
        const full = renderMd(facts.join('\n'));
        const topN = facts.slice(0, Math.min(1, facts.length)).join(' ');
        sections.push(disclosureCard('已知事实', escapeHtml(oneLinePreview(topN, 40)), full, true));
      }
      // 7. inferences
      if (inferences.length) {
        const full = renderMd(inferences.join('\n'));
        const topN = inferences.slice(0, Math.min(1, inferences.length)).join(' ');
        sections.push(disclosureCard('合理推测', escapeHtml(oneLinePreview(topN, 40)), full, true));
      }
      // 8. full report
      if (report && !report.trim().startsWith('{') && !report.trim().startsWith('[')) {
        sections.push(disclosureCard('完整分析报告', escapeHtml(oneLinePreview(report, 40)),
          '<div class="da-markdown">' + renderMd(report) + '</div>', true));
      }

      if (!sections.length) {
        sections.push('<div class="da-card" style="padding:16px;color:var(--color-text-muted);font-size:13px;">分析结果解析中，请稍后重试。</div>');
      }
      document.getElementById('da-result').innerHTML = sections.join('\n');
      // wire toggle
      document.querySelectorAll('.da-toggle').forEach(btn => {
        btn.addEventListener('click', () => {
          const card = btn.closest('.da-card');
          const body = card.querySelector('.da-toggle-body');
          const teaser = card.querySelector('.da-card-teaser');
          const expanded = body.classList.toggle('hidden') === false;
          if (teaser) teaser.style.display = expanded ? 'none' : '';
        });
      });
  }

  // Minimal safe Markdown → HTML for Detailed Analysis.
  // Handles: headings (##, ###), bold **, lists (-/numbered), paragraphs.
  // Escapes HTML first, so LLM content cannot inject script/iframe.
  function renderMd(src) {
    if (!src) return '';
    const esc = escapeHtml(src);
    const lines = esc.split('\n');
    let html = '';
    let inList = false;
    for (const line of lines) {
      const t = line.trim();
      if (!t) { if (inList) { html += '</ul>'; inList = false; } continue; }
      let m;
      if ((m = t.match(/^#{1,4}\s+(.*)/))) {
        if (inList) { html += '</ul>'; inList = false; }
        html += '<div class="da-md-h">' + m[1] + '</div>';
      } else if ((m = t.match(/^[-•]\s+(.*)/))) {
        if (!inList) { html += '<ul class="da-md-ul">'; inList = true; }
        html += '<li>' + m[1] + '</li>';
      } else if ((m = t.match(/^\d+[.、)]\s*(.*)/))) {
        if (!inList) { html += '<ul class="da-md-ul">'; inList = true; }
        html += '<li>' + m[1] + '</li>';
      } else {
        if (inList) { html += '</ul>'; inList = false; }
        html += '<div class="da-md-p">' + t + '</div>';
      }
    }
    if (inList) html += '</ul>';
    // bold **text**
    html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
    return html;
  }

  function oneLinePreview(text, maxLen) {
    if (!text) return '';
    const flat = text.replace(/\s+/g, ' ').trim();
    return flat.length > (maxLen || 50) ? flat.substring(0, maxLen || 50) + '…' : flat;
  }

  function emotionBlock(title, list) {
    if (!list.length) return '<div class="da-emotion-row"><b>' + title + '：</b><span class="muted">信息不足，暂不判断</span></div>';
    return '<div class="da-emotion-row"><b>' + title + '：</b>' +
      list.map(e => '<span class="da-emotion">' + escapeHtml(e.label) +
        (e.hint ? '<span class="da-emotion-hint">（' + escapeHtml(e.hint) + '）</span>' : '') +
        '</span>').join('') + '</div>';
  }

  function disclosureCard(title, teaserHtml, bodyHtml, collapsible) {
    const toggle = collapsible ? '<button class="da-toggle">展开/收起</button>' : '';
    const teaser = (collapsible && teaserHtml)
      ? '<div class="da-card-teaser">' + teaserHtml + '</div>' : '';
    return '<div class="da-card">' +
      '<div class="da-card-head"><span class="da-card-title">' + escapeHtml(title) + '</span>' + toggle + '</div>' +
      teaser +
      '<div class="da-toggle-body' + (collapsible ? ' hidden' : '') + '">' + bodyHtml + '</div>' +
      '</div>';
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
    window.__jevePage = page;
    console.log('[RouteDebug] navigate from=' + currentPage + ' to=' + page);
    console.log('[WebUI][INFO] navigate:', page);
    const fn = pages[page];
    if (!fn) { console.log('[RouteDebug] MISSING page render fn for=' + page); return; }
    currentPage = page;
    document.body.className = document.body.className.replace(/\bpage-\S+/g, '').trim();
    document.body.classList.add('page-' + page);
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
      const resolved = PAGE_PARENT[page] || 'home';
      console.log('[BackDebug] page=' + page + ' -> parent=' + resolved);
      navigate(resolved);
    };
    if (page === 'home') {
      bindHome();
      // Restore toolbar visibility based on whether results already exist
      const tb = document.querySelector('.qr-toolbar');
      if (tb && quickReplyState && quickReplyState.generated) tb.style.display = 'none';
      if (tb && quickReplyState && quickReplyState.items && quickReplyState.items.length) tb.style.display = 'none';
    }
    if (page === 'ai-observation') bindDeepObs();
    // Recalc main custom scrollbar after content renders.
    setTimeout(updateMainScrollbar, 0);
    if (page === 'chat-history') {
      loadChatHistory();
      // Auto-refresh a few times if a recent import was triggered (background sync still writing)
      try {
        const lastImport = parseInt(localStorage.getItem('ruiLastImportAt') || '0', 10);
        if (lastImport && (Date.now() - lastImport) < 15000) {
          [3000, 6000, 10000].forEach(d => setTimeout(() => {
            if (window.__jevePage === 'chat-history') loadChatHistory();
          }, d));
        }
      } catch(e){}
    }
    if (page === 'ai-config') bindAiConfig();
    if (page === 'collect-settings') bindCollectSettings();
    if (page === 'wechat-history') bindWechatHistory();
    if (page === 'wechat-read') bindWechatRead();
    initJeveDropdowns();
    if (page === 'settings' || page === 'theme-settings') { bindSettings(); const sl = document.getElementById('settings-current-skill-name'); if (sl) sl.textContent = currentSkillName || '默认三策略'; }
    if (page === 'skill-management') bindSkillManagement();
    if (page === 'distill-identity') bindDistillIdentity();
    if (page === 'distill') bindDistill();
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
    // Clear pressed state
    document.querySelectorAll('.bubble.pressed').forEach(b => b.classList.remove('pressed'));
  }
  let editTargetId = null;
  document.addEventListener('contextmenu', (e) => {
    e.preventDefault();
    const menu = document.getElementById('ctx-menu');
    if (e.target.closest && e.target.closest('#ctx-menu')) return;
    const bubble = e.target.closest ? e.target.closest('.bubble') : null;
    if (!bubble) { closeCtxMenu(); return; }
    const row = bubble.closest('.bubble-row');
    ctxTargetId = row ? row.dataset.id : null;
    document.querySelectorAll('.bubble.pressed').forEach(b => b.classList.remove('pressed'));
    bubble.classList.add('pressed');
    menu.style.display = 'block';
    let left = e.clientX, top = e.clientY;
    const r = menu.getBoundingClientRect();
    const vw = window.innerWidth, vh = window.innerHeight;
    if (left + r.width > vw) left = vw - r.width - 8;
    if (top + r.height > vh) top = vh - r.height - 8;
    left = Math.max(8, left); top = Math.max(8, top);
    menu.style.left = left + 'px';
    menu.style.top = top + 'px';
    ctxMenuOpenAt = Date.now();
  });
  let ctxMenuOpenAt = 0;
  // 只在菜单外部点击时关闭；菜单项自身的点击由 1073 行处理。
  // 300ms 内忽略 click，避免 contextmenu 后的合成 click 立即关闭菜单。
  document.addEventListener('click', (e) => {
    if (Date.now() - ctxMenuOpenAt < 300) return;
    const menu = document.getElementById('ctx-menu');
    if (menu && menu.style.display === 'block' && !menu.contains(e.target)) {
      menu.style.display = 'none';
      document.querySelectorAll('.bubble.pressed').forEach(b => b.classList.remove('pressed'));
    }
  });
  document.addEventListener('scroll', () => {
    // Don't close on scroll within 500ms of opening (prevents reflow-triggered flicker)
    if (Date.now() - ctxMenuOpenAt < 500) return;
    closeCtxMenu();
  }, true);
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
    const profileBtn = document.getElementById('do-open-profile');
    if (profileBtn) profileBtn.onclick = () => {
      if (window.desktopBridge && typeof window.desktopBridge.openProfilePage === 'function') {
        window.desktopBridge.openProfilePage();
      }
    };
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
    if (quickReplyState.generated) renderQuickCards(quickReplyState);
    const previewBtn = document.getElementById('chat-preview-toggle');
    if (previewBtn) previewBtn.addEventListener('click', toggleChatPreview);
    const prs = document.getElementById('chat-preview-resync');
    if (prs) prs.addEventListener('click', chatPreviewResync);
  }

  let chatPreviewTimer = null;
  let chatPreviewLastSig = '';

  async function toggleChatPreview() {
    const panel = document.getElementById('chat-preview-panel');
    const arrow = document.getElementById('chat-preview-arrow');
    const rs = document.getElementById('chat-preview-resync');
    if (!panel || !arrow) return;
    const open = panel.style.display !== 'none';
    if (open) {
      panel.style.display = 'none';
      arrow.textContent = '˅';
      if (rs) rs.style.display = 'none';
      if (chatPreviewTimer) { clearInterval(chatPreviewTimer); chatPreviewTimer = null; }
      return;
    }
    panel.style.display = 'block';
    arrow.textContent = '˄';
    if (rs) rs.style.display = '';
    chatPreviewLastSig = '';  // force re-render on each open
    await refreshChatPreview();
    if (chatPreviewTimer) clearInterval(chatPreviewTimer);
    chatPreviewTimer = setInterval(refreshChatPreview, 1000);
  }

  async function chatPreviewResync() {
    try {
      await fetch('http://127.0.0.1:18080/api/dbsource/sync', { method: 'POST' });
    } catch (e) { console.warn('[chatPreview] sync failed', e); }
    chatPreviewLastSig = '';
    await refreshChatPreview();
  }

  async function refreshChatPreview() {
    const list = document.getElementById('chat-preview-list');
    if (!list) return;
    list.onscroll = () => updateCustomScrollbar(list, 'cp-scrollbar', 'cp-thumb');
    try {
      const ctxSize = parseInt(localStorage.getItem('aiContextSize') || '10', 10);
      const res = await fetch('http://127.0.0.1:18080/api/chat-history?size=' + ctxSize);
      const d = await res.json();
      const msgs = (d.messages || []).slice().reverse();
      const sig = msgs.length + '|' + (msgs[msgs.length-1] ? msgs[msgs.length-1].id : 0);
      const nearBottom = (list.scrollHeight - list.scrollTop - list.clientHeight) < 30;
      if (sig !== chatPreviewLastSig) {
        chatPreviewLastSig = sig;
        list.innerHTML = msgs.map(m => {
          const sp = m.speaker || m.senderType || 'OTHER';
          const who = (sp === 'ME' || sp === 'SELF') ? '我' : '对方';
          return `<div class="chat-preview-msg"><span class="chat-preview-who">${who}：</span>${escapeHtml(m.content || '')}</div>`;
        }).join('');
        if (nearBottom) requestAnimationFrame(() => { list.scrollTop = list.scrollHeight; });
      }
      requestAnimationFrame(() => updateCustomScrollbar(list, 'cp-scrollbar', 'cp-thumb'));
    } catch (e) {
      // silently skip poll errors; don't wipe existing messages
      console.warn('[chatPreview] refresh failed', e);
    }
  }
  function renderQuickCards(state) {
    const grid = document.getElementById('qr-grid');
    if (!grid) return;
    const byStrategy = state.byStrategy || {};
    const candidates = state.candidates || [];
    const single = !!state.single;
    const singleSkillLabel = state.singleSkillLabel || '';
    const items = state.items || [];

    // ── Stage 5-2: 权威 items[] 渲染 ─────────────────────────────────────
    const sendables = items.filter(it => it.type === 'SENDABLE_REPLY');
    const shorts = items.filter(it => it.type === 'SHORT_ANALYSIS');

    // Case: 默认三策略 —— sendables 带 strategyKey，按 NATURAL/PROACTIVE/LIGHT_FLIRT 分组。
    const grouped = {};
    sendables.forEach((it, idx) => {
      const k = it.strategyKey || 'SINGLE';
      (grouped[k] = grouped[k] || []).push({...it, _idx: idx});
    });
    const hasGroups = grouped.NATURAL || grouped.PROACTIVE || grouped.LIGHT_FLIRT;

    if (hasGroups) {
      const order = ['NATURAL','PROACTIVE','LIGHT_FLIRT'];
      const labels = {NATURAL:'自然', PROACTIVE:'主动', LIGHT_FLIRT:'轻微暧昧'};
      let html = '';
      order.forEach(k => {
        const arr = grouped[k] || [];
        if (!arr.length) return;
        html += `<div class="qr-group-title-row"><span>${escapeHtml(labels[k])}</span><button class="qr-refresh" data-strategy="${k}" title="重新获取">↻</button></div>`;
        html += '<div class="qr-card qr-group-card">';
        arr.forEach((it, idx) => {
          if (idx > 0) html += '<div class="qr-item-divider"></div>';
          html += `
            <div class="qr-item">
              <div class="qr-text">${escapeHtml(it.text || '（无候选）')}</div>
              <div class="qr-actions"><button data-use="${escapeHtml(it.text || '')}">复制</button></div>
            </div>`;
        });
        html += '</div>';
      });
      if (!html) html = '<div class="qr-empty">（未生成回复）</div>';
      grid.innerHTML = html;
      grid.querySelectorAll('[data-use]').forEach(b => {
        b.addEventListener('click', async () => {
          await fetch('http://127.0.0.1:18080/api/clipboard', {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({text:b.dataset.use})});
          b.textContent='已复制';
        });
      });
      grid.querySelectorAll('.qr-refresh').forEach(b => {
        b.addEventListener('click', async (e) => {
          e.stopPropagation();
          const origText = b.textContent;
          b.disabled = true; b.textContent = '◌';
          try {
            const s = b.dataset.strategy;
            // Use the v2 endpoint (known working) and extract only the requested strategy
            const r = await fetch('http://127.0.0.1:18080/api/quick-reply-v2', {
              method: 'POST', headers: {'Content-Type':'application/json'},
              body: JSON.stringify({skillName: currentSkillName || '', selectedDocumentIds: getCurrentSkillSelectedDocs()})
            });
            const d = await r.json();
            if (d.success && Array.isArray(d.items)) {
              const newItems = d.items.filter(it => it.type === 'SENDABLE_REPLY');
              // Replace items for this strategy
              const otherItems = (quickReplyState.items || []).filter(it => it.type !== 'SENDABLE_REPLY' || it.strategyKey !== s);
              const newStratItems = newItems.filter(it => it.strategyKey === s);
              quickReplyState.items = [...otherItems, ...newStratItems];
              renderQuickCards(quickReplyState);
            }
          } catch(err) {
            // On error, just re-render to restore button
            renderQuickCards(quickReplyState);
          }
        });
      });
      return;
    }

    // Case: tong-jincheng —— 1 条 SENDABLE_REPLY + 1 条 SHORT_ANALYSIS。
    if (sendables.length && shorts.length) {
      grid.innerHTML = `
        <div class="qr-card">
          <div class="qr-head"><h3>可发送回复</h3></div>
          <div class="qr-text">${escapeHtml(sendables[0].text || '（无候选）')}</div>
          <div class="qr-actions"><button data-use="${escapeHtml(sendables[0].text || '')}">复制</button></div>
        </div>
        <div class="qr-card qr-short-analysis" style="padding:8px 12px;">
          <div class="qr-head" style="padding:0;margin-bottom:2px;"><h3 style="font-size:13px;font-weight:600;color:var(--color-text-primary);">视角点评 <button class="sa-toggle" style="background:none;border:none;color:var(--color-accent);font-size:11px;cursor:pointer;font-weight:normal;">展开</button></h3></div>
          <div class="sa-body" style="display:none;color:#6d5bd0;line-height:1.6;font-size:13px;font-weight:normal;">${escapeHtml(shorts[0].text || '')}</div>
        </div>`;
      const b = grid.querySelector('[data-use]');
      if (b) b.addEventListener('click', async () => {
        await fetch('http://127.0.0.1:18080/api/clipboard', {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({text:b.dataset.use})});
        b.textContent='已复制';
      });
      const saBtn = grid.querySelector('.sa-toggle');
      const saBody = grid.querySelector('.sa-body');
      if (saBtn && saBody) saBtn.addEventListener('click', () => {
        const open = saBody.style.display !== 'none';
        saBody.style.display = open ? 'none' : 'block';
        saBtn.textContent = open ? '展开' : '收起';
      });
      return;
    }

    // Case: 单条 SENDABLE_REPLY（goutoujunshi / quick-reply / 兜底）。
    if (sendables.length === 1) {
      const text = sendables[0].text || '';
      grid.innerHTML = `
        <div class="qr-card">
          <div class="qr-head">
            <h3>${escapeHtml(singleSkillLabel || '回复')}</h3>
            <button class="qr-refresh" data-single="1" title="刷新">↻</button>
          </div>
          <div class="qr-text">${escapeHtml(text || '（无候选）')}</div>
          <div class="qr-actions"><button data-use="${escapeHtml(text)}">复制</button></div>
        </div>`;
      const b = grid.querySelector('[data-use]');
      if (b) b.addEventListener('click', async () => {
        await fetch('http://127.0.0.1:18080/api/clipboard', {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({text:b.dataset.use})});
        b.textContent='已复制';
      });
      const rb = grid.querySelector('.qr-refresh');
      if (rb) rb.addEventListener('click', async (e) => {
        e.stopPropagation(); rb.disabled = true;
        try { await generate(); } finally { rb.disabled = false; }
      });
      return;
    }

    // ── 兼容旧 candidates[] 渲染 ─────────────────────────────────────────
    if (single) {
      const text = (candidates[0] && candidates[0].replyText) || byStrategy.SINGLE || '';
      grid.innerHTML = `
        <div class="qr-card">
          <div class="qr-head">
            <h3>${escapeHtml(singleSkillLabel || '回复')}</h3>
            <button class="qr-refresh" data-single="1" title="刷新">↻</button>
          </div>
          <div class="qr-text">${escapeHtml(text || '（无候选）')}</div>
          <div class="qr-actions"><button data-use="${escapeHtml(text)}">使用</button></div>
        </div>`;
      grid.querySelectorAll('[data-use]').forEach(b => {
        b.addEventListener('click', async () => {
          await fetch('http://127.0.0.1:18080/api/clipboard', {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({text:b.dataset.use})});
          b.textContent='已复制';
        });
      });
      const rb = grid.querySelector('.qr-refresh');
      if (rb) rb.addEventListener('click', async (e) => {
        e.stopPropagation(); rb.disabled = true;
        try { await generate(); } finally { rb.disabled = false; }
      });
      return;
    }
    const cards = candidates.length ? candidates : Object.keys(byStrategy).map(k => ({
      strategy: k, strategyLabel: STRATEGY_LABEL[k] || k, replyText: byStrategy[k]
    }));
    grid.innerHTML = cards.map(c => {
      const label = c.strategyLabel || STRATEGY_LABEL[c.strategy] || c.strategy;
      const text = c.replyText || '';
      const isLegacy = (c.strategy === 'NATURAL' || c.strategy === 'PROACTIVE' || c.strategy === 'LIGHT_FLIRT');
      return `
        <div class="qr-card">
          <div class="qr-head">
            <h3>${escapeHtml(label)}</h3>
            <button class="qr-refresh" ${isLegacy ? `data-strategy="${escapeHtml(c.strategy)}"` : `data-custom="1"`} title="刷新">↻</button>
          </div>
          <div class="qr-text">${escapeHtml(text || '（无候选）')}</div>
          <div class="qr-actions"><button data-use="${escapeHtml(text)}">使用</button></div>
        </div>`;
    }).join('');
    grid.querySelectorAll('[data-use]').forEach(b => {
      b.addEventListener('click', async () => {
        await fetch('http://127.0.0.1:18080/api/clipboard', {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({text:b.dataset.use})});
        b.textContent='已复制';
      });
    });
    grid.querySelectorAll('.qr-refresh').forEach(b => {
      b.addEventListener('click', async (e) => {
        e.stopPropagation(); b.disabled = true;
        try {
          if (b.dataset.strategy) {
            const s = b.dataset.strategy;
            const r = await fetch('http://127.0.0.1:18080/api/quick-reply/refresh', {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({strategy:s})});
            const d = await r.json();
            if (d.success && d.candidate) {
              quickReplyState.byStrategy[s] = d.candidate.replyText;
              renderQuickCards(quickReplyState);
            }
          } else {
            await generate();
          }
        } finally { b.disabled = false; }
      });
    });
  }

  function showToast(msg) {
    let el = document.getElementById('rui-toast');
    if (!el) {
      el = document.createElement('div');
      el.id = 'rui-toast';
      el.style.cssText = 'position:fixed;left:50%;bottom:40px;transform:translateX(-50%);background:rgba(40,30,60,0.95);color:#fff;padding:10px 18px;border-radius:10px;z-index:9999;max-width:80%;font-size:13px;box-shadow:0 4px 20px rgba(0,0,0,0.3);';
      document.body.appendChild(el);
    }
    el.textContent = msg;
    el.style.opacity = '1';
    clearTimeout(el._t);
    el._t = setTimeout(() => { el.style.opacity = '0'; }, 3500);
  }

  async function generate() {
    const status = document.getElementById('qr-status');
    document.getElementById('qr-generate').disabled = true;
    status.textContent = '正在生成回复……';
    try {
      const res = await fetch('http://127.0.0.1:18080/api/quick-reply-v2', {
        method: 'POST', headers: {'Content-Type':'application/json'},
        body: JSON.stringify({skillName: currentSkillName || '', selectedDocumentIds: getCurrentSkillSelectedDocs()})
      });
      const data = await res.json();
      if (!data.success) throw new Error(data.error || data.status || 'failed');
      const byStrategy = {};
      const candidates = (data.candidates || []).map(c => ({
        strategy: c.strategy,
        strategyLabel: c.strategyLabel,
        replyText: c.replyText || ''
      }));
      candidates.forEach(c => { byStrategy[normalizeStrategy(c.strategy)] = c.replyText; });
      let single = false;
      let singleLabel = '';
      if (data.single) {
        single = true;
        singleLabel = currentSkillName || '回复';
      }
      // Stage 5-2: 权威 items[]
      const items = Array.isArray(data.items) ? data.items : [];
      const notice = data.notice || '';
      if (notice) {
        showToast(notice);
      }
      quickReplyState = { generated: true, byStrategy, candidates, single, singleSkillLabel: singleLabel, items, notice };
      // Persist into qrCache keyed by (relationshipId, skillName).
      const key = (currentRelationshipId || '?') + '|' + currentSkillName;
      qrCache[key] = { byStrategy, candidates, single, singleSkillLabel: singleLabel, items };
      renderQuickCards(quickReplyState);
      const hp = document.getElementById('home-page');
      if (hp) hp.classList.remove('initial');
      // Hide entire generate toolbar after first successful generation
      const tb = document.querySelector('.qr-toolbar');
      if (tb) tb.style.display = 'none';
      status.textContent = '完成';
    } catch (e) {
      status.textContent = '生成失败：' + e.message;
    } finally {
      document.getElementById('qr-generate').disabled = false;
    }
  }

  function renderSkillManagement() {
    return `<div class="settings-list">
      <div id="sm-list" class="settings-list"></div>
    </div>`;
  }

  // New structure: { skillName: { collectionId: [docIds] } }
  function getSkillKnowledgeMap() {
    try {
      const raw = JSON.parse(localStorage.getItem('ruiSkillKnowledge') || '{}');
      // Migrate old format { skillName: [collectionId] } -> new format
      const out = {};
      for (const [k, v] of Object.entries(raw)) {
        if (Array.isArray(v)) {
          // old format: v is [collectionId,...]
          out[k] = {};
          v.forEach(cid => { out[k][cid] = []; });
        } else if (typeof v === 'object' && v !== null) {
          out[k] = v;
        }
      }
      return out;
    } catch(e) { return {}; }
  }
  function getSkillCollections(skillName) {
    const m = getSkillKnowledgeMap();
    if (skillName === 'goutoujunshi' && !(skillName in m)) return ['relationship-knowledge'];
    return Object.keys(m[skillName] || {});
  }
  function getSkillDocs(skillName, collectionId) {
    const m = getSkillKnowledgeMap();
    if (skillName === 'goutoujunshi' && !(skillName in m)) return null; // null = default all
    return (m[skillName] && m[skillName][collectionId]) || [];
  }
  function setSkillDocs(skillName, collectionId, docIds) {
    const m = getSkillKnowledgeMap();
    if (!m[skillName]) m[skillName] = {};
    m[skillName][collectionId] = docIds;
    try { localStorage.setItem('ruiSkillKnowledge', JSON.stringify(m)); } catch(e) {}
  }

  function getCurrentSkillSelectedDocs() {
    const skillName = currentSkillName || 'default-three-strategy';
    const colls = getSkillCollections(skillName);
    const out = [];
    for (const cid of colls) {
      let docs = getSkillDocs(skillName, cid);
      if (docs === null) {
        // default all for goutoujunshi first run — but we don't know doc list here
        docs = [];
      }
      out.push(...docs);
    }
    return out;
  }
  let skCache = { skills: [], collections: [] };
  let smView = { mode: 'list', skill: null, expandedColl: null };

  function skillDisplayName(s) {
    return s.name === 'default-three-strategy' ? '默认三策略' : s.name;
  }

  function renderSkillMgmtList() {
    const { skills } = skCache;
    const distillEntry = `<div class="qr-card settings-item" data-open-distill="1" style="border:1px solid rgba(139,92,246,0.4);background:linear-gradient(135deg,rgba(124,58,237,0.12),rgba(99,102,241,0.08));">
      <div class="settings-item-main" style="min-width:0;">
        <h3 style="font-size:15px;font-weight:700;color:var(--color-accent);margin:0 0 2px;">✨ 人物蒸馏</h3>
        <p class="muted" style="font-size:11px;margin:0;color:var(--color-text-primary);">从聊天记录生成前任/个人说话风格 Skill</p>
      </div>
      <span class="settings-arrow">›</span>
    </div>`;
    const list = skills.map(s => {
      const isActive = (s.name === 'default-three-strategy' && currentSkillName === '') || s.name === currentSkillName;
      const kindLabel = s.kind === 'SYSTEM' ? 'RUI 内置' : '用户 Skill';
      const enabled = s.enabled !== false;
      const toggle = s.kind === 'SYSTEM'
        ? ''
        : `<button data-toggle-skill="${escapeHtml(s.name)}" data-enabled="${enabled}" style="padding:3px 10px;font-size:11px;border-radius:5px;cursor:pointer;border:1px solid ${enabled ? 'rgba(255,100,100,0.4)' : 'rgba(100,200,100,0.4)'};background:transparent;color:${enabled ? '#ff8888' : '#88dd88'};white-space:nowrap;">${enabled ? '停用' : '启用'}</button>`;
      return `<div class="qr-card settings-item" data-skill="${escapeHtml(s.name)}" style="${enabled ? '' : 'opacity:0.6;'}">
        <div class="settings-item-main" style="min-width:0;">
          <h3 style="font-size:15px;font-weight:700;color:var(--color-accent);margin:0 0 2px;">${escapeHtml(skillDisplayName(s))}</h3>
          <p class="muted" style="font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;margin:0 0 2px;color:var(--color-text-primary);">${escapeHtml(s.description || '')}</p>
          <p class="muted" style="font-size:11px;color:#999;margin:0;">${kindLabel} · ${enabled ? '已启用' : '已停用'}</p>
        </div>
        ${toggle}
      </div>`;
    }).join('');
    return distillEntry + list;
  }

  function renderSkillDetail(skillName) {
    const s = skCache.skills.find(x => x.name === skillName);
    if (!s) return '';
    const kindLabel = s.kind === 'SYSTEM' ? 'RUI 内置' : '用户 Skill';
    const colls = skCache.collections;
    const collHtml = colls.map(c => {
      const bound = getSkillCollections(skillName).includes(c.id);
      let selDocs = getSkillDocs(skillName, c.id);
      if (selDocs === null) selDocs = (c.documents || []).map(d => d.id);
      const selCount = bound ? selDocs.length : 0;
      return `<div class="qr-card settings-item" style="margin-bottom:8px;cursor:pointer;padding:12px;" data-open-coll="${c.id}">
        <div class="settings-item-main">
          <h3 style="font-size:14px;">${escapeHtml(c.name)}</h3>
          <p class="muted" style="font-size:11px;">${bound ? '已使用 · ' + selCount + '/' + c.docCount + ' 篇' : '未使用 · 0/' + c.docCount + ' 篇'}</p>
        </div>
        <span class="settings-arrow">›</span>
      </div>`;
    }).join('');
    return `<div class="qr-card" style="margin-bottom:12px;">
      <h3>${escapeHtml(skillDisplayName(s))}</h3>
      <p class="muted" style="font-size:11px;color:#999;">${kindLabel}</p>
      <p class="muted" style="margin-top:8px;">${escapeHtml(s.description || '')}</p>
    </div>
    <h4 style="margin:16px 0 8px;font-size:13px;color:#666;">知识库</h4>
    ${collHtml}`;
  }

  function openKnowledgeDialog(cid) {
    const c = skCache.collections.find(x => x.id === cid);
    if (!c) return;
    const skillName = smView.skill;
    const docs = c.documents || [];
    // ── Local temp state (not persisted until 完成) ──
    let isBound = getSkillCollections(skillName).includes(cid);
    let savedDocs = getSkillDocs(skillName, cid);
    if (savedDocs === null) savedDocs = docs.map(d => d.id);
    let tempSel = new Set(savedDocs);

    function renderDialogBody() {
      const docList = docs.map(d => `
        <label style="display:flex;align-items:flex-start;gap:8px;padding:6px 0;border-bottom:1px solid rgba(0,0,0,0.05);cursor:pointer;font-size:12px;">
          <input type="checkbox" data-doc="${escapeHtml(d.id)}" ${isBound && tempSel.has(d.id) ? 'checked' : ''} ${isBound ? '' : 'disabled'} style="margin-top:2px;">
          <span>${escapeHtml(d.name)}</span>
        </label>`).join('');
      return `<div class="rui-glass-dialog" style="width:360px;max-height:80vh;display:flex;flex-direction:column;">
        <h3 style="margin:0 0 12px;font-size:15px;font-weight:600;">${escapeHtml(c.name)}</h3>
        <label style="display:flex;align-items:center;gap:6px;font-size:12px;margin-bottom:4px;cursor:pointer;user-select:none;">
          <input type="checkbox" data-bind-coll="${cid}" ${isBound ? 'checked' : ''} style="cursor:pointer;"> 使用该知识库
        </label>
        <div class="muted" style="font-size:10px;margin-bottom:10px;opacity:0.7;">提示：使用的知识库内容越多，Skill 之间的回答可能越趋同。</div>
        <div style="display:flex;gap:8px;margin-bottom:8px;">
          <button class="primary" data-coll-select-all="${cid}" style="font-size:11px;" ${isBound ? '' : 'disabled'}>全选</button>
          <button class="primary" data-coll-clear="${cid}" style="font-size:11px;" ${isBound ? '' : 'disabled'}>清空</button>
        </div>
        <div style="position:relative;flex:1;overflow:hidden;min-height:120px;">
          <div id="kd-list" style="height:100%;overflow-y:auto;width:calc(100% + 16px);padding-right:16px;box-sizing:border-box;">
            ${docList}
          </div>
          <div id="kd-scrollbar" class="custom-scrollbar"><div id="kd-thumb" class="custom-scrollbar-thumb"></div></div>
        </div>
        <div style="display:flex;justify-content:flex-end;margin-top:14px;">
          <button class="primary" id="kd-close">完成</button>
        </div>
      </div>`;
    }

    const ov = document.createElement('div');
    ov.className = 'rui-glass-overlay';
    ov.id = 'kd-overlay';

    function rebuild() {
      ov.innerHTML = renderDialogBody();
      // Bind
      ov.querySelector('[data-bind-coll]').onchange = (e) => {
        isBound = e.target.checked;
        if (isBound && tempSel.size === 0) tempSel = new Set(docs.map(d => d.id));
        rebuild();
      };
      ov.querySelectorAll('[data-coll-select-all]').forEach(b => b.onclick = () => {
        tempSel = new Set(docs.map(d => d.id));
        rebuild();
      });
      ov.querySelectorAll('[data-coll-clear]').forEach(b => b.onclick = () => {
        tempSel = new Set();
        rebuild();
      });
      ov.querySelectorAll('[data-doc]').forEach(cb => cb.onchange = () => {
        if (cb.checked) tempSel.add(cb.dataset.doc); else tempSel.delete(cb.dataset.doc);
      });
      ov.querySelector('#kd-close').onclick = () => {
        // Persist
        const m = getSkillKnowledgeMap();
        if (!m[skillName]) m[skillName] = {};
        if (isBound) {
          m[skillName][cid] = Array.from(tempSel);
          delete m[skillName]['_prev_' + cid];
        } else {
          m[skillName]['_prev_' + cid] = Array.from(tempSel);
          delete m[skillName][cid];
        }
        try { localStorage.setItem('ruiSkillKnowledge', JSON.stringify(m)); } catch(e) {}
        ov.remove();
        renderSmView();
      };
      // Scrollbar
      const kdList = document.getElementById('kd-list');
      kdList.onscroll = () => updateCustomScrollbar(kdList, 'kd-scrollbar', 'kd-thumb');
      requestAnimationFrame(() => updateCustomScrollbar(kdList, 'kd-scrollbar', 'kd-thumb'));
    }

    ov.onclick = (e) => { if (e.target === ov) ov.remove(); };
    document.body.appendChild(ov);
    rebuild();
  }

  async function bindSkillManagement() {
    const cur = document.getElementById('sm-current');
    const list = document.getElementById('sm-list');
    if (!list) return;
    try {
      const [skillsRes, kcRes] = await Promise.all([
        fetch('http://127.0.0.1:18080/api/skills'),
        fetch('http://127.0.0.1:18080/api/knowledge/collections')
      ]);
      const skillsData = await skillsRes.json();
      const kcData = await kcRes.json();
      skCache.skills = (skillsData.skills || []).filter(s => !['quick-reply','direct-reply'].includes(s.name));
      skCache.collections = kcData.collections || [];
      if (cur) cur.textContent = currentSkillName || '默认三策略';
      renderSmView();
    } catch (e) {
      list.innerHTML = '<div class="qr-card"><p class="muted">加载失败：' + escapeHtml(String(e)) + '</p></div>';
    }
  }

  function saveCollDocs(skillName, collectionId) {
    const list = document.getElementById('sm-list');
    const checked = list.querySelectorAll(`[data-doc]:checked`);
    const ids = Array.from(checked).map(cb => cb.dataset.doc);
    setSkillDocs(skillName, collectionId, ids);
  }

  function renderSmView() {
    const list = document.getElementById('sm-list');
    if (!list) return;
    if (smView.mode === 'list') {
      document.getElementById('header-title').textContent = 'Skill 管理';
      // Restore back button to navigate to settings (parent page)
      const backBtn = document.getElementById('header-back');
      if (backBtn) backBtn.onclick = () => navigate('settings');
      list.innerHTML = renderSkillMgmtList();
      const distillEntry = list.querySelector('[data-open-distill]');
      if (distillEntry) distillEntry.addEventListener('click', () => navigate('distill-identity'));
      // Toggle enable/disable
      list.querySelectorAll('[data-toggle-skill]').forEach(btn => {
        btn.addEventListener('click', async (e) => {
          e.stopPropagation();
          e.preventDefault();
          const name = btn.dataset.toggleSkill;
          const currentlyEnabled = btn.dataset.enabled === 'true';
          const action = currentlyEnabled ? 'disable' : 'enable';
          btn.disabled = true;
          try {
            const r = await fetch('http://127.0.0.1:18080/api/skills/' + encodeURIComponent(name) + '/' + action, { method: 'POST' });
            const d = await r.json();
            if (d.success) {
              // refresh cache and re-render
              const skillsRes = await fetch('http://127.0.0.1:18080/api/skills');
              const skillsData = await skillsRes.json();
              skCache.skills = (skillsData.skills || []).filter(s => !['quick-reply','direct-reply'].includes(s.name));
              renderSmView();
              // If we just disabled the currently selected skill, reset selection
              if (action === 'disable' && currentSkillName === name) {
                currentSkillName = '';
                try { localStorage.setItem('ruiActiveSkill', ''); } catch(e2) {}
              }
              loadSkillSelector();
            } else {
              alert('操作失败: ' + (d.error || '未知错误'));
              btn.disabled = false;
            }
          } catch (err) {
            alert('请求失败: ' + err.message);
            btn.disabled = false;
          }
        });
      });
      list.querySelectorAll('[data-skill]').forEach(card => {
        card.addEventListener('click', (e) => {
          // Don't navigate to detail when clicking toggle button
          if (e.target.closest('[data-toggle-skill]')) return;
          smView = { mode: 'detail', skill: card.dataset.skill, expandedColl: null };
          renderSmView();
        });
      });
    } else if (smView.mode === 'detail') {
      const s = skCache.skills.find(x => x.name === smView.skill);
      document.getElementById('header-title').textContent = s ? skillDisplayName(s) : 'Skill 详情';
      list.innerHTML = renderSkillDetail(smView.skill);
      const backBtn = document.getElementById('header-back');
      if (backBtn) backBtn.onclick = () => { smView = { mode: 'list', skill: null, expandedColl: null }; renderSmView(); };
      // Toggle collection expand
      list.querySelectorAll('[data-open-coll]').forEach(row => {
        row.addEventListener('click', (e) => {
          if (e.target.closest('input,button,label')) return;
          openKnowledgeDialog(row.dataset.openColl);
        });
      });
      // Bind/unbind collection
      list.querySelectorAll('[data-bind-coll]').forEach(cb => {
        cb.addEventListener('change', () => {
          const cid = cb.dataset.bindColl;
          const docs = skCache.collections.find(c => c.id === cid);
          const allIds = docs ? docs.documents.map(d => d.id) : [];
          const m = getSkillKnowledgeMap();
          if (!m[smView.skill]) m[smView.skill] = {};
          if (cb.checked) {
            // Enable: restore previous docs or default all
            const prev = m[smView.skill]['_prev_' + cid];
            m[smView.skill][cid] = Array.isArray(prev) ? prev : allIds;
            delete m[smView.skill]['_prev_' + cid];
          } else {
            // Disable: save docs for later restore, then remove active binding
            m[smView.skill]['_prev_' + cid] = m[smView.skill][cid] || [];
            delete m[smView.skill][cid];
          }
          try { localStorage.setItem('ruiSkillKnowledge', JSON.stringify(m)); } catch(e) {}
          renderSmView();
        });
      });
      // Doc checkboxes
      list.querySelectorAll('[data-doc]').forEach(cb => {
        cb.addEventListener('change', () => saveCollDocs(smView.skill, smView.expandedColl));
      });
      // Select all / clear
      list.querySelectorAll('[data-coll-select-all]').forEach(btn => {
        btn.addEventListener('click', () => {
          list.querySelectorAll(`[data-doc]`).forEach(cb => { if (!cb.disabled) cb.checked = true; });
          saveCollDocs(smView.skill, btn.dataset.collSelectAll);
          renderSmView();
        });
      });
      list.querySelectorAll('[data-coll-clear]').forEach(btn => {
        btn.addEventListener('click', () => {
          list.querySelectorAll(`[data-doc]`).forEach(cb => { if (!cb.disabled) cb.checked = false; });
          saveCollDocs(smView.skill, btn.dataset.collClear);
          renderSmView();
        });
      });
    }
  }

  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  }

  global.escapeHtml = escapeHtml;
  global.JeveRouter = { navigate };
  global.applyTheme = applyTheme;
  global.getCurrentRelationshipId = () => currentRelationshipId;
})(window);
  function bindSettings() {
    document.querySelectorAll('.theme-option-card').forEach(card => {
      card.onclick = (e) => {
        e.preventDefault();
        e.stopPropagation();
        const t = card.dataset.themeVal;
        try { localStorage.setItem('ruiTheme', t); } catch(e2) {}
        applyTheme(t);
        document.querySelectorAll('.theme-option-card').forEach(c => {
          c.classList.toggle('active', c.dataset.themeVal === t);
        });
      };
    });
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
    const about = document.getElementById('settings-about');
    if (about) about.onclick = () => {
      const ov = document.createElement('div');
      ov.className = 'rui-glass-overlay';
      ov.innerHTML = '<div class="rui-glass-dialog">' +
        '<h3 style="margin:0 0 12px;font-size:15px;font-weight:600;">关于 RUI</h3>' +
        '<p style="font-size:13px;color:#555;line-height:1.6;margin:0 0 8px;">RUI — Relationship AI Assistant</p>' +
        '<p style="font-size:12px;color:#999;margin:0 0 16px;">Version: Development</p>' +
        '<div style="display:flex;justify-content:flex-end;"><button class="primary" id="about-close">关闭</button></div>' +
        '</div>';
      document.body.appendChild(ov);
      ov.onclick = (e) => { if (e.target === ov) ov.remove(); };
      document.getElementById('about-close').onclick = () => ov.remove();
    };
  }

  function bindDistill() {
    const status = document.getElementById('distill-status');
    function setStatus(msg) {
      if (status) { status.textContent = msg; status.style.color = '#e8e0ff'; status.style.fontWeight = '500'; }
      console.log('[DISTILL] ' + msg);
    }
    function getCurrentRelId() {
      try {
        if (typeof window.getCurrentRelationshipId === 'function') {
          const id = window.getCurrentRelationshipId();
          if (id) return id;
        }
      } catch(e) {}
      const dd = document.getElementById('contact-select');
      if (dd && dd.dataset.value && dd.dataset.value !== 'current') {
        const v = parseInt(dd.dataset.value, 10);
        if (!isNaN(v)) return v;
      }
      return null;
    }

    const TYPE_META = {
      WECHAT_FILE:  { label: '微信导出文件',     placeholder: 'C:\\path\\wechat.txt（.txt/.html/.csv）' },
      QQ_FILE:      { label: 'QQ 导出文件',     placeholder: 'C:\\path\\qq.txt（.txt/.html/.csv）' },
      SMS_FILE:     { label: '短信导出',         placeholder: 'C:\\path\\sms.xml（.xml/.csv/.txt）' },
      SOCIAL_FILE:  { label: '社交媒体导出',     placeholder: 'C:\\path\\social.json' },
      PHOTO_DIR:    { label: '照片目录',         placeholder: 'C:\\path\\photos\\' }
    };

    const materialsBox = document.getElementById('ex-materials');

    function addMaterialRow(type) {
      const meta = TYPE_META[type];
      if (!meta) return;
      const row = document.createElement('div');
      row.dataset.type = type;
      row.style.cssText = 'display:flex;gap:6px;align-items:center;flex-wrap:nowrap;';
      row.innerHTML = `
        <span style="font-size:11px;color:#a78bfa;min-width:80px;flex-shrink:0;">${meta.label}</span>
        <input data-path placeholder="${meta.placeholder || ''}"
          style="flex:1;min-width:0;padding:6px;border-radius:6px;background:rgba(255,255,255,0.08);border:1px solid rgba(139,92,246,0.3);color:var(--jeve-text,#eee);font-size:11px;">
        <button data-rm style="flex-shrink:0;padding:4px 8px;font-size:11px;background:transparent;border:1px solid rgba(255,100,100,0.4);color:#ff8888;border-radius:4px;cursor:pointer;">×</button>`;
      row.querySelector('[data-rm]').onclick = () => row.remove();
      materialsBox.appendChild(row);
    }

    // 自定义下拉
    const ddBtn = document.getElementById('ex-add-type');
    const ddPanel = document.getElementById('ex-add-type-panel');
    const ddLabel = ddBtn;
    const ddItems = ddPanel.querySelectorAll('.ex-dd-item');
    function setDDValue(val, label) {
      ddBtn.dataset.value = val;
      ddBtn.textContent = label;
      ddItems.forEach(it => it.classList.toggle('selected', it.dataset.val === val));
      ddPanel.style.display = 'none';
    }
    ddBtn.onclick = (e) => {
      e.stopPropagation();
      ddPanel.style.display = ddPanel.style.display === 'block' ? 'none' : 'block';
    };
    ddItems.forEach(it => {
      it.onclick = (e) => {
        e.stopPropagation();
        setDDValue(it.dataset.val, it.textContent);
      };
    });
    document.addEventListener('click', (e) => {
      if (!ddPanel.contains(e.target) && e.target !== ddBtn) ddPanel.style.display = 'none';
    });

    document.getElementById('ex-add').onclick = () => {
      addMaterialRow(ddBtn.dataset.value || 'WECHAT_FILE');
    };

    const btn = document.getElementById('ex-start');
    if (!btn) { console.warn('[DISTILL] ex-start button not found'); return; }
    btn.onclick = async () => {
      const relId = getCurrentRelId();
      if (!relId) { setStatus('请先在首页选择一个联系人'); return; }
      const nickname = (document.getElementById('ex-nickname') || {}).value || '';
      const relationshipDesc = (document.getElementById('ex-relationship') || {}).value || '';
      const tags = (document.getElementById('ex-tags') || {}).value || '';
      if (!nickname.trim()) { setStatus('请填写她的昵称/代号'); return; }

      // 收集有效材料：DB 由复选框控制；外部类型必须有非空路径
      const useDb = document.getElementById('ex-use-db').checked;
      const materials = [];
      if (useDb) materials.push({type: 'DB', path: ''});
      for (const row of materialsBox.children) {
        const type = row.dataset.type;
        const inp = row.querySelector('[data-path]');
        const p = (inp && inp.value || '').trim();
        if (p) materials.push({type, path: p});
      }
      if (materials.length === 0) {
        setStatus('请勾选 RUI 数据库，或至少添加一个外部材料并填写路径');
        return;
      }

      btn.disabled = true;
      setStatus('正在蒸馏，4 阶段 LLM 调用中，约 60-120 秒...');
      try {
        const payload = {
          relationshipId: relId,
          nickname: nickname.trim(),
          relationshipDesc: relationshipDesc.trim(),
          personalityTags: tags.trim(),
          materials
        };
        const resp = await fetch('http://127.0.0.1:18080/api/distill-ex', {
          method: 'POST',
          headers: {'Content-Type': 'application/json'},
          body: JSON.stringify(payload)
        });
        const d = await resp.json();
        console.log('[DISTILL] response=', d);
        if (d.success) {
          setStatus('成功！已生成前任 Skill: ' + d.slug + '（样本 ' + d.materialCount + ' 条）。请到 Skill 管理中选择。');
        } else {
          const errMsg = d.message || d.error || '未知错误';
          setStatus('失败: ' + errMsg);
          if (/未检测到 Python|python.*not found|no python/i.test(errMsg)) {
            showPythonInstallPrompt();
          }
        }
      } catch (e) {
        console.error('[DISTILL] error', e);
        setStatus('请求失败: ' + e.message);
      } finally {
        btn.disabled = false;
      }
    };
    console.log('[DISTILL] ex-start bound, relId=' + getCurrentRelId());
  }

  function showPythonInstallPrompt() {
    if (document.getElementById('python-install-modal')) return;
    const mask = document.createElement('div');
    mask.id = 'python-install-modal';
    mask.style.cssText = 'position:fixed;inset:0;background:rgba(0,0,0,0.55);z-index:9999;display:flex;align-items:center;justify-content:center;';
    mask.innerHTML = `
      <div style="background:#1e1b3a;border:1px solid rgba(139,92,246,0.4);border-radius:12px;padding:20px;max-width:340px;color:#e8e0ff;font-family:sans-serif;">
        <div style="font-size:15px;font-weight:600;margin-bottom:10px;">需要安装 Python</div>
        <div style="font-size:12px;line-height:1.6;color:#c8c2e0;margin-bottom:16px;">
          解析微信导出文件需要 Python 运行环境。当前电脑未检测到可用的 Python，因此暂时无法解析该文件。安装完成后，可以重新尝试蒸馏。
        </div>
        <div style="display:flex;gap:8px;justify-content:flex-end;">
          <button id="py-install-cancel" style="padding:7px 14px;border:1px solid rgba(139,92,246,0.4);background:transparent;color:#a78bfa;border-radius:6px;cursor:pointer;font-size:12px;">取消</button>
          <button id="py-install-go" style="padding:7px 14px;border:none;background:linear-gradient(135deg,#7c3aed,#6366f1);color:#fff;border-radius:6px;cursor:pointer;font-size:12px;">安装 Python</button>
        </div>
      </div>`;
    document.body.appendChild(mask);
    document.getElementById('py-install-cancel').onclick = () => mask.remove();
    document.getElementById('py-install-go').onclick = () => {
      const url = 'https://www.python.org/downloads/windows/';
      try {
        if (window.desktopBridge && typeof window.desktopBridge.openExternalUrl === 'function') {
          window.desktopBridge.openExternalUrl(url);
        } else {
          window.open(url, '_blank');
        }
      } catch (e) {
        console.error('[PY-INSTALL] open url failed', e);
      }
      mask.remove();
    };
    mask.addEventListener('click', (e) => { if (e.target === mask) mask.remove(); });
  }

  function bindCollectSettings() {
    fetch('http://127.0.0.1:18080/api/dbsource/config').then(r=>r.json()).then(d=>{
      if (!d.success) return;
      window.__jeveSourceType = d.sourceType || 'database';
      const st = document.getElementById('source-type');
      if (st) {
        st.dataset.value = window.__jeveSourceType;
        const lbl = st.querySelector('.jeve-dropdown-trigger span');
        if (lbl) lbl.textContent = window.__jeveSourceType === 'database' ? '数据库读取' : '屏幕 OCR';
        st.querySelectorAll('.jeve-dropdown-option').forEach(o => o.classList.toggle('selected', o.dataset.value === window.__jeveSourceType));
      }
      const bs = document.getElementById('batch-size');
      if (bs) {
        const bv = String(d.batchSize || 50);
        bs.dataset.value = bv;
        const lbl = bs.querySelector('.jeve-dropdown-trigger span');
        if (lbl) lbl.textContent = bv + ' 条';
        bs.querySelectorAll('.jeve-dropdown-option').forEach(o => o.classList.toggle('selected', o.dataset.value === bv));
      }
      if (typeof window.updateHeaderAction === 'function') window.updateHeaderAction();
    }).catch(()=>{});
  }

  function bindWechatHistory() {
    document.getElementById('wh-import-existing').onclick = () => openWechatImportModal();
  }

  let wrState = { accounts: [], selectedAccount: null };

  function bindWechatRead() {
    wrState = { accounts: [], selectedAccount: null };
    const statusCard = document.getElementById('wr-status-card');
    statusCard.innerHTML = '<p class="muted">正在检查微信环境…</p>';
    fetch('http://127.0.0.1:18080/api/dbsource/detect')
      .then(r => r.json())
      .then(d => renderDetectResult(d))
      .catch(() => {
        statusCard.innerHTML = '<p>! 暂时无法检测微信环境</p><p class="muted">请确认微信已经登录，然后点击重新检测。</p><button class="primary" id="wr-retry">重新检测</button>';
        document.getElementById('wr-retry').onclick = bindWechatRead;
      });
  }

  function renderDetectResult(d) {
    const statusCard = document.getElementById('wr-status-card');
    const accCard = document.getElementById('wr-accounts');
    const conCard = document.getElementById('wr-contacts');
    conCard.style.display = 'none';

    if (!d.ok || !d.accounts || d.accounts.length === 0) {
      let msg = '请确认微信已经登录并正常使用，然后点击重新检测。';
      if (d.errorCode === 'PYTHON_NOT_FOUND') {
        msg = 'RUI 未找到 Python 环境。请在 application-local.properties 中设置 python.executable= 或设置 RUI_PYTHON 环境变量后重启 RUI。';
      } else if (d.error) {
        msg = d.error;
      }
      statusCard.innerHTML = '<p>! 未检测到微信聊天数据</p><p class="muted">' + escapeHtml(msg) + '</p><button class="primary" id="wr-retry">重新检测</button>';
      document.getElementById('wr-retry').onclick = bindWechatRead;
      accCard.style.display = 'none';
      return;
    }

    wrState.accounts = d.accounts;
    // Enrich each account with selfinfo (nickname) if possible
    wrState.accounts.forEach(a => {
      fetch('http://127.0.0.1:18080/api/dbsource/selfinfo?account=' + encodeURIComponent(a.account))
        .then(r => r.json())
        .then(si => {
          if (si && si.ok && (si.nickname || si.wechatId)) {
            a.nickname = si.nickname || '';
            a.wechatId = si.wechatId || '';
            const el = document.querySelector('.wr-acc[data-i="' + wrState.accounts.indexOf(a) + '"] .muted');
            if (el) el.textContent = a.nickname + ' · ' + a.wxid;
          }
        }).catch(() => {});
    });
    if (d.accounts.length === 1) {
      // Single account: auto-select
      wrState.selectedAccount = d.accounts[0];
      statusCard.innerHTML = '<p>✓ 微信环境正常</p><p class="muted">当前账号：' + escapeHtml(d.accounts[0].nickname || d.accounts[0].wxid) + '</p><button class="primary" id="wr-load-contacts">选择联系人</button>';
      accCard.style.display = 'none';
      document.getElementById('wr-load-contacts').onclick = () => loadContacts();
    } else {
      // Multiple accounts: show list
      statusCard.innerHTML = '<p>✓ 检测到 ' + d.accounts.length + ' 个微信账号，请选择：</p><button class="ghost" id="wr-retry">重新检测</button>';
      document.getElementById('wr-retry').onclick = bindWechatRead;
      accCard.style.display = '';
      accCard.innerHTML = d.accounts.map((a, i) =>
        `<div class="qr-card settings-item wr-acc" data-i="${i}" style="cursor:pointer"><div class="settings-item-main"><h3>${escapeHtml(a.nickname || a.wxid)}</h3><p class="muted">${escapeHtml(a.wxid)}</p></div><span class="settings-arrow">›</span></div>`
      ).join('');
      accCard.querySelectorAll('.wr-acc').forEach(el => {
        el.onclick = () => {
          wrState.selectedAccount = d.accounts[parseInt(el.dataset.i)];
          statusCard.innerHTML = '<p>✓ 已选择：' + escapeHtml(wrState.selectedAccount.nickname || wrState.selectedAccount.wxid) + '</p><button class="primary" id="wr-load-contacts">选择联系人</button>';
          accCard.style.display = 'none';
          document.getElementById('wr-load-contacts').onclick = () => loadContacts();
        };
      });
    }
  }

  function loadContacts() {
    const conCard = document.getElementById('wr-contacts');
    conCard.style.display = '';
    conCard.innerHTML = '<p class="muted">正在加载联系人…</p>';
    const acct = wrState.selectedAccount ? wrState.selectedAccount.account : '';
    fetch('http://127.0.0.1:18080/api/dbsource/contacts?account=' + encodeURIComponent(acct))
      .then(r => r.json())
      .then(d => {
        if (!d.ok || !d.contacts) {
          conCard.innerHTML = '<p>! 联系人加载失败</p><p class="muted">' + escapeHtml(d.error || '未知错误') + '</p>';
          return;
        }
        if (!d.contacts.length) {
          conCard.innerHTML = '<p class="muted">该账号暂无会话联系人</p>';
          return;
        }
        wrState.allContacts = d.contacts;
        renderContactSearch(conCard, d.contacts);
      })
      .catch((e) => { conCard.innerHTML = '<p class="muted">联系人加载失败：' + escapeHtml(String(e)) + '</p>'; });
  }

  function renderContactSearch(conCard, contacts) {
    conCard.innerHTML =
      '<div class="wr-section-title">选择联系人</div>' +
      '<input id="wr-contact-search" type="text" placeholder="按备注搜索联系人…" style="width:100%;padding:8px 10px;margin:6px 0;border:1px solid rgba(96,76,199,0.25);border-radius:8px;background:rgba(255,255,255,0.6);box-sizing:border-box;font-size:13px"/>' +
      '<div class="wi-viewport" style="position:relative;height:260px;overflow:hidden;border-radius:0 0 10px 10px"><div id="wr-contact-list" class="wr-contact-list">' +
      contacts.map(c => {
        const display = c.remark || c.nickname || c.wxid;
        return `<div class="qr-card settings-item wr-contact" data-wxid="${escapeHtml(c.wxid)}" data-remark="${escapeHtml(c.remark||'')}" data-nick="${escapeHtml(c.nickname||'')}" style="cursor:pointer"><div class="settings-item-main"><h3>${escapeHtml(display)}</h3><p class="muted">${escapeHtml(c.wxid)}</p></div><span class="settings-arrow">›</span></div>`;
      }).join('') +
      '</div><div id="wr-scrollbar" class="custom-scrollbar custom-scrollbar-force"><div id="wr-thumb" class="custom-scrollbar-thumb"></div></div></div>' +
      '<p id="wr-contact-empty" class="muted" style="display:none;padding:12px 0;text-align:center">未找到匹配的联系人</p>';
    requestAnimationFrame(() => {
      const lst = document.getElementById('wr-contact-list');
      if (lst) {
        lst.onscroll = () => updateCustomScrollbar(lst, 'wr-scrollbar', 'wr-thumb');
        setTimeout(() => updateCustomScrollbar(lst, 'wr-scrollbar', 'wr-thumb'), 50);
      }
    });
    const search = document.getElementById('wr-contact-search');
    if (search) {
      search.oninput = () => {
        const q = search.value.trim().toLowerCase();
        const items = conCard.querySelectorAll('.wr-contact');
        let visible = 0;
        items.forEach(el => {
          const remark = (el.dataset.remark||'').toLowerCase();
          const nick = (el.dataset.nick||'').toLowerCase();
          const hit = !q || remark.includes(q) || nick.includes(q);
          el.style.display = hit ? '' : 'none';
          if (hit) visible++;
        });
        const empty = document.getElementById('wr-contact-empty');
        if (empty) empty.style.display = visible === 0 ? '' : 'none';
        const lst = document.getElementById('wr-contact-list');
        if (lst) setTimeout(() => updateCustomScrollbar(lst, 'wr-scrollbar', 'wr-thumb'), 30);
      };
    }
    conCard.querySelectorAll('.wr-contact').forEach(el => {
      el.onclick = () => {
        const wxid = el.dataset.wxid;
        const name = el.querySelector('h3').textContent;
        renderImportPanel(conCard, name, wxid);
      };
    });
  }

  let wrImportTimer = null;
  function renderImportPanel(conCard, name, wxid) {
    if (wrImportTimer) { clearInterval(wrImportTimer); wrImportTimer = null; }
    conCard.innerHTML =
      '<p>即将导入：<b>' + escapeHtml(name) + '</b>（' + escapeHtml(wxid) + '）</p>' +
      '<button class="primary" id="wr-import">一键导入</button>' +
      '<p class="muted" id="wr-import-status"></p>';
    const btn = document.getElementById('wr-import');
    const status = document.getElementById('wr-import-status');
    btn.onclick = async () => {
      btn.disabled = true;
      btn.textContent = '正在导入';
      let dots = 0;
      status.textContent = '正在导入.';
      wrImportTimer = setInterval(() => {
        dots = (dots + 1) % 3;
        status.textContent = '正在导入' + '.'.repeat(dots + 1);
      }, 1000);
      const stopAnim = (finalText, ok) => {
        if (wrImportTimer) { clearInterval(wrImportTimer); wrImportTimer = null; }
        status.textContent = finalText;
        btn.disabled = false;
        btn.textContent = ok ? '重新导入' : '一键导入';
      };
      try {
        await fetch('http://127.0.0.1:18080/api/dbsource/config', {
          method: 'POST', headers: {'Content-Type':'application/json'},
          body: JSON.stringify({ account: wrState.selectedAccount.account })
        });
        const syncR = await fetch('http://127.0.0.1:18080/api/dbsource/sync', {
          method: 'POST', headers: {'Content-Type':'application/json'},
          body: JSON.stringify({ targetWxid: wxid, name: name })
        });
        const sync = await syncR.json().catch(()=>({success:true}));
        if (sync.success === false) throw new Error(sync.error || '同步失败');
        try { localStorage.setItem('ruiLastImportAt', String(Date.now())); } catch(e){}
        stopAnim('✓ 导入成功，历史消息正在后台同步', true);
      } catch (e) {
        stopAnim('× 导入失败：' + (e && e.message ? e.message : '请检查微信是否正常运行后重试'), false);
      }
    };
  }

  /* ===== Wechat history import modal ===== */
  const wiState = { root: '', contacts: [], targetWxid: null, selfWxid: null };

  function openWechatImportModal() {
    document.getElementById('wechat-import-modal').style.display = 'flex';
    document.getElementById('wi-status').textContent = '';
    document.getElementById('wi-contact-block').style.display = 'none';
    document.getElementById('wi-self-block').style.display = 'none';
    document.getElementById('wi-run').disabled = true;
    wiState.root = ''; wiState.contacts = []; wiState.targetWxid = null;
    document.getElementById('wi-contact-list').innerHTML = '';
    document.getElementById('wi-self-list').innerHTML = '';
    loadCurrentSelfWxid();
  }

  async function loadCurrentSelfWxid() {
    try {
      const r = await fetch('http://127.0.0.1:18080/api/wechat/self-wxid');
      const d = await r.json();
      wiState.selfWxid = d.configured ? d.selfWxid : null;
      const rootInput = document.getElementById('wi-root');
      if (rootInput && d.lastRoot) rootInput.value = d.lastRoot;
    } catch (e) { wiState.selfWxid = null; }
  }

  async function wiScan() {
    const root = (document.getElementById('wi-root').value || '').trim();
    if (!root) { document.getElementById('wi-status').textContent = '请输入 WChatSJ 目录'; return; }
    wiState.root = root;
    const status = document.getElementById('wi-status');
    status.textContent = '正在扫描…';
    const scanBtn = document.getElementById('wi-scan');
    scanBtn.disabled = true;
    try {
      const r = await fetch('http://127.0.0.1:18080/api/wechat/contacts?root=' + encodeURIComponent(root));
      const d = await r.json();
      if (!d.success) { status.textContent = '扫描失败：' + (d.error || ''); return; }
      wiState.contacts = d.contacts || [];
      if (!wiState.contacts.length) { status.textContent = '未找到个人联系人'; return; }
      // If selfWxid not configured, fetch candidates first.
      if (!wiState.selfWxid) {
        status.textContent = '请确认你的微信账号';
        document.getElementById('wi-self-block').style.display = '';
        const sr = await fetch('http://127.0.0.1:18080/api/wechat/scan-self', {
          method: 'POST', headers: {'Content-Type':'application/json'},
          body: JSON.stringify({root})
        });
        const sd = await sr.json();
        const cands = sd.candidates || [];
        const list = document.getElementById('wi-self-list');
        list.innerHTML = cands.map(c => {
          const name = c.displayName ? escapeHtml(c.displayName) : '<span class="muted">未识别昵称</span>';
          return `<button type="button" class="wi-opt" data-wxid="${escapeHtml(c.wxid)}">
            <div><b>${name}</b></div>
            <div class="muted">${escapeHtml(c.wxid)} · ${c.messageCount} 条</div>
          </button>`;
        }).join('');
        list.querySelectorAll('.wi-opt').forEach(b => b.onclick = async () => {
          const wxid = b.dataset.wxid;
          await fetch('http://127.0.0.1:18080/api/wechat/self-wxid', {
            method: 'POST', headers: {'Content-Type':'application/json'},
            body: JSON.stringify({wxid})
          });
          wiState.selfWxid = wxid;
          list.querySelectorAll('.wi-opt').forEach(x => x.classList.toggle('selected', x === b));
          status.textContent = '已确认本人账号：' + wxid;
          showContactList();
        });
      } else {
        showContactList();
        status.textContent = '扫描完成，请选择联系人';
      }
    } catch (e) {
      status.textContent = '请求失败：' + e.message;
    } finally {
      scanBtn.disabled = false;
    }
  }

  function showContactList() {
    const status = document.getElementById('wi-status');
    document.getElementById('wi-contact-block').style.display = '';
    const list = document.getElementById('wi-contact-list');
    list.innerHTML = wiState.contacts.map(c => {
      const t = c.lastMsgTime ? new Date(c.lastMsgTime * 1000).toLocaleString() : '';
      return `<button type="button" class="wi-opt" data-wxid="${escapeHtml(c.wxid)}">
        <div><b>${escapeHtml(c.displayName)}</b></div>
        <div class="muted">${c.messageCount} 条 · ${t}</div>
      </button>`;
    }).join('');
    list.querySelectorAll('.wi-opt').forEach(b => b.onclick = () => {
      list.querySelectorAll('.wi-opt').forEach(x => x.classList.toggle('selected', x === b));
      wiState.targetWxid = b.dataset.wxid;
      document.getElementById('wi-run').disabled = false;
      status.textContent = '已选择：' + b.querySelector('b').textContent;
    });
    list.onscroll = () => updateCustomScrollbar(list, 'wi-scrollbar', 'wi-thumb');
    requestAnimationFrame(() => updateCustomScrollbar(list, 'wi-scrollbar', 'wi-thumb'));
  }

  async function wiFindByAlias() {
    const alias = (document.getElementById('wi-alias').value || '').trim();
    const status = document.getElementById('wi-find-status');
    if (!alias) { status.textContent = '请输入微信号'; return; }
    if (!wiState.root) { status.textContent = '请先扫描联系人'; return; }
    status.textContent = '查找中…';
    try {
      const r = await fetch('http://127.0.0.1:18080/api/wechat/find?root=' + encodeURIComponent(wiState.root)
              + '&alias=' + encodeURIComponent(alias));
      const d = await r.json();
      if (!d.success || !d.contact) {
        status.textContent = d.error || '未找到该联系人';
        return;
      }
      const c = d.contact;
      status.textContent = '找到：' + c.displayName + '，已选中';
      // inject into contact list and auto-select
      wiState.targetWxid = c.wxid;
      const list = document.getElementById('wi-contact-list');
      list.querySelectorAll('.wi-opt').forEach(x => x.classList.toggle('selected', x.dataset.wxid === c.wxid));
      if (!list.querySelector('[data-wxid="' + c.wxid + '"]')) {
        const t = c.lastMsgTime ? new Date(c.lastMsgTime * 1000).toLocaleString() : '';
        list.insertAdjacentHTML('afterbegin',
          `<button type="button" class="wi-opt selected" data-wxid="${escapeHtml(c.wxid)}">
            <div><b>${escapeHtml(c.displayName)}</b></div>
            <div class="muted">${c.messageCount} 条 · ${t}</div>
          </button>`);
        list.querySelector('[data-wxid="' + c.wxid + '"]').onclick = () => {
          list.querySelectorAll('.wi-opt').forEach(x => x.classList.toggle('selected', x.dataset.wxid === c.wxid));
          wiState.targetWxid = c.wxid;
          document.getElementById('wi-run').disabled = false;
          document.getElementById('wi-status').textContent = '已选择：' + c.displayName;
        };
      }
      document.getElementById('wi-run').disabled = false;
      document.getElementById('wi-status').textContent = '已选择：' + c.displayName;
    } catch (e) {
      status.textContent = '查找失败：' + e.message;
    }
  }

  async function wiRunImport() {    if (!wiState.root || !wiState.targetWxid) return;
    const btn = document.getElementById('wi-run');
    const status = document.getElementById('wi-status');
    btn.disabled = true;
    status.textContent = '正在导入…';
    try {
      const r = await fetch('http://127.0.0.1:18080/api/wechat/import', {
        method: 'POST', headers: {'Content-Type':'application/json'},
        body: JSON.stringify({root: wiState.root, targetWxid: wiState.targetWxid})
      });
      const d = await r.json();
      if (!d.success) {
        status.textContent = '导入失败：' + (d.error || d.code || '');
        btn.disabled = false;
        return;
      }
      const dupMsg = d.duplicates > 0 ? `（${d.duplicates} 条已存在）` : '';
      status.textContent = `完成：${d.displayName} · 解析 ${d.parsed} · 新增 ${d.inserted} ${dupMsg}`;
      // Switch to that relationship
      if (d.relationshipId) {
        await fetch('http://127.0.0.1:18080/api/use-relationship', {
          method: 'POST', headers: {'Content-Type':'application/json'},
          body: String(d.relationshipId)
        });
        currentRelationshipId = d.relationshipId;
        const cur = document.getElementById('contact-current');
        if (cur) cur.textContent = d.displayName;
      }
      // Show result modal
      const title = document.getElementById('wr-title');
      title.textContent = (d.inserted > 0) ? '✓ 导入成功' : '✓ 导入完成';
      document.getElementById('wr-contact').textContent = d.displayName || '';
      document.getElementById('wr-parsed').textContent = d.parsed;
      document.getElementById('wr-inserted').textContent = d.inserted;
      document.getElementById('wr-duplicates').textContent = d.duplicates;
      document.getElementById('wechat-import-modal').style.display = 'none';
      document.getElementById('wechat-result-modal').style.display = 'flex';
      btn.disabled = false;
    } catch (e) {
      status.textContent = '导入请求失败：' + e.message;
      btn.disabled = false;
    }
  }

  document.addEventListener('DOMContentLoaded', () => {
    const scan = document.getElementById('wi-scan');
    if (scan) scan.onclick = wiScan;
    const run = document.getElementById('wi-run');
    if (run) run.onclick = wiRunImport;
    const cancel = document.getElementById('wi-cancel');
    if (cancel) cancel.onclick = () => { document.getElementById('wechat-import-modal').style.display = 'none'; };
    const mask = document.getElementById('wechat-import-modal');
    if (mask) mask.addEventListener('click', (e) => { if (e.target.id === 'wechat-import-modal') mask.style.display = 'none'; });
    const wrClose = document.getElementById('wr-close');
    if (wrClose) wrClose.onclick = () => { document.getElementById('wechat-result-modal').style.display = 'none'; };
    const wrMask = document.getElementById('wechat-result-modal');
    if (wrMask) wrMask.addEventListener('click', (e) => { if (e.target.id === 'wechat-result-modal') wrMask.style.display = 'none'; });
    const wiFindBtn = document.getElementById('wi-find');
    if (wiFindBtn) wiFindBtn.onclick = wiFindByAlias;
  });

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
        d.classList.remove('open', 'menu-up', 'menu-right');
        const card = d.closest('.qr-card, .da-card, .settings-item');
        if (card) { card.style.zIndex = ''; }
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
          // Bump the parent card's stacking context so the menu floats above sibling cards.
          // .qr-card uses backdrop-filter which creates a stacking context; without this,
          // the next sibling card paints over the open dropdown.
          const card = dd.closest('.qr-card, .da-card, .settings-item');
          if (card) { card.style.position = 'relative'; card.style.zIndex = '300'; }
          const menu = dd.querySelector('.jeve-dropdown-menu');
          const rect = dd.getBoundingClientRect();
          const menuH = menu.offsetHeight;
          if (rect.bottom + menuH + 8 > window.innerHeight) dd.classList.add('menu-up');
          // Custom overlay scrollbar for the inner scroller.
          const inner = menu.querySelector('.jeve-dropdown-menu-inner');
          if (inner) {
            const sb = menu.querySelector('.custom-scrollbar');
            const thumb = sb ? sb.querySelector('.custom-scrollbar-thumb') : null;
            if (sb && thumb) {
              const sbId = sb.id, thumbId = thumb.id;
              updateCustomScrollbar(inner, sbId, thumbId);
              if (!inner._scrollBound) {
                inner.addEventListener('scroll', () => updateCustomScrollbar(inner, sbId, thumbId));
                inner._scrollBound = true;
              }
            }
          }
          console.log('[DropdownUI] open id=', dd.id, 'menu-up=', dd.classList.contains('menu-up'), 'menu-right=', dd.classList.contains('menu-right'));
        } else {
          dd.classList.remove('open', 'menu-up', 'menu-right');
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
          dd.classList.remove('open', 'menu-up', 'menu-right');
          console.log('[DropdownUI] select id=', dd.id, 'value=', val);
          if (dd.id === 'contact-select') console.log('[ContactDebug] change value=', val);
          if (dd.id === 'skill-select') console.log('[SkillDebug] change value=', val);
          if (dd.id === 'ctx-len') console.log('[ContextLengthDebug] change value=', val);
          if (dd.id === 'source-type') {
            fetch('http://127.0.0.1:18080/api/dbsource/config', {
              method:'POST', headers:{'Content-Type':'application/json'},
              body: JSON.stringify({sourceType: val})
            }).catch(()=>{});
            console.log('[SourceType] saved value=', val);
            window.__jeveSourceType = val;
            const captureBtn = document.getElementById('capture-toggle');
            if (val === 'database' && captureBtn && captureBtn.dataset.state === 'ON') {
              captureBtn.click();
            }
            if (typeof window.updateHeaderAction === 'function') window.updateHeaderAction();
          }
          if (dd.id === 'batch-size') {
            fetch('http://127.0.0.1:18080/api/dbsource/config', {
              method:'POST', headers:{'Content-Type':'application/json'},
              body: JSON.stringify({batchSize: parseInt(val) || 50})
            }).catch(()=>{});
            console.log('[BatchSize] saved value=', val);
          }
        });
      });
    });
    document.onclick = () => closeAllDropdowns(null);
    document.onkeydown = (e) => { if (e.key === 'Escape') closeAllDropdowns(null); };
    window.onscroll = () => closeAllDropdowns(null);
  }