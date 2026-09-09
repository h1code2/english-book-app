package org.h1code2.english.notebook.tts

/**
 * 语音设置纯逻辑（可 JVM 单测）。
 */
object TtsSettingsLogic {

    const val RATE_MIN = 0.5f
    const val RATE_MAX = 1.5f
    const val RATE_DEFAULT = 1.0f

    const val ENGINE_AUTO = "auto"
    const val ENGINE_OFFLINE = "offline"

    /** 语速合法化：范围外取边界值，非法输入回默认 */
    fun clampRate(raw: Float): Float = when {
        raw.isNaN() || raw.isInfinite() -> RATE_DEFAULT
        raw < RATE_MIN -> RATE_MIN
        raw > RATE_MAX -> RATE_MAX
        else -> raw
    }

    /** 引擎字符串 → TtsHelper.Mode */
    fun modeFrom(raw: String?): TtsHelper.Mode =
        if (raw == ENGINE_OFFLINE) TtsHelper.Mode.OFFLINE else TtsHelper.Mode.AUTO

    /** TtsHelper.Mode → 存储字符串 */
    fun modeKey(mode: TtsHelper.Mode): String =
        if (mode == TtsHelper.Mode.OFFLINE) ENGINE_OFFLINE else ENGINE_AUTO
}
