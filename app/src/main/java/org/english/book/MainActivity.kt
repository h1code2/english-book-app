package org.english.book

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.view.isVisible
import org.english.book.data.EntryType
import org.english.book.data.ListFilter
import org.english.book.data.SortMode
import org.english.book.databinding.ActivityMainBinding
import org.english.book.tts.TtsHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder

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
                applyFilter()
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

    override fun onDestroy() {
        tts.shutdown()
        super.onDestroy()
    }
}
