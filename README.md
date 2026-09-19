# AlarmCard

카드 형식으로 **주식 시세 · 버스 도착정보 · 환율**을 한 화면에 모아 보는 안드로이드 앱.  
카드를 자유롭게 추가·삭제하고, 우상단 새로고침 버튼을 누르면 등록된 카드들이 병렬로 갱신됩니다.  
앱(또는 홈 화면)이 **active(foreground) 상태가 될 때 자동으로 갱신**됩니다.

- **주식**: 국내(KOSPI/KOSDAQ) + 해외(미국, 예: `AAPL.O`) — 네이버 증권 모바일 페이지 크롤링
- **버스**: 서울/수도권 정류장 도착 정보 — 네이버 지도 대중교통 XHR 엔드포인트 크롤링
- **환율**: 주요 통화쌍 (USD/KRW, JPY/KRW, EUR/KRW …) — 네이버 증권 환율 페이지 크롤링

> 데이터 취득은 공식 API 대신 네이버 페이지를 파싱하는 방식을 사용합니다.  
> **개인 학습/개인용** 목적 범위로 사용해 주세요. 페이지 구조가 바뀌면 파서 업데이트가 필요할 수 있습니다.  
> 크롤러는 `__NEXT_DATA__` JSON 파싱을 우선 사용하고, 실패 시 CSS 셀렉터 fallback으로 동작하도록 이중화되어 있습니다.

---

## 프로젝트 구성

```
app/src/main/java/com/jpark/alarmcard/
├─ AlarmCardApp.kt          # HiltAndroidApp 진입점
├─ domain/model/            # Card sealed interface (StockCard, BusCard, FxCard)
├─ data/
│   ├─ local/               # Room Entity/DAO/Database
│   ├─ remote/              # OkHttp/HttpClient, __NEXT_DATA__ 파서, Json 확장
│   ├─ crawler/             # NaverStock/Fx/MapBusCrawler
│   └─ CardRepository.kt    # 병렬 새로고침 등 카드 CRUD
├─ di/AppModule.kt          # Hilt provides
└─ ui/                      # Compose UI (Home / Add / ViewModel / Nav)
```

## 기술 스택

- Kotlin 2.0 + Jetpack Compose (Material3)
- Hilt (DI) + KSP
- Coroutines / Flow
- OkHttp + Jsoup + kotlinx.serialization  ← 크롤링 파이프라인
- Room (카드 및 마지막 값 캐시)
- Navigation-Compose

---

## 폰에서 바로 다운로드 & 설치 (권장)

`main` 브랜치에 push 될 때마다 GitHub Actions 가 debug APK 를 빌드해 **GitHub Release `latest`** 태그에 붙여둡니다.  
따라서 폰 브라우저에서 아래 링크를 눌러 바로 설치할 수 있습니다:

```
https://github.com/jpark0409/alarmcard/releases/latest/download/alarmcard-debug.apk
```

- 안드로이드 “출처를 알 수 없는 앱 설치 허용” 이 필요할 수 있습니다.
- `latest` APK는 저장소에 포함된 고정 개발용 debug keystore로 서명됩니다. 따라서 앞으로 같은 링크에서 받은 APK는 기존 앱을 삭제하지 않고 업데이트 설치할 수 있습니다.
- 과거에 다른 서명키로 빌드된 APK가 이미 설치되어 있다면 Android 보안 정책상 최초 1회는 삭제 후 재설치가 필요합니다. 그 이후부터는 동일 서명으로 업데이트됩니다.
- 처음 push 후 첫 릴리즈가 만들어지기까지 5분 정도 걸립니다 (`Release APK` 워크플로우).
- 특정 버전을 배포하고 싶으면 `git tag v1.0.0 && git push --tags` 형태로 태그를 push 하세요. 해당 태그의 정식 릴리즈도 생성됩니다.

## 빌드 (GitHub Actions에서 APK 자동 산출)

이 저장소는 **push/PR 될 때마다 GitHub Actions가 debug APK를 만들어 아티팩트로 업로드**합니다.  
로컬에 Android SDK를 설치하지 않아도 됩니다.

1. 이 저장소를 GitHub에 push (기본 브랜치: `main` 또는 `master`).
2. GitHub의 **Actions** 탭 → `Android CI - Build APK` 워크플로우 실행 확인.
3. 완료 후 실행 상세 페이지 하단의 **Artifacts → `alarmcard-debug-apk`** 다운로드.
4. 압축 안의 `app-debug.apk` 파일을 안드로이드 기기에 설치. (개발자 옵션: **알 수 없는 앱 설치** 허용 필요)

수동 실행: Actions 탭에서 워크플로우를 열고 **Run workflow** 클릭.


## (선택) 로컬 빌드

로컬에서 빌드하려면 아래가 필요합니다.

- **JDK 17** (Temurin 권장)
- **Android SDK** (platform 34, build-tools 34.0.0)
- 환경변수 `ANDROID_HOME` 설정 및 `sdkmanager --licenses` 수락

빌드:
```bash
gradle wrapper --gradle-version 8.9 --distribution-type bin   # 최초 1회
./gradlew :app:assembleDebug
# 산출물: app/build/outputs/apk/debug/app-debug.apk
```

로컬/CI debug APK는 `app/debug.keystore`를 사용해 서명이 고정됩니다. CI에서는 `GITHUB_RUN_NUMBER`를 `versionCode`로 사용하므로 새 빌드가 기존 설치본보다 낮은 버전으로 간주되는 문제를 줄입니다. 필요하면 수동으로 아래처럼 지정할 수 있습니다.

```bash
./gradlew :app:assembleDebug -PALARM_CARD_VERSION_CODE=123 -PALARM_CARD_VERSION_NAME=0.1.0
```

Windows에서는 `gradlew.bat` 을 사용하세요.

---

## PC에서 실행/테스트

Android 폰이 없어도 PC에서 3가지 방법으로 테스트할 수 있습니다.

### 방법 A — 이 저장소의 자동 스크립트 (Google 공식 Android Emulator) ✅ 검증됨

3단계로 나뉜 스크립트를 순서대로 실행합니다. Windows PowerShell(관리자 아님)에서:

**1) JDK 17 자동 설치 (winget 사용)**
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\install_jdk.ps1
```
→ Eclipse Temurin JDK 17을 `C:\Program Files\Eclipse Adoptium\jdk-17.x.x-hotspot` 에 설치.

**2) SDK + 시스템 이미지 + AVD + 에뮬레이터 + APK 설치 (한 번에)**
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\setup_emu.ps1
```
→ 자동으로:
1. `%USERPROFILE%\AndroidSDK\cmdline-tools\latest` 에 Android SDK cmdline-tools 다운로드 (curl 사용, 재시도 지원)
2. 라이선스 파일 자동 생성 (프롬프트 없음)
3. `platform-tools`, `emulator`, `platforms;android-34`, `system-images;android-34;google_apis;x86_64` 설치
4. `alarmcard_avd` AVD 생성 (Pixel 5, Android 14)
5. 에뮬레이터 부팅 → APK 자동 설치 → 앱 실행

최초 다운로드: 약 2~3GB (JDK 160MB + cmdline-tools 150MB + emulator/system-image 등). BIOS 가상화(Intel VT-x/AMD-V) 활성화 필요.

로그: `scripts\setup_emu.log` 로 진행 상황 확인 가능.

**3) 이미 설치된 뒤 재실행할 때는**
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run_emu2.ps1
```
→ SDK 재다운로드 없이 AVD 기동 + APK 재설치 + 앱 실행 (약 30초~1분).

**APK 파일이 없을 때**는 먼저 다운로드:
```powershell
# 최신 성공한 CI run에서 자동 다운로드
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\download.ps1
```

### 방법 B — Windows 11 WSA (Windows Subsystem for Android)

1. Microsoft Store에서 **Amazon Appstore** 설치 (자동으로 WSA도 함께 설치됨)
2. **Windows Subsystem for Android 설정** 앱을 열고 **개발자 모드 켜기**
3. IP:포트 표시 확인 (예: `127.0.0.1:58526`)
4. 커맨드에서 `adb connect 127.0.0.1:58526` 후 `adb install app-debug.apk`

WSA 부팅 후 `adb`가 잡히면 스크립트 방법 A의 마지막 두 단계 (install/am start)만 그대로 동작합니다.

### 방법 C — 다른 서드파티 에뮬레이터

- **Genymotion Personal**: VirtualBox 기반. Drag&drop APK 지원.
- **BlueStacks / NoxPlayer / LDPlayer**: 게임용에 특화됐지만 일반 앱도 APK 파일을 그냥 드래그하면 설치됨. 성능 좋음.
- **Anbox / Waydroid** (Linux): 컨테이너 기반, 가볍고 빠름.

---

## 사용 방법

1. 앱 실행 → 홈에서 우상단 **＋** 버튼으로 카드 추가.
   - **주식**: 종목명/티커로 검색 후 선택 (`삼성전자`, `AAPL` 등)
   - **버스**: 정류장명 검색 → 정류장 선택 → 원하는 노선 체크 → 카드 추가
   - **환율**: 프리셋에서 통화쌍 선택
2. 홈 화면에서 각 카드가 실시간에 가까운 값으로 표시됨.
3. 우상단 **🔄 새로고침**을 누르면 등록된 카드 전체를 병렬 갱신.
4. 앱을 백그라운드에 뒀다가 다시 켜서 홈 화면으로 복귀하면 **자동 새로고침** (최소 간격 15초).

---

## 데이터 소스 요약

| 카드 | 소스 URL(예시) | 파싱 우선순위 |
|---|---|---|
| 국내주식 | `https://m.stock.naver.com/domestic/stock/{code}/total` | `__NEXT_DATA__` → CSS |
| 해외주식 | `https://m.stock.naver.com/worldstock/stock/{symbol}/total` (예: `AAPL.O`) | `__NEXT_DATA__` → CSS |
| 환율 | `https://m.stock.naver.com/marketindex/exchange/{FX_XXXKRW}` | `__NEXT_DATA__` → CSS |
| 버스 검색 | `https://map.naver.com/p/api/search/allSearch?query=...&type=bus_station` (+ 대체 URL) | JSON 재귀 스캔 |
| 버스 도착 | `https://pubtrans.map.naver.com/api/station/{stationId}` (+ 대체 URL) | JSON 재귀 스캔 |

---

## 알려진 이슈 / 한계

- 네이버 페이지 스키마가 변경되면 파싱이 실패할 수 있습니다. 실패 카드는 하단에 ⚠ 경고 문구가 표시되지만 앱은 죽지 않습니다.
- 네이버 지도의 대중교통 XHR URL은 지역/버전에 따라 상이할 수 있습니다. 크롤러는 여러 후보 URL을 순서대로 시도합니다.
- 서울/수도권 정류장 검색이 가장 안정적입니다.

## 라이선스

MIT
