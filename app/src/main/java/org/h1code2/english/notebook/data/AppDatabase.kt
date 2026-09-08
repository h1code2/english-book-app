package org.h1code2.english.notebook.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [EntryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun entryDao(): EntryDao

    companion object {
        const val DB_NAME = "english_notebook.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .addCallback(SeedData.callback)
                    .build()
                    .also {
                        INSTANCE = it
                        SeedData.INSTANCE = it
                    }
            }

        /**
         * 关闭并清空单例。还原备份后必须调用：
         * 关闭旧连接、丢弃旧实例，下次 [get] 时基于替换后的文件重新打开。
         */
        fun closeInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
                SeedData.INSTANCE = null
            }
        }
    }
}
