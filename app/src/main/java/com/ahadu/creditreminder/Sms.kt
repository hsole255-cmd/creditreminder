package com.ahadu.creditreminder

import android.content.Context
import android.telephony.SmsManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Sms {
    const val DEF_SHOP = "የእኛ ሱቅ"
    const val DEF_ACCOUNT = "CBE / Telebirr"
    const val DEF_TEMPLATE = "ሰላም! {ሱቅ} ነው። ቀሪ የብድር መጠንዎ {መጠን} ብር ነው። እባክዎ በ{አካውንት} ይክፈሉ። አመሰግናለሁ!"
    const val DEF_PROMO = "ሰላም {ስም}! {ሱቅ} አዳዲስ እቃዎች እና ጥሩ ቅናሽ አለው። መጥተው ይጎብኙን!"

    fun normalize(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.startsWith("251") && digits.length >= 12 -> "+" + digits.take(12)
            digits.startsWith("09") && digits.length == 10 -> "+251" + digits.drop(1)
            digits.startsWith("07") && digits.length == 10 -> "+251" + digits.drop(1)
            digits.length == 9 && (digits.startsWith("9") || digits.startsWith("7")) -> "+251" + digits
            raw.trim().startsWith("+") && digits.isNotEmpty() -> "+" + digits
            else -> raw.trim()
        }
    }

    fun today(): Long {
        val dayMs = 86_400_000L
        val eatNow = System.currentTimeMillis() + 3L * 3_600_000L
        return eatNow / dayMs
    }

    fun stamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date())
    }

    fun buildMessage(ctx: Context, amount: Long): String {
        val shop = Store.getText(ctx, "shop", DEF_SHOP)
        val account = Store.getText(ctx, "account", DEF_ACCOUNT)
        val tpl = Store.getText(ctx, "template", DEF_TEMPLATE)
        val formattedAmount = String.format(Locale.getDefault(), "%,d", amount as Any)
        return tpl
            .replace("{ሱቅ}", shop)
            .replace("{መጠን}", formattedAmount)
            .replace("{አካውንት}", account)
    }

    fun buildPromo(ctx: Context, customer: Customer): String {
        val shop = Store.getText(ctx, "shop", DEF_SHOP)
        val tpl = Store.getText(ctx, "promoText", DEF_PROMO)
        val safeName = if (customer.name.isBlank()) "ደንበኛ" else customer.name.trim()
        return tpl
            .replace("{ስም}", safeName)
            .replace("{ሱቅ}", shop)
    }

    fun sendSms(ctx: Context, phone: String, message: String): Boolean {
        val target = normalize(phone)
        if (target.isBlank() || message.isBlank()) return false
        return try {
            @Suppress("DEPRECATION")
            val mgr = ctx.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
            val parts = mgr.divideMessage(message)
            if (parts.size > 1) {
                mgr.sendMultipartTextMessage(target, null, parts, null, null)
            } else {
                mgr.sendTextMessage(target, null, message, null, null)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}

fun runReminders(ctx: Context): Int {
    val todayDay: Long = Sms.today()
    val list = Store.load(ctx)
    var sentCount = 0

    for (i in list.indices) {
        if (sentCount >= 8) break
        val d = list[i]
        val lastDay: Long = d.lastSentDay.toLong()
        val daysPassed: Long = todayDay - lastDay
        if (!d.paid && d.phone.isNotBlank() && d.amount > 0L && daysPassed >= 3L) {
            val msg = Sms.buildMessage(ctx, d.amount)
            val ok = Sms.sendSms(ctx, d.phone, msg)
            Store.addLog(
                ctx,
                LogEntry(
                    name = d.name,
                    kind = "ብድር SMS",
                    time = Sms.stamp(),
                    ok = ok
                )
            )
            if (ok) {
                list[i] = d.copy(lastSentDay = todayDay.toInt())
                sentCount++
            }
        }
    }

    Store.save(ctx, list)
    return sentCount
}

fun runPromo(ctx: Context, forceNow: Boolean): Int {
    val todayDay: Long = Sms.today()
    val intervalDays: Long = Store.getInt(ctx, "promoInterval", 14).coerceAtLeast(1).toLong()
    val batchLimit: Int = Store.getInt(ctx, "promoBatch", 10).coerceAtLeast(1)
    val list = Store.loadCustomers(ctx)
    var sentCount = 0

    for (i in list.indices) {
        if (sentCount >= batchLimit) break
        val cu = list[i]
        val lastPromoDay: Long = Store.getInt(ctx, "promo_day_${cu.id}", -999).toLong()
        val promoDaysPassed: Long = todayDay - lastPromoDay
        val due = forceNow || (promoDaysPassed >= intervalDays)
        if (!cu.promoOff && cu.phone.isNotBlank() && due) {
            val msg = Sms.buildPromo(ctx, cu)
            val ok = Sms.sendSms(ctx, cu.phone, msg)
            Store.addLog(
                ctx,
                LogEntry(
                    name = cu.name,
                    kind = "ማስታወቂያ SMS",
                    time = Sms.stamp(),
                    ok = ok
                )
            )
            if (ok) {
                Store.setInt(ctx, "promo_day_${cu.id}", todayDay.toInt())
                sentCount++
            }
        }
    }

    Store.saveCustomers(ctx, list)
    return sentCount
}
