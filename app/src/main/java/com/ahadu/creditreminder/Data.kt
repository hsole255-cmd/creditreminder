package com.ahadu.creditreminder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Debtor(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String,
    val amount: Long,
    val paid: Boolean = false,
    val lastSentDay: Long = 0L
)

data class Customer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String,
    val promoOff: Boolean = false
)

data class LogEntry(
    val time: String,
    val name: String,
    val kind: String,
    val ok: Boolean
)

object Store {
    private fun prefs(c: Context) =
        c.getSharedPreferences("credit_prefs", Context.MODE_PRIVATE)

    fun load(c: Context): MutableList<Debtor> {
        val arr = JSONArray(prefs(c).getString("debtors", "[]") ?: "[]")
        val list = mutableListOf<Debtor>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                Debtor(
                    o.getString("id"), o.getString("name"), o.getString("phone"),
                    o.getLong("amount"), o.getBoolean("paid"), o.getLong("last")
                )
            )
        }
        return list
    }

    fun save(c: Context, list: List<Debtor>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("name", it.name)
                    .put("phone", it.phone).put("amount", it.amount)
                    .put("paid", it.paid).put("last", it.lastSentDay)
            )
        }
        prefs(c).edit().putString("debtors", arr.toString()).apply()
    }

    fun loadCustomers(c: Context): MutableList<Customer> {
        val arr = JSONArray(prefs(c).getString("customers", "[]") ?: "[]")
        val list = mutableListOf<Customer>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                Customer(
                    o.getString("id"), o.getString("name"), o.getString("phone"),
                    o.optBoolean("off", false)
                )
            )
        }
        return list
    }

    fun saveCustomers(c: Context, list: List<Customer>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("name", it.name)
                    .put("phone", it.phone).put("off", it.promoOff)
            )
        }
        prefs(c).edit().putString("customers", arr.toString()).apply()
    }

    fun loadLog(c: Context): List<LogEntry> {
        val arr = JSONArray(prefs(c).getString("log", "[]") ?: "[]")
        val list = mutableListOf<LogEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                LogEntry(
                    o.getString("t"), o.getString("n"),
                    o.getString("k"), o.getBoolean("ok")
                )
            )
        }
        return list
    }

    fun addLog(c: Context, e: LogEntry) {
        val old = loadLog(c)
        val all = mutableListOf(e)
        all.addAll(old)
        val arr = JSONArray()
        all.take(300).forEach {
            arr.put(
                JSONObject().put("t", it.time).put("n", it.name)
                    .put("k", it.kind).put("ok", it.ok)
            )
        }
        prefs(c).edit().putString("log", arr.toString()).apply()
    }

    fun getText(c: Context, key: String, def: String): String =
        prefs(c).getString(key, def) ?: def

    fun setText(c: Context, key: String, v: String) {
        prefs(c).edit().putString(key, v).apply()
    }

    fun getInt(c: Context, key: String, def: Int): Int = prefs(c).getInt(key, def)

    fun setInt(c: Context, key: String, v: Int) {
        prefs(c).edit().putInt(key, v).apply()
    }

    fun getLong(c: Context, key: String, def: Long): Long = prefs(c).getLong(key, def)

    fun setLong(c: Context, key: String, v: Long) {
        prefs(c).edit().putLong(key, v).apply()
    }

    fun getBool(c: Context, key: String, def: Boolean): Boolean =
        prefs(c).getBoolean(key, def)

    fun setBool(c: Context, key: String, v: Boolean) {
        prefs(c).edit().putBoolean(key, v).apply()
    }

    fun backup(c: Context): String {
        val arr = JSONArray()
        for ((k, v) in prefs(c).all) {
            val t = when (v) {
                is Boolean -> "b"
                is Long -> "l"
                is Int -> "i"
                else -> "s"
            }
            arr.put(JSONObject().put("k", k).put("t", t).put("v", v.toString()))
        }
        return arr.toString()
    }

    fun restore(c: Context, text: String): Boolean {
        return try {
            val arr = JSONArray(text.trim())
            val e = prefs(c).edit().clear()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val k = o.getString("k")
                val v = o.getString("v")
                when (o.getString("t")) {
                    "b" -> e.putBoolean(k, v.toBoolean())
                    "l" -> e.putLong(k, v.toLong())
                    "i" -> e.putInt(k, v.toInt())
                    else -> e.putString(k, v)
                }
            }
            e.apply()
            true
        } catch (ex: Exception) {
            false
        }
    }
}