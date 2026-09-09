package org.h1code2.english.notebook

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.h1code2.english.notebook.data.BackupManager
import org.h1code2.english.notebook.databinding.ActivitySettingsBinding
import org.h1code2.english.notebook.tts.TtsHelper
import org.h1code2.english.notebook.tts.TtsSettingsLogic
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val tts by lazy { TtsHelper(this) }
    private val listPrefs by lazy {
        getSharedPreferences("list_prefs", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupVoice()
        setupReview()
        setupListSection()
        setupBackup()
        setupSyncRow()
    }

    //region 语音

    private fun setupVoice() {
        when (tts.mode) {
            TtsHelper.Mode.OFFLINE -> binding.toggleEngine.check(R.id.btnEngineOffline)
            else -> binding.toggleEngine.check(R.id.btnEngineAuto)
        }
        binding.toggleEngine.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val mode = if (checkedId == R.id.btnEngineOffline) TtsHelper.Mode.OFFLINE
            else TtsHelper.Mode.AUTO
            tts.setMode(mode)
            binding.textEngineDesc.text = getString(
                if (mode == TtsHelper.Mode.OFFLINE) R.string.settings_engine_offline_desc
                else R.string.settings_engine_auto_desc
            )
            Toast.makeText(this, R.string.settings_engine_applied, Toast.LENGTH_SHORT).show()
        }

        binding.sliderRate.valueFrom = TtsSettingsLogic.RATE_MIN
        binding.sliderRate.valueTo = TtsSettingsLogic.RATE_MAX
        binding.sliderRate.stepSize = 0.05f
        binding.sliderRate.value = tts.speechRate
        binding.textRateValue.text = fmtRate(tts.speechRate)
        binding.sliderRate.addOnChangeListener { _, value, fromUser ->
            binding.textRateValue.text = fmtRate(value)
            if (fromUser) tts.setSpeechRate(value)
        }
        // 松手即试听（固定示例文本，能明显听出语速差异）
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
    }

    private fun fmtRate(v: Float): String = String.format("%.2fx", v)

    //endregion

    //region 复习

    private fun setupReview() {
        binding.rowReview.setOnClickListener { showReviewModeDialog() }
    }

    private fun showReviewModeDialog() {
        val items = arrayOf(
            getString(R.string.review_mode_all),
            getString(R.string.review_mode_learning)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.menu_review)
            .setItems(items) { _, which ->
                startActivity(
                    ReviewActivity.intent(this, if (which == 0) "all" else "learning")
                )
            }
            .show()
    }

    //endregion

    //region 列表：只看未掌握

    private fun setupListSection() {
        binding.switchLearningOnly.isChecked = listPrefs.getBoolean("only_learning", false)
        binding.switchLearningOnly.setOnCheckedChangeListener { _, checked ->
            listPrefs.edit().putBoolean("only_learning", checked).apply()
            Toast.makeText(this, R.string.settings_applied_back_hint, Toast.LENGTH_SHORT).show()
        }
    }

    //endregion

    //region 备份 / 还原

    private fun setupBackup() {
        binding.rowBackupExport.setOnClickListener { launchExport() }
        binding.rowBackupImport.setOnClickListener { launchImport() }
        binding.rowAutoRestore.setOnClickListener { showAutoBackupDialog() }
    }

    private fun maybeRequestPersistable(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // 一次性授权即可，不影响导出导入
        }
    }

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            if (uri == null) return@registerForActivityResult
            maybeRequestPersistable(uri)
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    BackupManager.export(applicationContext, uri)
                }
                Toast.makeText(
                    this@SettingsActivity,
                    if (result.ok) getString(R.string.backup_export_ok, result.count)
                    else result.message,
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            maybeRequestPersistable(uri)
            lifecycleScope.launch {
                val check = withContext(Dispatchers.IO) {
                    BackupManager.inspect(applicationContext, uri)
                }
                if (!check.ok) {
                    Toast.makeText(this@SettingsActivity, check.message, Toast.LENGTH_LONG).show()
                    return@launch
                }
                showRestoreConfirm(check.count) { BackupManager.restore(applicationContext, uri) }
            }
        }

    private fun showAutoBackupDialog() {
        lifecycleScope.launch {
            val backups = withContext(Dispatchers.IO) {
                BackupManager.listAutoBackups(applicationContext)
            }
            if (backups.isEmpty()) {
                Toast.makeText(this@SettingsActivity, R.string.auto_backup_list_empty, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val labels = backups.map { BackupManager.autoBackupLabel(applicationContext, it) }.toTypedArray()
            MaterialAlertDialogBuilder(this@SettingsActivity)
                .setTitle(R.string.restore_from_auto)
                .setItems(labels) { _, which ->
                    val file = backups[which]
                    lifecycleScope.launch {
                        val check = withContext(Dispatchers.IO) {
                            BackupManager.inspect(applicationContext, Uri.fromFile(file))
                        }
                        if (!check.ok) {
                            Toast.makeText(this@SettingsActivity, check.message, Toast.LENGTH_LONG).show()
                            return@launch
                        }
                        showRestoreConfirm(check.count) {
                            BackupManager.restoreFromAuto(applicationContext, file)
                        }
                    }
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }
    }

    /** 还原确认框（展示条数）→ 执行 restore → 成功后重启应用 */
    private fun showRestoreConfirm(
        count: Int,
        restore: suspend () -> BackupManager.Result
    ) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.backup_confirm_title)
            .setMessage(getString(R.string.backup_confirm_msg, count))
            .setPositiveButton(R.string.action_restore) { _, _ ->
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) { restore() }
                    if (result.ok) {
                        Toast.makeText(
                            this@SettingsActivity,
                            getString(R.string.backup_restore_ok, result.count),
                            Toast.LENGTH_LONG
                        ).show()
                        restartApp()
                    } else {
                        Toast.makeText(this@SettingsActivity, result.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun launchExport() {
        val name = "english_notebook_" +
            SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date()) + ".db"
        exportLauncher.launch(name)
    }

    private fun launchImport() {
        importLauncher.launch(arrayOf("application/octet-stream", "*/*"))
    }

    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        finish()
        startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    //endregion

    //region 电脑同步

    private fun setupSyncRow() {
        binding.rowSyncPc.setOnClickListener {
            startActivity(Intent(this, SyncActivity::class.java))
        }
    }

    //endregion
}
