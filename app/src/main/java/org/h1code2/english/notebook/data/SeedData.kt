package org.h1code2.english.notebook.data

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 首次创建数据库时预置示例内容。
 */
object SeedData {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val callback = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            scope.launch {
                val database = INSTANCE ?: return@launch
                database.entryDao().insertAll(samples())
            }
        }
    }

    /** 供回调与测试共用；INSTANCE 由 AppDatabase.get 设置 */
    internal var INSTANCE: AppDatabase? = null

    fun samples(): List<EntryEntity> {
        val now = System.currentTimeMillis()
        return listOf(
            EntryEntity(
                title = "wallet",
                content = """
                    **wallet** 的音标是：**/ˈwɑːlɪt/**

                    读音近似：**“沃利特”**
                    意思是：**钱包**。

                    拼读：

                    > **w-a-l-l-e-t = wallet**

                    例句：

                    > **This is my wallet.**
                    > 这是我的钱包。

                    注意：**wallet** 中的 **l** 要发音，但 **a** 发短音，读作 **/ˈwɑːlɪt/**，不要读成 “wa-let”。
                """.trimIndent(),
                type = EntryType.WORD.name,
                createdAt = now,
                updatedAt = now
            ),
            EntryEntity(
                title = "summer",
                content = """
                    **summer** 的音标是：**/ˈsʌmər/**

                    读音近似：**“萨默”**
                    意思是：**夏天**。

                    拼写：

                    > **s-u-m-m-e-r**

                    例句：

                    > **I like summer.**
                    > 我喜欢夏天。

                    注意：**summer** 中间有两个 **m**。
                """.trimIndent(),
                type = EntryType.WORD.name,
                createdAt = now + 1,
                updatedAt = now + 1
            ),
            EntryEntity(
                title = "The watch is expensive.",
                content = """
                    意思是：**这块手表很贵。**

                    读音：**/ðə wɑːtʃ ɪz ɪkˈspensɪv/**
                    近似读法：**“泽 沃奇 依兹 伊克斯彭西夫”**

                    - **watch**：手表
                    - **expensive**：昂贵的、贵的
                    - **The watch**：这块手表 / 这只手表
                """.trimIndent(),
                type = EntryType.SENTENCE.name,
                createdAt = now + 2,
                updatedAt = now + 2
            ),
            EntryEntity(
                title = "The road is black and long.",
                content = """
                    意思是：**这条路又黑又长。**

                    读音：**/ðə roʊd ɪz blæk ænd lɔːŋ/**
                    近似读法：**“泽 柔德 依兹 布莱克 安德 隆”**

                    - **road**：道路
                    - **black**：黑色的
                    - **long**：长的
                    - **and**：和、并且
                """.trimIndent(),
                type = EntryType.SENTENCE.name,
                createdAt = now + 3,
                updatedAt = now + 3
            )
        )
    }
}
