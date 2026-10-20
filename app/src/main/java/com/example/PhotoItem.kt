package com.example

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 拍攝照片的資料模型
 *
 * 用途：
 * 封裝照片檔案實體 [File] 以及對應的檔案名稱、時間戳記與檔案大小，
 * 並提供時間格式化與檔案大小格式化之便利函式。
 *
 * @param file 相片的實際本地檔案物件
 * @param name 檔案名稱（預設為 file.name）
 * @param timestamp 檔案最後修改時間（毫秒）
 * @param sizeBytes 檔案大小（位元組 bytes）
 */
data class PhotoItem(
    val file: File,
    val name: String = file.name,
    val timestamp: Long = file.lastModified(),
    val sizeBytes: Long = file.length()
) {
    /**
     * 格式化為簡短時間字串
     *
     * @return 格式化字串，例如 "14:30:25"
     */
    fun formattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /**
     * 格式化為完整日期與時間字串
     *
     * @return 格式化字串，例如 "2026/10/20 14:30"
     */
    fun formattedDateTime(): String {
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /**
     * 格式化檔案大小為人類易讀的單位（KB 或 MB）
     *
     * @return 格式化字串，例如 "850.5 KB" 或 "3.2 MB"
     */
    fun formattedSize(): String {
        val kb = sizeBytes / 1024.0
        return if (kb < 1024) {
            String.format(Locale.getDefault(), "%.1f KB", kb)
        } else {
            val mb = kb / 1024.0
            String.format(Locale.getDefault(), "%.1f MB", mb)
        }
    }
}
