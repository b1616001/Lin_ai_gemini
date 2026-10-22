package com.example.Utils

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.PhotoItem
import com.example.PhotoRepository
import java.io.File
import java.util.concurrent.Executor

/**
 * 相機控制、拍攝、儲存與照片管理工具類別 (CameraUtils)
 *
 * 主要功能：
 * 1. 【拍照與儲存】：建立照片檔案、配置 ImageCapture 輸出選項、非同步拍攝並儲存照片
 * 2. 【相片管理與刪除】：查詢所有相片清單、取得最新照片、刪除指定照片檔案
 * 3. 【相機生命週期與啟動】：綁定 ProcessCameraProvider 與 PreviewView，設定前後鏡頭與閃光燈
 * 4. 【鏡頭與閃光燈控制】：切換前後鏡頭 (Lens Facing)、切換閃光燈模式 (Auto / On / Off)
 * 5. 【手勢對焦與縮放】：處理點擊測光對焦 (Tap-to-focus) 與雙指縮放比例 (Zoom)
 *
 * @param context Android 上下文，用於相片目錄存取與主執行緒調度
 */
class CameraUtils(private val context: Context) {

    /**
     * 照片儲存庫實例，負責本地檔案目錄管理與檔案 CRUD 操作
     */
    val photoRepository: PhotoRepository = PhotoRepository(context)

    // =========================================================================
    // 1. 照片查詢、儲存與刪除功能
    // =========================================================================

    /**
     * 取得所有已儲存的照片清單（依拍攝時間由新至舊排序）
     *
     * @return 排序後的 [PhotoItem] 清單
     */
    fun getAllPhotos(): List<PhotoItem> {
        return photoRepository.getAllPhotos()
    }

    /**
     * 取得最新拍攝的第一張照片
     *
     * @return 最新的 [PhotoItem] 物件，若無相片則回傳 null
     */
    fun getLatestPhoto(): PhotoItem? {
        return photoRepository.getLatestPhoto()
    }

    /**
     * 取得目前儲存的照片總數量
     *
     * @return 照片數量
     */
    fun getPhotoCount(): Int {
        return getAllPhotos().size
    }

    /**
     * 建立一張新照片的本地檔案物件（包含年月日時分秒毫秒之唯一命名）
     *
     * @return 本地 [File] 物件
     */
    fun createNewPhotoFile(): File {
        return photoRepository.createNewPhotoFile()
    }

    /**
     * 刪除指定的照片項目
     *
     * @param photo 欲刪除的 [PhotoItem]
     * @return 刪除成功回傳 true，檔案不存在或失敗回傳 false
     */
    fun deletePhoto(photo: PhotoItem): Boolean {
        return photoRepository.deletePhoto(photo)
    }

    /**
     * 刪除指定的本地實體檔案
     *
     * @param file 欲刪除的 [File]
     * @return 刪除成功回傳 true，否則回傳 false
     */
    fun deletePhotoByFile(file: File): Boolean {
        return photoRepository.deletePhotoByFile(file)
    }

    // =========================================================================
    // 2. 拍照與儲存執行功能
    // =========================================================================

    /**
     * 執行拍照並將照片儲存至檔案
     *
     * @param imageCapture CameraX 的 [ImageCapture] 實例
     * @param executor 背景執行拍照與存檔任務的執行緒池
     * @param onImageSaved 拍照存檔成功回呼，帶入成功儲存的 [File] 物件
     * @param onError 拍照或存檔失敗回呼，帶入 [ImageCaptureException]
     * @return 正在儲存的目的地 [File] 物件
     */
    fun takePhoto(
        imageCapture: ImageCapture,
        executor: Executor,
        onImageSaved: (File) -> Unit,
        onError: (ImageCaptureException) -> Unit
    ): File {
        val photoFile = createNewPhotoFile()
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    onError(exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    onImageSaved(photoFile)
                }
            }
        )
        return photoFile
    }

    // =========================================================================
    // 3. 相機啟動與生命週期綁定
    // =========================================================================

    /**
     * 初始化並啟動相機預覽與拍照功能
     *
     * @param lifecycleOwner 生命週期擁有者（例如 Fragment 的 viewLifecycleOwner）
     * @param previewView 顯示預覽畫面的 [PreviewView]
     * @param lensFacing 鏡頭朝向（如 [CameraSelector.LENS_FACING_BACK] 或 [CameraSelector.LENS_FACING_FRONT]）
     * @param flashMode 閃光燈模式（如 [ImageCapture.FLASH_MODE_AUTO]、[ImageCapture.FLASH_MODE_ON]、[ImageCapture.FLASH_MODE_OFF]）
     * @param onCameraBound 相機綁定成功回呼，傳回 [Camera] 與新建立的 [ImageCapture]
     * @param onError 啟動或綁定過程發生例外時的回呼
     */
    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        lensFacing: Int,
        flashMode: Int,
        onCameraBound: (camera: Camera, imageCapture: ImageCapture) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                // 配置預覽 UseCase
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                // 配置拍照 UseCase（最小延遲模式 + 指定閃光燈模式）
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setFlashMode(flashMode)
                    .build()

                // 選擇前後鏡頭
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                cameraProvider.unbindAll()

                // 綁定生命週期
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )

                onCameraBound(camera, imageCapture)
            } catch (exc: Exception) {
                onError(exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // =========================================================================
    // 4. 鏡頭切換、閃光燈切換、縮放與對焦輔助功能
    // =========================================================================

    /**
     * 切換鏡頭方向（後置 <-> 前置）
     *
     * @param currentFacing 目前的鏡頭朝向
     * @return 切換後的新鏡頭朝向
     */
    fun toggleLensFacing(currentFacing: Int): Int {
        return if (currentFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
    }

    /**
     * 循環切換閃光燈模式：自動 (AUTO) -> 開啟 (ON) -> 關閉 (OFF) -> 自動 (AUTO)
     *
     * @param currentMode 目前閃光燈模式
     * @return 切換後的新閃光燈模式
     */
    fun getNextFlashMode(currentMode: Int): Int {
        return when (currentMode) {
            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
            else -> ImageCapture.FLASH_MODE_AUTO
        }
    }

    /**
     * 觸發點擊位置測光與對焦 (Tap-to-focus)
     *
     * @param cameraControl CameraX 相機控制器
     * @param point 觸控螢幕座標對應的 [MeteringPoint]
     */
    fun triggerFocusAndMetering(cameraControl: CameraControl?, point: MeteringPoint) {
        if (cameraControl == null) return
        val action = FocusMeteringAction.Builder(point).build()
        cameraControl.startFocusAndMetering(action)
    }

    /**
     * 雙指手勢縮放比例調整
     *
     * @param cameraControl CameraX 相機控制器
     * @param cameraInfo CameraX 相機資訊
     * @param scaleFactor 手勢縮放因子
     */
    fun applyZoom(cameraControl: CameraControl?, cameraInfo: CameraInfo?, scaleFactor: Float) {
        if (cameraControl == null || cameraInfo == null) return
        val currentZoomRatio = cameraInfo.zoomState.value?.zoomRatio ?: 1f
        cameraControl.setZoomRatio(currentZoomRatio * scaleFactor)
    }
}
