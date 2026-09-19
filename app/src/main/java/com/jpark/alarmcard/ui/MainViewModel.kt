package com.jpark.alarmcard.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jpark.alarmcard.data.CardRepository
import com.jpark.alarmcard.data.crawler.BusStationSearchResult
import com.jpark.alarmcard.data.crawler.FxQuote
import com.jpark.alarmcard.data.crawler.NaverFxCrawler
import com.jpark.alarmcard.data.crawler.NaverMapBusCrawler
import com.jpark.alarmcard.data.crawler.NaverStockCrawler
import com.jpark.alarmcard.data.crawler.YahooFinanceCrawler
import com.jpark.alarmcard.data.crawler.StockSearchResult
import com.jpark.alarmcard.domain.model.BusCard
import com.jpark.alarmcard.domain.model.Card
import com.jpark.alarmcard.domain.model.FxCard
import com.jpark.alarmcard.domain.model.StockCard
import com.jpark.alarmcard.notify.AutoEnableWorker
import com.jpark.alarmcard.notify.BusAlarmWorker
import com.jpark.alarmcard.notify.StockAlarmWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    app: Application,
    private val repo: CardRepository,
    private val stockCrawler: YahooFinanceCrawler,
    private val naverStockCrawler: NaverStockCrawler,
    private val fxCrawler: NaverFxCrawler,
    private val busCrawler: NaverMapBusCrawler
) : AndroidViewModel(app) {

    val cards: StateFlow<List<Card>> = repo.observeCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var lastAutoRefresh = 0L
    private val autoRefreshMinIntervalMs = 15_000L // 화면 진입 시 자동 갱신 최소 간격

    /** 사용자 명시적 새로고침 (버튼) */
    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repo.refreshAll()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** 화면 active 시 자동 새로고침. 짧은 시간 내 중복은 무시. */
    fun onScreenResumed() {
        val now = System.currentTimeMillis()
        if (now - lastAutoRefresh < autoRefreshMinIntervalMs) return
        lastAutoRefresh = now
        refresh()
    }

    fun deleteCard(id: String) = viewModelScope.launch {
        repo.remove(id)
        AutoEnableWorker.cancel(getApplication(), id)
        rescheduleBusAlarmWorker()
        rescheduleStockAlarmWorker()
    }

    fun reorder(ids: List<String>) = viewModelScope.launch { repo.reorder(ids) }

    fun exportCards(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        val json = repo.exportToJson()
        getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
            it.write(json.toByteArray())
        }
    }

    fun importCards(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        val json = getApplication<Application>().contentResolver.openInputStream(uri)?.use {
            it.bufferedReader().readText()
        }
        if (!json.isNullOrBlank()) {
            repo.importFromJson(json)
            rescheduleAutoEnableWorkers()
            rescheduleBusAlarmWorker()
            rescheduleStockAlarmWorker()
            refresh()
        }
    }

    /** 버스카드 알람 on/off 및 임계값 저장. 활성 시 Worker 예약. */
    fun setBusAlarm(cardId: String, enabled: Boolean, minutesBefore: Int) = viewModelScope.launch {
        repo.setBusAlarm(cardId, enabled, minutesBefore)
        rescheduleBusAlarmWorker()
    }

    fun setStockAlarm(id: String, enabled: Boolean, price: Double?, rate: Double?) = viewModelScope.launch {
        repo.setStockAlarm(id, enabled, price, rate)
        rescheduleStockAlarmWorker()
    }

    fun setAutoEnable(id: String, enabled: Boolean, days: Int, time: String?) = viewModelScope.launch {
        repo.setAutoEnable(id, enabled, days, time)
        val entity = repo.getCardById(id) ?: return@launch
        if (entity.autoEnabled) {
            AutoEnableWorker.scheduleNext(getApplication(), entity)
        } else {
            AutoEnableWorker.cancel(getApplication(), id)
        }
    }

    fun setDisplayName(id: String, name: String?) = viewModelScope.launch {
        repo.setDisplayName(id, name)
    }

    private suspend fun rescheduleStockAlarmWorker() {
        val ctx = getApplication<Application>()
        if (repo.hasActiveStockAlarm()) {
            StockAlarmWorker.scheduleNext(ctx, delaySec = 5)
        } else {
            StockAlarmWorker.cancel(ctx)
        }
    }

    private suspend fun rescheduleBusAlarmWorker() {
        val ctx = getApplication<Application>()
        if (repo.hasActiveBusAlarm()) {
            // 즉시 한번 실행 후 스스로 다음 사이클 예약
            BusAlarmWorker.scheduleNext(ctx, delaySec = 5)
        } else {
            BusAlarmWorker.cancel(ctx)
        }
    }

    private suspend fun rescheduleAutoEnableWorkers() {
        val ctx = getApplication<Application>()
        repo.getAutoEnabledCards().forEach { entity ->
            AutoEnableWorker.scheduleNext(ctx, entity)
        }
    }

    /* ---------- Add flows ---------- */

    fun addStock(sel: StockSearchResult) = viewModelScope.launch {
        repo.addStock(
            StockCard(
                id = "", order = 0, updatedAt = 0, lastError = null,
                symbol = sel.symbol, market = sel.market, name = sel.name,
                currency = sel.currency
            )
        )
    }

    fun addFx(q: FxQuote) = viewModelScope.launch {
        val (base, quote) = parseFxCode(q.code)
        repo.addFx(
            FxCard(
                id = "", order = 0, updatedAt = 0, lastError = null,
                code = q.code, base = base, quote = quote
            )
        )
    }

    fun addBus(st: BusStationSearchResult, routeIds: List<String>) = viewModelScope.launch {
        repo.addBus(
            BusCard(
                id = "", order = 0, updatedAt = 0, lastError = null,
                stationId = st.stationId, stationName = st.stationName,
                cityCode = st.cityCode,
                filterRouteIds = routeIds
            )
        )
    }

    /* ---------- Search helpers (UI가 호출) ---------- */

    suspend fun searchStocks(q: String): List<StockSearchResult> {
        val naver = naverStockCrawler.search(q)
        if (naver.isNotEmpty()) return naver
        return stockCrawler.search(q)
    }
    suspend fun searchStations(q: String) = busCrawler.searchStation(q)
    suspend fun listFxPresets() = fxCrawler.listAvailable()
    suspend fun previewStationArrivals(stationId: String, cityCode: String?) =
        busCrawler.fetchArrivals(stationId, cityCode)

    private fun parseFxCode(code: String): Pair<String, String> {
        // FX_USDKRW → USD, KRW
        val stripped = code.removePrefix("FX_")
        return if (stripped.length >= 6) stripped.substring(0, 3) to stripped.substring(3, 6)
        else stripped to "KRW"
    }
}
