package kr.telelian.examguard.model

/** 감지된 신호원의 종류. */
enum class SignalType { BLE, WIFI }

/**
 * 스캔으로 감지된 하나의 무선 신호원.
 *
 * @param id BLE MAC 주소 또는 WiFi BSSID (고유 키)
 * @param name 광고 이름 / SSID (없을 수 있음)
 * @param type 신호 종류
 * @param rssi 수신 세기(dBm). 0 에 가까울수록 강함(가까움)
 * @param lastSeen 마지막 수신 시각(epoch ms)
 * @param peakHeadingDeg 방향 찾기 모드에서 RSSI 최대였던 방위(도). 없으면 null
 */
data class DetectedDevice(
    val id: String,
    val name: String?,
    val type: SignalType,
    val rssi: Int,
    val lastSeen: Long,
    val peakHeadingDeg: Float? = null,
) {
    /** 표시용 이름 (없으면 종류+MAC 뒷자리). */
    val displayName: String
        get() = when {
            !name.isNullOrBlank() -> name
            type == SignalType.BLE -> "알 수 없는 BLE (${id.takeLast(5)})"
            else -> "숨김 WiFi (${id.takeLast(5)})"
        }
}

/** RSSI(dBm) 를 레이더 반지름 비율(0=중심, 1=바깥)로 변환. */
fun rssiToRadiusFraction(rssi: Int): Float {
    val strong = -40f // 중심 근처
    val weak = -100f  // 바깥 테두리
    return ((strong - rssi) / (strong - weak)).coerceIn(0f, 1f)
}
