package com.example

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 圖片處理與高效率載入工具類別
 *
 * 用途：
 * 1. 在 [Dispatchers.IO] 協程環境中執行非同步圖檔解碼，防止主執行緒 (Main Thread) 阻塞卡頓
 * 2. 透過 [calculateInSampleSize] 先僅解碼邊界資訊 (inJustDecodeBounds)，動態計算取樣倍率以預防 OOM 記憶體溢出
 * 3. 讀取並解析 EXIF 旋轉方向標籤 (ExifInterface.TAG_ORIENTATION)，自動矯正相機感應器旋轉角度（90度、180度、270度）
 */
object ImageLoaderUtils {

    /**
     * 取樣壓縮並讀取本機圖檔為 [Bitmap]
     *
     * 流程：
     * 1. 使用 `inJustDecodeBounds = true` 取得原圖長寬而不載入像素
     * 2. 計算適合的 `inSampleSize` 縮減記憶體佔用
     * 3. 解碼圖檔為點陣圖 (Bitmap)
     * 4. 讀取 EXIF 資訊，如有旋轉偏差則透過 [Matrix.postRotate] 自動轉正
     *
     * @param file 圖檔實體物件
     * @param targetWidth 期望的目標寬度（像素）
     * @param targetHeight 期望的目標高度（像素）
     * @return 處理完畢的 [Bitmap]，若檔案不存在或解碼失敗則回傳 null
     */
    suspend fun loadBitmapSampled(file: File, targetWidth: Int, targetHeight: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                if (!file.exists()) return@withContext null

                // 步驟 1：僅讀取圖檔邊界長寬資訊
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(file.absolutePath, options)

                // 步驟 2：計算適合的二次冪取樣倍率 (inSampleSize)
                options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight)
                options.inJustDecodeBounds = false

                // 步驟 3：實際解碼載入像素至記憶體
                val decodedBitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return@withContext null

                // 步驟 4：檢查 EXIF 旋轉標記並校正角度
                val exif = ExifInterface(file.absolutePath)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )

                val rotationDegrees = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }

                if (rotationDegrees != 0f) {
                    val matrix = Matrix().apply { postRotate(rotationDegrees) }
                    Bitmap.createBitmap(
                        decodedBitmap,
                        0,
                        0,
                        decodedBitmap.width,
                        decodedBitmap.height,
                        matrix,
                        true
                    )
                } else {
                    decodedBitmap
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    /**
     * 計算 BitmapFactory 取樣倍率 (inSampleSize)
     * 依據原圖尺寸與期望的長寬，以 2 的次方遞增計算最適倍率
     *
     * @param options 包含原圖 outWidth 與 outHeight 的 BitmapFactory.Options
     * @param reqWidth 請求的目標寬度
     * @param reqHeight 請求的目標高度
     * @return 縮放取樣係數（值為 1, 2, 4, 8...）
     */
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
