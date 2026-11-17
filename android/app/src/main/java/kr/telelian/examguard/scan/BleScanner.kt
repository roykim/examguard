package kr.telelian.examguard.scan

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import kr.telelian.examguard.model.DetectedDevice
import kr.telelian.examguard.model.SignalType

/**
 * BLE 광고 패킷을 지속 스캔하여 기기별 RSSI 를 콜백으로 흘려보낸다.
 * 권한(BLUETOOTH_SCAN)은 호출 측에서 보장해야 한다.
 */
class BleScanner(context: Context) {

    private val manager =
        context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter = manager?.adapter

    /** 기기에 BLE 가 없거나 꺼져있으면 false. */
    val isAvailable: Boolean get() = adapter != null

    /** 어댑터가 켜져 있는지. */
    val isEnabled: Boolean get() = adapter?.isEnabled == true

    private var scanner: BluetoothLeScanner? = null
    private var onResult: ((DetectedDevice) -> Unit)? = null

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = handle(result)

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { handle(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "BLE scan failed: $errorCode")
        }
    }

    private fun handle(result: ScanResult) {
        val cb = onResult ?: return
        cb(
            DetectedDevice(
                id = result.device.address,
                name = result.scanRecord?.deviceName,
                type = SignalType.BLE,
                rssi = result.rssi,
                lastSeen = result.timestampNanos / 1_000_000L, // 상대값이지만 정렬엔 무방
            ),
        )
    }

    @SuppressLint("MissingPermission")
    fun start(onResult: (DetectedDevice) -> Unit) {
        if (!isEnabled) return
        this.onResult = onResult
        scanner = adapter?.bluetoothLeScanner
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setReportDelay(0)
            .build()
        try {
            scanner?.startScan(null, settings, callback)
        } catch (e: SecurityException) {
            Log.w(TAG, "BLE start denied: ${e.message}")
        } catch (e: IllegalStateException) {
            Log.w(TAG, "BLE start failed: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        try {
            scanner?.stopScan(callback)
        } catch (e: SecurityException) {
            Log.w(TAG, "BLE stop denied: ${e.message}")
        } catch (e: IllegalStateException) {
            // adapter off
        }
        onResult = null
    }

    companion object {
        private const val TAG = "BleScanner"
    }
}
