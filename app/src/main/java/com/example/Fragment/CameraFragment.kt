package com.example.Fragment

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.CameraUtils
import com.example.ImageLoaderUtils
import com.example.PhotoAdapter
import com.example.PhotoItem
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
 * 相機拍攝、即時照片清單與全螢幕大圖展示畫面
 *
 * 用途：
 * 1. 繼承 [BaseBindingFragment]，使用 ViewBinding 自動管理視圖
 * 2. 【縮圖點擊放出整張圖片】：點擊任意縮圖（相機主畫面縮圖、水平相片條或相簿網格）皆可立即放大展示全螢幕大圖
 * 3. 大圖檢視支援點擊隱藏/顯示操作列、雙指縮放檢視細節、刪除照片以及快速前往相簿
 * 4. 透過 [CameraUtils] 管理 CameraX 生命週期、即時預覽、拍照存檔、安全刪除與手勢對焦/縮放
 * 5. 透過 [PhotoAdapter] 在相機畫面上即時以橫向捲動列表展示所有已拍攝相片
 */
class CameraFragment : BaseBindingFragment<FragmentCameraBinding>() {

    /**
     * 相機與相片工具類別，封裝相機拍照、檔案儲存、刪除與查詢功能
     */
    private lateinit var cameraUtils: CameraUtils

    /**
     * 相機畫面上橫向捲動相片列表配接器 (PhotoAdapter)
     */
    private lateinit var cameraPhotoAdapter: PhotoAdapter

    /**
     * CameraX 背景拍照任務執行緒池
     */
    private lateinit var cameraExecutor: ExecutorService

    /**
     * CameraX 拍照 UseCase
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
     * 視圖初始化：建立 CameraUtils、初始化 PhotoAdapter 圖片列表、檢查權限並載入已拍攝照片
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初始化 CameraUtils
        cameraUtils = CameraUtils(requireContext())
        cameraExecutor = Executors.newSingleThreadExecutor()

        setupPhotoListAdapter()
        checkCameraPermission()
        setupListeners()
        refreshPhotoLists()
    }

    /**
     * 初始化相機主畫面上的 PhotoAdapter 水平圖片列表
     */
    private fun setupPhotoListAdapter() {
        val b = binding ?: return

        cameraPhotoAdapter = PhotoAdapter(
            coroutineScope = viewLifecycleOwner.lifecycleScope,
            isHorizontal = true,
            onPhotoClick = { photo ->
                // 點擊縮圖：立即放大全螢幕檢視整張圖片
                showPhotoDetailDialog(photo)
            },
            onDeleteClick = { photo ->
                showDeleteConfirmDialog(photo) {
                    refreshPhotoLists()
                }
            }
        )

        b.rvCameraPhotoList.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = cameraPhotoAdapter
        }
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
            b.rvCameraPhotoList.visibility = View.GONE
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

        // 點擊左下角縮圖：若有相片則立即放大展示整張大圖，若無相片則開啟相簿
        b.layoutGallery.setOnClickListener {
            val latest = cameraUtils.getLatestPhoto()
            if (latest != null) {
                showPhotoDetailDialog(latest)
            } else {
                openGalleryBottomSheet()
            }
        }

        // 長按左下角縮圖開啟完整相簿底部彈窗
        b.layoutGallery.setOnLongClickListener {
            openGalleryBottomSheet()
            true
        }

        // 快速刪除最新一張照片
        b.btnQuickDelete.setOnClickListener {
            val latest = cameraUtils.getLatestPhoto()
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
                    cameraUtils.applyZoom(cameraControl, cameraInfo, detector.scaleFactor)
                    return true
                }
            }
        )

        b.viewFinder.setOnTouchListener { view, event ->
            scaleGestureDetector.onTouchEvent(event)

            if (event.action == MotionEvent.ACTION_UP && !scaleGestureDetector.isInProgress) {
                val factory = b.viewFinder.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)

                cameraUtils.triggerFocusAndMetering(cameraControl, point)
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
     * 透過 CameraUtils 啟動相機並綁定生命週期
     */
    private fun startCamera() {
        val b = binding ?: return

        cameraUtils.startCamera(
            lifecycleOwner = viewLifecycleOwner,
            previewView = b.viewFinder,
            lensFacing = lensFacing,
            flashMode = flashMode,
            onCameraBound = { boundCamera, boundCapture ->
                if (!isAdded || binding == null) return@startCamera

                camera = boundCamera
                imageCapture = boundCapture
                cameraControl = boundCamera.cameraControl
                cameraInfo = boundCamera.cameraInfo
            },
            onError = { exc ->
                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.photo_save_failed, exc.localizedMessage ?: "啟動相機失敗"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    /**
     * 透過 CameraUtils 執行拍照並儲存照片
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

        // 呼叫 CameraUtils 執行拍照存檔
        cameraUtils.takePhoto(
            imageCapture = imageCapture,
            executor = cameraExecutor,
            onImageSaved = {
                activity?.runOnUiThread {
                    if (isAdded) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.photo_saved_success),
                            Toast.LENGTH_SHORT
                        ).show()
                        // 拍照儲存完成後刷新相片列表並自動滾動到最前頭
                        refreshPhotoLists()
                        binding?.rvCameraPhotoList?.smoothScrollToPosition(0)
                    }
                }
            },
            onError = { exc ->
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
        )
    }

    /**
     * 切換鏡頭方向（前置/後置），並帶有 180 度圖示旋轉轉場動畫
     */
    private fun toggleCameraFacing() {
        lensFacing = cameraUtils.toggleLensFacing(lensFacing)
        binding?.btnSwitchCamera?.animate()
            ?.rotationBy(180f)
            ?.setDuration(300)
            ?.start()
        startCamera()
    }

    /**
     * 循環切換閃光燈模式：自動 (Auto) -> 開啟 (On) -> 關閉 (Off)
     */
    private fun toggleFlashMode() {
        val b = binding ?: return
        flashMode = cameraUtils.getNextFlashMode(flashMode)

        when (flashMode) {
            ImageCapture.FLASH_MODE_AUTO -> {
                b.btnFlash.setImageResource(R.drawable.ic_flash_auto)
                b.btnFlash.contentDescription = getString(R.string.flash_auto)
                Toast.makeText(requireContext(), getString(R.string.flash_auto), Toast.LENGTH_SHORT).show()
            }
            ImageCapture.FLASH_MODE_ON -> {
                b.btnFlash.setImageResource(R.drawable.ic_flash_on)
                b.btnFlash.contentDescription = getString(R.string.flash_on)
                Toast.makeText(requireContext(), getString(R.string.flash_on), Toast.LENGTH_SHORT).show()
            }
            else -> {
                b.btnFlash.setImageResource(R.drawable.ic_flash_off)
                b.btnFlash.contentDescription = getString(R.string.flash_off)
                Toast.makeText(requireContext(), getString(R.string.flash_off), Toast.LENGTH_SHORT).show()
            }
        }
        imageCapture?.flashMode = flashMode
    }

    /**
     * 刷新相片列表顯示：
     * 1. 更新相機畫面上的水平 PhotoAdapter 圖片列表
     * 2. 更新左下角最新縮圖與張數徽章
     */
    private fun refreshPhotoLists() {
        val b = binding ?: return
        if (!isAdded) return

        val photos = cameraUtils.getAllPhotos()
        val latest = photos.firstOrNull()

        // 1. 更新相機畫面上的橫向相片列表
        if (photos.isNotEmpty()) {
            b.rvCameraPhotoList.visibility = View.VISIBLE
            cameraPhotoAdapter.submitList(photos)
        } else {
            b.rvCameraPhotoList.visibility = View.GONE
            cameraPhotoAdapter.submitList(emptyList())
        }

        // 2. 更新左下角最新縮圖與數量徽章
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
     * 使用 PhotoAdapter 以 3 欄網格展示歷史所有照片
     */
    private fun openGalleryBottomSheet() {
        val context = requireContext()
        val dialog = BottomSheetDialog(context, R.style.Theme_MyApplication)
        activeGalleryDialog = dialog

        val sheetBinding = BottomSheetGalleryBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        lateinit var adapter: PhotoAdapter
        adapter = PhotoAdapter(
            coroutineScope = viewLifecycleOwner.lifecycleScope,
            isHorizontal = false,
            onPhotoClick = { photo ->
                // 點擊相簿網格縮圖：立即放大全螢幕展示整張大圖
                showPhotoDetailDialog(photo)
            },
            onDeleteClick = { photo ->
                showDeleteConfirmDialog(photo) {
                    refreshGalleryList(sheetBinding, adapter)
                    refreshPhotoLists()
                }
            }
        )

        sheetBinding.rvPhotos.layoutManager = GridLayoutManager(context, 3)
        sheetBinding.rvPhotos.adapter = adapter

        val photos = cameraUtils.getAllPhotos()
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
        val updatedPhotos = cameraUtils.getAllPhotos()
        updateGalleryUI(sheetBinding, updatedPhotos, adapter)
        refreshPhotoLists()
    }

    /**
     * 【縮圖點擊放出整張圖片】：開啟全螢幕大圖檢視對話框
     *
     * 特色：
     * 1. 滿版無黑邊全螢幕展示大圖
     * 2. 先秒開低延遲縮圖，隨後平滑載入原圖細節，無白屏或延遲感
     * 3. 支援單指點擊隱藏/顯示操作列（沉浸式體驗）
     * 4. 支援雙指手勢縮放 (Pinch to zoom) 與雙擊放大/還原
     * 5. 頂部提供關閉、照片資訊、直接開啟相簿總覽與刪除按鈕
     *
     * @param photo 點選的照片物件
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun showPhotoDetailDialog(photo: PhotoItem) {
        val context = requireContext()
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val detailBinding = DialogPhotoDetailBinding.inflate(layoutInflater)
        dialog.setContentView(detailBinding.root)

        // 設定視窗全螢幕填滿與背景為黑色
        dialog.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.BLACK))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }

        detailBinding.tvDetailFileName.text = photo.name
        detailBinding.tvDetailMeta.text = "${photo.formattedDateTime()} • ${photo.formattedSize()}"

        // 關閉大圖檢視
        detailBinding.btnDetailClose.setOnClickListener {
            dialog.dismiss()
        }

        // 開啟完整相簿總覽
        detailBinding.btnDetailGallery.setOnClickListener {
            dialog.dismiss()
            openGalleryBottomSheet()
        }

        // 刪除大圖照片
        detailBinding.btnDetailDelete.setOnClickListener {
            showDeleteConfirmDialog(photo) {
                dialog.dismiss()
                activeGalleryDialog?.dismiss()
                refreshPhotoLists()
            }
        }

        // 點擊照片切換頂部控制列顯示/隱藏
        var isBarVisible = true
        fun toggleTopBar() {
            isBarVisible = !isBarVisible
            detailBinding.detailTopBar.animate()
                .alpha(if (isBarVisible) 1f else 0f)
                .translationY(if (isBarVisible) 0f else -detailBinding.detailTopBar.height.toFloat())
                .setDuration(200)
                .start()
        }

        // 設置雙擊放大與單擊切換頂部列手勢監聽
        var currentScale = 1.0f
        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                toggleTopBar()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                currentScale = if (currentScale > 1.2f) 1.0f else 2.2f
                detailBinding.ivFullPhoto.animate()
                    .scaleX(currentScale)
                    .scaleY(currentScale)
                    .setDuration(250)
                    .start()
                return true
            }
        })

        // 支援雙指手勢縮放大圖
        val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                currentScale *= detector.scaleFactor
                currentScale = currentScale.coerceIn(0.8f, 4.0f)
                detailBinding.ivFullPhoto.scaleX = currentScale
                detailBinding.ivFullPhoto.scaleY = currentScale
                return true
            }
        })

        detailBinding.ivFullPhoto.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            true
        }

        // 步驟 1：先立即載入低解析度縮圖（秒開，無黑屏等待）
        viewLifecycleOwner.lifecycleScope.launch {
            val quickThumb = ImageLoaderUtils.loadBitmapSampled(photo.file, 300, 300)
            if (quickThumb != null) {
                detailBinding.ivFullPhoto.setImageBitmap(quickThumb)
            }

            // 步驟 2：背景非同步載入原解析度相片並更新
            detailBinding.pbDetailLoading.visibility = View.VISIBLE
            val metrics = resources.displayMetrics
            val fullBitmap = ImageLoaderUtils.loadBitmapSampled(photo.file, metrics.widthPixels, metrics.heightPixels)
            detailBinding.pbDetailLoading.visibility = View.GONE
            if (fullBitmap != null) {
                detailBinding.ivFullPhoto.setImageBitmap(fullBitmap)
            }
        }

        dialog.show()
    }

    /**
     * 彈出刪除相片的二次確認對話框，透過 CameraUtils 執行刪除
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
                val success = cameraUtils.deletePhoto(photo)
                if (success) {
                    if (isAdded) {
                        Toast.makeText(requireContext(), getString(R.string.photo_deleted_success), Toast.LENGTH_SHORT).show()
                        refreshPhotoLists()
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
     * 視圖銷毀時釋放相機背景執行緒與彈窗資源
     */
    override fun onDestroyView() {
        super.onDestroyView()
        activeGalleryDialog?.dismiss()
        activeGalleryDialog = null
        cameraExecutor.shutdown()
    }
}
