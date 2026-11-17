# 시험감독관 (ExamGuard) — 설계 스펙

작성일: 2026-11-16
플랫폼: **Android 전용** (Kotlin + Jetpack Compose)

## 1. 목적

공공기관 시험장 등에서 **주변의 무선 신호(BLE·WiFi)를 스캔**하여,
신호 세기(RSSI)를 **숫자·그래픽(레이더)** 으로 시각화하고,
**현재 위치의 지도**를 배경에 깔고 **폰의 방위(나침반)** 를 함께 표시하는 탐지 도구.

주 용도: 시험장에서 부정행위에 사용될 수 있는 무선 송신 기기(초소형 BLE 이어셋 등)의
존재와 대략적 근접도를 파악.

## 2. 기술적 현실 (설계 전제)

스마트폰은 광대역 스펙트럼 분석기(SDR)가 아니므로 폰 라디오가 실제로 "들을 수 있는" 신호만 감지된다.

| 대상 | 가능 여부 | 비고 |
|---|---|---|
| BLE 광고 패킷 기기 (RSSI) | ✅ | 무선 이어셋·트래커·비컨 등 |
| WiFi AP/기기 (RSSI) | ✅ | OS 스캔 쓰로틀 존재 (Android 9+: 2분당 4회) |
| 꺼진·비행기모드·셀룰러 전용 기기 | ❌ | 물리적으로 감지 불가 |
| 신호원의 **방향** | ⚠️ | 안테나 1개로는 직접 계산 불가 → 회전 피크 탐지로 우회 |

- RSSI는 **상대 세기**이며 절대 거리로 환산하지 않는다(환경 영향이 큼). 시각화는 "강할수록 중심(가까움)".
- 방향은 사용자가 **폰을 회전**시켜 특정 기기의 RSSI가 최대가 되는 방위를 찾는 방식으로 추정한다.

## 3. 아키텍처

```
MainActivity (권한 게이트)
  └─ ExamGuardApp (Compose)
       ├─ MapBackground      (Google Maps Compose, 반투명 배경, 키 없으면 폴백)
       ├─ RadarOverlay       (Canvas: 동심원 + 블립 + 나침반, heading에 맞춰 회전)
       └─ DeviceListPanel    (LazyColumn: RSSI 막대 + dBm + 타입 아이콘)

ScanViewModel (StateFlow<UiState>)
  ├─ BleScanner       (BluetoothLeScanner 콜백 → DetectedDevice)
  ├─ WifiScanner      (WifiManager.scanResults → DetectedDevice)
  ├─ CompassProvider  (SensorManager ROTATION_VECTOR → azimuth)
  └─ LocationProvider (FusedLocationProvider → LatLng)
```

데이터 모델:
```kotlin
enum class SignalType { BLE, WIFI }
data class DetectedDevice(
    val id: String,          // MAC 또는 BSSID
    val name: String?,       // 광고명 / SSID
    val type: SignalType,
    val rssi: Int,           // dBm
    val lastSeen: Long,      // epoch ms
    val peakHeadingDeg: Float? = null // 방향찾기 모드에서 기록된 방위
)
```

## 4. 컴포넌트 명세

### BleScanner
- `BluetoothLeScanner.startScan`, `ScanSettings.SCAN_MODE_LOW_LATENCY`, `MATCH_MODE_AGGRESSIVE`
- 콜백 `onScanResult`에서 `result.rssi`, `device.address`, `scanRecord?.deviceName` 수집
- 최근 수신 맵 유지, 오래된(예: 10초) 기기 만료

### WifiScanner
- `WifiManager.startScan()` 호출 + `scanResults` 폴링, `SCAN_RESULTS_AVAILABLE_ACTION` 브로드캐스트 수신
- 쓰로틀 고려: 마지막 결과 캐시 표시, 재스캔 주기 ≥ 30초 권장
- `ScanResult.level`(RSSI), `SSID`, `BSSID`

### CompassProvider
- `Sensor.TYPE_ROTATION_VECTOR` → `getRotationMatrixFromVector` → `getOrientation` → azimuth(도)
- 저역통과 필터로 떨림 완화

### LocationProvider
- `FusedLocationProviderClient.requestLocationUpdates`, 우선순위 BALANCED
- 위치 없으면 지도는 기본 위치/레이더 전용으로 폴백

### RadarOverlay (Canvas)
- 동심원 3~4개(그리드), 중심=사용자
- 블립 반지름 = `rssiToRadius(rssi)` (예: -40→0.1R, -100→1.0R)
- 전체 레이더를 `-heading` 만큼 회전시켜 화면 상단이 항상 북쪽
- 선택된 기기의 `peakHeadingDeg` 방향에 화살표

### 방향 찾기 모드
- 목록에서 기기 선택 → 수집 중 해당 기기 RSSI 최대값 시점의 heading 저장
- "폰을 천천히 돌리세요" 가이드 + 현재/최대 RSSI 표시

## 5. 권한

AndroidManifest:
- `BLUETOOTH_SCAN` (uses-permission, `android:usesPermissionFlags="neverForLocation"` 미사용 — 위치연계 WiFi 때문에 FINE_LOCATION 필요)
- `BLUETOOTH_CONNECT`
- `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`
- `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`
- (레거시) maxSdk 30 `BLUETOOTH`, `BLUETOOTH_ADMIN`

런타임 권한 플로우: 앱 진입 시 일괄 요청 → 거부 시 기능별 안내.

## 6. 빌드 / 서명

- compileSdk 36, minSdk 24, targetSdk 36
- AGP 8.13.0, Gradle 8.14.3, Kotlin 2.0.x (+ compose compiler plugin)
- 내부 테스트: debug 서명으로 APK 사이드로드
- Google Maps: `local.properties`의 `MAPS_API_KEY`를 manifest placeholder로 주입. 키 없으면 지도 비활성 + 레이더 전용.

## 7. 범위 밖 (YAGNI)

- iOS 버전 (WiFi 스캔 불가로 제외)
- 신호 로깅/서버 업로드, 계정, 다국어
- 정밀 삼각측량(다중 측정점) — 단일 기기 회전 탐지로 충분
