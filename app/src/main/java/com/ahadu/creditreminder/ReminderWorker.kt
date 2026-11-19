package com.ahadu.creditreminder

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

fun runReminders(c: Context, interval: Int = 3): Int {
    val list = Store.load(c)
    val today = Sms.today()
    var sent = 0
    for (i in list.indices) {
        val d = list[i]
        if (d.paid || d.phone.isBlank() || d.amount <= 0) continue
        if (today - d.lastSentDay < interval) continue
        val text = Sms.buildMessage(c, d.amount)
        if (Sms.send(c, d.phone, text)) {
            list[i] = d.copy(lastSentDay = today)
            sent++
        }
        Thread.sleep(1500)
    }
    Store.save(c, list)
    return sent
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        runReminders(applicationContext)
        return Result.success()
    }
}
