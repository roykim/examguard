package kr.telelian.examguard.scan

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.util.Log
import androidx.core.content.ContextCompat
import kr.telelian.examguard.model.DetectedDevice
import kr.telelian.examguard.model.SignalType

/**
 * 주변 WiFi AP 를 스캔하여 BSSID 별 RSSI 를 콜백으로 전달한다.
 *
 * 주의: Android 9+ 는 스캔 횟수를 제한(2분당 4회)한다. 제한에 걸리면
 * [android.net.wifi.WifiManager.startScan] 이 false 를 반환하고, 마지막 캐시 결과가
 * [android.net.wifi.WifiManager.getScanResults] 로 계속 제공된다. 그래서 요청과 무관하게
 * 캐시 결과를 주기적으로 읽어 표시한다.
 */
class WifiScanner(context: Context) {

    private val appContext = context.applicationContext
    private val wifi =
        appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    val isAvailable: Boolean get() = wifi != null

    private var onResult: ((DetectedDevice) -> Unit)? = null
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            emitCached()
        }
    }

    fun start(onResult: (DetectedDevice) -> Unit) {
        this.onResult = onResult
        if (!registered) {
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            registered = true
        }
        emitCached()   // 즉시 캐시 결과 1회
        requestScan()
    }

    /** OS 에 재스캔 요청 (쓰로틀되면 무시됨). 캐시 읽기는 항상 수행. */
    @SuppressLint("MissingPermission")
    fun requestScan() {
        try {
            @Suppress("DEPRECATION")
            wifi?.startScan()
        } catch (e: SecurityException) {
            Log.w(TAG, "wifi scan denied: ${e.message}")
        }
        emitCached()
    }

    @SuppressLint("MissingPermission")
    private fun emitCached() {
        val cb = onResult ?: return
        val results = try {
            wifi?.scanResults ?: emptyList()
        } catch (e: SecurityException) {
            Log.w(TAG, "scanResults denied: ${e.message}")
            emptyList()
        }
        for (r in results) {
            @Suppress("DEPRECATION")
            val ssid = r.SSID
            cb(
                DetectedDevice(
                    id = r.BSSID ?: continue,
                    name = ssid?.ifBlank { null },
                    type = SignalType.WIFI,
                    rssi = r.level,
                    lastSeen = 0L,
                ),
            )
        }
    }

    fun stop() {
        if (registered) {
            try {
                appContext.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
            }
            registered = false
        }
        onResult = null
    }

    companion object {
        private const val TAG = "WifiScanner"
    }
}
