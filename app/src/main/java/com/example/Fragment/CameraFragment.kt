package com.example.Fragment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.ImageLoaderUtils
import com.example.PhotoAdapter
import com.example.PhotoItem
import com.example.PhotoRepository
import com.example.R
import com.example.databinding.BottomSheetGalleryBinding
import com.example.databinding.DialogPhotoDetailBinding
import com.example.databinding.FragmentCameraBinding
import com.example.viewBinding.BaseBindingFragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 相機拍攝與相簿管理畫面
 *
 * 用途：
 * 1. 繼承 [BaseBindingFragment]，使用 CameraX 實現相機即時預覽與低延遲拍照
 * 2. 支援相機執行時期權限動態請求與權限被拒的替代 UI 引導
 * 3. 具備前後鏡頭切換 (Lens Facing) 與閃光燈模式切換 (Auto / On / Off)
 * 4. 支援雙指縮放 (Pinch-to-zoom) 與點擊畫面測光對焦 (Tap-to-focus) 動畫
 * 5. 拍照快門白光閃爍反饋動畫，並儲存高解析度照片
 * 6. 左下角即時更新最新相片縮圖、拍攝數量徽章，支援快速刪除
 * 7. 提供底部滑出式相簿 (BottomSheetDialog) 與全螢幕大圖檢視/刪除功能
 */
class CameraFragment : BaseBindingFragment<FragmentCameraBinding>() {

    /**
     * 照片儲存與檔案管理 Repository
     */
    private lateinit var photoRepository: PhotoRepository

    /**
     * CameraX 背景拍照任務執行緒池
     */
    private lateinit var cameraExecutor: ExecutorService

    /**
     * CameraX 拍照使用案例
     */
    private var imageCapture: ImageCapture? = null

    /**
     * CameraX 相機控制器，用於對焦與縮放控制
     */
    private var cameraControl: CameraControl? = null

    /**
     * CameraX 相機資訊，用於查詢縮放比例與狀態
     */
    private var cameraInfo: CameraInfo? = null

    /**
     * CameraX 相機實例
     */
    private var camera: Camera? = null

    /**
     * 當前鏡頭方向（預設後置鏡頭）
     */
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    /**
     * 當前閃光燈模式（預設自動閃光）
     */
    private var flashMode = ImageCapture.FLASH_MODE_AUTO

    /**
     * 當前開啟的底部相簿彈窗實例
     */
    private var activeGalleryDialog: BottomSheetDialog? = null

    /**
     * 執行時期權限請求回呼：處理相機權限授權結果
     */
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            setupCameraUI()
            startCamera()
        } else {
            showPermissionDeniedUI()
        }
    }

    /**
     * 實作基底類別方法：載入相機畫面的 ViewBinding
     */
    override fun onCreateViewBinding(layoutInflater: LayoutInflater): FragmentCameraBinding? {
        return FragmentCameraBinding.inflate(layoutInflater)
    }

    /**
     * 視圖初始化：建立 Repository、執行緒池、檢查相機權限與載入歷史相片縮圖
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        photoRepository = PhotoRepository(requireContext())
        cameraExecutor = Executors.newSingleThreadExecutor()

        checkCameraPermission()
        setupListeners()
        updateRecentThumbnail()
    }

    /**
     * 檢查是否已取得相機權限，若未授權則展示授權導引畫面
     */
    private fun checkCameraPermission() {
        val context = requireContext()
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            setupCameraUI()
            startCamera()
        } else {
            showPermissionDeniedUI()
        }
    }

    /**
     * 權限已授予時顯示相機預覽與控制面板
     */
    private fun setupCameraUI() {
        binding?.let { b ->
            b.layoutPermission.visibility = View.GONE
            b.topBar.visibility = View.VISIBLE
            b.bottomBar.visibility = View.VISIBLE
            b.viewFinder.visibility = View.VISIBLE
        }
    }

    /**
     * 權限被拒時顯示授權按鈕與說明提示
     */
    private fun showPermissionDeniedUI() {
        binding?.let { b ->
            b.layoutPermission.visibility = View.VISIBLE
            b.topBar.visibility = View.GONE
            b.bottomBar.visibility = View.GONE
        }
    }

    /**
     * 設置所有按鈕點擊監聽事件
     */
    private fun setupListeners() {
        val b = binding ?: return

        // 返回首頁按鈕
        b.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        // 授權相機權限按鈕
        b.btnGrantPermission.setOnClickListener {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        // 拍照快門按鈕
        b.btnCapture.setOnClickListener {
            takePhoto()
        }

        // 切換前後鏡頭按鈕
        b.btnSwitchCamera.setOnClickListener {
            toggleCameraFacing()
        }

        // 切換閃光燈模式按鈕
        b.btnFlash.setOnClickListener {
            toggleFlashMode()
        }

        // 點擊左下角縮圖開啟相簿彈窗
        b.layoutGallery.setOnClickListener {
            openGalleryBottomSheet()
        }

        // 快速刪除最新一張照片
        b.btnQuickDelete.setOnClickListener {
            val latest = photoRepository.getLatestPhoto()
            if (latest != null) {
                showDeleteConfirmDialog(latest)
            }
        }

        // 啟用手勢對焦與雙指縮放
        setupFocusAndZoom()
    }

    /**
     * 設置相機預覽畫面的觸控手勢（支援雙指縮放與單指點擊對焦）
     */
    private fun setupFocusAndZoom() {
        val b = binding ?: return
        val scaleGestureDetector = ScaleGestureDetector(
            requireContext(),
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val currentZoomRatio = cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
                    val delta = detector.scaleFactor
                    cameraControl?.setZoomRatio(currentZoomRatio * delta)
                    return true
                }
            }
        )

        b.viewFinder.setOnTouchListener { view, event ->
            scaleGestureDetector.onTouchEvent(event)

            if (event.action == MotionEvent.ACTION_UP && !scaleGestureDetector.isInProgress) {
                val factory = b.viewFinder.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)
                val action = FocusMeteringAction.Builder(point).build()

                cameraControl?.startFocusAndMetering(action)

                // 於點擊座標展示對焦圓環動畫
                showFocusRing(event.x, event.y)
                view.performClick()
            }
            true
        }
    }

    /**
     * 顯示對焦圓環動畫
     *
     * @param x 點擊 X 座標
     * @param y 點擊 Y 座標
     */
    private fun showFocusRing(x: Float, y: Float) {
        binding?.focusRing?.apply {
            this.x = x - (width / 2f)
            this.y = y - (height / 2f)
            visibility = View.VISIBLE
            alpha = 1f
            scaleX = 1.3f
            scaleY = 1.3f
            animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(0f)
                .setDuration(600)
                .withEndAction { visibility = View.GONE }
                .start()
        }
    }

    /**
     * 綁定 CameraX 生命週期並啟動相機預覽與拍照實例
     */
    private fun startCamera() {
        val context = context ?: return
        val b = binding ?: return
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            if (!isAdded || binding == null) return@addListener

            try {
                val cameraProvider = cameraProviderFuture.get()

                // 配置相機預覽 UseCase
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = b.viewFinder.surfaceProvider
                }

                // 配置拍照 UseCase（最小延遲模式 + 當前閃光燈模式）
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setFlashMode(flashMode)
                    .build()

                // 選擇前後鏡頭
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                cameraProvider.unbindAll()

                // 綁定生命週期
                camera = cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )

                cameraControl = camera?.cameraControl
                cameraInfo = camera?.cameraInfo

            } catch (exc: Exception) {
                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.photo_save_failed, exc.localizedMessage ?: "啟動相機失敗"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * 執行拍照動作：觸發快門白色遮罩動畫，儲存相片至本機目錄
     */
    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val b = binding ?: return

        // 快門白光反饋動畫
        b.captureFlashOverlay.apply {
            visibility = View.VISIBLE
            alpha = 0.85f
            animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction { visibility = View.GONE }
                .start()
        }

        // 產生新照片檔案路徑
        val photoFile = photoRepository.createNewPhotoFile()
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // 執行背景拍照與存檔
        imageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    activity?.runOnUiThread {
                        if (isAdded) {
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.photo_save_failed, exc.message),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    activity?.runOnUiThread {
                        if (isAdded) {
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.photo_saved_success),
                                Toast.LENGTH_SHORT
                            ).show()
                            // 更新左下角最新縮圖與張數
                            updateRecentThumbnail()
                        }
                    }
                }
            }
        )
    }

    /**
     * 切換前置與後置鏡頭，並帶有 180 度圖示旋轉轉場動畫
     */
    private fun toggleCameraFacing() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        binding?.btnSwitchCamera?.animate()
            ?.rotationBy(180f)
            ?.setDuration(300)
            ?.start()
        startCamera()
    }

    /**
     * 切換閃光燈模式：自動 (Auto) -> 開啟 (On) -> 關閉 (Off) 循環切換
     */
    private fun toggleFlashMode() {
        val b = binding ?: return
        when (flashMode) {
            ImageCapture.FLASH_MODE_AUTO -> {
                flashMode = ImageCapture.FLASH_MODE_ON
                b.btnFlash.setImageResource(R.drawable.ic_flash_on)
                b.btnFlash.contentDescription = getString(R.string.flash_on)
                Toast.makeText(requireContext(), getString(R.string.flash_on), Toast.LENGTH_SHORT).show()
            }
            ImageCapture.FLASH_MODE_ON -> {
                flashMode = ImageCapture.FLASH_MODE_OFF
                b.btnFlash.setImageResource(R.drawable.ic_flash_off)
                b.btnFlash.contentDescription = getString(R.string.flash_off)
                Toast.makeText(requireContext(), getString(R.string.flash_off), Toast.LENGTH_SHORT).show()
            }
            else -> {
                flashMode = ImageCapture.FLASH_MODE_AUTO
                b.btnFlash.setImageResource(R.drawable.ic_flash_auto)
                b.btnFlash.contentDescription = getString(R.string.flash_auto)
                Toast.makeText(requireContext(), getString(R.string.flash_auto), Toast.LENGTH_SHORT).show()
            }
        }
        imageCapture?.flashMode = flashMode
    }

    /**
     * 讀取儲存庫最新拍攝照片，非同步取樣壓縮解碼並更新左下角縮圖與照片數量徽章
     */
    private fun updateRecentThumbnail() {
        val b = binding ?: return
        if (!isAdded) return

        val photos = photoRepository.getAllPhotos()
        val latest = photos.firstOrNull()

        if (latest != null && latest.file.exists()) {
            b.tvPhotoCountBadge.text = photos.size.toString()
            b.tvPhotoCountBadge.visibility = View.VISIBLE
            b.btnQuickDelete.visibility = View.VISIBLE

            viewLifecycleOwner.lifecycleScope.launch {
                val bitmap = ImageLoaderUtils.loadBitmapSampled(latest.file, 120, 120)
                if (bitmap != null && binding != null) {
                    binding?.ivRecentThumbnail?.setImageBitmap(bitmap)
                    binding?.ivRecentThumbnail?.clearColorFilter()
                }
            }
        } else {
            b.tvPhotoCountBadge.visibility = View.GONE
            b.btnQuickDelete.visibility = View.GONE
            b.ivRecentThumbnail.setImageResource(R.drawable.ic_photo_library)
            b.ivRecentThumbnail.setColorFilter(
                ContextCompat.getColor(requireContext(), R.color.icon_tint)
            )
        }
    }

    /**
     * 開啟底部彈出式相簿 (BottomSheetDialog)
     * 使用 3 欄式網格呈現所有歷史拍攝相片，支援點擊檢視與個別刪除
     */
    private fun openGalleryBottomSheet() {
        val context = requireContext()
        val dialog = BottomSheetDialog(context, R.style.Theme_MyApplication)
        activeGalleryDialog = dialog
        val sheetBinding = BottomSheetGalleryBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        lateinit var adapter: PhotoAdapter
        adapter = PhotoAdapter(
            onPhotoClick = { photo ->
                showPhotoDetailDialog(photo)
            },
            onDeleteClick = { photo ->
                showDeleteConfirmDialog(photo) {
                    refreshGalleryList(sheetBinding, adapter)
                }
            }
        )

        sheetBinding.rvPhotos.layoutManager = GridLayoutManager(context, 3)
        sheetBinding.rvPhotos.adapter = adapter

        val photos = photoRepository.getAllPhotos()
        updateGalleryUI(sheetBinding, photos, adapter)

        sheetBinding.btnCloseGallery.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * 更新相簿彈窗內的視圖元件（處理空清單狀態與照片筆數標題）
     */
    private fun updateGalleryUI(
        sheetBinding: BottomSheetGalleryBinding,
        photos: List<PhotoItem>,
        adapter: PhotoAdapter
    ) {
        sheetBinding.tvGalleryTitle.text = "${getString(R.string.gallery_title)} (${photos.size})"
        if (photos.isEmpty()) {
            sheetBinding.layoutEmptyState.visibility = View.VISIBLE
            sheetBinding.rvPhotos.visibility = View.GONE
        } else {
            sheetBinding.layoutEmptyState.visibility = View.GONE
            sheetBinding.rvPhotos.visibility = View.VISIBLE
            adapter.submitList(photos)
        }
    }

    /**
     * 刪除相片後刷新相簿列表與外層縮圖
     */
    private fun refreshGalleryList(
        sheetBinding: BottomSheetGalleryBinding,
        adapter: PhotoAdapter
    ) {
        val updatedPhotos = photoRepository.getAllPhotos()
        updateGalleryUI(sheetBinding, updatedPhotos, adapter)
        updateRecentThumbnail()
    }

    /**
     * 顯示全螢幕相片詳情彈窗
     * 包含檔名、拍攝時間、檔案大小與大圖檢視，並提供刪除選項
     *
     * @param photo 點選的照片物件
     */
    private fun showPhotoDetailDialog(photo: PhotoItem) {
        val context = requireContext()
        val dialog = AlertDialog.Builder(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            .create()

        val detailBinding = DialogPhotoDetailBinding.inflate(layoutInflater)
        dialog.setView(detailBinding.root)

        detailBinding.tvDetailFileName.text = photo.name
        detailBinding.tvDetailMeta.text = "${photo.formattedDateTime()} • ${photo.formattedSize()}"

        detailBinding.btnDetailClose.setOnClickListener {
            dialog.dismiss()
        }

        detailBinding.btnDetailDelete.setOnClickListener {
            showDeleteConfirmDialog(photo) {
                dialog.dismiss()
                activeGalleryDialog?.dismiss()
                updateRecentThumbnail()
            }
        }

        // 非同步讀取並優化載入高解析度預覽
        viewLifecycleOwner.lifecycleScope.launch {
            val bitmap = ImageLoaderUtils.loadBitmapSampled(photo.file, 1080, 1920)
            if (bitmap != null) {
                detailBinding.ivFullPhoto.setImageBitmap(bitmap)
            }
        }

        dialog.show()
    }

    /**
     * 彈出刪除相片的二次確認對話框
     *
     * @param photo 欲刪除的照片物件
     * @param onDeleted 刪除成功後的回呼函式
     */
    private fun showDeleteConfirmDialog(photo: PhotoItem, onDeleted: (() -> Unit)? = null) {
        val context = context ?: return
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.delete_photo_confirm_title)
            .setMessage(R.string.delete_photo_confirm_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                val success = photoRepository.deletePhoto(photo)
                if (success) {
                    if (isAdded) {
                        Toast.makeText(requireContext(), getString(R.string.photo_deleted_success), Toast.LENGTH_SHORT).show()
                        updateRecentThumbnail()
                        onDeleted?.invoke()
                    }
                } else {
                    if (isAdded) {
                        Toast.makeText(requireContext(), getString(R.string.photo_delete_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    /**
     * 視圖銷毀時清理相機執行緒與彈窗實例
     */
    override fun onDestroyView() {
        super.onDestroyView()
        activeGalleryDialog?.dismiss()
        activeGalleryDialog = null
        cameraExecutor.shutdown()
    }
}
