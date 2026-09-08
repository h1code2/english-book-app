package org.english.book

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import org.english.book.data.EntryEntity
import org.english.book.data.EntryType
import org.english.book.databinding.ActivityEditEntryBinding
import org.english.book.ui.SimpleMarkdownRenderer

class EditEntryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "extra_id"
        const val EXTRA_NEW = "extra_new"

        /** 新增 */
        fun intent(context: android.content.Context): Intent =
            Intent(context, EditEntryActivity::class.java).putExtra(EXTRA_NEW, true)

        /** 编辑已有记录 */
        fun intent(context: android.content.Context, id: Long): Intent =
            Intent(context, EditEntryActivity::class.java).putExtra(EXTRA_ID, id)
    }

    private lateinit var binding: ActivityEditEntryBinding
    private lateinit var viewModel: EntryViewModel
    private var existing: EntryEntity? = null
    private var loaded = false

    private val isEditMode: Boolean
        get() = intent.hasExtra(EXTRA_ID)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditEntryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[EntryViewModel::class.java]

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.title = getString(if (isEditMode) R.string.btn_edit else R.string.add_entry)

        binding.btnTemplate.setOnClickListener { insertTemplate() }
        binding.btnSave.setOnClickListener { save() }

        if (isEditMode) {
            val id = intent.getLongExtra(EXTRA_ID, -1L)
            viewModel.entry(id).observe(this) { entity ->
                if (entity != null && !loaded) {
                    loaded = true
                    existing = entity
                    binding.inputTitle.setText(entity.title)
                    binding.inputContent.setText(entity.content)
                    if (entity.entryType == EntryType.SENTENCE) {
                        binding.radioSentence.isChecked = true
                    } else {
                        binding.radioWord.isChecked = true
                    }
                }
            }
        }
    }

    private fun selectedType(): EntryType =
        if (binding.radioSentence.isChecked) EntryType.SENTENCE else EntryType.WORD

    private fun insertTemplate() {
        val template = if (selectedType() == EntryType.WORD) {
            """
                **word** 的音标是：**/…/**

                读音近似：**“…”**
                意思是：**…**。

                拼读：

                > w-o-r-d = word

                例句：

                > **This is a word.**
                > 这是一个单词。

                注意：…
            """.trimIndent()
        } else {
            """
                意思是：**…**

                读音：**/…/**
                近似读法：**“…”**

                - **word1**：意思1
                - **word2**：意思2
            """.trimIndent()
        }
        val current = binding.inputContent.text?.toString()?.trim() ?: ""
        binding.inputContent.setText(if (current.isEmpty()) template else "$current\n\n$template")
        binding.inputContent.setSelection(binding.inputContent.text?.length ?: 0)
    }

    private fun save() {
        val title = binding.inputTitle.text?.toString()?.trim().orEmpty()
        if (title.isEmpty()) {
            Toast.makeText(this, R.string.title_required, Toast.LENGTH_SHORT).show()
            return
        }
        val content = binding.inputContent.text?.toString().orEmpty()
        val type = selectedType().name

        val base = existing ?: EntryEntity(
            title = title,
            content = content,
            type = type,
            createdAt = 0L,
            updatedAt = 0L
        )
        val toSave = base.copy(title = title, content = content, type = type)

        viewModel.save(toSave, isNew = existing == null) {
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
