// 公共逻辑：配置读取、类型判断、推送到手机（被 background 与 popup 复用）

const DEFAULT_CONFIG = { ip: '192.168.1.100', port: 8765, token: '' };

async function getConfig() {
  return new Promise((resolve) => {
    chrome.storage.local.get(['ip', 'port', 'token'], (items) => {
      resolve({ ...DEFAULT_CONFIG, ...items });
    });
  });
}

// 自动判断单词 / 句子（与应用端 PushProtocol.detectType 同规则）
function detectType(text) {
  const t = (text || '').trim();
  if (!t) return 'word';
  if (/^[A-Za-z][A-Za-z'’-]*$/.test(t)) return 'word';
  const spaces = (t.match(/\s/g) || []).length;
  const hasPunct = /[。！？.!?，,；;：:]/.test(t);
  const hasCjk = /[\u4e00-\u9fa5]/.test(t);
  return spaces >= 2 || hasPunct || hasCjk ? 'sentence' : 'word';
}

// 第一行作为标题
function splitTitleContent(text) {
  const t = (text || '').trim();
  if (!t) return { title: '', content: '' };
  const firstLine = t.split('\n').find((l) => l.trim())?.trim() || t;
  const title = firstLine.length <= 80 ? firstLine : firstLine.slice(0, 79) + '…';
  const content = t === title ? '' : t.slice(title.length).trim();
  return { title, content };
}

async function sendEntry({ title, content, type }) {
  const cfg = await getConfig();
  if (!cfg.token) {
    throw new Error('未配置令牌，请先在扩展设置中填写');
  }
  const url = `http://${cfg.ip}:${cfg.port}/add`;
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Token': cfg.token },
    body: JSON.stringify({ title, content, type })
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok || !data.ok) {
    throw new Error(data.error || `HTTP ${res.status}`);
  }
  return data; // {ok, added, duplicates}
}

async function testConnection() {
  const cfg = await getConfig();
  const url = `http://${cfg.ip}:${cfg.port}/ping`;
  const res = await fetch(url, { headers: { 'X-Token': cfg.token } });
  const data = await res.json().catch(() => ({}));
  if (!res.ok || !data.ok) {
    throw new Error(data.error || `HTTP ${res.status}`);
  }
  return data;
}

// 推送原始文本：自动拆标题/判类型
async function sendRawText(text) {
  const { title, content } = splitTitleContent(text);
  const type = detectType(title || content);
  return sendEntry({ title, content, type });
}

// badge 反馈
function flashBadge(ok, text) {
  chrome.action.setBadgeBackgroundColor({ color: ok ? '#2E7D32' : '#C62828' });
  chrome.action.setBadgeText({ text: text || (ok ? '✓' : '✗') });
  setTimeout(() => chrome.action.setBadgeText({ text: '' }), 2500);
}
