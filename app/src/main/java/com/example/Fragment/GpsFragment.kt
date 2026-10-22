package com.example.Fragment

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.R
import com.example.Utils.GpsUtils
import com.example.databinding.FragmentGpsBinding
import com.example.viewBinding.BaseBindingFragment
import kotlinx.coroutines.launch

/**
 * GPS 即時定位展示畫面 (GpsFragment)
 *
 * 功能：
 * 1. 繼承 [BaseBindingFragment]
 * 2. 透過 [GpsUtils] 管理定位權限檢測與請求
 * 3. 取得並即時展示經緯度、海拔高度、移動速度、方位角、精度與時間戳記
 * 4. 非同步透過 Geocoder 解析實體地址
 * 5. 支援定位啟動/停止切換、座標複製至剪貼簿與跳轉定位設定頁
 */
class GpsFragment : BaseBindingFragment<FragmentGpsBinding>() {

    private lateinit var gpsUtils: GpsUtils
    private var isTracking = false
    private var lastLatitude = 0.0
    private var lastLongitude = 0.0

    /**
     * 定位權限請求回呼
     */
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (fineGranted || coarseGranted) {
            startTracking()
        } else {
            updateStatusUi(
                badgeText = getString(R.string.gps_status_no_permission),
                isTrackingActive = false
            )
            Toast.makeText(requireContext(), R.string.gps_permission_prompt, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateViewBinding(layoutInflater: LayoutInflater): FragmentGpsBinding? {
        return FragmentGpsBinding.inflate(layoutInflater)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        gpsUtils = GpsUtils(requireContext())

        setupListeners()
        checkAndStartLocation()
    }

    private fun setupListeners() {
        // 返回按鈕
        binding?.btnGpsBack?.setOnClickListener {
            findNavController().navigateUp()
        }

        // 啟動 / 停止切換
        binding?.btnToggleTracking?.setOnClickListener {
            if (isTracking) {
                stopTracking()
            } else {
                checkAndStartLocation()
            }
        }

        // 開啟系統定位設定
        binding?.btnOpenLocationSettings?.setOnClickListener {
            startActivity(gpsUtils.createLocationSettingsIntent())
        }

        // 複製座標
        binding?.btnCopyCoordinates?.setOnClickListener {
            val coords = gpsUtils.formatCoordinates(lastLatitude, lastLongitude)
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("GPS Coordinates", coords)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), R.string.gps_copied_toast, Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndStartLocation() {
        if (!gpsUtils.hasLocationPermission()) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        if (!gpsUtils.isGpsEnabled() && !gpsUtils.isNetworkLocationEnabled()) {
            updateStatusUi(
                badgeText = getString(R.string.gps_status_disabled),
                isTrackingActive = false
            )
            Toast.makeText(requireContext(), R.string.gps_status_disabled, Toast.LENGTH_SHORT).show()
            return
        }

        startTracking()
    }

    private fun startTracking() {
        isTracking = true
        updateStatusUi(
            badgeText = getString(R.string.gps_status_active),
            isTrackingActive = true
        )

        gpsUtils.startLocationUpdates(
            minTimeMs = 1000L,
            minDistanceM = 1.0f,
            onLocationUpdate = { data ->
                renderLocationData(data)
            },
            onError = { err ->
                Toast.makeText(requireContext(), err, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun stopTracking() {
        isTracking = false
        gpsUtils.stopLocationUpdates()
        updateStatusUi(
            badgeText = getString(R.string.gps_btn_start),
            isTrackingActive = false
        )
    }

    private fun renderLocationData(data: GpsUtils.GpsLocationData) {
        lastLatitude = data.latitude
        lastLongitude = data.longitude

        binding?.apply {
            tvGpsLatitude.text = String.format(java.util.Locale.US, "%.6f", data.latitude)
            tvGpsLongitude.text = String.format(java.util.Locale.US, "%.6f", data.longitude)
            tvGpsAltitude.text = gpsUtils.formatAltitude(data.altitude)
            tvGpsSpeed.text = gpsUtils.formatSpeed(data.speed)
            tvGpsAccuracy.text = gpsUtils.formatAccuracy(data.accuracy)
            tvGpsBearing.text = if (data.bearing != null) "${data.bearing.toInt()}°" else "--"
            tvGpsProvider.text = data.provider
            tvGpsTimestamp.text = gpsUtils.formatTimestamp(data.timestamp)
        }

        // 非同步解析實體地址
        viewLifecycleOwner.lifecycleScope.launch {
            val address = gpsUtils.getAddressFromLocation(data.latitude, data.longitude)
            binding?.tvGpsAddress?.text = address ?: "緯度: ${data.latitude}, 經度: ${data.longitude}"
        }
    }

    private fun updateStatusUi(badgeText: String, isTrackingActive: Boolean) {
        binding?.apply {
            tvGpsStatusBadge.text = badgeText
            if (isTrackingActive) {
                btnToggleTracking.text = getString(R.string.gps_btn_stop)
                btnToggleTracking.setBackgroundColor(requireContext().getColor(R.color.delete_red))
            } else {
                btnToggleTracking.text = getString(R.string.gps_btn_start)
                btnToggleTracking.setBackgroundColor(requireContext().getColor(R.color.camera_accent))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isTracking) {
            gpsUtils.stopLocationUpdates()
        }
    }
}
