# AlarmCard 프로젝트 전체 분석 문서

## 프로젝트 개요

**프로젝트명**: AlarmCard  
**설명**: 카드 형식으로 주식 시세, 버스 도착정보, 환율을 한 화면에 모아 보는 안드로이드 앱  
**기술 스택**: Kotlin 2.0, Jetpack Compose, Hilt, Room, OkHttp, Jsoup, Coroutines

---

## 1. 핵심 목표 & 기능

### 주요 기능
1. **주식 카드**: 국내(KOSPI/KOSDAQ) + 해외(US: AAPL.O 형식) 종목 시세 및 해외 지수(.INX 등) 조회
2. **버스 카드**: 서울/수도권 정류장 도착 정보 조회 및 알림 기능
3. **환율 카드**: 주요 통화쌍(USD/KRW, JPY/KRW, EUR/KRW 등) 실시간 환율
4. **카드 관리**: 카드 추가/삭제/재정렬, 병렬 새로고침, 자동 갱신(화면 활성화 시)
5. **버스 알림**: 도착 N분 전 알림 설정 (WorkManager 기반, **차량 고유 번호(plateNo) 추적을 통한 중복 방지 및 연속 버스 알림 지원**)
6. **주식 알림**: 특정 가격 도달 또는 변동률 임계치 도달 시 알림 (WorkManager 기반)
7. **알림 자동 활성화**: 주 단위(요일) 및 특정 시각에 알림을 자동으로 켜주는 기능 (지정 시각에 Worker를 즉시 예약하여 안정성 강화)

### 데이터 소스
- **주식**: 야후 파이낸스 API (`v8/finance/chart` 기반 시세 조회 및 `v1/finance/search` 기반 종목 검색)
- **환율**: 네이버 증권 모바일 페이지 크롤링 (`__NEXT_DATA__` JSON 파싱 우선, CSS 셀렉터 Fallback)
- **버스**: 네이버 지도 대중교통 XHR 엔드포인트

---

## 2. 프로젝트 구조 (명칭 체계)

```
app/src/main/java/com/jpark/alarmcard/
├── AlarmCardApp.kt                    # @HiltAndroidApp 진입점
├── domain/
│   └── model/
│       ├── Card.kt                    # sealed interface: StockCard, BusCard, FxCard
│       └── AutoEnableSchedule.kt      # 자동 활성화 요일 bitmask 및 다음 실행 시각 계산
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt             # Room Database
│   │   ├── CardDao.kt                 # Room DAO (CRUD)
│   │   └── CardEntity.kt              # Room Entity + toDomain()/toEntity() 확장
│   ├── remote/
│   │   ├── Http.kt                    # HTTP 유틸리티
│   │   ├── HttpClient.kt              # OkHttp 클라이언트 설정
│   │   ├── JsonExt.kt                 # 직렬화 확장 함수
│   │   └── NextData.kt                # __NEXT_DATA__ JSON 파싱
│   ├── crawler/
│   │   ├── YahooFinanceCrawler.kt      # 주식 시세 크롤러 (야후 파이낸스 API 기반)
│   │   ├── NaverFxCrawler.kt           # 환율 크롤러
│   │   └── NaverMapBusCrawler.kt       # 버스 도착정보 크롤러
│   └── CardRepository.kt              # 데이터 레이어 Facade (병렬 새로고침, 카드 CRUD, 알림 로직)
├── di/
│   └── AppModule.kt                   # Hilt @Provides / Singleton 주입
├── notify/
│   ├── BusAlarmWorker.kt              # WorkManager 기반 버스 알림 Worker
│   ├── StockAlarmWorker.kt            # WorkManager 기반 주식 알림 Worker
│   ├── AutoEnableWorker.kt            # WorkManager 기반 알림 자동 활성화 Worker (선택 요일 기반 예약)
│   ├── AutoEnableReceiver.kt          # 부팅 시 알림 자동 활성 스케줄 복구 Receiver
│   ├── NotificationHelper.kt          # 채널 설정 및 알림 발송 유틸
│   └── AlarmDismissReceiver.kt        # 알림창에서 알림 해제 처리 (버스/주식 공통)
└── ui/
    ├── MainActivity.kt                # Activity 진입점 + Lifecycle 콜백
    ├── MainViewModel.kt               # ViewModel (상태 관리 + UI 이벤트)
    ├── HomeScreen.kt                  # 카드 목록 화면 (알림 설정 UI 포함)
    ├── AddCardScreen.kt               # 카드 추가 화면 (주식/버스/환율 검색)
    └── AppNav.kt                      # Navigation 라우팅
```

---

## 3. 데이터 흐름 (플로우)

### 3.1 앱 시작 플로우

```
AlarmCardApp.onCreate()
  ↓
Hilt DI 초기화 (@HiltAndroidApp)
  ↓
MainActivity 실행
  ↓
MainViewModel 초기화 (StateFlow<List<Card>> 구독)
  ↓
CardRepository.observeCards() → Room DAO → StateFlow 방출
  ↓
HomeScreen 렌더링 (현재 카드 목록)
```

### 3.2 카드 새로고침 플로우

```
사용자 액션 (버튼 클릭 또는 화면 재진입)
  ↓
MainViewModel.refresh() / onScreenResumed()
  ↓
CardRepository.refreshAll() (또는 refreshOne)
  ↓
coroutineScope { cards.map { async { refreshCard(card) } } } (병렬 실행)
  ↓
각 카드 타입별 크롤러 호출:
  - StockCard → YahooFinanceCrawler.fetchQuote() (야후 파이낸스 API 기반)
  - FxCard → NaverFxCrawler.fetchQuote()
  - BusCard → NaverMapBusCrawler.fetchArrivals()
  ↓
성공: CardEntity 업데이트 (updatedAt, price/rate/arrivals, lastError=null)
실패: CardEntity 업데이트 (lastError=에러메시지)
  ↓
CardDao.upsert() → Room DB 저장
  ↓
StateFlow 구독자들에게 변경 알림 (HomeScreen 자동 업데이트)
```

### 3.3 카드 추가 플로우

**주식 카드 추가**:
```
AddCardScreen (검색 UI)
  ↓
MainViewModel.searchStocks(q) → YahooFinanceCrawler.search() (야후 파이낸스 Search API)
  ↓
사용자 선택 (StockSearchResult)
  ↓
MainViewModel.addStock(sel)
  ↓
CardRepository.addStock()
  ├─ 새 UUID 생성 + order 계산
  ├─ CardDao.upsert()
  └─ refreshOne() → 최초 시세 가져오기
  ↓
HomeScreen 에 카드 추가됨
```

**버스 카드 추가**:
```
AddCardScreen (버스 검색 UI)
  ↓
MainViewModel.searchStations(q) → NaverMapBusCrawler.searchStation()
  ↓
사용자 정류장 선택 (BusStationSearchResult)
  ↓
MainViewModel.previewStationArrivals() → 해당 정류장 노선 미리보기
  ↓
사용자 필터링할 노선 체크
  ↓
MainViewModel.addBus(st, routeIds)
  ↓
CardRepository.addBus()
  ├─ 새 UUID 생성 + order 계산
  ├─ filterRouteIds 저장
  ├─ CardDao.upsert()
  └─ refreshOne() → 최초 도착정보 가져오기
  ↓
HomeScreen 에 카드 추가됨
```

**환율 카드 추가**:
```
AddCardScreen (환율 프리셋 UI)
  ↓
MainViewModel.listFxPresets() → NaverFxCrawler.listAvailable()
  ↓
사용자 통화쌍 선택 (FxQuote)
  ↓
MainViewModel.addFx(q)
  ↓
CardRepository.addFx()
  ├─ FX_USDKRW 형식 코드 파싱 (USD, KRW)
  ├─ CardDao.upsert()
  └─ refreshOne() → 최초 환율 가져오기
  ↓
HomeScreen 에 카드 추가됨
```

### 3.4 알림 플로우 (버스/주식)

**버스 알림**:
```
사용자 버스 카드에서 "알림 설정" 활성화
  ↓
MainViewModel.setBusAlarm(cardId, enabled=true, minutesBefore=3)
  ↓
CardRepository.setBusAlarm()
  ├─ CardEntity 업데이트 (alarmEnabled=true, alarmMinutesBefore=3)
  ├─ CardDao.upsert()
  └─ rescheduleBusAlarmWorker() 호출

rescheduleBusAlarmWorker() → BusAlarmWorker 실행 (주기적)
  ↓
CardRepository.refreshBusAlarmsAndSelectFireable()
  ├─ 모든 활성 버스카드 새로고침 (차량 번호 plateNo 포함)
  ├─ eta1Sec <= alarmMinutesBefore*60 조건 확인
  ├─ 차량 번호 비교: (새로운 차량인 경우 즉시 발송) OR (동일 차량인 경우 5분 경과 확인)
  └─ 발송 대상 리스트 반환 및 마지막 발송 차량 정보 업데이트

NotificationHelper.notifyBusArrival() 발송
```

**주식 알림**:
```
사용자 주식 카드에서 "알림 아이콘" 클릭 및 가격/변동률 설정
  ↓
MainViewModel.setStockAlarm(id, enabled=true, price, rate)
  ↓
CardRepository.setStockAlarm()
  ├─ CardEntity 업데이트 (alarmEnabled=true, 임계치 저장)
  └─ rescheduleStockAlarmWorker() 호출

StockAlarmWorker 실행 (약 10분 주기)
  ↓
CardRepository.refreshStockAlarmsAndSelectFireable()
  ├─ 활성 주식카드 새로고침
  ├─ 가격 도달 또는 변동률 임계치 초과 여부 확인
  └─ 중복 방지 (최근 30분 이내 발송 제외)

NotificationHelper.notifyStockAlarm() 발송
```

**알림 자동 활성화 (Auto-Enable)**:
```
사용자 카드에서 "톱니바퀴" 아이콘 클릭 및 요일/시간 설정
  ↓
MainViewModel.setAutoEnable(id, enabled, days, time)
  ↓
CardRepository.setAutoEnable() → DB 저장 (요일/시각 유효성 보정)
  ↓
AutoEnableWorker.scheduleNext() → 선택된 다음 요일/시각으로 WorkManager 등록 (OneTimeWorkRequest + InitialDelay)
  ↓
지정된 시각에 AutoEnableWorker.doWork() 실행
  ├─ 현재 요일 체크
  ├─ 요일 일치 시 repository.setBusAlarm() 또는 setStockAlarm() 호출하여 활성화
  ├─ 해당 타입의 AlarmWorker(Bus/Stock)를 즉시 예약하여 알람 루틴 시작 보장
  └─ 다음 실행을 위해 scheduleNext() 재귀 호출
```

---

## 4. 핵심 클래스/인터페이스 설명

### 4.1 Domain Layer

#### Card.kt (sealed interface)
```
Card (sealed interface)
├── StockCard: symbol, market, name, price, change, changeRate, currency, alarmEnabled, alarmPriceThreshold, alarmRateThreshold, autoEnabled, autoEnableDays, autoEnableTime
├── BusCard: stationId, stationName, filterRouteIds, arrivals[plateNo 포함], alarmEnabled, alarmMinutesBefore, alarmLastFiredVehicles, autoEnabled, autoEnableDays, autoEnableTime
└── FxCard: code, base, quote, rate, change, changeRate, autoEnabled, autoEnableDays, autoEnableTime
```

---

### 4.2 Data Layer

#### CardRepository (Singleton)
**책임**: 데이터 접근 통합 및 알림 로직 핵심 제어

**핵심 메서드**:
- `observeCards()`: StateFlow<List<Card>> 반환 (DAO 구독)
- `refreshAll()`: 모든 카드 병렬 갱신
- `setBusAlarm()` / `setStockAlarm()`: 알림 설정 저장
- `refreshBusAlarmsAndSelectFireable()`: 버스 알림 대상 추출
  - `refreshStockAlarmsAndSelectFireable()`: 주식 알림 대상 추출
- `exportToJson()` / `importFromJson(jsonStr)`: JSON 형식을 이용한 카드 데이터 백업 및 복구
- `CardEntity.decode(str)` / `CardEntity.encode(list)`: 버스 도착 정보 등 복합 데이터를 JSON으로 직렬화하여 DB에 저장

---

### 4.5 알림 & Worker

#### BusAlarmWorker & StockAlarmWorker
- WorkManager를 사용하여 주기적으로 시세를 체크하고 알림 조건을 판정합니다.
- `BusAlarmWorker`: 1분 주기 (스스로 재예약 방식)
- `StockAlarmWorker`: 약 10분 주기

#### NotificationHelper
- 앱 전용 알림 채널 관리 및 실제 시스템 알림 발송을 담당합니다.
- 버스 도착 알림 및 주식 시세 알림(가격/변동률)을 지원합니다.

#### YahooFinanceCrawler
- 야후 파이낸스 API를 사용하여 주식 시세 조회 및 종목 검색을 수행합니다.
- **시세 조회**: `v8/finance/chart` 엔드포인트를 사용하여 실시간 가격 및 등락 정보를 가져옵니다. (기존 `v1/quote` 404 문제 해결)
- **종목 검색**: `v1/finance/search` 엔드포인트를 사용하며, 한글 검색 시 400 에러를 방지하기 위해 쿼리 인코딩 및 종목코드 기반 Fallback 로직이 적용되어 있습니다. 한국 주식의 경우 검색어(한글)를 종목명으로 우선 매핑합니다.

#### AlarmDismissReceiver
- 알림의 '알람 끄기' 액션 클릭 시 트리거됩니다.
- 해당 카드의 `alarmEnabled`를 `false`로 변경하여 이후 알림을 중단합니다.

---

이 문서는 AlarmCard 프로젝트의 전체 구조, 흐름, 기술 스택을 정리한 것입니다.  
해외 주식/지수 지원 및 주식 알림 기능이 추가된 최신 상태를 반영하고 있습니다.
