package org.english.book.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * SQLite 数据库备份 / 还原。
 *
 * 备份文件就是 Room 底层的 SQLite 数据库文件（ english_notebook.db ）
 * 原样拷贝，不做任何私有封装——用任何 SQLite 工具都能直接打开查看。
 *
 * 导出：先执行 WAL checkpoint（把 -wal 临时日志合并进主文件），再拷贝主 db 文件。
 * 还原：校验文件确为合法 SQLite 库且包含 entries 表，再关闭连接、替换文件、重启应用。
 */
object BackupManager {

    data class Result(val ok: Boolean, val count: Int = 0, val message: String? = null)

    /** 校验 uri 指向的文件是包含 entries 表的合法 SQLite 库，返回条目数 */
    fun inspect(context: Context, uri: Uri): Result {
        return try {
            val tmp = File.createTempFile("restore_check", ".db", context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tmp).use { output -> input.copyTo(output) }
            } ?: return Result(false, message = "无法读取所选文件")

            val db = SQLiteDatabase.openDatabase(
                tmp.absolutePath, null, SQLiteDatabase.OPEN_READONLY
            )
            val hasTable = try {
                db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='entries'", null
                ).use { it.count > 0 }
            } catch (e: Exception) {
                false
            }
            val count = if (hasTable) {
                db.rawQuery("SELECT COUNT(*) FROM entries", null).use {
                    it.moveToFirst(); it.getInt(0)
                }
            } else 0
            db.close()
            tmp.delete()
            if (hasTable) Result(true, count)
            else Result(false, message = "不是本应用的备份文件（缺少数据表）")
        } catch (e: Exception) {
            Result(false, message = "文件校验失败：${e.message}")
        }
    }

    /** 导出当前数据库到 uri，返回写入的条目数 */
    fun export(context: Context, uri: Uri): Result {
        val db = AppDatabase.get(context)
        return try {
            // WAL 模式下把 -wal / -shm 日志合并进主文件，保证导出的单文件是完整的
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
            val src = context.getDatabasePath(AppDatabase.DB_NAME)
            if (!src.exists()) return Result(false, message = "数据库文件不存在")

            val count = db.openHelper.writableDatabase
                .query("SELECT COUNT(*) FROM entries")
                .use { it.moveToFirst(); it.getInt(0) }
            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                FileInputStream(src).use { input -> input.copyTo(output) }
            } ?: return Result(false, message = "无法写入所选位置")

            Result(true, count)
        } catch (e: Exception) {
            Result(false, message = "导出失败：${e.message}")
        }
    }

    /**
     * 用 uri 的备份文件替换当前数据库。
     * 成功后必须 [RestoreNeedsRestart] 由调用方重启应用以重新加载数据库连接。
     */
    fun restore(context: Context, uri: Uri): Result {
        val check = inspect(context, uri)
        if (!check.ok) return check

        return try {
            AppDatabase.closeInstance()

            val dbPath = context.getDatabasePath(AppDatabase.DB_NAME)
            // 删除旧库及 WAL/SHM 附属文件，避免残留日志与替换后的主文件冲突
            listOf(dbPath, File(dbPath.path + "-wal"), File(dbPath.path + "-shm"))
                .forEach { it.delete() }

            dbPath.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dbPath).use { output -> input.copyTo(output) }
            } ?: return Result(false, message = "无法读取所选文件")

            Result(true, check.count)
        } catch (e: Exception) {
            Result(false, message = "还原失败：${e.message}")
        }
    }
}
