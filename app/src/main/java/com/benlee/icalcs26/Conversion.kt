package com.benlee.icalcs26

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ConvertUnit(val name: String, val symbol: String, val factor: Double)
data class UnitCategory(val name: String, val units: List<ConvertUnit>)

object Converter {
    val categories = listOf(
        UnitCategory("길이", listOf(
            ConvertUnit("미터", "m", 1.0), ConvertUnit("킬로미터", "km", 1000.0),
            ConvertUnit("센티미터", "cm", .01), ConvertUnit("밀리미터", "mm", .001),
            ConvertUnit("인치", "in", .0254), ConvertUnit("피트", "ft", .3048),
            ConvertUnit("야드", "yd", .9144), ConvertUnit("마일", "mi", 1609.344)
        )),
        UnitCategory("무게", listOf(
            ConvertUnit("킬로그램", "kg", 1.0), ConvertUnit("그램", "g", .001),
            ConvertUnit("밀리그램", "mg", .000001), ConvertUnit("톤", "t", 1000.0),
            ConvertUnit("파운드", "lb", .45359237), ConvertUnit("온스", "oz", .028349523125)
        )),
        UnitCategory("온도", listOf(
            ConvertUnit("섭씨", "°C", 1.0), ConvertUnit("화씨", "°F", 1.0),
            ConvertUnit("켈빈", "K", 1.0)
        )),
        UnitCategory("면적", listOf(
            ConvertUnit("제곱미터", "m²", 1.0), ConvertUnit("제곱킬로미터", "km²", 1e6),
            ConvertUnit("평", "평", 3.30578512397), ConvertUnit("제곱피트", "ft²", .09290304),
            ConvertUnit("에이커", "ac", 4046.8564224), ConvertUnit("헥타르", "ha", 10000.0)
        )),
        UnitCategory("부피", listOf(
            ConvertUnit("리터", "L", 1.0), ConvertUnit("밀리리터", "mL", .001),
            ConvertUnit("세제곱미터", "m³", 1000.0), ConvertUnit("미국 갤런", "gal", 3.785411784),
            ConvertUnit("미국 컵", "cup", .2365882365)
        )),
        UnitCategory("시간", listOf(
            ConvertUnit("초", "s", 1.0), ConvertUnit("분", "min", 60.0),
            ConvertUnit("시간", "h", 3600.0), ConvertUnit("일", "d", 86400.0),
            ConvertUnit("주", "wk", 604800.0)
        )),
        UnitCategory("속도", listOf(
            ConvertUnit("초속 미터", "m/s", 1.0), ConvertUnit("시속 킬로미터", "km/h", 1.0 / 3.6),
            ConvertUnit("시속 마일", "mph", .44704), ConvertUnit("노트", "kn", .5144444444)
        )),
        UnitCategory("데이터", listOf(
            ConvertUnit("바이트", "B", 1.0), ConvertUnit("킬로바이트", "kB", 1000.0),
            ConvertUnit("메가바이트", "MB", 1e6), ConvertUnit("기가바이트", "GB", 1e9),
            ConvertUnit("키비바이트", "KiB", 1024.0), ConvertUnit("메비바이트", "MiB", 1048576.0),
            ConvertUnit("기비바이트", "GiB", 1073741824.0)
        )),
        UnitCategory("압력", listOf(
            ConvertUnit("파스칼", "Pa", 1.0), ConvertUnit("킬로파스칼", "kPa", 1000.0),
            ConvertUnit("바", "bar", 100000.0), ConvertUnit("기압", "atm", 101325.0),
            ConvertUnit("PSI", "psi", 6894.757293168)
        )),
        UnitCategory("에너지", listOf(
            ConvertUnit("줄", "J", 1.0), ConvertUnit("킬로줄", "kJ", 1000.0),
            ConvertUnit("칼로리", "cal", 4.184), ConvertUnit("킬로칼로리", "kcal", 4184.0),
            ConvertUnit("킬로와트시", "kWh", 3600000.0)
        )),
        UnitCategory("환율", listOf(
            ConvertUnit("미국 달러", "USD", 1.0), ConvertUnit("대한민국 원", "KRW", 1.0),
            ConvertUnit("일본 엔", "JPY", 1.0), ConvertUnit("유로", "EUR", 1.0),
            ConvertUnit("영국 파운드", "GBP", 1.0), ConvertUnit("중국 위안", "CNY", 1.0),
            ConvertUnit("태국 바트", "THB", 1.0), ConvertUnit("스위스 프랑", "CHF", 1.0),
            ConvertUnit("호주 달러", "AUD", 1.0), ConvertUnit("캐나다 달러", "CAD", 1.0)
        ))
    )
    fun convert(value: Double, category: UnitCategory, from: ConvertUnit, to: ConvertUnit, rates: Map<String, Double> = emptyMap()): Double? {
        if (category.name == "환율") {
            val f = rates[from.symbol] ?: return null
            val t = rates[to.symbol] ?: return null
            return value * f / t
        }
        if (category.name == "온도") {
            val c = when (from.symbol) { "°C" -> value; "°F" -> (value-32.0)*5.0/9.0; else -> value-273.15 }
            return when (to.symbol) { "°C" -> c; "°F" -> c*9.0/5.0+32.0; else -> c+273.15 }
        }
        return value * from.factor / to.factor
    }
}

/**
 * Official Hana API On is approval-based. The phone ONLY calls a user-configured
 * HTTPS adapter owned by the user; it does not scrape the bank or embed a secret.
 * Expected adapter response is documented in docs/hana-adapter.md.
 */
data class HanaQuote(
    val base: Double,
    val cashBuy: Double?, val cashSell: Double?,
    val remitSend: Double?, val remitReceive: Double?
)

enum class HanaRateMode(val label: String) {
    BASE("매매기준율"), CASH("현찰 거래"), REMIT("송금 거래")
}

data class RateSnapshot(
    val quotes: Map<String, HanaQuote>, val date: String,
    val isCached: Boolean, val source: String = "하나은행"
) {
    val rates: Map<String, Double> get() = quotes.mapValues { it.value.base }
    /** The chosen direction matters: cash buy and sell are not interchangeable. */
    fun convert(value: Double, from: String, to: String, mode: HanaRateMode): Double? {
        val base = quotes[from] ?: return null
        val target = quotes[to] ?: return null
        if (!value.isFinite()) return null
        if (from == to) return value
        val won = when(mode) {
            HanaRateMode.BASE -> value * base.base
            HanaRateMode.CASH -> value * (base.cashSell ?: return null)
            HanaRateMode.REMIT -> value * (base.remitReceive ?: return null)
        }
        val divisor = when(mode) {
            HanaRateMode.BASE -> target.base
            HanaRateMode.CASH -> target.cashBuy ?: return null
            HanaRateMode.REMIT -> target.remitSend ?: return null
        }
        return if (divisor > 0) won / divisor else null
    }
}

object CurrencyRates {
    private const val PREFS = "icalc_hana_rates"
    private const val MAX_BYTES = 180_000
    fun endpoint(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString("adapter_endpoint", "") ?: ""
    fun configure(context: Context, url: String) {
        val normalized = url.trim()
        require(normalized.isEmpty() || (normalized.startsWith("https://") &&
                URL(normalized).host.isNotBlank() && URL(normalized).userInfo == null)) {
            "HTTPS 주소만 입력할 수 있습니다"
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("adapter_endpoint", normalized)
            .remove("snapshot").remove("snapshot_endpoint").apply()
    }
    private fun parse(body: String, isCached: Boolean): RateSnapshot {
        val json = JSONObject(body)
        require(json.optString("provider") == "HANA_BANK") { "하나은행 출처 표기가 없습니다" }
        val publishedAt = json.getString("publishedAt")
        require(publishedAt.matches(Regex("\\d{4}-\\d{2}-\\d{2}T.+"))) { "고시 날짜 정보가 잘못되었습니다" }
        val entries = json.getJSONArray("rates")
        val quotes = mutableMapOf("KRW" to HanaQuote(1.0,1.0,1.0,1.0,1.0))
        fun positive(obj: JSONObject, field: String): Double? = if (obj.isNull(field) || !obj.has(field)) null
            else obj.optDouble(field).takeIf { it.isFinite() && it > 0 }
        for (i in 0 until entries.length()) {
            val e = entries.getJSONObject(i)
            val currency = e.getString("currency").uppercase(java.util.Locale.ROOT)
            if (!currency.matches(Regex("[A-Z]{3}")) || currency == "KRW") continue
            val unit = e.optDouble("unit", 1.0)
            if (!unit.isFinite() || unit <= 0) continue
            fun scaled(field: String) = positive(e, field)?.div(unit)
            val base = scaled("baseRate") ?: continue
            quotes[currency] = HanaQuote(base, scaled("cashBuy"), scaled("cashSell"),
                scaled("remitSend"), scaled("remitReceive"))
        }
        require(quotes.size > 1) { "환율 데이터가 비어 있습니다" }
        return RateSnapshot(quotes, publishedAt, isCached)
    }
    suspend fun refresh(context: Context): RateSnapshot? = withContext(Dispatchers.IO) {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val endpoint = endpoint(context)
        if (endpoint.isEmpty()) return@withContext null
        val cached = preferences.getString("snapshot", null)
            ?.takeIf { preferences.getString("snapshot_endpoint", null) == endpoint }
            ?.let { runCatching { parse(it, true) }.getOrNull() }
        try {
            val url = URL(endpoint)
            require(url.protocol == "https" && url.userInfo == null)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 6500
            connection.readTimeout = 6500
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "iCalcS26/1.1")
            try {
                if (connection.responseCode != 200) return@withContext cached
                val content = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                if (content.length > MAX_BYTES) return@withContext cached
                val snapshot = parse(content, false)
                preferences.edit().putString("snapshot", content)
                    .putString("snapshot_endpoint", endpoint).apply()
                snapshot
            } finally { connection.disconnect() }
        } catch (_: Exception) { cached }
    }
}
