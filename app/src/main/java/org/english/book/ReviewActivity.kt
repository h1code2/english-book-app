package org.english.book

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import org.english.book.data.EntryEntity
import org.english.book.data.EntryType
import org.english.book.databinding.ActivityReviewBinding
import org.english.book.tts.TtsHelper
import org.english.book.ui.SimpleMarkdownRenderer

class ReviewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE = "extra_mode"
        fun intent(context: android.content.Context, mode: String = "all"): android.content.Intent =
            android.content.Intent(context, ReviewActivity::class.java).putExtra(EXTRA_MODE, mode)
    }

    private lateinit var binding: ActivityReviewBinding
    private lateinit var viewModel: EntryViewModel
    private val tts by lazy { TtsHelper(this) }

    private var queue: List<EntryEntity> = emptyList()
    private var index = 0
    private var masteredCount = 0
    private var reviewedCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[EntryViewModel::class.java]

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val mode = intent.getStringExtra(EXTRA_MODE) ?: "all"
        viewModel.cardsForReview(mode) { list ->
            if (list.isEmpty()) {
                showState(State.EMPTY)
            } else {
                queue = list
                index = 0
                masteredCount = 0
                reviewedCount = 0
                showState(State.FRONT)
                showCurrent()
            }
        }

        binding.btnSpeakFront.setOnClickListener {
            current()?.let { tts.speak(SpeakText.forEntry(it)) }
        }

        binding.btnReveal.setOnClickListener {
            if (current() != null) showState(State.BACK)
        }

        binding.btnMastered.setOnClickListener { answer(mastered = true) }
        binding.btnAgain.setOnClickListener { answer(mastered = false) }

        binding.btnBackToList.setOnClickListener { finish() }
    }

    private fun current(): EntryEntity? = queue.getOrNull(index)

    private fun answer(mastered: Boolean) {
        val entry = current() ?: return
        viewModel.setMastered(entry, mastered)
        reviewedCount++
        if (mastered) masteredCount++
        index++
        if (index >= queue.size) {
            binding.textDoneStat.text = getString(R.string.review_done_stat, reviewedCount, masteredCount)
            showState(State.DONE)
        } else {
            showState(State.FRONT)
            showCurrent()
        }
    }

    private fun showCurrent() {
        val entry = current() ?: return
        binding.textProgress.text = getString(R.string.review_progress, index + 1, queue.size)
        binding.textFront.text = entry.title
        binding.textBackTitle.text = entry.title

        val tagRes = if (entry.entryType == EntryType.WORD) R.string.filter_words
        else R.string.filter_sentences
        binding.textFrontTag.setText(tagRes)
        val bgRes = if (entry.entryType == EntryType.WORD) R.drawable.bg_tag_word
        else R.drawable.bg_tag_sentence
        binding.textFrontTag.setBackgroundResource(bgRes)
        val fgRes = if (entry.entryType == EntryType.WORD) R.color.word_tag_fg
        else R.color.sentence_tag_fg
        binding.textFrontTag.setTextColor(ContextCompat.getColor(this, fgRes))

        binding.textBackContent.text = SimpleMarkdownRenderer.render(entry.content)
    }

    private enum class State { FRONT, BACK, DONE, EMPTY }

    private fun showState(state: State) {
        binding.layoutFront.isVisible = state == State.FRONT
        binding.btnReveal.isVisible = state == State.FRONT
        binding.layoutBack.isVisible = state == State.BACK
        binding.layoutDone.isVisible = state == State.DONE
        binding.textReviewEmpty.isVisible = state == State.EMPTY
        binding.textProgress.isVisible = state == State.FRONT || state == State.BACK
    }

    override fun onDestroy() {
        tts.shutdown()
        super.onDestroy()
    }
}
