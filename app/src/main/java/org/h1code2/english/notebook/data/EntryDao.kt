package org.h1code2.english.notebook.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/** 列表排序方式 */
enum class SortMode(val key: String) {
    RECENT("UPDATED_DESC"),
    NEWEST("CREATED_DESC"),
    TITLE_ASC("TITLE_ASC"),
    TITLE_DESC("TITLE_DESC");

    companion object {
        fun from(key: String?): SortMode = entries.firstOrNull { it.key == key } ?: RECENT
    }
}

/** 列表筛选状态 */
data class ListFilter(
    var type: String? = null,
    var query: String = "",
    var onlyLearning: Boolean = false,
    var sort: SortMode = SortMode.RECENT,
)

@Dao
interface EntryDao {

    @Query(
        """
        SELECT * FROM entries
        WHERE (:type IS NULL OR type = :type)
          AND (:onlyLearning = 0 OR mastered = 0)
          AND (:query = '' OR title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%')
        ORDER BY
            CASE WHEN :sort = 'TITLE_ASC' THEN title END ASC,
            CASE WHEN :sort = 'TITLE_DESC' THEN title END DESC,
            CASE WHEN :sort = 'CREATED_DESC' THEN created_at END DESC,
            CASE WHEN :sort = 'UPDATED_DESC' THEN updated_at END DESC
        """
    )
    fun observeAll(
        type: String?,
        query: String,
        onlyLearning: Boolean,
        sort: String,
    ): LiveData<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE id = :id LIMIT 1")
    fun observeById(id: Long): LiveData<EntryEntity?>

    @Query("SELECT * FROM entries ORDER BY created_at ASC")
    suspend fun getAllForReview(): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE mastered = 0 ORDER BY created_at ASC")
    suspend fun getLearningForReview(): List<EntryEntity>

    @Query("SELECT COUNT(*) FROM entries")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM entries WHERE mastered = 1")
    suspend fun masteredCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: EntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<EntryEntity>)

    @Update
    suspend fun update(entry: EntryEntity)

    @Delete
    suspend fun delete(entry: EntryEntity)
}
