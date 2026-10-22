package com.example.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Room 資料庫記事實體 (Entity)
 *
 * @param id 主鍵 ID (由 Room 自動遞增生成)
 * @param title 記事標題
 * @param content 記事內容
 * @param category 分類標籤 (一般、工作、生活、重要)
 * @param timestamp 建立/修改時間戳記
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val content: String,
    val category: String = "一般",
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * 格式化為年月日與時間字串 (如 2026/10/21 17:30)
     */
    fun formattedDateTime(): String {
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
