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

class CameraFragment : BaseBindingFragment<FragmentCameraBinding>() {

    private lateinit var photoRepository: PhotoRepository
    private lateinit var cameraExecutor: ExecutorService

    private var imageCapture: ImageCapture? = null
    private var cameraControl: CameraControl? = null
    private var cameraInfo: CameraInfo? = null
    private var camera: Camera? = null

    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var flashMode = ImageCapture.FLASH_MODE_AUTO
    private var activeGalleryDialog: BottomSheetDialog? = null

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

    override fun onCreateViewBinding(layoutInflater: LayoutInflater): FragmentCameraBinding? {
        return FragmentCameraBinding.inflate(layoutInflater)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        photoRepository = PhotoRepository(requireContext())
        cameraExecutor = Executors.newSingleThreadExecutor()

        checkCameraPermission()
        setupListeners()
        updateRecentThumbnail()
    }

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

    private fun setupCameraUI() {
        binding?.let { b ->
            b.layoutPermission.visibility = View.GONE
            b.topBar.visibility = View.VISIBLE
            b.bottomBar.visibility = View.VISIBLE
            b.viewFinder.visibility = View.VISIBLE
        }
    }

    private fun showPermissionDeniedUI() {
        binding?.let { b ->
            b.layoutPermission.visibility = View.VISIBLE
            b.topBar.visibility = View.GONE
            b.bottomBar.visibility = View.GONE
        }
    }

    private fun setupListeners() {
        val b = binding ?: return

        b.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        b.btnGrantPermission.setOnClickListener {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        b.btnCapture.setOnClickListener {
            takePhoto()
        }

        b.btnSwitchCamera.setOnClickListener {
            toggleCameraFacing()
        }

        b.btnFlash.setOnClickListener {
            toggleFlashMode()
        }

        b.layoutGallery.setOnClickListener {
            openGalleryBottomSheet()
        }

        b.btnQuickDelete.setOnClickListener {
            val latest = photoRepository.getLatestPhoto()
            if (latest != null) {
                showDeleteConfirmDialog(latest)
            }
        }

        setupFocusAndZoom()
    }

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

                // Show focus ring at tap coordinates
                showFocusRing(event.x, event.y)
                view.performClick()
            }
            true
        }
    }

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

    private fun startCamera() {
        val context = context ?: return
        val b = binding ?: return
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            if (!isAdded || binding == null) return@addListener

            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = b.viewFinder.surfaceProvider
                }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setFlashMode(flashMode)
                    .build()

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                cameraProvider.unbindAll()

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

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val b = binding ?: return

        // Shutter feedback animation
        b.captureFlashOverlay.apply {
            visibility = View.VISIBLE
            alpha = 0.85f
            animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction { visibility = View.GONE }
                .start()
        }

        val photoFile = photoRepository.createNewPhotoFile()
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

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
                            updateRecentThumbnail()
                        }
                    }
                }
            }
        )
    }

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

    private fun refreshGalleryList(
        sheetBinding: BottomSheetGalleryBinding,
        adapter: PhotoAdapter
    ) {
        val updatedPhotos = photoRepository.getAllPhotos()
        updateGalleryUI(sheetBinding, updatedPhotos, adapter)
        updateRecentThumbnail()
    }

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

        viewLifecycleOwner.lifecycleScope.launch {
            val bitmap = ImageLoaderUtils.loadBitmapSampled(photo.file, 1080, 1920)
            if (bitmap != null) {
                detailBinding.ivFullPhoto.setImageBitmap(bitmap)
            }
        }

        dialog.show()
    }

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

    override fun onDestroyView() {
        super.onDestroyView()
        activeGalleryDialog?.dismiss()
        activeGalleryDialog = null
        cameraExecutor.shutdown()
    }
}
