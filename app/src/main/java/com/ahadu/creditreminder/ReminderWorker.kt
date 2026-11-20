package com.ahadu.creditreminder

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

fun runReminders(c: Context, interval: Int = 3, maxCount: Int = 8): Int {
    val list = Store.load(c)
    val today = Sms.today()
    var sent = 0
    for (i in list.indices) {
        if (sent >= maxCount) break
        val d = list[i]
        if (d.paid || d.phone.isBlank() || d.amount <= 0) continue
        if (today - d.lastSentDay < interval) continue
        val text = Sms.buildMessage(c, d.amount)
        val ok = Sms.send(c, d.phone, text)
        Store.addLog(c, LogEntry(Sms.stamp(), d.name, "ብድር", ok))
        if (ok) {
            list[i] = d.copy(lastSentDay = today)
            sent++
        }
        Thread.sleep(3000)
    }
    Store.save(c, list)
    return sent
}

fun runPromo(c: Context, force: Boolean): Int {
    val customers = Store.loadCustomers(c)
    if (customers.isEmpty()) return 0
    val today = Sms.today()
    val interval = Store.getInt(c, "promoInterval", 14)
    val batch = Store.getInt(c, "promoBatch", 10)
    var index = Store.getInt(c, "promoIndex", 0)
    if (index >= customers.size) index = 0
    if (index == 0) {
        val start = Store.getLong(c, "promoRoundStart", 0L)
        if (!force && start != 0L && today - start < interval) return 0
        Store.setLong(c, "promoRoundStart", today)
    }
    var sent = 0
    while (index < customers.size && sent < batch) {
        val cu = customers[index]
        index++
        if (cu.promoOff || cu.phone.isBlank()) continue
        val ok = Sms.send(c, cu.phone, Sms.buildPromo(c, cu.name))
        Store.addLog(c, LogEntry(Sms.stamp(), cu.name, "ማስታወቂያ", ok))
        if (ok) sent++
        Thread.sleep(3000)
    }
    if (index >= customers.size) index = 0
    Store.setInt(c, "promoIndex", index)
    return sent
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        runReminders(applicationContext)
        if (Store.getBool(applicationContext, "promoAuto", false)) {
            runPromo(applicationContext, false)
        }
        return Result.success()
    }
}