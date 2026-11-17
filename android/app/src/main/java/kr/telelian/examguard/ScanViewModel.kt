package kr.telelian.examguard

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.telelian.examguard.model.DetectedDevice
import kr.telelian.examguard.model.SignalType
import kr.telelian.examguard.scan.BleScanner
import kr.telelian.examguard.scan.WifiScanner
import kr.telelian.examguard.sensor.CompassProvider
import kr.telelian.examguard.sensor.LocationProvider
import kr.telelian.examguard.sensor.MagneticProvider

/** 화면이 구독하는 전체 상태. */
data class UiState(
    val devices: List<DetectedDevice> = emptyList(),
    val headingDeg: Float = 0f,
    val lat: Double? = null,
    val lng: Double? = null,
    val magneticUt: Float = 0f,
    val magneticBaselineUt: Float = 0f,
    val scanning: Boolean = false,
    val bleAvailable: Boolean = true,
    val bleEnabled: Boolean = true,
    val wifiAvailable: Boolean = true,
    val nearThresholdDbm: Int = DEFAULT_NEAR_DBM,
    val selectedDeviceId: String? = null,
) {
    /** 1m 안팎(임계값 이상)으로 판정된 근접 기기들. */
    val nearDevices: List<DetectedDevice>
        get() = devices.filter { it.rssi >= nearThresholdDbm }

    /** 기준선 대비 자기장 이상치(µT). 양수로 클수록 금속/자성체 근접 가능성. */
    val magneticAnomalyUt: Float
        get() = (magneticUt - magneticBaselineUt).coerceAtLeast(0f)

    val magneticAlert: Boolean
        get() = magneticAnomalyUt >= MAG_ANOMALY_ALERT_UT

    companion object {
        const val DEFAULT_NEAR_DBM = -55       // 대략 1m 안팎 (환경에 따라 변동)
        const val MAG_ANOMALY_ALERT_UT = 30f   // 기준선 대비 +30µT 이상이면 경보
    }
}

/**
 * BLE/WiFi 스캐너와 나침반·자기장·위치 센서를 하나의 [UiState] 로 합친다.
 */
class ScanViewModel(app: Application) : AndroidViewModel(app) {

    private val ble = BleScanner(app)
    private val wifi = WifiScanner(app)
    private val compass = CompassProvider(app)
    private val magnetic = MagneticProvider(app)
    private val location = LocationProvider(app)

    private val _state = MutableStateFlow(
        UiState(
            bleAvailable = ble.isAvailable,
            bleEnabled = ble.isEnabled,
            wifiAvailable = wifi.isAvailable,
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    // 감지 기기 누적 맵 (스캔 콜백은 여러 스레드에서 올 수 있어 동기화)
    private val lock = Any()
    private val devices = HashMap<String, DetectedDevice>()

    // 방향 찾기용 peak 추적
    private var currentHeading = 0f
    private var selectedPeakRssi = Int.MIN_VALUE

    private var emitJob: Job? = null
    private var wifiRescanJob: Job? = null

    fun start() {
        if (_state.value.scanning) return
        _state.update { it.copy(scanning = true, bleEnabled = ble.isEnabled) }

        ble.start { onDevice(it) }
        wifi.start { onDevice(it) }
        compass.start { h ->
            currentHeading = h
            _state.update { it.copy(headingDeg = h) }
        }
        magnetic.start { cur, base ->
            _state.update { it.copy(magneticUt = cur, magneticBaselineUt = base) }
        }
        location.start { lat, lng ->
            _state.update { it.copy(lat = lat, lng = lng) }
        }

        // 주기적으로 스냅샷 방출 + 오래된 기기 만료
        emitJob = viewModelScope.launch {
            while (true) {
                publish()
                delay(200L)
            }
        }
        // WiFi 재스캔 요청 (쓰로틀 고려해 느리게)
        wifiRescanJob = viewModelScope.launch {
            while (true) {
                delay(30_000L)
                wifi.requestScan()
            }
        }
    }

    fun stop() {
        emitJob?.cancel(); emitJob = null
        wifiRescanJob?.cancel(); wifiRescanJob = null
        ble.stop(); wifi.stop(); compass.stop(); magnetic.stop(); location.stop()
        synchronized(lock) { devices.clear() }
        _state.update { it.copy(scanning = false, devices = emptyList()) }
    }

    fun selectDevice(id: String?) {
        selectedPeakRssi = Int.MIN_VALUE
        synchronized(lock) {
            // 기존 선택 기기의 peak 방위 제거
            _state.value.selectedDeviceId?.let { old ->
                devices[old]?.let { devices[old] = it.copy(peakHeadingDeg = null) }
            }
        }
        _state.update { it.copy(selectedDeviceId = id) }
    }

    fun setNearThreshold(dbm: Int) {
        _state.update { it.copy(nearThresholdDbm = dbm) }
    }

    private fun onDevice(d: DetectedDevice) {
        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            val existing = devices[d.id]
            var merged = d.copy(lastSeen = now, peakHeadingDeg = existing?.peakHeadingDeg)

            // 방향 찾기: 선택된 기기의 RSSI 최대 시점 방위 기록
            if (d.id == _state.value.selectedDeviceId) {
                if (d.rssi > selectedPeakRssi) {
                    selectedPeakRssi = d.rssi
                    merged = merged.copy(peakHeadingDeg = currentHeading)
                }
            }
            devices[d.id] = merged
        }
    }

    private fun publish() {
        val cutoff = SystemClock.elapsedRealtime() - EXPIRY_MS
        val snapshot: List<DetectedDevice>
        synchronized(lock) {
            val it = devices.entries.iterator()
            while (it.hasNext()) {
                val e = it.next()
                // WiFi 는 lastSeen 이 갱신돼도 캐시라 오래 유지; BLE 만 만료
                if (e.value.type == SignalType.BLE && e.value.lastSeen < cutoff) it.remove()
            }
            snapshot = devices.values.sortedByDescending { it.rssi }
        }
        _state.update { it.copy(devices = snapshot) }
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    companion object {
        private const val EXPIRY_MS = 10_000L
    }
}
