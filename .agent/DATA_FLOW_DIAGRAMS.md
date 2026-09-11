# AlarmCard 데이터 흐름 상세 다이어그램

## 1. 애플리케이션 아키텍처 개요

```
┌─────────────────────────────────────────────────────────────┐
│                      UI Layer (Compose)                      │
│  ┌─────────────┬──────────────┬────────────────┐             │
│  │ MainActivity│  HomeScreen  │  AddCardScreen │             │
│  └──────┬──────┴──────┬───────┴────────┬───────┘             │
│         │             │                │                     │
│         └─────────────┼────────────────┘                     │
│                       │                                      │
│                ┌──────▼────────┐                             │
│                │  MainViewModel│ (StateFlow<List<Card>>)     │
│                └──────┬────────┘                             │
├────────────────────────┼──────────────────────────────────────┤
│                 Domain Layer                                  │
│  ┌──────────────────────▼──────────────────────┐             │
│  │        Card (sealed interface)               │             │
│  │  ├─ StockCard                               │             │
│  │  ├─ BusCard                                 │             │
│  │  └─ FxCard                                  │             │
│  └──────────────────────────────────────────────┘             │
├────────────────────────────────────────────────────────────────┤
│                 Data Layer                                     │
│  ┌──────────────────────────────────────────┐                │
│  │      CardRepository (Singleton)           │                │
│  │  ┌────────────────┬──────────────────┐   │                │
│  │  │  Local Layer   │  Remote Layer    │   │                │
│  │  │                │                  │   │                │
│  │  │ ┌────────────┐ │ ┌──────────────┐ │   │                │
│  │  │ │  Room DB   │ │ │  Crawlers    │ │   │                │
│  │  │ ├CardEntity  │ │ ├─Stock       │ │   │                │
│  │  │ ├CardDao     │ │ ├─Fx          │ │   │                │
│  │  │ └AppDatabase │ │ └─Bus         │ │   │                │
│  │  └────────────┘ │ └──────────────┘ │   │                │
│  │                │  ├─HttpClient     │   │                │
│  │                │  ├─JsonExt        │   │                │
│  │                │  └─NextData       │   │                │
│  │                └──────────────────┘   │                │
│  └──────────────────────────────────────────┘                │
├────────────────────────────────────────────────────────────────┤
│              Background & Notification                         │
│  ┌──────────────────────────────────────────┐                │
│  │      BusAlarmWorker (WorkManager)        │                │
│  │      ↓ NotificationHelper                │                │
│  └──────────────────────────────────────────┘                │
├────────────────────────────────────────────────────────────────┤
│                    External                                    │
│  ├─ 네이버 증권 (주식/환율)                                    │
│  └─ 네이버 지도 (버스 도착정보)                                 │
└────────────────────────────────────────────────────────────────┘
```

---

## 2. 앱 시작 및 초기화 플로우

```
AndroidSystem
    │
    ├─ Application#onCreate()
    │   ↓
    ├─ AlarmCardApp#onCreate() [@HiltAndroidApp]
    │   ├─ Hilt DI 초기화
    │   ├─ WorkManager.setDefaultConfiguration(HiltWorkerFactory)
    │   ├─ Timber.plant(DebugTree) [DEBUG 모드]
    │   └─ NotificationHelper.ensureChannel(this)
    │
    └─ MainActivity#onCreate()
        ├─ setContent { AppNav() }
        └─ MainViewModel 초기화
            ├─ @Inject CardRepository
            ├─ StateFlow<List<Card>> = repo.observeCards()
            │   └─ CardDao.observeAll() [Room 구독]
            └─ _isRefreshing = MutableStateFlow(false)

[UI Rendering]
    ├─ HomeScreen { cards, onRefresh, ... }
    │   ├─ cards StateFlow 구독
    │   ├─ LazyColumn로 카드 목록 렌더링
    │   └─ Scaffold (TopAppBar + FAB)
    │
    └─ AddCardScreen { onStockAdd, onBusAdd, onFxAdd }
        ├─ TabRow (주식, 버스, 환율)
        └─ 각 탭별 검색 UI
```

---

## 3. 카드 새로고침 플로우 (병렬 처리)

```
사용자 액션 (UI)
    │
    ├─ 상황 1: 버튼 클릭
    │   └─ MainViewModel.refresh()
    │       └─ if (!isRefreshing) { isRefreshing = true; ... }
    │
    └─ 상황 2: 화면 재진입
        └─ MainActivity.onResume()
            └─ MainViewModel.onScreenResumed()
                └─ if (now - lastAutoRefresh >= 15초) { refresh() }

[refresh() 내부 로직]
    ├─ viewModelScope.launch {
    │   ├─ _isRefreshing.value = true
    │   └─ CardRepository.refreshAll()
    │       │
    │       └─ coroutineScope { 
    │           ├─ val cards = dao.getAll().map { it.toDomain() }
    │           └─ cards.map { card ->
    │               async { 
    │                   runCatching { refreshCard(card) }
    │               }
    │           }.forEach { it.await() }
    │       }
    │
    └─ } finally { _isRefreshing.value = false }

[refreshCard(card: Card) - 카드 타입별 처리]
    │
    ├─ StockCard
    │   ├─ NaverStockCrawler.fetchQuote(symbol, market)
    │   │   ├─ OkHttp GET 요청
    │   │   ├─ __NEXT_DATA__ JSON 파싱 (요청 종목코드와 일치하는 객체 우선)
    │   │   └─ Fallback: Jsoup CSS 셀렉터
    │   │       └─ StockQuote { name, price, change, changeRate, currency }
    │   └─ card.copy(price, change, changeRate, updatedAt=now, lastError=null)
    │
    ├─ FxCard
    │   ├─ NaverFxCrawler.fetchQuote(code)
    │   │   ├─ OkHttp GET 요청
    │   │   ├─ __NEXT_DATA__ 파싱
    │   │   └─ FxQuote { rate, change, changeRate }
    │   └─ card.copy(rate, change, changeRate, updatedAt=now, lastError=null)
    │
    └─ BusCard
        ├─ NaverMapBusCrawler.fetchArrivals(stationId, cityCode)
        │   ├─ OkHttp GET 요청
        │   ├─ JSON 재귀 스캔
        │   └─ BusDetail { stationName, arrivals[] }
        │       └─ BusArrival { routeId, routeNo, eta1Sec, eta2Sec, ... }
        └─ if (filterRouteIds.isEmpty()) 
               arrivals = detail.arrivals
           else 
               arrivals = detail.arrivals.filter { it.routeId in filterRouteIds }
           └─ card.copy(arrivals, updatedAt=now, lastError=null)

[성공/실패 처리]
    │
    ├─ 성공
    │   └─ dao.upsert(updated.toEntity())
    │       ├─ CardEntity 저장 (또는 업데이트)
    │       └─ StateFlow 구독자 알림
    │           └─ HomeScreen 자동 재렌더링
    │
    └─ 실패 (try-catch)
        ├─ Timber.w(t, "refresh failed for ${card.id}")
        └─ failed: Card = card.copy(lastError=errorMessage)
            └─ dao.upsert(failed.toEntity())
                └─ HomeScreen에 ⚠ 경고 표시
```

---

## 4. 카드 추가 플로우 (3가지 타입)

### 4.1 주식 카드 추가

```
AddCardScreen (주식 탭)
    │
    ├─ 사용자 검색 입력
    │   └─ MainViewModel.searchStocks(q)
    │       └─ NaverStockCrawler.search(q)
    │           ├─ OkHttp GET: https://m.stock.naver.com/search/search
    │           └─ JSON 파싱 → List<StockSearchResult>
    │               { symbol, market, name, currency }
    │
    ├─ 검색 결과 목록 표시
    │   └─ LazyColumn { searchResult ->
    │       Text(searchResult.name), Text(searchResult.symbol), ...
    │   }
    │
    └─ 사용자 항목 선택
        └─ MainViewModel.addStock(sel: StockSearchResult)
            ├─ viewModelScope.launch {
            │   └─ CardRepository.addStock(StockCard(...))
            │       ├─ order = dao.maxOrder() + 1
            │       ├─ id = UUID.randomUUID().toString()
            │       ├─ CardEntity stk = StockCard.toEntity()
            │       ├─ dao.upsert(stk)
            │       └─ refreshOne(id)
            │           └─ 최초 시세 가져오기
            │
            └─ HomeScreen에 카드 추가됨
                └─ StateFlow 업데이트 → 렌더링
```

### 4.2 버스 카드 추가

```
AddCardScreen (버스 탭)
    │
    ├─ 사용자 정류장 검색
    │   └─ MainViewModel.searchStations(q)
    │       └─ NaverMapBusCrawler.searchStation(q)
    │           ├─ OkHttp GET: https://map.naver.com/p/api/search/allSearch
    │           └─ JSON 재귀 스캔 → List<BusStationSearchResult>
    │               { stationId, stationName, cityCode }
    │
    ├─ 정류장 목록 표시
    │   └─ LazyColumn { station ->
    │       Text(station.stationName), ...
    │   }
    │
    ├─ 사용자 정류장 선택
    │   └─ MainViewModel.previewStationArrivals(stationId, cityCode)
    │       └─ NaverMapBusCrawler.fetchArrivals(stationId, cityCode)
    │           └─ BusDetail { arrivals[] }
    │               └─ 모든 노선 표시
    │
    ├─ 사용자 필터링할 노선 선택 (체크박스)
    │   └─ routeIds: List<String> 선택
    │
    └─ 카드 추가
        └─ MainViewModel.addBus(st, routeIds)
            ├─ viewModelScope.launch {
            │   └─ CardRepository.addBus(BusCard(..., filterRouteIds=routeIds))
            │       ├─ order, id 계산
            │       ├─ dao.upsert()
            │       └─ refreshOne() → 필터된 노선 도착정보만 가져오기
            │
            └─ HomeScreen에 카드 추가됨
```

### 4.3 환율 카드 추가

```
AddCardScreen (환율 탭)
    │
    ├─ 프리셋 목록 로드
    │   └─ MainViewModel.listFxPresets()
    │       └─ NaverFxCrawler.listAvailable()
    │           ├─ 내부 프리셋 배열 반환
    │           └─ List<FxQuote> { code, base, quote, ... }
    │               예: FX_USDKRW, FX_JPYKRW, FX_EURKRW, ...
    │
    ├─ 프리셋 드롭다운 또는 목록 표시
    │   └─ Dropdown { quote -> quote.code }
    │
    └─ 사용자 선택
        └─ MainViewModel.addFx(q: FxQuote)
            ├─ viewModelScope.launch {
            │   └─ CardRepository.addFx(FxCard(...))
            │       ├─ parseFxCode(q.code) → (base="USD", quote="KRW")
            │       ├─ order, id 계산
            │       ├─ dao.upsert()
            │       └─ refreshOne() → 현재 환율 가져오기
            │
            └─ HomeScreen에 카드 추가됨
```

---

## 5. 버스 알림 플로우

```
사용자 버스 카드 UI에서 알림 활성화
    │
    └─ MainViewModel.setBusAlarm(cardId, enabled=true, minutesBefore=3)
        └─ viewModelScope.launch {
            ├─ CardRepository.setBusAlarm(cardId, enabled, minutesBefore)
            │   ├─ entity = dao.getById(cardId)
            │   ├─ entity.alarmEnabled = true
            │   ├─ entity.alarmMinutesBefore = 3
            │   ├─ entity.alarmLastFiredAt = 0L (리셋, 활성화 시)
            │   └─ dao.upsert(entity)
            │
            └─ rescheduleBusAlarmWorker()
                ├─ repo.hasActiveBusAlarm() 확인
                └─ if (true) {
                    BusAlarmWorker.scheduleNext(ctx, delaySec=5)
                } else {
                    BusAlarmWorker.cancel(ctx)
                }
        }

[WorkManager 스케줄링]
    │
    └─ BusAlarmWorker.scheduleNext(ctx, delaySec=5)
        ├─ OneTimeWorkRequest 생성
        │   ├─ setInitialDelay(5초)
        │   ├─ setBackoffCriteria(exponential)
        │   └─ addTag("bus_alarm")
        └─ WorkManager.enqueueUniqueWork("bus_alarm", ...)

[BusAlarmWorker 실행 (백그라운드)]
    │
    └─ doWork()
        ├─ CardRepository.refreshBusAlarmsAndSelectFireable()
        │   │
        │   ├─ Step 1: 활성 버스카드들 새로고침
        │   │   └─ foreach (entity where type==BUS && alarmEnabled) {
        │   │       refreshCard(entity.toDomain())
        │   │   }
        │   │
        │   ├─ Step 2: 발송 대상 선별
        │   │   └─ foreach (entity where type==BUS && alarmEnabled) {
        │   │       bc = entity.toDomain() as BusCard
        │   │       threshold = bc.alarmMinutesBefore * 60 (초)
        │   │       hit = bc.arrivals.find { 
        │   │           it.eta1Sec != null && 
        │   │           it.eta1Sec in 0..threshold 
        │   │       }
        │   │       
        │   │       if (hit != null) {
        │   │           nowMs = System.currentTimeMillis()
        │   │           if (nowMs - bc.alarmLastFiredAt > 5분) {
        │   │               fire.add(bc)
        │   │               dao.upsert(entity.copy(alarmLastFiredAt=nowMs))
        │   │           }
        │   │       }
        │   │   }
        │   │
        │   └─ return fire: List<BusCard>
        │
        ├─ Step 3: 알림 발송
        │   └─ for (bc in fire) {
        │       NotificationHelper.showBusAlarm(ctx, bc)
        │   }
        │
        └─ Step 4: 자체 재스케줄
            └─ scheduleNext(ctx, delaySec=10) 호출
                └─ 다음 주기 예약
```

---

## 6. StateFlow 상태 관리

```
MainViewModel
    │
    ├─ cards: StateFlow<List<Card>>
    │   ├─ Source: CardRepository.observeCards()
    │   │   └─ CardDao.observeAll().map { list ->
    │   │       list.map { entity -> entity.toDomain() }
    │   │   }
    │   ├─ Subscription: HomeScreen
    │   │   └─ cards.collectAsState()
    │   └─ Update Trigger: CardDao.upsert() 호출 시
    │       └─ HomeScreen 자동 재렌더링
    │
    ├─ isRefreshing: StateFlow<Boolean>
    │   ├─ Subscription: HomeScreen (TopAppBar 로딩 표시)
    │   ├─ true: CardRepository.refreshAll() 실행 중
    │   └─ false: 완료 또는 실패
    │
    └─ lastAutoRefresh: Long (비공개)
        └─ onScreenResumed() 호출 시 체크
            └─ 15초 미만 중복 호출 무시
```

---

## 7. 데이터 모델 변환 흐름

```
CardEntity (Room)
    │
    ├─ type: String (TYPE_STOCK / TYPE_BUS / TYPE_FX)
    ├─ id, order, updatedAt, lastError (공통)
    └─ 카드 타입별 필드들
        ├─ STOCK: symbol, market, name, price, change, changeRate, currency
        ├─ BUS: stationId, stationName, cityCode, filterRouteIds (JSON), arrivals (JSON), alarm*
        └─ FX: code, base, quote, rate, change, changeRate

    ↓ toDomain()
    
Card (sealed interface)
    │
    ├─ StockCard(id, order, updatedAt, lastError, symbol, market, name, price, ...)
    ├─ BusCard(id, order, updatedAt, lastError, stationId, stationName, arrivals[], alarm*)
    └─ FxCard(id, order, updatedAt, lastError, code, base, quote, rate, ...)

    ↓ (UI에서 when으로 처리)
    
Composable Rendering
    │
    ├─ StockCardUI { ... card.price ... }
    ├─ BusCardUI { ... card.arrivals ... }
    └─ FxCardUI { ... card.rate ... }

    ↓ (사용자 수정)
    
Updated Card (예: price 변경)
    │
    ├─ card.copy(price = newPrice, updatedAt = now)
    │
    ↓ toEntity()
    
Updated CardEntity
    │
    └─ dao.upsert() → DB 저장
```

---

## 8. 에러 처리 및 복구

```
refreshCard(card: Card) 실행
    │
    ├─ try {
    │   ├─ 크롤러 호출 (fetch/search)
    │   ├─ 데이터 파싱
    │   └─ updated = card.copy(..., updatedAt, lastError=null)
    │       └─ dao.upsert(updated)
    │
    └─ } catch (t: Throwable) {
        ├─ Timber.w(t, "refresh failed for ${card.id}")
        ├─ failed = card.copy(lastError=t.message ?: "error")
        └─ dao.upsert(failed)
            └─ HomeScreen에 경고 아이콘 표시
                └─ lastError 텍스트 표시
    }

[UI에서의 에러 표시]
    │
    └─ if (card.lastError != null) {
        Icon(Icons.Default.Warning, "Error", tint=Red)
        Text(card.lastError, color=Red)
    }
```

---

## 9. 네비게이션 플로우

```
NavController
    │
    ├─ Route: "home"
    │   ├─ Navigation destination: HomeScreen
    │   ├─ Actions:
    │   │   ├─ FAB 클릭 → navigate("add")
    │   │   ├─ 카드 클릭 → 상세 화면 (선택)
    │   │   └─ 카드 삭제 → viewModel.deleteCard(id)
    │   └─ State: cards, isRefreshing, refresh()
    │
    └─ Route: "add"
        ├─ Navigation destination: AddCardScreen
        ├─ Arguments: (없음)
        ├─ Actions:
        │   ├─ 주식 추가 → viewModel.addStock() → popBackStack()
        │   ├─ 버스 추가 → viewModel.addBus() → popBackStack()
        │   ├─ 환율 추가 → viewModel.addFx() → popBackStack()
        │   └─ 뒤로가기 → popBackStack()
        └─ State: 검색 결과, 선택된 항목

[App 시작]
    │
    └─ startDestination = "home"
        └─ HomeScreen 먼저 렌더링
```

---

이 다이어그램들은 AlarmCard의 전체 데이터 흐름과 각 컴포넌트 간의 관계를 시각화합니다.  
신규 agent가 특정 기능을 수정할 때 이 흐름을 참고하여 영향 범위를 파악할 수 있습니다.