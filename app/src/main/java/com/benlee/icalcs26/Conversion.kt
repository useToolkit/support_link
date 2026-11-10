package com.benlee.icalcs26

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
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
            return value / f * t
        }
        if (category.name == "온도") {
            val c = when (from.symbol) { "°C" -> value; "°F" -> (value-32.0)*5.0/9.0; else -> value-273.15 }
            return when (to.symbol) { "°C" -> c; "°F" -> c*9.0/5.0+32.0; else -> c+273.15 }
        }
        return value * from.factor / to.factor
    }
}

data class RateSnapshot(val rates: Map<String, Double>, val date: String, val isCached: Boolean)

object CurrencyRates {
    private const val SOURCE = "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml"
    private const val PREFS = "icalc_rates"
    suspend fun refresh(context: Context): RateSnapshot? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cached = try {
            val obj = JSONObject(prefs.getString("rates", "{}") ?: "{}")
            val map = mutableMapOf("EUR" to 1.0)
            obj.keys().forEach { k -> map[k] = obj.getDouble(k) }
            if (map.size > 1) RateSnapshot(map, prefs.getString("date", "") ?: "", true) else null
        } catch (_: Exception) { null }
        try {
            val conn = URL(SOURCE).openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.setRequestProperty("User-Agent", "iCalcS26/1.0")
            try {
                if (conn.responseCode !in 200..299) return@withContext cached
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                conn.inputStream.use { parser.setInput(it, "UTF-8")
                    val map = mutableMapOf("EUR" to 1.0)
                    var date = ""
                    while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                        if (parser.eventType == XmlPullParser.START_TAG && parser.name == "Cube") {
                            parser.getAttributeValue(null, "time")?.let { date = it }
                            val currency = parser.getAttributeValue(null, "currency")
                            val rate = parser.getAttributeValue(null, "rate")?.toDoubleOrNull()
                            if (currency != null && rate != null && rate > 0) map[currency] = rate
                        }
                        parser.next()
                    }
                    if (map.size < 5) return@withContext cached
                    val obj = JSONObject()
                    map.forEach { (k,v) -> obj.put(k,v) }
                    prefs.edit().putString("rates", obj.toString()).putString("date", date).apply()
                    RateSnapshot(map, date, false)
                }
            } finally { conn.disconnect() }
        } catch (_: Exception) { cached }
    }
}
