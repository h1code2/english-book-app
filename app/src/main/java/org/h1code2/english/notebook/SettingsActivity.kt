package org.h1code2.english.notebook

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.Slider
import org.h1code2.english.notebook.databinding.ActivitySettingsBinding
import org.h1code2.english.notebook.tts.TtsHelper
import org.h1code2.english.notebook.tts.TtsSettingsLogic

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val tts by lazy { TtsHelper(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        // 引擎选择（初始状态）
        when (tts.mode) {
            TtsHelper.Mode.OFFLINE -> binding.toggleEngine.check(R.id.btnEngineOffline)
            else -> binding.toggleEngine.check(R.id.btnEngineAuto)
        }
        binding.toggleEngine.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val mode = if (checkedId == R.id.btnEngineOffline) TtsHelper.Mode.OFFLINE
            else TtsHelper.Mode.AUTO
            tts.setMode(mode)
            Toast.makeText(this, R.string.settings_engine_applied, Toast.LENGTH_SHORT).show()
        }

        // 语速滑条
        binding.sliderRate.valueFrom = TtsSettingsLogic.RATE_MIN
        binding.sliderRate.valueTo = TtsSettingsLogic.RATE_MAX
        binding.sliderRate.stepSize = 0.05f
        binding.sliderRate.value = tts.speechRate
        binding.textRateValue.text = fmtRate(tts.speechRate)
        binding.sliderRate.addOnChangeListener { _, value, fromUser ->
            binding.textRateValue.text = fmtRate(value)
            if (fromUser) tts.setSpeechRate(value)
        }
        // 松手即试听（用固定的示例文本，能明显听出语速差异）
        binding.sliderRate.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) = Unit
            override fun onStopTrackingTouch(slider: Slider) {
                tts.speak(getString(R.string.settings_sample_text))
            }
        })

        binding.btnResetDefaults.setOnClickListener {
            tts.setMode(TtsHelper.Mode.AUTO)
            tts.setSpeechRate(TtsSettingsLogic.RATE_DEFAULT)
            binding.toggleEngine.check(R.id.btnEngineAuto)
            binding.sliderRate.value = TtsSettingsLogic.RATE_DEFAULT
            Toast.makeText(this, R.string.settings_reset_done, Toast.LENGTH_SHORT).show()
        }

        binding.textEngineDesc.text =
            getString(if (tts.mode == TtsHelper.Mode.OFFLINE) R.string.settings_engine_offline_desc
            else R.string.settings_engine_auto_desc)
    }

    private fun fmtRate(v: Float): String = String.format("%.2fx", v)
}
