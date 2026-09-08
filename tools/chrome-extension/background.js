// 后台：右键菜单推送 + 消息转发（popup 也直接调 common，不做中转）

importScripts('common.js');

chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.create({
    id: 'push-selection',
    title: '推送到英语笔记本',
    contexts: ['selection']
  });
});

chrome.contextMenus.onClicked.addListener(async (info) => {
  if (info.menuItemId !== 'push-selection' || !info.selectionText) return;
  try {
    const cfg = await getConfig();
    if (!cfg.token) {
      chrome.runtime.openOptionsPage();
      flashBadge(false, '!');
      return;
    }
    const result = await sendRawText(info.selectionText);
    flashBadge(true);
    notify(
      result.duplicates > 0
        ? `已存在，跳过重复：${info.selectionText.slice(0, 40)}`
        : `已推送到手机：${info.selectionText.slice(0, 40)}`
    );
  } catch (e) {
    flashBadge(false);
    notify(`推送失败：${e.message}`);
  }
});

function notify(message) {
  chrome.notifications.create({
    type: 'basic',
    iconUrl: 'icons/icon128.png',
    title: '英语笔记本',
    message
  }, (id) => setTimeout(() => chrome.notifications.clear(id), 3000));
}

// popup / options 通过消息复用 common 里的函数（service worker 上下文）
chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  (async () => {
    try {
      if (msg.cmd === 'send') {
        sendResponse(await sendEntry(msg.payload));
      } else if (msg.cmd === 'sendRaw') {
        sendResponse(await sendRawText(msg.text));
      } else if (msg.cmd === 'test') {
        sendResponse(await testConnection());
      } else {
        sendResponse({ ok: false, error: 'unknown cmd' });
      }
    } catch (e) {
      sendResponse({ ok: false, error: e.message });
    }
  })();
  return true; // 异步 sendResponse
});
