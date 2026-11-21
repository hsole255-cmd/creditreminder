package com.ahadu.creditreminder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (applicationContext.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return Result.success()
        }
        return try {
            if (Store.getBool(applicationContext, "autoRemind", true)) {
                runReminders(applicationContext)
            }
            if (Store.getBool(applicationContext, "promoAuto", false)) {
                runPromo(applicationContext, false)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}