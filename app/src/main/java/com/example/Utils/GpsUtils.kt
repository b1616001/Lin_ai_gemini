package com.example.Utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * GPS 與即時定位管理工具類別 (GpsUtils)
 *
 * 主要功能：
 * 1. 檢查定位權限 (ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION)
 * 2. 檢測 GPS 與網路定位硬體/服務啟用狀態
 * 3. 註冊與監聽即時 LocationListener 更新
 * 4. 座標格式化 (經緯度、海拔高度、移動速度、方位角、精度誤差)
 * 5. 反向地理編碼 (Reverse Geocoding) 取得當前地址
 * 6. 提供跳轉系統定位設定頁面之 Intent
 */
class GpsUtils(private val context: Context) {

    /**
     * 定位資訊資料模型
     */
    data class GpsLocationData(
        val latitude: Double,
        val longitude: Double,
        val altitude: Double?,
        val accuracy: Float?,
        val speed: Float?,
        val bearing: Float?,
        val timestamp: Long,
        val provider: String,
        val address: String? = null
    )

    private val locationManager: LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private var activeLocationListener: LocationListener? = null

    /**
     * 檢查是否已取得定位權限 (粗略或精確定位)
     */
    fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineGranted || coarseGranted
    }

    /**
     * 檢查 GPS 硬體衛星定位服務是否已在系統設定中開啟
     */
    fun isGpsEnabled(): Boolean {
        return try {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 檢查網路輔助定位 (Network Provider) 是否已啟用
     */
    fun isNetworkLocationEnabled(): Boolean {
        return try {
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 取得最後一次快取的最佳定位物件
     */
    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(): Location? {
        if (!hasLocationPermission()) return null

        val gpsLoc = try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) {
            null
        }

        val netLoc = try {
            locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) {
            null
        }

        return when {
            gpsLoc != null && netLoc != null -> {
                if (gpsLoc.time > netLoc.time) gpsLoc else netLoc
            }
            gpsLoc != null -> gpsLoc
            else -> netLoc
        }
    }

    /**
     * 啟動即時 GPS / 網路定位監聽
     *
     * @param minTimeMs 兩次定位更新的最小時間間隔（毫秒）
     * @param minDistanceM 兩次定位更新的最小距離間隔（公尺）
     * @param onLocationUpdate 收到新位置時的回呼
     * @param onError 發生錯誤或權限不足時的回呼
     */
    @SuppressLint("MissingPermission")
    fun startLocationUpdates(
        minTimeMs: Long = 1000L,
        minDistanceM: Float = 1.0f,
        onLocationUpdate: (GpsLocationData) -> Unit,
        onError: ((String) -> Unit)? = null
    ) {
        if (!hasLocationPermission()) {
            onError?.invoke("Missing location permissions")
            return
        }

        // 若已在監聽中，先停止以防止重複註冊
        stopLocationUpdates()

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val data = GpsLocationData(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitude = if (location.hasAltitude()) location.altitude else null,
                    accuracy = if (location.hasAccuracy()) location.accuracy else null,
                    speed = if (location.hasSpeed()) location.speed else null,
                    bearing = if (location.hasBearing()) location.bearing else null,
                    timestamp = location.time,
                    provider = location.provider ?: "GPS"
                )
                onLocationUpdate(data)
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        activeLocationListener = listener

        var registered = false

        if (isGpsEnabled()) {
            try {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    minTimeMs,
                    minDistanceM,
                    listener
                )
                registered = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (isNetworkLocationEnabled()) {
            try {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    minTimeMs,
                    minDistanceM,
                    listener
                )
                registered = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (!registered) {
            onError?.invoke("GPS and Network providers are both disabled")
        } else {
            // 先傳回快取的最後已知座標，提升載入體驗
            getLastKnownLocation()?.let { loc ->
                onLocationUpdate(
                    GpsLocationData(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        altitude = if (loc.hasAltitude()) loc.altitude else null,
                        accuracy = if (loc.hasAccuracy()) loc.accuracy else null,
                        speed = if (loc.hasSpeed()) loc.speed else null,
                        bearing = if (loc.hasBearing()) loc.bearing else null,
                        timestamp = loc.time,
                        provider = "${loc.provider ?: "GPS"} (Cached)"
                    )
                )
            }
        }
    }

    /**
     * 停止定位監聽以節省電量
     */
    fun stopLocationUpdates() {
        activeLocationListener?.let {
            try {
                locationManager.removeUpdates(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        activeLocationListener = null
    }

    /**
     * 反向地理編碼：依據經緯度查詢實體地址（在 IO 協程中非同步執行）
     */
    suspend fun getAddressFromLocation(latitude: Double, longitude: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val addr = addresses[0]
                        return@withContext addr.getAddressLine(0) ?: "${addr.adminArea ?: ""} ${addr.locality ?: ""}"
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val addr = addresses[0]
                        return@withContext addr.getAddressLine(0) ?: "${addr.adminArea ?: ""} ${addr.locality ?: ""}"
                    }
                }
                null
            } catch (_: Exception) {
                null
            }
        }

    /**
     * 建立開啟系統定位設定頁面之 Intent
     */
    fun createLocationSettingsIntent(): Intent {
        return Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    }

    /**
     * 格式化經緯度為易讀十進位字串（保留 6 位小數）
     */
    fun formatCoordinates(latitude: Double, longitude: Double): String {
        return String.format(Locale.US, "%.6f, %.6f", latitude, longitude)
    }

    /**
     * 格式化速度為公里/小時 (km/h) 與 公尺/秒 (m/s)
     */
    fun formatSpeed(speedMps: Float?): String {
        if (speedMps == null || speedMps < 0) return "--"
        val kmh = speedMps * 3.6f
        return String.format(Locale.getDefault(), "%.1f km/h (%.1f m/s)", kmh, speedMps)
    }

    /**
     * 格式化海拔高度為公尺 (m)
     */
    fun formatAltitude(altitudeM: Double?): String {
        if (altitudeM == null) return "--"
        return String.format(Locale.getDefault(), "%.1f m", altitudeM)
    }

    /**
     * 格式化定位誤差半徑 (m)
     */
    fun formatAccuracy(accuracyM: Float?): String {
        if (accuracyM == null) return "--"
        return String.format(Locale.getDefault(), "±%.1f m", accuracyM)
    }

    /**
     * 格式化時間戳記為 HH:mm:ss
     */
    fun formatTimestamp(timestamp: Long): String {
        if (timestamp <= 0) return "--"
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
