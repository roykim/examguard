# 시험감독관 (ExamGuard)

공공기관 시험장 등에서 주변 **무선 신호(BLE·WiFi)의 세기(RSSI)** 를 스캔해
숫자·레이더 그래픽으로 보여주는 Android 탐지 도구.

- 지도 배경(현재 위치) + 나침반(폰 방위)
- RSSI 기반 레이더(중심=나, 바깥=약한 신호)
- **1m 안팎 근접 기기 경보**(강한 RSSI) + 진동
- **자기장(µT) 이상 감지** — 숨은 전자기기/자성체 의심 시 경보
- 방향 찾기 모드: 기기 선택 후 폰을 돌려 세기 최대 방향 추정

> 플랫폼: **Android 전용** (iOS는 OS 정책상 주변 WiFi 스캔 불가로 제외)

## 기술 스택
Kotlin · Jetpack Compose · Google Maps Compose · CoreLocation/Sensor · BLE/WiFi Scan API
compileSdk 36 · minSdk 24 · AGP 8.13 · Gradle 8.14.3 · JDK 17+

## 빌드 / 실행

```bash
cd android
# (필요 시) local.properties 에 sdk.dir 과 MAPS_API_KEY 설정
./gradlew assembleDebug          # APK 생성: app/build/outputs/apk/debug/
./gradlew installDebug           # 연결된 기기에 설치
```

### Google Maps 키 (선택)
`android/local.properties` 에 아래를 추가하면 지도 배경이 활성화된다. 없으면 레이더 전용으로 동작:
```
MAPS_API_KEY=발급받은_API_KEY
```
키는 Google Cloud Console → Maps SDK for Android 에서 발급.

## 권한
- `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` (Android 12+)
- `ACCESS_FINE_LOCATION` (무선 스캔에 OS 필수)
- `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `VIBRATE`

## 기술적 한계 (반드시 이해)
- 폰은 광대역 스펙트럼 분석기가 아니라, **라디오가 들을 수 있는 신호만** 감지:
  BLE 광고 기기, WiFi AP. **꺼짐/비행기모드/셀룰러 전용 기기는 감지 불가.**
- iOS 는 주변 WiFi 스캔을 OS 가 금지 → 본 앱은 Android 전용.
- RSSI 는 **상대 세기**이며 "1m" 등 거리는 환경에 따라 달라지는 **근사치**.
- 안테나 1개로는 신호원의 방향을 직접 계산할 수 없어, 폰 회전으로 세기 최대 방향을 찾는다.

## 설계 문서
`docs/2026-11-16-examguard-design.md`

## 저작권 및 라이선스
Copyright © 2026 Roy Kim · Telelian

이 프로그램은 자유 소프트웨어입니다. [GNU General Public License v3.0](LICENSE) 조건에 따라
자유롭게 재배포·수정할 수 있습니다. 이 프로그램은 유용하게 쓰이기를 바라며 배포되지만,
**상품성이나 특정 목적 적합성에 대한 보증 없이 "있는 그대로" 제공**됩니다. 자세한 내용은 `LICENSE` 파일을 참조하세요.

- 라이선스: **GPL-3.0** (파생 저작물도 GPL-3.0으로 공개해야 함)
- 패키지 ID: `kr.telelian.examguard`
