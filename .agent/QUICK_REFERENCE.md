# AlarmCard 빠른 참고 가이드 (Quick Reference)

## 신규 Agent가 Task 수행 시 먼저 확인하는 체크리스트

```
1. [ ] 프로젝트 목표 이해 → PROJECT_OVERVIEW.md 섹션 1-2 읽기
2. [ ] 파일 위치 파악 → FILE_STRUCTURE_MAP.md 확인
3. [ ] 데이터 흐름 이해 → DATA_FLOW_DIAGRAMS.md 필요한 부분 읽기
4. [ ] 기존 코드 분석 → 해당 파일들 읽고 패턴 파악
5. [ ] 수정 범위 결정 → 어느 계층(UI/Domain/Data)을 변경할지 판단
6. [ ] 변경 후 영향 범위 점검 → 관련 파일들 모두 업데이트
```

---

## 주요 컴포넌트 빠른 찾기

### 카드 추가 기능 수정 시
- **Domain**: `domain/model/Card.kt` (StockCard, BusCard, FxCard 정의)
- **Data**: `data/CardRepository.kt` (addStock, addBus, addFx 메서드)
- **UI**: `ui/AddCardScreen.kt` (카드 추가 UI)
- **ViewModel**: `ui/MainViewModel.kt` (addStock, addBus, addFx 호출)

### 새로고침 기능 수정 시
- **Data**: `data/CardRepository.kt` (refreshAll, refreshOne, refreshCard)
- **Crawlers**: `data/crawler/Naver*.kt` (fetchQuote, fetchArrivals)
- **ViewModel**: `ui/MainViewModel.kt` (refresh, onScreenResumed)
- **UI**: `ui/HomeScreen.kt` (새로고침 버튼, 로딩 표시)

### 버스 알림 기능 수정 시
- **Worker**: `notify/BusAlarmWorker.kt` (WorkManager 작업)
- **Repository**: `data/CardRepository.kt` (setBusAlarm, refreshBusAlarmsAndSelectFireable)
- **Notification**: `notify/NotificationHelper.kt` (알림 발송)
- **ViewModel**: `ui/MainViewModel.kt` (setBusAlarm 호출)
- **UI**: `ui/HomeScreen.kt` 또는 BusCard Composable (알림 설정 UI)

### 알림 자동 활성화(Auto-Enable) 기능 수정 시
- **Schedule Helper**: `domain/model/AutoEnableSchedule.kt` (요일 bitmask 규칙 및 선택 요일 기반 다음 실행 시각 계산)
- **Worker**: `notify/AutoEnableWorker.kt` (WorkManager 자동 활성화 실행 및 재예약)
- **Receiver**: `notify/AutoEnableReceiver.kt` (부팅 후 자동 활성 스케줄 복구)
- **Worker**: `notify/AutoDisableWorker.kt` (매일 23:59:30 알람 일괄 해제 로직)
- **Repository**: `data/CardRepository.kt` (setAutoEnable)
- **ViewModel**: `ui/MainViewModel.kt` (setAutoEnable 호출 및 Worker 예약 제어)
- **UI**: `ui/HomeScreen.kt` (AutoEnableSetupDialog 및 설정 아이콘)

### 데이터베이스 스키마 수정 시
- **Entity**: `data/local/CardEntity.kt` (필드 추가/삭제)
- **DAO**: `data/local/CardDao.kt` (쿼리 수정)
- **Database**: `data/local/AppDatabase.kt` (버전 증가, Migration 추가)
- **Model**: `domain/model/Card.kt` (toDomain/toEntity 확장 함수 업데이트)

### 크롤링 로직 수정 시
- **Crawler**: `data/crawler/Naver*.kt` (해당 크롤러 파일)
- **HTTP**: `data/remote/Http.kt`, `data/remote/HttpClient.kt` (HTTP 요청 로직)
- **Parser**: `data/remote/NextData.kt` (__NEXT_DATA__ 파싱)
- **Models**: `data/crawler/` 내 Quote/SearchResult DTO들

---

## 자주 사용되는 패턴

### 1. 새로운 데이터 타입 추가 (예: CryptoCard)

**Step 1**: Domain 모델 정의
```kotlin
// domain/model/Card.kt
data class CryptoCard(
    override val id: String,
    override val order: Int,
    override val updatedAt: Long,
    override val lastError: String?,
    val symbol: String,
    val name: String,
    val price: Double? = null,
    val change: Double? = null
) : Card
```

**Step 2**: DB Entity 필드 추가
```kotlin
// data/local/CardEntity.kt
@Entity
data class CardEntity(
    @PrimaryKey val id: String,
    val type: String,  // 기존
    // ... 기타 필드
    val cryptoSymbol: String? = null  // 새로 추가
    // ...
)
```

**Step 3**: toDomain/toEntity 확장 함수 업데이트
```kotlin
// data/local/CardEntity.kt
fun CardEntity.toDomain(): Card = when (type) {
    TYPE_CRYPTO -> CryptoCard(id, order, updatedAt, lastError, cryptoSymbol, cryptoName, cryptoPrice, cryptoChange)
    // ...
}

fun Card.toEntity(): CardEntity = when (this) {
    is CryptoCard -> CardEntity(
        type = TYPE_CRYPTO,
        cryptoSymbol = this.symbol,
        // ...
    )
    // ...
}
```

**Step 4**: Repository에 추가 메서드 구현
```kotlin
// data/CardRepository.kt
suspend fun addCrypto(card: CryptoCard): String {
    val order = dao.maxOrder() + 1
    val id = card.id.ifBlank { UUID.randomUUID().toString() }
    dao.upsert(card.copy(id = id, order = order).toEntity())
    refreshOne(id)
    return id
}

// refreshCard 내부에 when 분기 추가
is CryptoCard -> {
    val q = cryptoCrawler.fetchQuote(card.symbol)
    card.copy(price = q.price, change = q.change, updatedAt = now, lastError = null)
}
```

**Step 5**: ViewModel에 메서드 추가
```kotlin
// ui/MainViewModel.kt
fun addCrypto(sel: CryptoSearchResult) = viewModelScope.launch {
    repo.addCrypto(CryptoCard(...))
}

suspend fun searchCryptos(q: String) = cryptoCrawler.search(q)
```

**Step 6**: UI 추가
```kotlin
// ui/AddCardScreen.kt
// TabRow에 "암호화폐" 탭 추가
// CryptoTab { onCryptoAdd }

// ui/HomeScreen.kt
// when (card) { is CryptoCard -> CryptoCardUI(...) }
```

---

### 2. 새로운 크롤러 추가 (예: BitcoinCrawler)

**파일 생성**: `data/crawler/BitcoinCrawler.kt`

```kotlin
package com.jpark.alarmcard.data.crawler

import com.jpark.alarmcard.data.remote.Http
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable
data class BitcoinQuote(
    val price: Double,
    val change: Double,
    val changeRate: Double
)

class BitcoinCrawler @Inject constructor(
    private val http: Http
) {
    suspend fun fetchQuote(): BitcoinQuote {
        // HTTP 요청 → JSON 파싱 → BitcoinQuote 반환
        val html = http.get("https://bitcoin-api.example.com/price")
        // 파싱 로직...
        return BitcoinQuote(...)
    }
}
```

**Hilt 주입**: `di/AppModule.kt`
```kotlin
@Provides
@Singleton
fun provideBitcoinCrawler(http: Http): BitcoinCrawler = BitcoinCrawler(http)
```

---

### 3. UI 상태 필드 추가

**ViewModel에 추가**:
```kotlin
// ui/MainViewModel.kt
private val _someState = MutableStateFlow(initialValue)
val someState: StateFlow<SomeType> = _someState.asStateFlow()

// StateFlow 업데이트 (viewModelScope 내에서)
viewModelScope.launch {
    _someState.value = newValue
}
```

**UI에서 수집**:
```kotlin
// Composable 내에서
val state by viewModel.someState.collectAsState()
```

---

### 4. Composable 상태 관리

**변수 선언**:
```kotlin
@Composable
fun MyScreen() {
    var searchQuery by remember { mutableStateOf("") }
    var selectedItem by remember { mutableStateOf<Item?>(null) }
    
    // searchQuery 값 변경 시 자동 재렌더링
}
```

---

## 자주 발생하는 실수 및 해결책

| 실수 | 원인 | 해결책 |
|------|------|-------|
| "Cannot access field before initialization" | StateFlow 구독 전에 접근 | `collectAsState()` 사용 |
| "Room query on main thread" | 동기 쿼리 Main에서 호출 | `suspend` 또는 `Flow` 사용 |
| "Worker not running" | WorkManager 초기화 안 됨 | AlarmCardApp에서 HiltWorkerFactory 설정 확인 |
| "Composer can only be used from composition" | Composable 외부에서 호출 | Composable 함수 내부에서만 상태 변수 선언 |
| "Navigation graph not found" | AppNav 라우트 정의 오류 | AppNav.kt에서 route 이름 확인 |
| "Hilt binding not found" | DI 모듈에 제공 안 함 | AppModule.kt에 @Provides 메서드 추가 |

---

## 코드 스타일 가이드

### 네이밍 규칙
```kotlin
// ✅ Good
val isRefreshing: StateFlow<Boolean>
fun fetchQuote(symbol: String): StockQuote
private val _internalState = MutableStateFlow(false)

// ❌ Bad
val refreshing: StateFlow<Boolean>  // "is" 없음
fun getQuote(symbol: String)        // fetch 대신 get
val internalState = MutableStateFlow(false)  // 비공개인데 "_" 없음
```

### Suspend 함수
```kotlin
// ✅ Good
suspend fun addStock(card: StockCard): String { ... }
suspend fun refreshCard(card: Card) { ... }

// ❌ Bad
fun addStock(card: StockCard, callback: (String) -> Unit) { ... }  // Callback 사용
```

### 에러 처리
```kotlin
// ✅ Good
try {
    val data = fetcher.fetch()
    // 처리
} catch (t: Throwable) {
    Timber.w(t, "Failed to fetch")
    card = card.copy(lastError = t.message)
}

// ❌ Bad
fetcher.fetch()  // 예외 무시
try { ... } catch (e: Exception) { }  // 조용히 무시
```

### Flow 구독
```kotlin
// ✅ Good (Compose)
val cards by viewModel.cards.collectAsState()

// ✅ Good (Coroutine)
viewModelScope.launch {
    repo.observeCards().collect { cards ->
        // 처리
    }
}

// ❌ Bad
viewModel.cards.collect { }  // viewModelScope 없음, 메모리 누수
```

---

## 테스트 체크리스트 (변경 후)

신규 기능 추가 또는 수정 후:

```
[ ] 앱 빌드 성공 여부 확인 (컴파일 에러 없음)
[ ] 앱 실행 시 크래시 없음
[ ] 해당 기능 동작 확인 (UI 테스트)
[ ] 기존 기능들 정상 동작 여부 (회귀 테스트)
[ ] 에러 발생 시 앱이 죽지 않고 lastError 표시되는지 확인
[ ] 메모리 누수 없는지 확인 (Profile 탭)
[ ] StateFlow 업데이트 시 UI 반응 확인
```

---

## 유용한 디버깅 팁

### Timber 로깅
```kotlin
Timber.d("Debug message: $variable")  // Debug 레벨
Timber.w(exception, "Warning message")  // Warning 레벨
Timber.e(exception, "Error message")  // Error 레벨
```

### Room 쿼리 검증
```kotlin
// 쿼리 실행 전 로그
Timber.d("Query: SELECT * FROM card WHERE id = $id")

// 결과 로그
val result = dao.getById(id)
Timber.d("Result: $result")
```

### StateFlow 값 확인
```kotlin
// ViewModel에서
Timber.d("Cards: ${cards.value}")
Timber.d("IsRefreshing: ${isRefreshing.value}")
```

### HTTP 요청/응답 로깅
```kotlin
// HttpClient.kt에서 이미 구성됨
// OkHttp HttpLoggingInterceptor가 모든 요청/응답 로그
```

---

## 문서 참고 순서

1. **초기 이해**: PROJECT_OVERVIEW.md (개요)
2. **파일 위치**: FILE_STRUCTURE_MAP.md (구조)
3. **상세 흐름**: DATA_FLOW_DIAGRAMS.md (플로우)
4. **빠른 참고**: 이 문서 (QUICK_REFERENCE.md)
5. **실제 코드**: 프로젝트 파일들

---

## 자주 묻는 질문

**Q: 새로운 카드 타입 추가하려면 어디서부터 시작해야 하나?**  
A: 위의 "새로운 데이터 타입 추가" 패턴을 따르세요. Domain → Data → ViewModel → UI 순서입니다.

**Q: 크롤러가 실패하면 어떻게 되나?**  
A: `try-catch`로 잡아서 `card.lastError`에 메시지 저장하고, UI에서 경고 아이콘 표시합니다.

**Q: 버스 알림이 작동하지 않을 때는?**  
A: WorkManager 초기화 (AlarmCardApp), hasActiveBusAlarm() 확인, BusAlarmWorker.scheduleNext() 호출 확인.

**Q: StateFlow와 Flow의 차이는?**  
A: StateFlow는 현재 값 유지, Flow는 값 발행만. StateFlow는 collectAsState()로 Compose 연동.

**Q: 네비게이션이 작동하지 않을 때는?**  
A: AppNav.kt에서 route 이름 확인, NavController.navigate() 인자 확인.

---

이 가이드는 신규 agent가 빠르게 프로젝트에 적응하고 task를 수행하도록 돕습니다.  
필요시 PROJECT_OVERVIEW.md, FILE_STRUCTURE_MAP.md, DATA_FLOW_DIAGRAMS.md를 함께 참고하세요.