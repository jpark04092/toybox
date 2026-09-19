# AlarmCard 파일 구조 맵

## 전체 디렉토리 트리

```
alarmcard/ (ROOT)
├── .gitignore
├── build.gradle.kts                     # Root Gradle 설정
├── gradle.properties
├── settings.gradle.kts
├── README.md                            # 프로젝트 설명서
├── PUSH_TO_GITHUB.md
│
├── gradle/
│   ├── libs.versions.toml               # 라이브러리 버전 정의
│   └── wrapper/
│
├── app/
│   ├── build.gradle.kts                 # App 모듈 Gradle (JVM 17, Compose, Hilt, KSP)
│   ├── proguard-rules.pro
│   │
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml      # (자동 생성, 일반적으로 수정 최소)
│       │   │
│       │   ├── java/com/jpark/alarmcard/
│       │   │   ├── AlarmCardApp.kt                          # @HiltAndroidApp
│       │   │   │
│       │   │   ├── domain/
│       │   │   │   └── model/
│       │   │   │       └── Card.kt                          # sealed interface + data classes
│       │   │   │
│       │   │   ├── data/
│       │   │   │   ├── local/
│       │   │   │   │   ├── AppDatabase.kt                   # Room Database
│       │   │   │   │   ├── CardDao.kt                       # DAO CRUD
│       │   │   │   │   ├── CardEntity.kt                    # Entity + 변환 확장
│       │   │   │   │   └── Migrations.kt (if exists)
│       │   │   │   │
│       │   │   │   ├── remote/
│       │   │   │   │   ├── Http.kt                          # HTTP 유틸리티
│       │   │   │   │   ├── HttpClient.kt                    # OkHttp 설정
│       │   │   │   │   ├── JsonExt.kt                       # JSON 확장 함수
│       │   │   │   │   └── NextData.kt                      # __NEXT_DATA__ 파싱
│       │   │   │   │
│       │   │   │   ├── crawler/
│       │   │   │   │   ├── NaverStockCrawler.kt             # 주식 크롤러
│       │   │   │   │   ├── NaverFxCrawler.kt                # 환율 크롤러
│       │   │   │   │   ├── NaverMapBusCrawler.kt            # 버스 크롤러
│       │   │   │   │   ├── models/
│       │   │   │   │   │   ├── StockQuote.kt                # 주식 Quote DTO
│       │   │   │   │   │   ├── StockSearchResult.kt         # 주식 검색 결과
│       │   │   │   │   │   ├── FxQuote.kt                   # 환율 Quote DTO
│       │   │   │   │   │   ├── BusStationSearchResult.kt    # 버스 정류장 검색
│       │   │   │   │   │   ├── BusDetail.kt                 # 버스 도착정보
│       │   │   │   │   │   └── BusArrival.kt                # 버스 노선 도착정보
│       │   │   │   │   └── exceptions/ (if exists)
│       │   │   │   │
│       │   │   │   └── CardRepository.kt                    # Data Facade (Singleton)
│       │   │   │
│       │   │   ├── di/
│       │   │   │   └── AppModule.kt                         # Hilt @Module
│       │   │   │
│       │   │   ├── notify/
│       │   │   │   ├── BusAlarmWorker.kt                    # WorkManager Worker
│       │   │   │   └── NotificationHelper.kt                # 알림 유틸
│       │   │   │
│       │   │   └── ui/
│       │   │       ├── MainActivity.kt                      # Activity 진입점
│       │   │       ├── MainViewModel.kt                     # ViewModel (상태 관리)
│       │   │       ├── HomeScreen.kt                        # 카드 목록 화면
│       │   │       ├── AddCardScreen.kt                     # 카드 추가 화면
│       │   │       ├── AppNav.kt                            # Navigation 라우팅
│       │   │       ├── components/ (if exists)              # 재사용 Composable
│       │   │       └── theme/ (if exists)                   # Compose 테마
│       │   │
│       │   └── res/
│       │       ├── values/
│       │       │   ├── strings.xml
│       │       │   ├── colors.xml
│       │       │   └── styles.xml
│       │       ├── drawable/
│       │       └── mipmap/                                  # 앱 아이콘
│       │
│       └── test/ 및 androidTest/                             # 테스트 파일
│
├── docs/
│   └── bus_troubleshoot.md              # 버스 API 트러블슈팅 가이드
│
└── scripts/
    ├── *.ps1                            # PowerShell 빌드/설정 스크립트
    ├── *.cmd                            # Batch 프로브 스크립트
    ├── *.js                             # Node.js 크롤링 테스트 (선택)
    └── 기타 설정/테스트 스크립트
```

---

## 핵심 파일별 역할

### 1. 애플리케이션 진입점

| 파일 | 역할 | 핵심 내용 |
|------|------|---------|
| `AlarmCardApp.kt` | @HiltAndroidApp | Hilt 초기화, WorkManager 설정, Timber 로깅 |
| `di/AppModule.kt` | Hilt @Module | DB, HttpClient, Crawlers 싱글톤 주입 |
| `MainActivity.kt` | Activity | UI 렌더링, Lifecycle 콜백 (onResume) |

### 2. 도메인 레이어

| 파일 | 역할 | 핵심 내용 |
|------|------|---------|
| `domain/model/Card.kt` | 데이터 모델 | sealed interface Card, StockCard, BusCard, FxCard |

### 3. 데이터 레이어 - Local (Room)

| 파일 | 역할 | 핵심 내용 |
|------|------|---------|
| `data/local/AppDatabase.kt` | Room Database | @Database 정의, DAO 노출 |
| `data/local/CardDao.kt` | Room DAO | observeAll(), getAll(), upsert(), delete() |
| `data/local/CardEntity.kt` | Room Entity | DB 테이블 정의, toDomain()/toEntity() |

### 4. 데이터 레이어 - Remote (Crawling)

| 파일 | 역할 | 핵심 내용 |
|------|------|---------|
| `data/remote/HttpClient.kt` | OkHttp 설정 | User-Agent, 재시도 정책 |
| `data/remote/Http.kt` | HTTP 유틸 | get(), post() 래퍼 |
| `data/remote/NextData.kt` | __NEXT_DATA__ 파서 | JSON 추출 로직 |
| `data/remote/JsonExt.kt` | 직렬화 확장 | JSON 파싱 헬퍼 |

### 5. 데이터 레이어 - Crawlers

| 파일 | 역할 | 핵심 내용 |
|------|------|---------|
| `data/crawler/NaverStockCrawler.kt` | 주식 크롤링 | search(), fetchQuote() |
| `data/crawler/NaverFxCrawler.kt` | 환율 크롤링 | listAvailable(), fetchQuote() |
| `data/crawler/NaverMapBusCrawler.kt` | 버스 크롤링 | searchStation(), fetchArrivals() |

### 6. 데이터 레이어 - Facade

| 파일 | 역할 | 핵심 내용 |
|------|------|---------|
| `data/CardRepository.kt` | Repository | observeCards(), refreshAll(), addStock/Bus/Fx(), setBusAlarm() |

### 7. UI 레이어 - ViewModel

| 파일 | 역할 | 핵심 내용 |
|------|------|---------|
| `ui/MainViewModel.kt` | ViewModel | cards StateFlow, refresh(), search 헬퍼 |

### 8. UI 레이어 - Screens

| 파일 | 역할 | 핵심 내용 |
|------|------|---------|
| `ui/HomeScreen.kt` | 홈 화면 | 카드 목록, 드래그&드롭, 새로고침 |
| `ui/AddCardScreen.kt` | 추가 화면 | 검색 UI (주식/버스/환율) |
| `ui/AppNav.kt` | 네비게이션 | NavController, 라우트 정의 |

### 9. 알림 & Background

| 파일 | 역할 | 핵심 내용 |
|------|------|---------|
| `notify/BusAlarmWorker.kt` | WorkManager Worker | refreshBusAlarmsAndSelectFireable(), 알림 발송 |
| `notify/NotificationHelper.kt` | 알림 유틸 | ensureChannel(), showBusAlarm() |

---

## 빌드 설정 파일

### gradle/libs.versions.toml
라이브러리 버전 중앙 관리
- Kotlin, Compose, Hilt, Room, OkHttp, Jsoup 등

### app/build.gradle.kts
- compileSdk = 34
- minSdk = 26, targetSdk = 34
- jvmTarget = "17"
- Plugins: kotlin-serialization, hilt, ksp
- Dependencies: Compose, Coroutines, Room, OkHttp, Jsoup 등

---

## 리소스 디렉토리 구조 (res/)

```
res/
├── values/
│   ├── strings.xml          # 문자열 상수 (UI 텍스트)
│   ├── colors.xml           # 색상 정의
│   └── styles.xml           # 스타일 정의
├── drawable/                # 아이콘/이미지
├── mipmap/                  # 앱 런처 아이콘
└── layout/                  # (Compose 사용 시 최소)
```

---

## 스크립트 디렉토리 (scripts/)

### 개발자 편의 도구 (PowerShell/Batch)

| 스크립트 | 목적 |
|---------|------|
| `install_jdk.ps1` | JDK 17 자동 설치 |
| `setup_emu.ps1` | Android Emulator 초기 설정 |
| `run_emu2.ps1` | Emulator 부팅 + APK 재설치 |
| `download.ps1` | 최신 CI 아티팩트 (APK) 다운로드 |
| `check_env.ps1` | 환경 검증 |
| `wait_for_deploy.ps1` | 배포 완료 대기 |
| `probe_*.ps1` | 네이버 페이지 프로브 (크롤링 테스트) |

---

## 주요 설정 파일

| 파일 | 목적 |
|------|------|
| `.gitignore` | Git 무시 목록 |
| `README.md` | 프로젝트 설명서 |
| `app/debug.keystore` | CI/로컬 debug APK 업데이트 설치를 위한 고정 개발용 서명키 |
| `PUSH_TO_GITHUB.md` | 배포 가이드 |
| `gradle.properties` | Gradle 속성 |
| `settings.gradle.kts` | 멀티 모듈 설정 |
| `build.gradle.kts` | Root 빌드 설정 |

---

## 파일 네이밍 규칙 요약

| 타입 | 패턴 | 예시 |
|------|------|------|
| Activity | `Main{Purpose}Activity.kt` | `MainActivity.kt` |
| ViewModel | `{Screen}ViewModel.kt` | `MainViewModel.kt` |
| Screen | `{ScreenName}Screen.kt` | `HomeScreen.kt`, `AddCardScreen.kt` |
| Repository | `{Domain}Repository.kt` | `CardRepository.kt` |
| DAO | `{Entity}Dao.kt` | `CardDao.kt` |
| Database | `{Domain}Database.kt` | `AppDatabase.kt` |
| Crawler | `{Source}{Domain}Crawler.kt` | `NaverStockCrawler.kt` |
| Worker | `{Purpose}Worker.kt` | `BusAlarmWorker.kt` |
| Model/Entity | `{DomainName}.kt` | `Card.kt`, `CardEntity.kt` |
| Utility | `{Function}Helper.kt` 또는 `{Function}Ext.kt` | `NotificationHelper.kt`, `JsonExt.kt` |

---

## 의존성 방향 (Architecture Clean)

```
UI Layer (MainActivity, ViewModel, Screens)
    ↓
Domain Layer (Card sealed interface)
    ↓
Data Layer (Repository)
    ↓
├── Local (Room: DAO, Entity, Database)
└── Remote (Crawlers: Stock, Fx, Bus)
```

**특징**:
- UI ← ViewModel ← Repository ← (DAO + Crawlers)
- 역의존성 없음 (Clean Architecture)
- StateFlow/Flow로 반응형 업데이트

---

## 파일 수정 체크리스트

신규 기능 추가 시 다음 파일들을 검토하세요:

- [ ] `domain/model/Card.kt` - 새 카드 타입 정의 필요?
- [ ] `data/local/CardEntity.kt` - 필드 추가?
- [ ] `data/local/CardDao.kt` - 쿼리 추가?
- [ ] `data/CardRepository.kt` - CRUD 메서드 추가?
- [ ] `data/crawler/*.kt` - 크롤러 업데이트?
- [ ] `ui/MainViewModel.kt` - 이벤트 처리 추가?
- [ ] `ui/{Screen}Screen.kt` - UI 렌더링 수정?
- [ ] `di/AppModule.kt` - 의존성 주입?
- [ ] `notify/*.kt` - 알림 기능 관련?
- [ ] `build.gradle.kts` - 라이브러리 추가?

---

이 문서는 프로젝트의 전체 파일 구조와 각 파일의 역할을 명시합니다.  
신규 agent가 특정 기능 수정 시 어느 파일을 찾아야 하는지 빠르게 파악할 수 있습니다.