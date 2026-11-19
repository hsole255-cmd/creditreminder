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

    fun getText(c: Context, key: String, def: String): String =
        prefs(c).getString(key, def) ?: def

    fun setText(c: Context, key: String, v: String) {
        prefs(c).edit().putString(key, v).apply()
    }
}
