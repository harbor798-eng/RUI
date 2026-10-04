// ============ AI 档案前端逻辑（连接本地 Java HTTP API） ============
const API = 'http://127.0.0.1:18080/api';

let currentOwner = 'ME'; // ME or OTHER
let allItems = {};      // { key: value }
let allPerms = {};       // { key: bool }

// ---------- API calls ----------
async function loadProfile(owner) {
  currentOwner = owner;
  try {
    const res = await fetch(`${API}/profile?owner=${owner}`);
    const data = await res.json();
    allItems = data.items || {};
    allPerms = data.permissions || {};
    renderDetail();
    renderPermissions();
  } catch (e) {
    console.error('Load profile failed:', e);
  }
}

async function saveItem(key, value, category) {
  await fetch(`${API}/profile/item`, {
    method: 'POST',
    headers: {'Content-Type': 'application/x-www-form-urlencoded'},
    body: `owner=${currentOwner}&key=${encodeURIComponent(key)}&value=${encodeURIComponent(value)}&category=${category || 'CUSTOM'}`
  });
}

async function setPermission(key, visible) {
  await fetch(`${API}/profile/permission`, {
    method: 'POST',
    headers: {'Content-Type': 'application/x-www-form-urlencoded'},
    body: `owner=${currentOwner}&key=${encodeURIComponent(key)}&visible=${visible}`
  });
  allPerms[key] = visible;
}

// ---------- Basic fields ----------
const BASIC_FIELDS = ['姓名', '年龄', '性别', '职业', '所在地', '兴趣爱好'];

function renderDetail() {
  // Fill basic fields
  BASIC_FIELDS.forEach(key => {
    const el = document.getElementById('f-' + fieldId(key));
    if (el) el.value = allItems[key] || '';
  });
  // Render custom fields (any key not in BASIC_FIELDS)
  const c = document.getElementById('custom-fields');
  c.innerHTML = '';
  Object.keys(allItems).forEach(key => {
    if (BASIC_FIELDS.includes(key)) return;
    c.innerHTML += `<div class="custom-field">
      <div class="custom-field-head">
        <span class="custom-field-name">${esc(key)}</span>
        <div class="actions">
          <button class="btn-sm" onclick="editCustomField('${esc(key)}')">编辑</button>
          <button class="btn-sm danger" onclick="removeCustomField('${esc(key)}')">删除</button>
        </div>
      </div>
      <div class="custom-field-value">${esc(allItems[key])}</div>
    </div>`;
  });
}

function fieldId(name) {
  const map = {'姓名':'name','年龄':'age','性别':'gender','职业':'job','所在地':'location','兴趣爱好':'hobby'};
  return map[name] || name;
}

function esc(s) {
  const d = document.createElement('div');
  d.textContent = s;
  return d.innerHTML;
}

// ---------- Page navigation ----------
function showDetail(who) {
  document.getElementById('detail-title').textContent = who === 'me' ? '我的档案' : '对方档案';
  loadProfile(who === 'me' ? 'ME' : 'OTHER');
  goPage('detail');
}

function goPage(page) {
  document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
  document.getElementById('page-' + page).classList.add('active');
  document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
  document.querySelectorAll('.nav-item').forEach(n => {
    if (n.dataset.page === page) n.classList.add('active');
  });
}

document.querySelectorAll('.nav-item').forEach(n => {
  n.addEventListener('click', () => {
    if (n.dataset.page === 'permissions') renderPermissions();
    goPage(n.dataset.page);
  });
});

// ---------- Basic field save ----------
async function saveProfile() {
  for (const key of BASIC_FIELDS) {
    const el = document.getElementById('f-' + fieldId(key));
    if (el) await saveItem(key, el.value.trim(), 'BASIC');
  }
  showToast('已保存');
  // Reload to refresh custom fields list
  loadProfile(currentOwner);
}

// ---------- Custom fields ----------
async function addCustomField() {
  const name = prompt('信息名称：');
  if (!name) return;
  const value = prompt('信息内容：');
  if (!value) return;
  await saveItem(name, value, 'CUSTOM');
  loadProfile(currentOwner);
}

async function editCustomField(oldName) {
  const name = prompt('信息名称：', oldName);
  if (!name) return;
  const value = prompt('信息内容：', allItems[oldName] || '');
  if (!value) return;
  if (name !== oldName) {
    await saveItem(oldName, '', 'CUSTOM'); // archive old
  }
  await saveItem(name, value, 'CUSTOM');
  loadProfile(currentOwner);
}

async function removeCustomField(key) {
  if (!confirm('删除这个自定义信息？')) return;
  await saveItem(key, '', 'CUSTOM');
  loadProfile(currentOwner);
}

// ---------- Permissions page ----------
function renderPermissions() {
  // Render for both ME and OTHER
  renderPermGroup('perm-me-list', 'ME');
  renderPermGroup('perm-other-list', 'OTHER');
}

function renderPermGroup(containerId, owner) {
  const container = document.getElementById(containerId);
  if (!container) return;
  // Load owner's items if not current
  // For simplicity, we already have allPerms for currentOwner; fetch other on demand
  fetch(`${API}/profile?owner=${owner}`).then(r => r.json()).then(data => {
    const items = data.items || {};
    const perms = data.permissions || {};
    container.innerHTML = '';
    Object.keys(items).sort().forEach(key => {
      const visible = perms[key] !== false; // default true
      container.innerHTML += `
        <div class="toggle-row">
          <div><div class="toggle-name">${esc(key)}</div></div>
          <label class="toggle">
            <input type="checkbox" ${visible ? 'checked' : ''}
              onchange="togglePerm('${owner}','${esc(key)}', this.checked)">
            <span class="slider"></span>
          </label>
        </div>`;
    });
    if (Object.keys(items).length === 0) {
      container.innerHTML = '<div style="color:#B5B2C5;padding:12px 0;font-size:13px;">暂无字段</div>';
    }
  });
}

async function togglePerm(owner, key, visible) {
  await fetch(`${API}/profile/permission`, {
    method: 'POST',
    headers: {'Content-Type': 'application/x-www-form-urlencoded'},
    body: `owner=${owner}&key=${encodeURIComponent(key)}&visible=${visible}`
  });
  showToast(visible ? '已允许 AI 使用' : '已禁止 AI 使用');
}

// ---------- Toast ----------
function showToast(msg) {
  const t = document.getElementById('toast');
  t.textContent = msg;
  t.classList.add('show');
  setTimeout(() => t.classList.remove('show'), 1500);
}
