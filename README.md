# 英语笔记本 (English Notebook)

一个原生安卓英语学习笔记应用：记录单词和句子，支持音标 / 近似读音 / 例句 / 注意事项等结构化内容的 Markdown 排版展示、点击发音（美音 / 英音）和闪卡复习。

## 功能

- **记录**：单词、句子两种类型；正文为自由 Markdown 文本（支持 `**加粗**`、`>` 引用块、`- ` 列表），一键插入单词 / 句子模板
- **浏览**：卡片列表（字母头像 + 时间徽标）+ 类型筛选 + **实时搜索**（输入即过滤，匹配标题和正文）+ 排序（最近修改 / 最新添加 / 标题 A→Z / Z→A）+「只看未掌握」+ 条数统计
- **发音**：点击喇叭或「发音」按钮用系统 TTS 朗读，可在美音 / 英音间切换
- **复习**：闪卡模式（可复习全部或只复习未掌握）——正面只显示单词或句子，翻面看完整内容，「已掌握 / 再看看」记录掌握状态并持久化
- **首次启动**自动预置 4 条示例（wallet、summer、两个例句）
- 包名：`org.english.book`

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

产物：`app/build/outputs/apk/debug/app-debug.apk`（根目录 `EnglishNotebook-v1.1-debug.apk` 为同一文件的副本）

## 项目结构

```
app/src/main/java/org/english/book/
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

## 后续可扩展

收藏 / 标签、导出导入（JSON）、深色模式微调、复习统计。数据库为单表结构，加列即可扩展。
