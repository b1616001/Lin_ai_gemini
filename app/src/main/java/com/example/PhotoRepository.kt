package com.example

import android.content.Context
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhotoRepository(private val context: Context) {

    private val photosDir: File
        get() {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                ?: File(context.filesDir, "photos")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    fun getAllPhotos(): List<PhotoItem> {
        val files = photosDir.listFiles { file ->
            file.isFile && (file.extension.equals("jpg", ignoreCase = true) ||
                    file.extension.equals("jpeg", ignoreCase = true) ||
                    file.extension.equals("png", ignoreCase = true))
        } ?: emptyArray()

        return files.map { PhotoItem(it) }
            .sortedByDescending { it.timestamp }
    }

    fun getLatestPhoto(): PhotoItem? {
        return getAllPhotos().firstOrNull()
    }

    fun createNewPhotoFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
        return File(photosDir, "IMG_$timeStamp.jpg")
    }

    fun deletePhoto(photo: PhotoItem): Boolean {
        return if (photo.file.exists()) {
            photo.file.delete()
        } else {
            false
        }
    }

    fun deletePhotoByFile(file: File): Boolean {
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }
}
