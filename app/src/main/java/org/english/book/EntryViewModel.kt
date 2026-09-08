package org.english.book

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import org.english.book.data.AppDatabase
import org.english.book.data.EntryDao
import org.english.book.data.EntryEntity
import org.english.book.data.ListFilter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class EntryViewModel(app: Application) : AndroidViewModel(app) {

    private val dao: EntryDao = AppDatabase.get(app).entryDao()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val params = MutableLiveData<ListFilter>().apply { value = ListFilter() }

    val entries: LiveData<List<EntryEntity>> = params.switchMap { f ->
        dao.observeAll(f.type, f.query, f.onlyLearning, f.sort.key)
    }

    private val counts = MutableLiveData<Stats>().apply { value = Stats() }
    val stats: LiveData<Stats> = counts

    data class Stats(val total: Int = 0, val mastered: Int = 0)

    init {
        refreshStats()
    }

    fun search(filter: ListFilter) {
        params.value = filter
    }

    fun entry(id: Long): LiveData<EntryEntity?> = dao.observeById(id)

    fun save(entry: EntryEntity, isNew: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val toSave = if (isNew) entry.copy(createdAt = now, updatedAt = now)
            else entry.copy(updatedAt = now)
            withContext(Dispatchers.IO) { if (isNew) dao.insert(toSave) else dao.update(toSave) }
            refreshStats()
            mainHandler.post { onDone() }
        }
    }

    fun delete(entry: EntryEntity, onDone: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { dao.delete(entry) }
            refreshStats()
            mainHandler.post { onDone() }
        }
    }

    fun setMastered(entry: EntryEntity, mastered: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.update(entry.copy(mastered = mastered, updatedAt = System.currentTimeMillis()))
            }
            refreshStats()
        }
    }

    /** mode: "all" 全部 / "learning" 只复习未掌握 */
    fun cardsForReview(mode: String, onResult: (List<EntryEntity>) -> Unit) {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                if (mode == "learning") dao.getLearningForReview() else dao.getAllForReview()
            }
            mainHandler.post { onResult(list) }
        }
    }

    fun refreshStats() {
        viewModelScope.launch {
            val total = withContext(Dispatchers.IO) { dao.count() }
            val mastered = withContext(Dispatchers.IO) { dao.masteredCount() }
            mainHandler.post { counts.value = Stats(total, mastered) }
        }
    }
}
