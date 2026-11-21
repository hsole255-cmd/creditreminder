package com.ahadu.creditreminder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Patterns
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class OtpChallenge(
    val email: String,
    val codeHash: String,
    val saltHex: String,
    val expiresAtMs: Long,
    val attemptsLeft: Int = 5
)

object Auth {
    private const val KEY_EMAIL = "auth_email"
    private const val KEY_EXTRA_INFO = "auth_extra_info"
    private const val KEY_EMAIL_VERIFIED = "auth_email_verified"
    private const val KEY_REG_PASS_HASH = "auth_reg_pass_hash"
    private const val KEY_REG_PASS_SALT = "auth_reg_pass_salt"
    private const val KEY_LOGIN_PIN_HASH = "auth_login_pin_hash"
    private const val KEY_LOGIN_PIN_SALT = "auth_login_pin_salt"
    private const val KEY_LOCK_ENABLED = "auth_lock_enabled"
    private const val KEY_OTP_GATEWAY_URL = "auth_otp_gateway_url"
    private const val OTP_TTL_MS = 10 * 60 * 1000L

    fun isValidEmail(email: String): Boolean {
        val e = email.trim()
        return e.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(e).matches()
    }

    fun isValidLoginPin(pin: String): Boolean {
        val p = pin.trim()
        return (p.length == 4 || p.length == 6) && p.all { it.isDigit() }
    }

    fun savedEmail(c: Context): String =
        Store.getText(c, KEY_EMAIL, "").trim().lowercase()

    fun savedExtraInfo(c: Context): String =
        Store.getText(c, KEY_EXTRA_INFO, "").trim()

    fun isEmailVerified(c: Context): Boolean =
        Store.getBool(c, KEY_EMAIL_VERIFIED, false)

    fun hasLoginPin(c: Context): Boolean =
        Store.getText(c, KEY_LOGIN_PIN_HASH, "").isNotBlank()

    fun isRegistrationComplete(c: Context): Boolean =
        isValidEmail(savedEmail(c)) && isEmailVerified(c) && hasLoginPin(c)

    fun isLockEnabled(c: Context): Boolean =
        hasLoginPin(c) && Store.getBool(c, KEY_LOCK_ENABLED, true)

    fun setLockEnabled(c: Context, enabled: Boolean) {
        Store.setBool(c, KEY_LOCK_ENABLED, enabled)
    }

    fun getOtpGatewayUrl(c: Context): String =
        Store.getText(c, KEY_OTP_GATEWAY_URL, "").trim()

    fun setOtpGatewayUrl(c: Context, url: String) {
        Store.setText(c, KEY_OTP_GATEWAY_URL, url.trim())
    }

    private fun generateSaltHex(): String {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return salt.joinToString("") { "%02x".format(it) }
    }

    private fun hashSecret(secret: String, saltHex: String): String {
        val saltBytes = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val spec = PBEKeySpec(secret.toCharArray(), saltBytes, 10_000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = factory.generateSecret(spec).encoded
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun generateSecureOtp(): String =
        (SecureRandom().nextInt(900_000) + 100_000).toString()

    fun createChallenge(email: String, otpCode: String): OtpChallenge {
        val salt = generateSaltHex()
        return OtpChallenge(
            email = email.trim().lowercase(),
            codeHash = hashSecret(otpCode.trim(), salt),
            saltHex = salt,
            expiresAtMs = System.currentTimeMillis() + OTP_TTL_MS,
            attemptsLeft = 5
        )
    }

    fun verifyChallenge(
        c: Context,
        challenge: OtpChallenge?,
        enteredOtp: String
    ): Pair<Boolean, String> {
        if (challenge == null) return false to "የOTP ጥያቄ አልተገኘም። እንደገና ይላኩ።"
        if (System.currentTimeMillis() > challenge.expiresAtMs) {
            return false to "የOTP ጊዜው አልፏል። አዲስ OTP ይላኩ።"
        }
        if (challenge.attemptsLeft <= 0) {
            return false to "የሙከራ ዕድል አልቋል። አዲስ OTP ይላኩ።"
        }
        val cleaned = enteredOtp.trim()
        if (cleaned.length != 6 || !cleaned.all { it.isDigit() }) {
            return false to "ባለ 6 አሃዝ የOTP ኮድ ያስገቡ።"
        }
        val matches = hashSecret(cleaned, challenge.saltHex) == challenge.codeHash
        return if (matches) {
            Store.setBool(c, KEY_EMAIL_VERIFIED, true)
            true to "ኢሜይል በትክክል ተረጋግጧል።"
        } else {
            false to "የተሳሳተ የOTP ኮድ ነው።"
        }
    }

    fun savePendingRegistration(
        c: Context,
        email: String,
        extraInfo: String,
        regPassword: String
    ): Boolean {
        if (!isValidEmail(email) || extraInfo.isBlank() || regPassword.length < 6) return false
        val salt = generateSaltHex()
        val hash = hashSecret(regPassword, salt)
        Store.setText(c, KEY_EMAIL, email.trim().lowercase())
        Store.setText(c, KEY_EXTRA_INFO, extraInfo.trim())
        Store.setText(c, KEY_REG_PASS_SALT, salt)
        Store.setText(c, KEY_REG_PASS_HASH, hash)
        Store.setBool(c, KEY_EMAIL_VERIFIED, false)
        return true
    }

    fun setLoginPin(c: Context, pin: String): Boolean {
        if (!isValidLoginPin(pin)) return false
        val salt = generateSaltHex()
        val hash = hashSecret(pin.trim(), salt)
        Store.setText(c, KEY_LOGIN_PIN_SALT, salt)
        Store.setText(c, KEY_LOGIN_PIN_HASH, hash)
        Store.setBool(c, KEY_LOCK_ENABLED, true)
        return true
    }

    fun verifyLoginPin(c: Context, pin: String): Boolean {
        val hash = Store.getText(c, KEY_LOGIN_PIN_HASH, "")
        val salt = Store.getText(c, KEY_LOGIN_PIN_SALT, "")
        if (hash.isBlank() || salt.isBlank()) return true
        return hashSecret(pin.trim(), salt) == hash
    }

    fun dispatchOtpToEmail(c: Context, targetEmail: String, otpCode: String): Pair<Boolean, String> {
        val cleanEmail = targetEmail.trim().lowercase()
        if (!isValidEmail(cleanEmail)) return false to "ትክክለኛ ኢሜይል አይደለም።"

        val gatewayUrl = getOtpGatewayUrl(c)
        if (gatewayUrl.isNotEmpty()) {
            return try {
                val url = URL(gatewayUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                }
                val payload = JSONObject()
                    .put("email", cleanEmail)
                    .put("otp", otpCode)
                    .toString()
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(payload)
                    writer.flush()
                }
                val code = conn.responseCode
                conn.disconnect()
                if (code in 200..299) {
                    true to "OTP ወደ $cleanEmail ተልኳል።"
                } else {
                    false to "ሰርቨሩ መልስ አልሰጠም (HTTP $code)።"
                }
            } catch (ex: Exception) {
                false to "OTP መላክ አልተቻለም፦ ${ex.localizedMessage ?: "የኔትወርክ ስህተት"}"
            }
        }

        val shop = Store.getText(c, "shop", Sms.DEF_SHOP)
        val body = """
            ሰላም፣
            ለ "$shop" (Credit Reminder) ምዝገባ የማረጋገጫ ኮድዎ (OTP)፦ $otpCode
            ይህ ኮድ ለ10 ደቂቃ ብቻ ያገለግላል።
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(cleanEmail))
            putExtra(Intent.EXTRA_SUBJECT, "የምዝገባ ማረጋገጫ ኮድ (OTP) - $shop")
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            c.startActivity(
                Intent.createChooser(intent, "OTP ወደ ኢሜይል ላክ")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true to "የኢሜይል መላኪያ ተከፍቷል። ወደ $cleanEmail ይላኩ።"
        } catch (_: Exception) {
            false to "በስልኩ ላይ የኢሜይል መተግበሪያ አልተገኘም።"
        }
    }

    fun sendBackupToRegisteredEmail(c: Context): Boolean {
        val target = savedEmail(c)
        if (!isValidEmail(target)) return false
        val shop = Store.getText(c, "shop", Sms.DEF_SHOP)
        val body = """
            የ "$shop" የብድር አስታዋሽ ምትኬ (Backup) - ${Sms.stamp()}
            የተመዘገበ ኢሜይል፦ $target
            የተመዘገበ ማስረጃ/መረጃ፦ ${savedExtraInfo(c)}

            የምትኬ ዳታ (JSON)፦
            ${Store.backup(c)}
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(target))
            putExtra(Intent.EXTRA_SUBJECT, "የብድር አስታዋሽ ምትኬ - $shop")
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            c.startActivity(
                Intent.createChooser(intent, "ባክአፕ ወደ ኢሜይል ላክ")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (_: Exception) {
            false
        }
    }
}
