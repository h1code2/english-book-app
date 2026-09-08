# 英语笔记本 (English Notebook)

一个原生安卓英语学习笔记应用：记录单词和句子，支持音标 / 近似读音 / 例句 / 注意事项等结构化内容的 Markdown 排版展示、点击发音（美音 / 英音）、点词朗读、Markdown 预览、自动备份和闪卡复习。

## 下载安装

前往 [GitHub Releases](https://github.com/h1code2/english-book-app/releases) 下载最新的 `EnglishNotebook-v*.apk`，传到手机直接安装（需允许安装未知来源应用）。

> 从 v1.2 或更早的 debug 签名版本升级：签名不同无法覆盖安装。请先在旧版里**导出备份**，安装新版后再**从备份还原**。

## 功能

- **记录**：单词、句子两种类型；正文为自由 Markdown 文本（支持 `**加粗**`、`>` 引用块、`- ` 列表），一键插入单词 / 句子模板
- **浏览**：卡片列表（字母头像 + 时间徽标）+ 类型筛选 + **实时搜索**（输入即过滤，匹配标题和正文）+ 排序（最近修改 / 最新添加 / 标题 A→Z / Z→A）+「只看未掌握」+ 条数统计
- **发音**：点击喇叭或「发音」按钮用系统 TTS 朗读，可在美音 / 英音间切换
- **点词朗读**：详情页与闪卡背面，点按正文中的任意英文单词即朗读该词（高亮反馈）
- **Markdown 预览**：编辑页可在「编辑 / 预览」间切换，录入时实时查看排版效果
- **复习**：闪卡模式（可复习全部或只复习未掌握）——正面只显示单词或句子，翻面看完整内容，「已掌握 / 再看看」记录掌握状态并持久化
- **备份 / 还原**：数据保存在 SQLite 数据库文件（`english_notebook.db`）；可一键导出为 `.db` 文件（任意设备/工具可读），也可从备份文件还原（还原前校验文件合法性并二次确认，完成后自动重启应用）
- **电脑推送**：内置局域网接收服务，配合 Chrome 扩展，网页上选中单词 / 句子右键即推送到手机（见下文「电脑推送到手机」）
- **自动备份**：每次修改数据后自动在应用私有目录留一份备份（保留最近 7 份），可在备份对话框中一键从自动备份恢复
- **深色模式**：跟随系统深色 / 浅色模式
- **首次启动**自动预置 4 条示例（wallet、summer、两个例句）
- 包名：`org.h1code2.english.notebook`，隐私说明见 [PRIVACY.md](PRIVACY.md)

## 技术栈

- Kotlin + View 体系（Material 3），无 Compose
- Room 2.6.1（KAPT）单表存储，LiveData 驱动 UI
- 自研轻量 Markdown 渲染器（`SimpleMarkdownRenderer`，纯 android.text 实现，含单元测试）
- 系统 `TextToSpeech`

## 构建要求

- JDK 17（`gradle.properties` 已指向 Android Studio 内置 JBR；系统 Java 25 与 AGP 8.6 不兼容）
- Android SDK Platform 34（`local.properties` 指向本机 SDK）
- 依赖已预取到工作区 `.gradle-home/`（AndroidX 等库的离线缓存）

## 常用命令

```bash
# 构建 Debug APK（沙箱内请保持 GRADLE_USER_HOME 指向工作区缓存）
export GRADLE_USER_HOME="$PWD/.gradle-home"
./gradlew :app:assembleDebug

# 运行单元测试
./gradlew :app:testDebugUnitTest

# 安装到已连接设备 / 模拟器
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

产物：`app/build/outputs/apk/debug/app-debug.apk`（根目录 `EnglishNotebook-v*-release.apk` 为发布副本）

## 项目结构

```
app/src/main/java/org/h1code2/english/notebook/
├── MainActivity.kt          # 列表：搜索、筛选、入口
├── EditEntryActivity.kt     # 新增 / 编辑 + 模板插入
├── DetailActivity.kt        # 详情：Markdown 渲染 + TTS
├── ReviewActivity.kt        # 闪卡复习
├── EntryViewModel.kt        # LiveData + 协程封装
├── EntryAdapter.kt          # 列表适配器
├── SpeakText.kt             # TTS 文本生成
├── data/                    # Room：Entity / DAO / DB / SeedData
├── ui/SimpleMarkdownRenderer.kt
└── tts/TtsHelper.kt
```

## 本机沙箱相关的特殊配置（一般构建可忽略）

- `debug.keystore`：项目内 debug 签名（构建沙箱不允许写 `~/.android`）
- `.gradle-home/`：预填的 Gradle 发行版与依赖缓存（`GRADLE_USER_HOME` 指向它）
- `.android-home/`：AVD 的工作区克隆（模拟器验证用，约 8.8 GB，不需要可整目录删除）

## 电脑推送到手机（Chrome 扩展）

在电脑浏览器上看到单词 / 句子，几秒推送到手机：

**一次性配置**
1. 手机：右上角菜单 → 「电脑同步」→ 打开「开启电脑推送接收」，记下显示的 **服务地址** 与 **6 位令牌**
2. 电脑 Chrome：`chrome://extensions` → 开发者模式 → 「加载已解压的扩展程序」→ 选择仓库里的 `tools/chrome-extension` 目录（或从 Release 下载 zip 解压）
3. 扩展图标右键 → 「选项」→ 填入地址与令牌 → **测试连接** 显示成功

**日常使用**
- 网页选中文字 → 右键 → 「推送到英语笔记本」（自动判断单词 / 句子，重复自动跳过）
- 或点扩展图标 → 弹窗中粘贴 / 编辑 → 推送

注意：手机与电脑需在同一 Wi-Fi；推送时保持手机应用在前台（接收服务以前台通知形式运行）。

## 数据与备份

数据保存在应用的 SQLite 数据库文件中：`/data/data/org.h1code2.english.notebook/databases/english_notebook.db`（通过 Room 访问）。

- **导出**：工具栏云朵图标 → 「导出备份」，生成形如 `english_notebook_20260908_1430.db` 的文件到所选位置（导出前自动做 WAL checkpoint 合并日志，文件即完整数据库）
- **还原**：工具栏云朵图标 → 「从备份还原」，选择 `.db` 文件；应用会先校验文件（必须是包含 entries 表的合法 SQLite 库）并显示记录条数，确认后替换数据库并自动重启
- 备份文件就是标准 SQLite 数据库，可用电脑上的 DB Browser for SQLite、sqlite3 等工具直接打开查看

## 后续可扩展

收藏 / 标签、导出导入（JSON）、深色模式微调、复习统计。数据库为单表结构，加列即可扩展。
