package com.example

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ImageLoaderUtils {

    suspend fun loadBitmapSampled(file: File, targetWidth: Int, targetHeight: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                if (!file.exists()) return@withContext null

                // Decode bounds first
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(file.absolutePath, options)

                // Calculate inSampleSize
                options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight)
                options.inJustDecodeBounds = false

                val decodedBitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return@withContext null

                // Handle EXIF orientation rotation
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
