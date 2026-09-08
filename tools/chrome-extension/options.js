const $ = (id) => document.getElementById(id);

document.addEventListener('DOMContentLoaded', async () => {
  const cfg = await getConfig();
  $('ip').value = cfg.ip;
  $('port').value = cfg.port;
  $('token').value = cfg.token;
});

$('save').addEventListener('click', async () => {
  const ip = $('ip').value.trim();
  const port = parseInt($('port').value.trim(), 10) || 8765;
  const token = $('token').value.trim().toUpperCase();
  if (!/^\d{1,3}(\.\d{1,3}){3}$/.test(ip)) { setStatus('IP 格式不正确', true); return; }
  if (!token) { setStatus('请填写令牌', true); return; }
  await new Promise((r) => chrome.storage.local.set({ ip, port, token }, r));
  setStatus('已保存 ✓', false, true);
});

$('test').addEventListener('click', async () => {
  // 先暂存当前输入再测试
  await new Promise((r) => chrome.storage.local.set({
    ip: $('ip').value.trim(),
    port: parseInt($('port').value.trim(), 10) || 8765,
    token: $('token').value.trim().toUpperCase()
  }, r));
  setStatus('连接中…');
  try {
    await testConnection();
    setStatus('连接成功 ✓ 手机接收服务在线', false, true);
  } catch (e) {
    setStatus(`连接失败：${e.message}（检查 IP / 令牌 / 同一 Wi-Fi / 应用是否开启接收）`, true);
  }
});

function setStatus(text, isError, ok) {
  const el = $('status');
  el.textContent = text;
  el.className = isError ? 'err' : ok ? 'ok' : '';
}
