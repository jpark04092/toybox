package com.jpark.alarmcard.data.crawler

import com.jpark.alarmcard.data.remote.httpGetString
import com.jpark.alarmcard.domain.model.StockMarket
import com.jpark.alarmcard.data.remote.NextData
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 네이버 증권 크롤러.
 */
@Singleton
class NaverStockCrawler @Inject constructor() {

    suspend fun fetchQuote(symbol: String, market: StockMarket): StockQuote {
        return when (market) {
            StockMarket.DOMESTIC -> fetchDomesticQuote(symbol)
            StockMarket.US -> fetchOverseasQuote(symbol)
        }
    }

    suspend fun search(keyword: String): List<StockSearchResult> {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) return emptyList()

        // 1) 국내: URL 또는 코드/지수명
        val code = extractDomesticCode(trimmed)
        if (code != null) {
            return runCatching {
                val q = fetchDomesticQuote(code)
                listOf(
                    StockSearchResult(
                        symbol = code,
                        name = q.name,
                        market = StockMarket.DOMESTIC,
                        currency = "KRW",
                        price = q.price,
                        change = q.change,
                        changeRate = q.changeRate
                    )
                )
            }.onFailure { Timber.w(it, "stock preview failed for domestic $code") }
                .getOrDefault(emptyList())
        }

        // 2) 해외: URL 또는 심볼
        val overseas = extractOverseasSymbol(trimmed)
        if (overseas != null) {
            return runCatching {
                val q = fetchOverseasQuote(overseas)
                listOf(
                    StockSearchResult(
                        symbol = overseas,
                        name = q.name,
                        market = StockMarket.US,
                        currency = q.currency ?: "USD",
                        price = q.price,
                        change = q.change,
                        changeRate = q.changeRate
                    )
                )
            }.onFailure { Timber.w(it, "stock preview failed for overseas $overseas") }
                .getOrDefault(emptyList())
        }

        return emptyList()
    }

    /* ---------------- 국내 ---------------- */

    private suspend fun fetchDomesticQuote(code: String): StockQuote {
        // 국내 지수 (KOSPI, KOSDAQ, KPI200) 여부 확인
        val isIndex = code.matches(Regex("^(KOSPI|KOSDAQ|KPI200)$"))
        
        if (isIndex) {
            return fetchDomesticIndexFromMobile(code)
        }

        // 일반 종목: PC 버전 시도
        val pcResult = runCatching { fetchDomesticFromPc(code) }.getOrNull()
        if (pcResult != null) return pcResult

        // 모바일 버전 시도
        val mobileResult = runCatching { fetchDomesticFromMobile(code) }.getOrNull()
        if (mobileResult != null) return mobileResult

        error("Failed to fetch domestic quote for $code")
    }

    private suspend fun fetchDomesticIndexFromMobile(code: String): StockQuote {
        val url = "https://m.stock.naver.com/domestic/index/$code/total"
        val html = httpGetString(url, referer = "https://m.stock.naver.com/")
        return parseNextData(html, code) 
            ?: parseMobileCssFallback(html, code)
            ?: error("Failed to parse domestic index for $code")
    }

    private suspend fun fetchDomesticFromPc(code: String): StockQuote {
        val url = "https://finance.naver.com/item/main.naver?code=$code"
        val html = httpGetString(
            url = url,
            referer = "https://finance.naver.com/",
            extraHeaders = mapOf("User-Agent" to "Mozilla/5.0 (Linux; Android 10) AlarmCard/1.0")
        )
        val doc = Jsoup.parse(html)
        val name = doc.selectFirst(".wrap_company h2 a")?.text()?.trim()
            ?.ifBlank { null }
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.substringBefore(" - ")?.trim()
            ?: code

        val priceText = doc.selectFirst("p.no_today .blind")?.text()
        val price = priceText?.replace(",", "")?.trim()?.toDoubleOrNull()
            ?: error("Cannot extract price from PC version")

        val exdayEm = doc.selectFirst("p.no_exday em")
        val sign = when {
            exdayEm?.hasClass("no_down") == true -> -1.0
            exdayEm?.hasClass("no_up") == true -> 1.0
            else -> 0.0
        }
        val exBlinds = doc.select("p.no_exday .blind").map { it.text().trim() }
        val change = exBlinds.getOrNull(0)?.replace(",", "")?.toDoubleOrNull()?.let { it * sign }
        val rate = exBlinds.getOrNull(1)?.replace(",", "")?.replace("%", "")?.toDoubleOrNull()?.let { it * sign }

        return StockQuote(code, name, price, change, rate, "KRW")
    }

    private suspend fun fetchDomesticFromMobile(code: String): StockQuote {
        val url = "https://m.stock.naver.com/domestic/stock/$code/total"
        val html = httpGetString(url, referer = "https://m.stock.naver.com/")
        return parseNextData(html, code) ?: parseMobileCssFallback(html, code) ?: error("Cannot parse mobile version for $code")
    }

    /* ---------------- 해외 ---------------- */

    private suspend fun fetchOverseasQuote(symbol: String): StockQuote {
        val type = if (symbol.startsWith(".")) "index" else "stock"
        val url = "https://m.stock.naver.com/worldstock/$type/$symbol/total"
        val html = httpGetString(url, referer = "https://m.stock.naver.com/")
        return parseNextData(html, symbol)
            ?: parseCssFallback(html, symbol)
            ?: error("Failed to parse overseas quote for $symbol")
    }

    /* ---------------- 공통 파싱 ---------------- */

    private fun parseNextData(html: String, symbol: String): StockQuote? {
        val root = NextData.extract(html) ?: return null
        val stockObj = findStockObject(root, symbol) ?: return null

        val price = stockObj.doubleOrNull("closePrice")
            ?: stockObj.doubleOrNull("nowPrice")
            ?: stockObj.doubleOrNull("currentPrice")
            ?: stockObj.doubleOrNull("price")
        val change = stockObj.doubleOrNull("compareToPreviousClosePrice")
            ?: stockObj.doubleOrNull("compareToPreviousPrice")
            ?: stockObj.doubleOrNull("change")
        val rate = stockObj.doubleOrNull("fluctuationsRatio")
            ?: stockObj.doubleOrNull("changeRate")
        val name = stockObj.strOrNull("stockName")
            ?: stockObj.strOrNull("name")
            ?: stockObj.strOrNull("itemCode")
            ?: symbol
        val currency = stockObj.strOrNull("currency")

        if (price == null) return null
        return StockQuote(symbol, name, price, change, rate, currency)
    }

    private fun findStockObject(root: JsonElement, symbol: String): JsonObject? {
        val stack = ArrayDeque<JsonElement>()
        stack.addLast(root)
        var firstStockObject: JsonObject? = null
        var firstMatchedStockObject: JsonObject? = null
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            if (cur is JsonObject) {
                val keys = cur.keys
                val looksLikeStock = keys.contains("closePrice") ||
                    keys.contains("nowPrice") ||
                    keys.contains("currentPrice") ||
                    (keys.contains("stockName") && (keys.contains("compareToPreviousClosePrice") || keys.contains("fluctuationsRatio"))) ||
                    (keys.contains("itemCode") && (keys.contains("closePrice") || keys.contains("nowPrice")))
                
                if (looksLikeStock) {
                    if (cur.matchesSymbol(symbol)) {
                        if (cur.hasStockName()) return cur
                        if (firstMatchedStockObject == null) firstMatchedStockObject = cur
                    }
                    if (firstStockObject == null) firstStockObject = cur
                }
                cur.values.forEach { stack.addLast(it) }
            } else if (cur is kotlinx.serialization.json.JsonArray) {
                cur.forEach { stack.addLast(it) }
            }
        }

        // 국내 종목 페이지에는 업종 비교/인기 종목 등 여러 종목 객체가 함께 들어온다.
        // 요청 코드와 일치하지 않는 첫 번째 객체를 반환하면 다른 종목(예: 이오테크닉스)이 표시될 수 있으므로
        // 국내 6자리 코드에서는 반드시 일치 객체만 허용한다.
        return firstMatchedStockObject ?: firstStockObject.takeUnless { symbol.matches(Regex("^\\d{6}$")) }
    }

    private fun parseMobileCssFallback(html: String, code: String): StockQuote? {
        val doc = Jsoup.parse(html)
        if (code.matches(Regex("^\\d{6}$"))) {
            val ogUrl = doc.selectFirst("meta[property=og:url]")?.attr("content").orEmpty()
            if (!ogUrl.contains("/domestic/stock/$code/")) return null
        }
        val priceText = doc.select("strong[class*=price]").firstOrNull()?.text()
            ?: doc.select("[class*=PriceTable]").firstOrNull()?.text()
        val price = priceText?.replace(",", "")?.toDoubleOrNull() ?: return null
        val name = doc.selectFirst("meta[property=og:title]")?.attr("content")
            ?.substringBefore(" - ")
            ?.ifBlank { null }
            ?: code
        if (code.matches(Regex("^\\d{6}$")) && name == "Npay 증권") return null
        return StockQuote(code, name, price, null, null, "KRW")
    }

    private fun parseCssFallback(html: String, symbol: String): StockQuote? {
        val doc = Jsoup.parse(html)
        val priceText = doc.select("strong[class*=price]").firstOrNull()?.text()
            ?: doc.select("[class*=GraphMain_price]").firstOrNull()?.text()
        val price = priceText?.replace(",", "")?.toDoubleOrNull() ?: return null
        val name = doc.selectFirst("meta[property=og:title]")?.attr("content") ?: symbol
        return StockQuote(symbol, name, price, null, null, null)
    }

    /* ---------------- 입력 추출 ---------------- */

    private fun extractDomesticCode(input: String): String? {
        // 1) 국내 지수 URL: /domestic/index/KOSPI
        Regex("domestic/index/([A-Z]{2,10})").find(input)?.let { return it.groupValues[1] }
        // 2) 국내 종목 URL: /domestic/stock/005930
        Regex("domestic/stock/(\\d{6})").find(input)?.let { return it.groupValues[1] }
        // 3) PC URL: code=005930
        Regex("[?&]code=(\\d{6})").find(input)?.let { return it.groupValues[1] }
        // 4) 6자리 숫자
        if (input.matches(Regex("^\\d{6}$"))) return input
        // 5) 국내 지수명 직접 입력
        if (input.matches(Regex("^(KOSPI|KOSDAQ|KPI200)$"))) return input.uppercase()
        return null
    }

    private fun extractOverseasSymbol(input: String): String? {
        // 1) 해외 URL: /worldstock/(stock|index)/(.INX|NVDA.O)
        Regex("worldstock/(?:stock|index)/([A-Za-z0-9.\\-]+)").find(input)?.let { return it.groupValues[1].uppercase() }
        // 2) 지수 형태: .INX
        if (input.matches(Regex("^\\.[A-Z]{2,10}$"))) return input.uppercase()
        // 3) 종목 형태: AAPL, NVDA.O
        if (input.matches(Regex("^[A-Z0-9]{1,10}(\\.[A-Z])?$"))) return input.uppercase()
        return null
    }
}

private fun JsonObject.strOrNull(key: String): String? =
    this[key]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }?.takeIf { it.isNotBlank() && it != "null" }

private fun JsonObject.doubleOrNull(key: String): Double? =
    this[key]?.let { runCatching { it.jsonPrimitive.content.replace(",", "").toDouble() }.getOrNull() }

private fun JsonObject.hasStockName(): Boolean =
    strOrNull("stockName") != null || strOrNull("name") != null

private fun JsonObject.matchesSymbol(symbol: String): Boolean {
    val normalizedSymbol = symbol.uppercase()
    return listOf("itemCode", "reutersCode", "symbol", "code", "id")
        .mapNotNull { strOrNull(it)?.uppercase() }
        .any { candidate ->
            candidate == normalizedSymbol ||
                candidate.substringBefore('.').takeIf { it.length == 6 } == normalizedSymbol
        }
}
