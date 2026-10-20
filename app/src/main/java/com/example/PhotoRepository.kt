package com.example

import android.content.Context
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 相片本機儲存與檔案管理儲存庫 (Repository)
 *
 * 用途：
 * 1. 管理應用程式的相片儲存目錄（優先使用 App-specific 外部儲存目錄，無需額外儲存權限）
 * 2. 提供新照片檔案名稱建立（包含時間戳記）
 * 3. 讀取所有已拍攝相片，並依拍攝時間由新至舊排序
 * 4. 取得最新單張相片
 * 5. 執行檔案刪除操作
 *
 * @param context Android 上下文，用於取得目錄路徑
 */
class PhotoRepository(private val context: Context) {

    /**
     * 相片儲存目錄
     * 優先取得 context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)，若無則降級為 filesDir/photos
     */
    private val photosDir: File
        get() {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                ?: File(context.filesDir, "photos")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    /**
     * 查詢所有已儲存的照片清單
     * 過濾副檔名為 jpg, jpeg, png 之圖檔，並依最後修改時間由新到舊排序
     *
     * @return 排序後的 [PhotoItem] 清單
     */
    fun getAllPhotos(): List<PhotoItem> {
        val files = photosDir.listFiles { file ->
            file.isFile && (file.extension.equals("jpg", ignoreCase = true) ||
                    file.extension.equals("jpeg", ignoreCase = true) ||
                    file.extension.equals("png", ignoreCase = true))
        } ?: emptyArray()

        return files.map { PhotoItem(it) }
            .sortedByDescending { it.timestamp }
    }

    /**
     * 取得最新拍攝的第一張照片
     *
     * @return 最新的 [PhotoItem] 物件，若目錄無相片則回傳 null
     */
    fun getLatestPhoto(): PhotoItem? {
        return getAllPhotos().firstOrNull()
    }

    /**
     * 產生一張新照片的本地檔案路徑（以年月日時分秒毫秒命名）
     * 範例：IMG_20261020_143025_123.jpg
     *
     * @return 準備寫入的照片 [File] 物件
     */
    fun createNewPhotoFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
        return File(photosDir, "IMG_$timeStamp.jpg")
    }

    /**
     * 刪除指定的相片物件
     *
     * @param photo 欲刪除的 [PhotoItem]
     * @return 刪除成功回傳 true，檔案不存在或失敗回傳 false
     */
    fun deletePhoto(photo: PhotoItem): Boolean {
        return if (photo.file.exists()) {
            photo.file.delete()
        } else {
            false
        }
    }

    /**
     * 刪除指定的實體檔案
     *
     * @param file 欲刪除的 [File]
     * @return 刪除成功回傳 true，檔案不存在或失敗回傳 false
     */
    fun deletePhotoByFile(file: File): Boolean {
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }
}
