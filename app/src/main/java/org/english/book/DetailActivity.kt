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
import org.english.book.databinding.ActivityDetailBinding
import org.english.book.tts.TtsHelper
import org.english.book.ui.SimpleMarkdownRenderer
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class DetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "extra_id"
        fun intent(context: android.content.Context, id: Long): Intent =
            Intent(context, DetailActivity::class.java).putExtra(EXTRA_ID, id)
    }

    private lateinit var binding: ActivityDetailBinding
    private lateinit var viewModel: EntryViewModel
    private val tts by lazy { TtsHelper(this) }
    private var entry: EntryEntity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[EntryViewModel::class.java]
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id <= 0) {
            finish()
            return
        }

        viewModel.entry(id).observe(this) { entity ->
            entry = entity ?: return@observe
            render(entity)
        }

        binding.btnSpeak.setOnClickListener {
            entry?.let { tts.speak(SpeakText.forEntry(it)) }
        }
        binding.btnAccent.setOnClickListener {
            val accent = tts.switchAccent()
            binding.btnAccent.text =
                getString(if (accent == TtsHelper.Accent.US) R.string.accent_us else R.string.accent_uk)
            entry?.let { tts.speak(SpeakText.forEntry(it)) }
        }
        // 点按正文中的英文单词即朗读
        org.english.book.ui.WordTapSpeaker.attach(binding.textContent) { word ->
            tts.speak(word)
        }
    }

    private fun render(entity: EntryEntity) {
        binding.textTitle.text = entity.title
        val tagTextRes = if (entity.entryType == EntryType.WORD) R.string.filter_words
        else R.string.filter_sentences
        binding.textTypeTag.setText(tagTextRes)
        val bgRes = if (entity.entryType == EntryType.WORD) R.drawable.bg_tag_word
        else R.drawable.bg_tag_sentence
        binding.textTypeTag.setBackgroundResource(bgRes)
        val fgRes = if (entity.entryType == EntryType.WORD) R.color.word_tag_fg
        else R.color.sentence_tag_fg
        binding.textTypeTag.setTextColor(
            androidx.core.content.ContextCompat.getColor(this, fgRes)
        )
        binding.textContent.text = SimpleMarkdownRenderer.render(
            entity.content,
            quoteColor = androidx.core.content.ContextCompat.getColor(this, R.color.quote_bar),
            codeBg = androidx.core.content.ContextCompat.getColor(this, R.color.code_bg),
            codeFg = androidx.core.content.ContextCompat.getColor(this, R.color.code_fg)
        )
        binding.textContent.isVisible = entity.content.isNotBlank()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val current = entry
        return when (item.itemId) {
            R.id.action_edit -> {
                current?.let {
                    startActivity(EditEntryActivity.intent(this, it.id))
                }
                true
            }
            R.id.action_delete -> {
                current?.let { confirmDelete(it) }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun confirmDelete(entity: EntryEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_confirm_title)
            .setMessage(entity.title)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                viewModel.delete(entity) {
                    Toast.makeText(this, R.string.deleted, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun onDestroy() {
        tts.shutdown()
        super.onDestroy()
    }
}
