package org.h1code2.english.notebook.tts

import android.content.Context
import android.content.SharedPreferences
import android.speech.tts.TextToSpeech
import android.widget.Toast
import org.h1code2.english.notebook.R
import java.util.Locale

/**
 * 语音引擎统一封装。
 *
 * 策略（长按详情页发音按钮可在 自动 / 内置离线 间切换）：
 *  1. AUTO   — 系统引擎优先（保留美/英音切换），初始化失败或无英语语音时自动落回内置离线引擎
 *  2. OFFLINE— 强制内置离线引擎（sherpa-onnx + Piper，美音，完全离线）
 *
 * 系统默认引擎初始化失败时，枚举设备上已安装的其他 TTS 引擎逐个重试
 * （部分 ROM 的引擎不是系统默认引擎）；全部失败才使用内置离线引擎。
 */
class TtsHelper(context: Context) {

    enum class Accent(val locale: Locale) { US(Locale.US), UK(Locale.UK) }
    enum class Mode { AUTO, OFFLINE }

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("tts_prefs", Context.MODE_PRIVATE)
    private var engine: TextToSpeech? = null
    private var ready = false
    private var systemUsableForEnglish = false
    private var currentEnginePackage: String? = null
    private var triedEngines = mutableSetOf<String>()

    /** 当前生效的引擎描述（供 UI 显示） */
    @Volatile
    var engineLabel: String = ""
        private set

    var accent: Accent = Accent.US
        private set

    /** AUTO = 系统优先离线兜底；OFFLINE = 强制离线（持久化于设置） */
    @Volatile
    var mode: Mode = TtsSettingsLogic.modeFrom(
        context.getSharedPreferences("tts_prefs", Context.MODE_PRIVATE)
            .getString(KEY_ENGINE, TtsSettingsLogic.ENGINE_AUTO)
    )
        private set

    /** 全局语速（持久化），系统与离线引擎共用 */
    @Volatile
    var speechRate: Float = TtsSettingsLogic.clampRate(
        context.getSharedPreferences("tts_prefs", Context.MODE_PRIVATE)
            .getFloat(KEY_RATE, TtsSettingsLogic.RATE_DEFAULT)
    )
        private set

    private var pending: String? = null

    /** 设置页调用：切换引擎并持久化 */
    fun setMode(newMode: Mode) {
        forceMode(newMode)
        prefs.edit().putString(KEY_ENGINE, TtsSettingsLogic.modeKey(newMode)).apply()
    }

    /** 设置页调用：调整语速并持久化（打断当前播放，下次发音生效） */
    fun setSpeechRate(rate: Float) {
        speechRate = TtsSettingsLogic.clampRate(rate)
        prefs.edit().putFloat(KEY_RATE, speechRate).apply()
        OfflineTtsEngine.stop()
    }

    /** 系统引擎链全部失败、已自动落离线 */
    @Volatile
    private var offlineFallback = false

    fun forceMode(newMode: Mode) {
        mode = newMode
        if (newMode == Mode.OFFLINE) {
            engineLabel = "内置离线（美音）"
        } else {
            offlineFallback = false
            engineLabel = ""
            ready = false
            triedEngines.clear()
            currentEnginePackage = null
            initSystemEngine()
        }
    }

    private val listener = TextToSpeech.OnInitListener { status ->
        if (status == TextToSpeech.SUCCESS) {
            val avail = try {
                engine?.isLanguageAvailable(Accent.US.locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
            } catch (_: Exception) {
                TextToSpeech.LANG_NOT_SUPPORTED
            }
            // LANG_AVAILABLE 之上才认为真正可发音；MISSING_DATA（如缺语音包）不可用
            systemUsableForEnglish = avail >= TextToSpeech.LANG_AVAILABLE
            if (!systemUsableForEnglish) {
                android.util.Log.w("TtsHelper", "系统引擎对 en-US 不可用: $avail (2=MISSING_DATA)")
            }
        }

        if (status == TextToSpeech.SUCCESS && systemUsableForEnglish) {
            ready = true
            engineLabel = "系统（${accent.name}音）"
            pending?.let { text ->
                pending = null
                speakNow(text)
            }
            return@OnInitListener
        }

        // 失败 → 尝试其他已安装引擎 → 全失败落离线
        if (!tryNextSystemEngine()) {
            offlineFallback = true
            if (mode == Mode.AUTO) {
                engineLabel = "内置离线（美音）"
                Toast.makeText(appContext, R.string.tts_using_offline, Toast.LENGTH_LONG).show()
            }
            ready = false
            flushPendingOffline()
        }
    }

    private fun initSystemEngine(enginePackage: String? = null) {
        engine?.let { try { it.shutdown() } catch (_: Exception) { } }
        engine = null
        ready = false
        systemUsableForEnglish = false
        enginePackage?.let { triedEngines.add(it) }
        engine = enginePackage?.let { TextToSpeech(appContext, listener, it) }
            ?: TextToSpeech(appContext, listener)
    }

    private fun tryNextSystemEngine(): Boolean {
        if (mode == Mode.OFFLINE) return false
        val current = engine ?: return false
        val installed = try {
            current.engines?.map { it.name } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        val next = installed.filter { it !in triedEngines }.firstOrNull() ?: return false
        initSystemEngine(next)
        return true
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        // 离线兜底已生效（或强制离线）：直接离线合成，不再走系统引擎队列
        if (mode == Mode.OFFLINE || offlineFallback) {
            OfflineTtsEngine.stop()
            OfflineTtsEngine.speak(appContext, text, speechRate)
            return
        }
        if (engine == null) initSystemEngine()
        if (!ready) {
            pending = text
            return
        }
        speakNow(text)
    }

    private fun flushPendingOffline() {
        pending?.let { text ->
            pending = null
            OfflineTtsEngine.speak(appContext, text, speechRate)
        }
    }

    private fun speakNow(text: String) {
        val tts = engine ?: run {
            OfflineTtsEngine.speak(appContext, text, speechRate)
            return
        }
        if (mode == Mode.AUTO && !systemUsableForEnglish) {
            OfflineTtsEngine.speak(appContext, text, speechRate)
            return
        }
        tts.language = when (accent) {
            Accent.US -> Locale.US
            Accent.UK -> Locale.UK
        }
        tts.setSpeechRate(speechRate)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "english_notebook_$accent")
    }

    fun switchAccent(): Accent {
        accent = if (accent == Accent.US) Accent.UK else Accent.US
        engineLabel = if (mode == Mode.OFFLINE) "内置离线（美音）" else "系统（${accent.name}音）"
        return accent
    }

    val isReady: Boolean get() = ready || mode == Mode.OFFLINE

    fun shutdown() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        ready = false
        OfflineTtsEngine.stop()
    }

    companion object {
        const val KEY_ENGINE = "engine_mode"
        const val KEY_RATE = "speech_rate"
    }
}
