package org.h1code2.english.notebook

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.view.isVisible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.h1code2.english.notebook.data.BackupManager
import org.h1code2.english.notebook.data.EntryType
import org.h1code2.english.notebook.data.ListFilter
import org.h1code2.english.notebook.data.SortMode
import org.h1code2.english.notebook.databinding.ActivityMainBinding
import org.h1code2.english.notebook.tts.TtsHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: EntryViewModel
    private lateinit var adapter: EntryAdapter
    private val tts by lazy { TtsHelper(this) }

    private val filter = ListFilter()
    private val sortModes = listOf(SortMode.RECENT, SortMode.NEWEST, SortMode.TITLE_ASC, SortMode.TITLE_DESC)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[EntryViewModel::class.java]

        setSupportActionBar(binding.toolbar)

        adapter = EntryAdapter(
            onClick = { entry -> openDetail(entry.id) },
            onSpeak = { entry -> tts.speak(SpeakText.forEntry(entry)) }
        )

        binding.recyclerEntries.layoutManager = LinearLayoutManager(this)
        binding.recyclerEntries.adapter = adapter

        binding.chipAll.setOnClickListener { setType(null) }
        binding.chipWords.setOnClickListener { setType(EntryType.WORD.name) }
        binding.chipSentences.setOnClickListener { setType(EntryType.SENTENCE.name) }

        // 实时搜索：输入即过滤
        binding.inputSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                filter.query = s?.toString()?.trim() ?: ""
                applyFilter()
            }
        })

        binding.fabAdd.setOnClickListener {
            startActivity(EditEntryActivity.intent(this))
        }

        observeEntries()
        observeStats()
    }

    private fun setType(type: String?) {
        filter.type = type
        binding.chipAll.isChecked = type == null
        binding.chipWords.isChecked = type == EntryType.WORD.name
        binding.chipSentences.isChecked = type == EntryType.SENTENCE.name
        applyFilter()
    }

    private fun applyFilter() {
        viewModel.search(filter.copy())
    }

    private fun observeEntries() {
        viewModel.entries.observe(this) { list ->
            adapter.submitList(list)
            binding.layoutEmpty.isVisible = list.isEmpty()
            binding.recyclerEntries.isVisible = list.isNotEmpty()
            viewModel.refreshStats()
            val hasCondition =
                filter.query.isNotEmpty() || filter.type != null || filter.onlyLearning
            if (list.isEmpty()) {
                binding.textEmptyTitle.setText(
                    if (hasCondition) R.string.empty_no_result_title else R.string.empty_title
                )
                binding.textEmptySubtitle.setText(
                    if (hasCondition) R.string.empty_no_result_subtitle else R.string.empty_subtitle
                )
            }
        }
    }

    private fun observeStats() {
        viewModel.stats.observe(this) { s ->
            binding.textCount.text =
                getString(R.string.count_format, s.total, s.total - s.mastered)
            binding.textCount.isVisible = s.total > 0
        }
    }

    private fun openDetail(id: Long) {
        startActivity(DetailActivity.intent(this, id))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_review -> {
                showReviewModeDialog()
                true
            }
            R.id.action_sort -> {
                showSortDialog()
                true
            }
            R.id.action_learning -> {
                filter.onlyLearning = !filter.onlyLearning
                item.setIcon(
                    if (filter.onlyLearning) R.drawable.ic_school_checked else R.drawable.ic_school
                )
                // 选中态用金色：menu XML 的 iconTint(白) 会覆盖新 icon 自带颜色，这里同步纠正
                item.setIconTintList(
                    android.content.res.ColorStateList.valueOf(
                        androidx.core.content.ContextCompat.getColor(
                            this,
                            if (filter.onlyLearning) R.color.again_orange else android.R.color.white
                        )
                    )
                )
                applyFilter()
                true
            }
            R.id.action_backup -> {
                showBackupDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
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

    private fun showSortDialog() {
        val labels = arrayOf(
            getString(R.string.sort_recent),
            getString(R.string.sort_newest),
            getString(R.string.sort_title_asc),
            getString(R.string.sort_title_desc)
        )
        val modes = sortModes
        val checked = modes.indexOf(filter.sort)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_sort)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                filter.sort = modes[which]
                applyFilter()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    //region 备份 / 还原

    private fun maybeRequestPersistable(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // 非持久化授权（例如某些提供方），导出导入一次性进行，不影响功能
        }
    }

    private fun showBackupDialog() {
        val items = arrayOf(
            getString(R.string.backup_export),
            getString(R.string.backup_import),
            getString(R.string.restore_from_auto)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_backup)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> launchExport()
                    1 -> launchImport()
                    2 -> showAutoBackupDialog()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showAutoBackupDialog() {
        lifecycleScope.launch {
            val backups = withContext(Dispatchers.IO) {
                BackupManager.listAutoBackups(applicationContext)
            }
            if (backups.isEmpty()) {
                Toast.makeText(this@MainActivity, R.string.auto_backup_list_empty, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val labels = backups.map {
                BackupManager.autoBackupLabel(applicationContext, it)
            }.toTypedArray()
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(R.string.restore_from_auto)
                .setItems(labels) { _, which ->
                    val file = backups[which]
                    lifecycleScope.launch {
                        val check = withContext(Dispatchers.IO) {
                            BackupManager.inspect(applicationContext, Uri.fromFile(file))
                        }
                        if (!check.ok) {
                            Toast.makeText(this@MainActivity, check.message, Toast.LENGTH_LONG).show()
                            return@launch
                        }
                        MaterialAlertDialogBuilder(this@MainActivity)
                            .setTitle(R.string.backup_confirm_title)
                            .setMessage(getString(R.string.backup_confirm_msg, check.count))
                            .setPositiveButton(R.string.action_restore) { _, _ ->
                                lifecycleScope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        BackupManager.restoreFromAuto(applicationContext, file)
                                    }
                                    if (result.ok) {
                                        Toast.makeText(
                                            this@MainActivity,
                                            getString(R.string.backup_restore_ok, result.count),
                                            Toast.LENGTH_LONG
                                        ).show()
                                        restartApp()
                                    } else {
                                        Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                            .setNegativeButton(R.string.action_cancel, null)
                            .show()
                    }
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
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
                if (result.ok) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.backup_export_ok, result.count),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            maybeRequestPersistable(uri)
            // 先在主线程做一次轻量确认文案所需的信息在 IO 校验后给出
            lifecycleScope.launch {
                val check = withContext(Dispatchers.IO) {
                    BackupManager.inspect(applicationContext, uri)
                }
                if (!check.ok) {
                    Toast.makeText(this@MainActivity, check.message, Toast.LENGTH_LONG).show()
                    return@launch
                }
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle(R.string.backup_confirm_title)
                    .setMessage(getString(R.string.backup_confirm_msg, check.count))
                    .setPositiveButton(R.string.action_restore) { _, _ ->
                        lifecycleScope.launch {
                            val result = withContext(Dispatchers.IO) {
                                BackupManager.restore(applicationContext, uri)
                            }
                            if (result.ok) {
                                Toast.makeText(
                                    this@MainActivity,
                                    getString(R.string.backup_restore_ok, result.count),
                                    Toast.LENGTH_LONG
                                ).show()
                                // 数据库文件已整体替换，重启进程以重建连接
                                restartApp()
                            } else {
                                Toast.makeText(
                                    this@MainActivity, result.message, Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                    .setNegativeButton(R.string.action_cancel, null)
                    .show()
            }
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

    override fun onDestroy() {
        tts.shutdown()
        super.onDestroy()
    }
}
