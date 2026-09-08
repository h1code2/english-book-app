package org.h1code2.english.notebook.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class EntryType {
    WORD, SENTENCE;

    companion object {
        fun from(name: String?): EntryType =
            entries.firstOrNull { it.name == name } ?: WORD
    }
}

@Entity(tableName = "entries")
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** 单词或句子正文，用于列表展示、搜索、TTS 朗读、闪卡正面 */
    @ColumnInfo(name = "title") val title: String,
    /** Markdown 自由文本正文（音标、近似读音、意思、例句、注意等） */
    @ColumnInfo(name = "content") val content: String,
    /** WORD / SENTENCE */
    @ColumnInfo(name = "type") val type: String = EntryType.WORD.name,
    /** 闪卡复习：是否已掌握 */
    @ColumnInfo(name = "mastered") val mastered: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
) {
    val entryType: EntryType
        get() = EntryType.from(type)
}
