package com.example

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PhotoItem(
    val file: File,
    val name: String = file.name,
    val timestamp: Long = file.lastModified(),
    val sizeBytes: Long = file.length()
) {
    fun formattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formattedDateTime(): String {
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

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
