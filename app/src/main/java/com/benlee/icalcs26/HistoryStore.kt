package com.benlee.icalcs26

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class CalcHistory(val expression: String, val result: String, val timestamp: Long)

class HistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("icalc_history", Context.MODE_PRIVATE)
    fun load(): List<CalcHistory> = try {
        val arr = JSONArray(prefs.getString("history", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            CalcHistory(o.getString("expression"), o.getString("result"), o.optLong("time"))
        }
    } catch (_: Exception) { emptyList() }
    fun save(items: List<CalcHistory>) {
        val arr = JSONArray()
        items.take(200).forEach { h ->
            arr.put(JSONObject().put("expression", h.expression).put("result", h.result).put("time", h.timestamp))
        }
        prefs.edit().putString("history", arr.toString()).apply()
    }
}

class NotesStore(context: Context) {
    private val prefs = context.getSharedPreferences("icalc_notes", Context.MODE_PRIVATE)
    fun load(): String = prefs.getString("content", "") ?: ""
    fun save(content: String) = prefs.edit().putString("content", content).apply()
}
