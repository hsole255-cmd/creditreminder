package com.ahadu.creditreminder

import android.content.Context
import android.os.Build
import android.telephony.SmsManager

object Sms {
    const val DEF_SHOP = "የእኔ ሱቅ"
    const val DEF_ACCOUNT = "ባንክ: ____ ቁጥር: ____"
    const val DEF_TEMPLATE =
        "ውድ ደንበኛችን፣ ከ{ሱቅ} {መጠን} ብር ቀሪ ብድር አለብዎት። እባክዎ ይክፈሉ፦\n{አካውንት}\nእናመሰግናለን።"

    fun today(): Long = (System.currentTimeMillis() + 3L * 3600000L) / 86400000L

    fun normalize(p: String): String {
        val s = p.replace(Regex("[\\s\\-()]"), "")
        return when {
            s.startsWith("+251") -> s
            s.startsWith("251") -> "+$s"
            s.startsWith("0") -> "+251" + s.substring(1)
            s.length == 9 -> "+251$s"
            else -> s
        }
    }

    fun buildMessage(c: Context, amount: Long): String {
        val shop = Store.getText(c, "shop", DEF_SHOP)
        val account = Store.getText(c, "account", DEF_ACCOUNT)
        val template = Store.getText(c, "template", DEF_TEMPLATE)
        return template
            .replace("{ሱቅ}", shop)
            .replace("{መጠን}", String.format("%,d", amount))
            .replace("{አካውንት}", account)
    }

    @Suppress("DEPRECATION")
    private fun manager(c: Context): SmsManager =
        if (Build.VERSION.SDK_INT >= 31) c.getSystemService(SmsManager::class.java)
        else SmsManager.getDefault()

    fun send(c: Context, phone: String, text: String): Boolean {
        return try {
            val sm = manager(c)
            val parts = sm.divideMessage(text)
            sm.sendMultipartTextMessage(normalize(phone), null, parts, null, null)
            true
        } catch (e: Exception) {
            false
        }
    }
}
