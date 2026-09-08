const $ = (id) => document.getElementById(id);

// 打开弹窗时若网页有选中文本，自动带入
chrome.tabs ? null : null;
document.addEventListener('DOMContentLoaded', async () => {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  if (tab?.id != null) {
    try {
      const [result] = await chrome.scripting.executeScript({
        target: { tabId: tab.id },
        func: () => window.getSelection()?.toString() || ''
      });
      if (result?.result) $('text').value = result.result;
    } catch (_) { /* 某些页面无法注入，忽略 */ }
  }
  refreshStatus();
});

$('openOptions').addEventListener('click', () => chrome.runtime.openOptionsPage());

$('send').addEventListener('click', async () => {
  const text = $('text').value.trim();
  if (!text) { setStatus('请输入内容', true); return; }
  let type = $('type').value;
  if (type === 'auto') type = detectType(text);
  const { title, content } = splitTitleContent(text);
  $('send').disabled = true;
  setStatus('推送中…');
  try {
    const data = await new Promise((resolve, reject) => {
      chrome.runtime.sendMessage({ cmd: 'send', payload: { title, content, type } }, (res) => {
        chrome.runtime.lastError ? reject(new Error(chrome.runtime.lastError.message)) : resolve(res);
      });
    });
    if (data.duplicates > 0) {
      setStatus('已存在相同记录，已跳过', false, true);
    } else {
      setStatus(`已推送到手机 ✓（类型：${type === 'word' ? '单词' : '句子'}）`, false, true);
      $('text').value = '';
    }
    flashBadge(true);
  } catch (e) {
    setStatus(`失败：${e.message}`, true);
    flashBadge(false);
  } finally {
    $('send').disabled = false;
  }
});

function setStatus(text, isError, ok) {
  const el = $('status');
  el.textContent = text;
  el.className = isError ? 'err' : ok ? 'ok' : '';
}

async function refreshStatus() {
  const cfg = await getConfig();
  if (!cfg.token) {
    setStatus('未配置，请先填写手机地址与令牌', true);
  } else {
    setStatus(`目标：${cfg.ip}:${cfg.port}`);
  }
}
