package com.jpark.alarmcard.data

import com.jpark.alarmcard.data.crawler.NaverFxCrawler
import com.jpark.alarmcard.data.crawler.NaverMapBusCrawler
import com.jpark.alarmcard.data.crawler.NaverStockCrawler
import com.jpark.alarmcard.data.crawler.YahooFinanceCrawler
import com.jpark.alarmcard.data.local.CardDao
import com.jpark.alarmcard.data.local.toDomain
import com.jpark.alarmcard.data.local.toEntity
import com.jpark.alarmcard.domain.model.BusCard
import com.jpark.alarmcard.domain.model.Card
import com.jpark.alarmcard.domain.model.FxCard
import com.jpark.alarmcard.domain.model.StockCard
import com.jpark.alarmcard.domain.model.AutoEnableSchedule
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CardRepository @Inject constructor(
    private val dao: CardDao,
    private val stockCrawler: YahooFinanceCrawler,
    private val naverStockCrawler: NaverStockCrawler,
    private val fxCrawler: NaverFxCrawler,
    private val busCrawler: NaverMapBusCrawler
) {
    fun observeCards(): Flow<List<Card>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun addStock(card: StockCard): String {
        val order = dao.maxOrder() + 1
        val id = card.id.ifBlank { UUID.randomUUID().toString() }
        dao.upsert(card.copy(id = id, order = order).toEntity())
        // 최초 값 가져오기
        refreshOne(id)
        return id
    }

    suspend fun addBus(card: BusCard): String {
        val order = dao.maxOrder() + 1
        val id = card.id.ifBlank { UUID.randomUUID().toString() }
        dao.upsert(card.copy(id = id, order = order).toEntity())
        refreshOne(id)
        return id
    }

    suspend fun addFx(card: FxCard): String {
        val order = dao.maxOrder() + 1
        val id = card.id.ifBlank { UUID.randomUUID().toString() }
        dao.upsert(card.copy(id = id, order = order).toEntity())
        refreshOne(id)
        return id
    }

    suspend fun remove(id: String) = dao.deleteById(id)

    suspend fun getCardById(id: String) = dao.getById(id)

    /** 버스카드 알람 설정 갱신 */
    suspend fun setBusAlarm(id: String, enabled: Boolean, minutesBefore: Int) {
        val card = dao.getById(id)?.toDomain() as? BusCard ?: return
        dao.upsert(
            card.copy(
                alarmEnabled = enabled,
                alarmMinutesBefore = minutesBefore,
                // 활성화 시 이전 발송 이력 리셋
                alarmLastFiredAt = if (enabled) 0L else card.alarmLastFiredAt
            ).toEntity()
        )
    }

    /** 알람이 켜져있는 버스카드가 하나라도 있는지 (Worker 스케줄러가 사용) */
    suspend fun hasActiveBusAlarm(): Boolean =
        dao.getAll().any {
            it.type == com.jpark.alarmcard.data.local.CardEntity.TYPE_BUS && it.alarmEnabled
        }

    /** 주식카드 알람 설정 갱신 */
    suspend fun setStockAlarm(id: String, enabled: Boolean, price: Double?, rate: Double?) {
        val card = dao.getById(id)?.toDomain() as? StockCard ?: return
        
        // enabled 가 false 여도 기존 설정값(price, rate)이 있으면 유지하도록 처리 (자동 활성화 연동 위해)
        val finalPrice = if (!enabled && price == null) card.alarmPriceThreshold else price
        val finalRate = if (!enabled && rate == null) card.alarmRateThreshold else rate
        
        dao.upsert(
            card.copy(
                alarmEnabled = enabled,
                alarmPriceThreshold = finalPrice,
                alarmRateThreshold = finalRate,
                alarmLastFiredAt = if (enabled) 0L else card.alarmLastFiredAt
            ).toEntity()
        )
    }

    suspend fun hasActiveStockAlarm(): Boolean =
        dao.getAll().any {
            it.type == com.jpark.alarmcard.data.local.CardEntity.TYPE_STOCK && it.alarmEnabled
        }

    suspend fun hasActiveStockAlarmOrAutoEnable(): Boolean =
        dao.getAll().any {
            it.type == com.jpark.alarmcard.data.local.CardEntity.TYPE_STOCK && (it.alarmEnabled || it.autoEnabled)
        }

    suspend fun setAutoEnable(id: String, enabled: Boolean, days: Int, time: String?) {
        val card = dao.getById(id)?.toDomain() ?: return
        val sanitizedDays = days and AutoEnableSchedule.VALID_DAYS_MASK
        val sanitizedTime = time?.split(":")
            ?.takeIf { it.size == 2 }
            ?.mapNotNull { it.toIntOrNull() }
            ?.takeIf { it.size == 2 && it[0] in 0..23 && it[1] in 0..59 }
            ?.let { String.format(Locale.US, "%02d:%02d", it[0], it[1]) }
        val finalEnabled = enabled && AutoEnableSchedule.hasSelectedDay(sanitizedDays) && sanitizedTime != null
        val updated = when (card) {
            is StockCard -> card.copy(autoEnabled = finalEnabled, autoEnableDays = sanitizedDays, autoEnableTime = sanitizedTime)
            is BusCard -> card.copy(autoEnabled = finalEnabled, autoEnableDays = sanitizedDays, autoEnableTime = sanitizedTime)
            is FxCard -> card.copy(autoEnabled = finalEnabled, autoEnableDays = sanitizedDays, autoEnableTime = sanitizedTime)
        }
        dao.upsert(updated.toEntity())
    }

    suspend fun setDisplayName(id: String, name: String?) {
        val card = dao.getById(id)?.toDomain() ?: return
        val updated = when (card) {
            is StockCard -> card.copy(displayName = name)
            is BusCard -> card.copy(displayName = name)
            is FxCard -> card.copy(displayName = name)
        }
        dao.upsert(updated.toEntity())
    }

    suspend fun refreshStockAlarmsAndSelectFireable(): List<StockCard> {
        val allEntities = dao.getAll()
        val entitiesToRefresh = allEntities.filter {
            it.type == com.jpark.alarmcard.data.local.CardEntity.TYPE_STOCK && (it.alarmEnabled || it.autoEnabled)
        }
        // 새로고침
        entitiesToRefresh.forEach { runCatching { refreshCard(it.toDomain()) } }

        val nowMs = System.currentTimeMillis()
        val fire = mutableListOf<StockCard>()
        for (e in dao.getAll()) {
            if (e.type != com.jpark.alarmcard.data.local.CardEntity.TYPE_STOCK || !e.alarmEnabled) continue
            val sc = e.toDomain() as StockCard
            val currentPrice = sc.price ?: continue

            var isHit = false
            // 1) 가격 임계점 (절대값 도달)
            sc.alarmPriceThreshold?.let { threshold ->
                // 특정 가격 도달 시 (보통 상하방 구분 없이 근접/통과 시 알려줌)
                // 여기서는 간단히 마지막 발송 시점 대비 현재 상태만 체크
                isHit = true 
            }
            // 2) 퍼센티지 임계점 (변동률 절대값)
            sc.alarmRateThreshold?.let { threshold ->
                if (kotlin.math.abs(sc.changeRate ?: 0.0) >= threshold) {
                    isHit = true
                }
            }

            if (isHit) {
                // 중복 방지: 1시간(또는 30분) 이내 동일 종목 알림 방지
                if (nowMs - sc.alarmLastFiredAt > 30 * 60_000L) {
                    dao.upsert(e.copy(alarmLastFiredAt = nowMs))
                    fire += sc
                }
            }
        }
        return fire
    }

    /**
     * 활성 버스카드들을 새로고침한 뒤, 알림을 발송해야 할 카드 목록을 반환.
     * (실제 발송은 상위 레이어에서 수행)
     */
    suspend fun refreshBusAlarmsAndSelectFireable(): List<com.jpark.alarmcard.domain.model.BusCard> {
        val entities = dao.getAll().filter {
            it.type == com.jpark.alarmcard.data.local.CardEntity.TYPE_BUS && it.alarmEnabled
        }
        // 새로고침
        entities.forEach { runCatching { refreshCard(it.toDomain()) } }
        // 최신 상태로 다시 읽어 임계치 만족 여부 검사
        val nowMs = System.currentTimeMillis()
        val fire = mutableListOf<com.jpark.alarmcard.domain.model.BusCard>()
        for (e in dao.getAll()) {
            if (e.type != com.jpark.alarmcard.data.local.CardEntity.TYPE_BUS || !e.alarmEnabled) continue
            val bc = e.toDomain() as com.jpark.alarmcard.domain.model.BusCard
            // 필터된 노선 중에 eta1Sec <= alarmMinutesBefore*60 인 것이 있는지
            val threshold = bc.alarmMinutesBefore * 60
            
            // 각 노선별로 마지막 발송 차량 정보를 파싱 (routeId:plateNo,...)
            val lastFiredMap = bc.alarmLastFiredVehicles?.split(",")
                ?.filter { it.contains(":") }
                ?.associate { it.split(":").let { p -> p[0] to p[1] } }
                ?: emptyList<Pair<String, String>>().toMap()

            val toFireArrivals = bc.arrivals.filter { a -> 
                val isTimeMatch = a.eta1Sec != null && a.eta1Sec in 0..threshold
                if (!isTimeMatch) return@filter false
                
                val lastPlate = lastFiredMap[a.routeId]
                val currentPlate = a.plateNo1
                
                // 조건: (차량 번호가 다르거나 모름) OR (마지막 발송 후 5분 경과)
                val isNewVehicle = currentPlate != null && currentPlate != lastPlate
                val isTimeout = (nowMs - bc.alarmLastFiredAt > 5 * 60_000L)
                
                isNewVehicle || isTimeout
            }

            if (toFireArrivals.isNotEmpty()) {
                // 발송 차량 정보 업데이트
                val newFiredMap = lastFiredMap.toMutableMap()
                toFireArrivals.forEach { a ->
                    a.plateNo1?.let { newFiredMap[a.routeId] = it }
                }
                val newFiredCsv = newFiredMap.entries.joinToString(",") { "${it.key}:${it.value}" }
                
                dao.upsert(e.copy(
                    alarmLastFiredAt = nowMs,
                    alarmLastFiredVehicles = newFiredCsv
                ))
                fire += bc
            }

        }
        return fire
    }

    suspend fun reorder(ids: List<String>) {
        val entities = dao.getAll().associateBy { it.id }
        ids.forEachIndexed { idx, id ->
            entities[id]?.copy(orderIdx = idx)?.let { dao.update(it) }
        }
    }

    /** 모든 카드를 병렬로 새로고침. 개별 실패는 lastError에 저장. */
    suspend fun refreshAll() = coroutineScope {
        val cards = dao.getAll().map { it.toDomain() }
        cards.map { card ->
            async { runCatching { refreshCard(card) } }
        }.forEach { it.await() }
    }

    suspend fun refreshOne(id: String) {
        val entity = dao.getById(id) ?: return
        runCatching { refreshCard(entity.toDomain()) }
    }

    suspend fun exportToJson(): String {
        val entities = dao.getAll()
        val json = kotlinx.serialization.json.Json {
            prettyPrint = true
            encodeDefaults = true
        }
        return json.encodeToString(kotlinx.serialization.builtins.ListSerializer(com.jpark.alarmcard.data.local.CardEntity.serializer()), entities)
    }

    suspend fun importFromJson(jsonStr: String) {
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
        val entities = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(com.jpark.alarmcard.data.local.CardEntity.serializer()), jsonStr)
        dao.upsertAll(entities)
    }

    private suspend fun refreshCard(card: Card) {
        val now = System.currentTimeMillis()
        try {
            val updated: Card = when (card) {
                is StockCard -> {
                    val q = if (card.market == com.jpark.alarmcard.domain.model.StockMarket.DOMESTIC) {
                        naverStockCrawler.fetchQuote(card.symbol, card.market)
                    } else {
                        stockCrawler.fetchQuote(card.symbol, card.market)
                    }
                    card.copy(
                        name = q.name.ifBlank { card.name },
                        price = q.price,
                        change = q.change,
                        changeRate = q.changeRate,
                        currency = q.currency ?: card.currency,
                        updatedAt = now,
                        lastError = null
                    )
                }
                is FxCard -> {
                    val q = fxCrawler.fetchQuote(card.code)
                    card.copy(
                        rate = q.rate,
                        change = q.change,
                        changeRate = q.changeRate,
                        updatedAt = now,
                        lastError = null
                    )
                }
                is BusCard -> {
                    val detail = busCrawler.fetchArrivals(card.stationId, card.cityCode)
                    val filtered = if (card.filterRouteIds.isEmpty()) detail.arrivals
                    else detail.arrivals.filter { it.routeId in card.filterRouteIds }
                    card.copy(
                        stationName = detail.stationName.ifBlank { card.stationName },
                        arrivals = filtered,
                        updatedAt = now,
                        lastError = null
                    )
                }
            }
            dao.upsert(updated.toEntity())
        } catch (t: Throwable) {
            Timber.w(t, "refresh failed for ${card.id}")
            val failed: Card = when (card) {
                is StockCard -> card.copy(lastError = t.message ?: t::class.simpleName ?: "error")
                is FxCard -> card.copy(lastError = t.message ?: t::class.simpleName ?: "error")
                is BusCard -> card.copy(lastError = t.message ?: t::class.simpleName ?: "error")
            }
            dao.upsert(failed.toEntity())
        }
    }
}
