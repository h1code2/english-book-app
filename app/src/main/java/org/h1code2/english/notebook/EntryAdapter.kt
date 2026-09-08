package org.h1code2.english.notebook

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.h1code2.english.notebook.data.EntryEntity
import org.h1code2.english.notebook.data.EntryType
import org.h1code2.english.notebook.databinding.ItemEntryBinding
import org.h1code2.english.notebook.ui.SimpleMarkdownRenderer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EntryAdapter(
    private val onClick: (EntryEntity) -> Unit,
    private val onSpeak: (EntryEntity) -> Unit
) : ListAdapter<EntryEntity, EntryAdapter.EntryViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<EntryEntity>() {
            override fun areItemsTheSame(oldItem: EntryEntity, newItem: EntryEntity) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: EntryEntity, newItem: EntryEntity) =
                oldItem == newItem
        }

        private val DAY_FORMAT = SimpleDateFormat("M月d日", Locale.getDefault())
        private const val DAY_MS = 24 * 60 * 60 * 1000L
    }

    inner class EntryViewHolder(val binding: ItemEntryBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntryViewHolder {
        val binding = ItemEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EntryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EntryViewHolder, position: Int) {
        val entry = getItem(position)
        val ctx = holder.binding.root.context

        holder.binding.textTitle.text = entry.title
        holder.binding.textSummary.text = SimpleMarkdownRenderer.summary(entry.content)
        holder.binding.textSummary.isVisible = holder.binding.textSummary.text.isNotBlank()

        // 头像：取标题首字符
        val first = entry.title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        holder.binding.textAvatar.text = first
        when (entry.entryType) {
            EntryType.WORD -> {
                holder.binding.textAvatar.setBackgroundResource(R.drawable.bg_avatar_word)
                holder.binding.textAvatar.setTextColor(
                    ContextCompat.getColor(ctx, R.color.word_tag_fg)
                )
                holder.binding.textTypeTag.text = ctx.getString(R.string.filter_words)
                holder.binding.textTypeTag.setBackgroundResource(R.drawable.bg_tag_word)
                holder.binding.textTypeTag.setTextColor(
                    ContextCompat.getColor(ctx, R.color.word_tag_fg)
                )
            }
            EntryType.SENTENCE -> {
                holder.binding.textAvatar.setBackgroundResource(R.drawable.bg_avatar_sentence)
                holder.binding.textAvatar.setTextColor(
                    ContextCompat.getColor(ctx, R.color.sentence_tag_fg)
                )
                holder.binding.textTypeTag.text = ctx.getString(R.string.filter_sentences)
                holder.binding.textTypeTag.setBackgroundResource(R.drawable.bg_tag_sentence)
                holder.binding.textTypeTag.setTextColor(
                    ContextCompat.getColor(ctx, R.color.sentence_tag_fg)
                )
            }
        }

        holder.binding.textMastered.isVisible = entry.mastered

        // 时间徽标：今天显示时:分，昨天及以前显示日期
        val now = System.currentTimeMillis()
        holder.binding.textTime.text = when {
            now - entry.updatedAt < DAY_MS -> {
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                sdf.format(Date(entry.updatedAt))
            }
            else -> DAY_FORMAT.format(Date(entry.updatedAt))
        }

        holder.binding.root.setOnClickListener { onClick(entry) }
        holder.binding.btnSpeak.setOnClickListener { onSpeak(entry) }
    }
}
