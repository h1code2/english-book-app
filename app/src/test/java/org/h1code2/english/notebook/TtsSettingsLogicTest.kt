package org.h1code2.english.notebook

import org.h1code2.english.notebook.tts.TtsHelper
import org.h1code2.english.notebook.tts.TtsSettingsLogic
import org.junit.Assert.assertEquals
import org.junit.Test

class TtsSettingsLogicTest {

    @Test
    fun `rate clamped to bounds`() {
        assertEquals(0.5f, TtsSettingsLogic.clampRate(0.1f))
        assertEquals(1.5f, TtsSettingsLogic.clampRate(2.0f))
        assertEquals(1.0f, TtsSettingsLogic.clampRate(1.0f))
        assertEquals(0.75f, TtsSettingsLogic.clampRate(0.75f))
    }

    @Test
    fun `rate invalid input falls back to default`() {
        assertEquals(1.0f, TtsSettingsLogic.clampRate(Float.NaN))
        assertEquals(1.0f, TtsSettingsLogic.clampRate(Float.POSITIVE_INFINITY))
    }

    @Test
    fun `mode string mapping round trip`() {
        assertEquals(TtsHelper.Mode.OFFLINE, TtsSettingsLogic.modeFrom("offline"))
        assertEquals(TtsHelper.Mode.AUTO, TtsSettingsLogic.modeFrom("auto"))
        assertEquals(TtsHelper.Mode.AUTO, TtsSettingsLogic.modeFrom("garbage"))
        assertEquals(TtsHelper.Mode.AUTO, TtsSettingsLogic.modeFrom(null))
        assertEquals("offline", TtsSettingsLogic.modeKey(TtsHelper.Mode.OFFLINE))
        assertEquals("auto", TtsSettingsLogic.modeKey(TtsHelper.Mode.AUTO))
    }
}
