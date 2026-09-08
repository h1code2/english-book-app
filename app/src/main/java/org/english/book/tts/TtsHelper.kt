package org.english.book.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.Toast
import java.util.Locale

/**
 * 系统 TTS 封装：支持美音 / 英音切换，统一初始化与释放。
 */
class TtsHelper(context: Context) {

    enum class Accent(val locale: Locale) { US(Locale.US), UK(Locale.UK) }

    private val appContext = context.applicationContext
    private var engine: TextToSpeech? = null
    private var ready = false
    var accent: Accent = Accent.US
        private set

    private val listener = TextToSpeech.OnInitListener { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            // 引擎就绪后补发初始化期间的待读文本（点词朗读首次触达时常见）
            pending?.let { text ->
                pending = null
                speakNow(text)
            }
        } else {
            Toast.makeText(appContext, "当前设备没有可用的语音引擎", Toast.LENGTH_SHORT).show()
        }
    }

    private var pending: String? = null

    fun speak(text: String) {
        if (text.isBlank()) return
        if (engine == null) {
            engine = TextToSpeech(appContext, listener)
        }
        if (!ready) {
            pending = text
            return
        }
        speakNow(text)
    }

    private fun speakNow(text: String) {
        engine?.let { tts ->
            tts.language = when (accent) {
                Accent.US -> Locale.US
                Accent.UK -> Locale.UK
            }
            tts.setSpeechRate(0.95f)
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "english_notebook_$accent")
        }
    }

    fun switchAccent(): Accent {
        accent = if (accent == Accent.US) Accent.UK else Accent.US
        return accent
    }

    val isReady: Boolean get() = ready

    fun shutdown() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        ready = false
    }
}
