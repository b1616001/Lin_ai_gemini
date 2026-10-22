package com.example.Utils

import android.content.Context
import com.example.room.AppDatabase
import com.example.room.NoteEntity
import com.example.room.NoteRepository

/**
 * Room 資料庫業務輔助工具類別 (RoomUtils)
 *
 * 主要功能：
 * 1. 提供 [NoteRepository] 單例取得入口
 * 2. 記事分類標籤常數與色彩對應
 * 3. 預設範例資料批次初始化（方便展示與測試 CRUD 功能）
 * 4. 表單輸入防呆驗證
 */
object RoomUtils {

    /**
     * 支援的記事分類標籤清單
     */
    val CATEGORIES = listOf("一般", "工作", "生活", "重要")

    /**
     * 取得記事儲存庫單例物件
     */
    fun getNoteRepository(context: Context): NoteRepository {
        val database = AppDatabase.getDatabase(context)
        return NoteRepository(database.noteDao())
    }

    /**
     * 驗證使用者輸入的標題與內容是否合法
     */
    fun isInputValid(title: String, content: String): Boolean {
        return title.trim().isNotBlank() && content.trim().isNotBlank()
    }

    /**
     * 預先填入多筆實用範例記事至資料庫
     */
    suspend fun populateSampleData(repository: NoteRepository) {
        val samples = listOf(
            NoteEntity(
                title = "Android 專案功能模組化",
                content = "已將相機、GPS 定位、NFC 感應與 Room 資料庫各自拆分專屬 Utils 工具類別，提升程式碼內聚度與可維護性。",
                category = "工作",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 30
            ),
            NoteEntity(
                title = "週末登山踏青行程",
                content = "攜帶相機與手機，利用 GPS 工具即時紀錄步道海拔高度與移動軌跡，回程前往咖啡廳小憩。",
                category = "生活",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 120
            ),
            NoteEntity(
                title = "門禁 NFC 識別卡測試",
                content = "測試公司門禁卡 UID 讀取功能，確認 13.56 MHz RFID 標籤可正確解析十六進位與十進位卡號。",
                category = "重要",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 300
            ),
            NoteEntity(
                title = "每週讀書會備忘錄",
                content = "閱讀《Clean Architecture》章節，探討 Repository 模式如何隔離資料庫層與 Presentation 層。",
                category = "一般",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 600
            )
        )

        for (sample in samples) {
            repository.insert(sample)
        }
    }
}
