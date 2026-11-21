package com.ahadu.creditreminder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

val ProBg = Color(0xFF0B0F17)
val ProSurface = Color(0xFF131A26)
val ProCard = Color(0xFF192232)
val NeonGreen = Color(0xFF22E584)
val NeonBlue = Color(0xFF38BDF8)
val NeonPurple = Color(0xFFA855F7)
val TextMuted = Color(0xFF94A3B8)
val RedSoft = Color(0xFF3A161C)
val RedDark = Color(0xFFFF6B81)
val GreenSoft = Color(0xFF103324)
val GreenDark = Color(0xFF22E584)

private val ProScheme = darkColorScheme(
    primary = NeonGreen,
    onPrimary = Color(0xFF062113),
    secondary = NeonBlue,
    onSecondary = Color(0xFF061A29),
    background = ProBg,
    onBackground = Color.White,
    surface = ProSurface,
    onSurface = Color.White
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleDaily(this)
        setContent {
            MaterialTheme(colorScheme = ProScheme) { App() }
        }
    }
}

fun scheduleDaily(c: Context) {
    val dayMs = 86400000L
    val nowEat = System.currentTimeMillis() + 3L * 3600000L
    var target = (nowEat / dayMs) * dayMs + 8L * 3600000L
    if (target <= nowEat) target += dayMs
    val delay = target - nowEat
    val req = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .build()
    WorkManager.getInstance(c).enqueueUniquePeriodicWork(
        "daily_reminder", ExistingPeriodicWorkPolicy.KEEP, req
    )
}

fun toast(c: Context, msg: String) {
    Toast.makeText(c, msg, Toast.LENGTH_LONG).show()
}

fun parseCustomers(text: String): List<Customer> {
    val out = mutableListOf<Customer>()
    for (line in text.lines()) {
        val l = line.trim()
        if (l.isEmpty()) continue
        val idx = l.indexOfFirst { it.isDigit() || it == '+' }
        val name = (if (idx < 0) l else l.substring(0, idx)).trim().trimEnd(',', ':', '-').trim()
        val phone = if (idx < 0) "" else l.substring(idx).filter { it.isDigit() || it == '+' }
        if (name.isNotEmpty()) out.add(Customer(name = name, phone = phone))
    }
    return out
}

@Composable
fun proFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NeonGreen,
    unfocusedBorderColor = Color(0xFF2B3850),
    focusedLabelColor = NeonGreen,
    unfocusedLabelColor = TextMuted,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = NeonGreen
)

@Composable
fun App() {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var unlocked by remember { mutableStateOf(!Auth.isLockEnabled(ctx)) }

    DisposableEffect(lifecycleOwner, ctx) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && Auth.isLockEnabled(ctx)) {
                unlocked = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!unlocked && Auth.isLockEnabled(ctx)) {
        AuthFlowScreen(ctx = ctx, onUnlocked = { unlocked = true })
        return
    }

    val debtors = remember { mutableStateListOf<Debtor>().apply { addAll(Store.load(ctx)) } }
    val customers = remember { mutableStateListOf<Customer>().apply { addAll(Store.loadCustomers(ctx)) } }
    var tab by remember { mutableStateOf(0) }

    val tabs = listOf("💳" to "ብድር", "👥" to "ደንበኞች", "📜" to "ታሪክ", "⚙️" to "ቅንብር")

    Scaffold(
        containerColor = ProBg,
        bottomBar = {
            NavigationBar(containerColor = ProSurface) {
                tabs.forEachIndexed { i, p ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(p.first, fontSize = 20.sp) },
                        label = { Text(p.second) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NeonGreen,
                            selectedTextColor = NeonGreen,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                            indicatorColor = Color(0xFF1D2A3E)
                        )
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> DebtorsScreen(ctx, debtors)
                1 -> CustomersScreen(ctx, customers)
                2 -> HistoryScreen(ctx)
                else -> SettingsScreen(ctx)
            }
        }
    }
}

@Composable
fun Header(title: String, sub: String) {
    Column(
        Modifier.fillMaxWidth().background(ProSurface).padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Text(title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text(sub, color = NeonBlue, fontSize = 13.sp)
    }
}

@Composable
fun Chip(text: String, bg: Color, fg: Color) {
    Box(
        Modifier.background(bg, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun AuthFlowScreen(
    ctx: Context,
    onUnlocked: () -> Unit
) {
    val registeredComplete = remember { Auth.isRegistrationComplete(ctx) }
    var mode by remember {
        mutableStateOf(
            when {
                registeredComplete -> "LOGIN"
                Auth.savedEmail(ctx).isNotBlank() && !Auth.isEmailVerified(ctx) -> "OTP"
                Auth.isEmailVerified(ctx) && !Auth.hasLoginPin(ctx) -> "SET_PIN"
                else -> "REGISTER"
            }
        )
    }

    var email by remember { mutableStateOf(Auth.savedEmail(ctx)) }
    var extraInfo by remember { mutableStateOf(Auth.savedExtraInfo(ctx)) }
    var regPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var challenge by remember { mutableStateOf<OtpChallenge?>(null) }
    var otpInput by remember { mutableStateOf("") }
    var sendingOtp by remember { mutableStateOf(false) }

    var loginPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun sendOtpNow(targetEmail: String) {
        val code = Auth.generateSecureOtp()
        val newChallenge = Auth.createChallenge(targetEmail, code)
        challenge = newChallenge
        sendingOtp = true
        Thread {
            val (ok, msg) = Auth.dispatchOtpToEmail(ctx, targetEmail, code)
            Handler(Looper.getMainLooper()).post {
                sendingOtp = false
                if (ok) {
                    errorMsg = null
                    mode = "OTP"
                    toast(ctx, msg)
                } else {
                    errorMsg = msg
                }
            }
        }.start()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ProBg)
    ) {
        Header(
            title = when (mode) {
                "REGISTER" -> "ምዝገባ (Register)"
                "OTP" -> "የኢሜይል OTP ማረጋገጫ"
                "SET_PIN" -> "የመግቢያ ፓስወርድ/PIN ያዘጋጁ"
                else -> "መግቢያ"
            },
            sub = when (mode) {
                "REGISTER" -> "ኢሜይል፣ ማስረጃ/መረጃ እና ፓስወርድ ያስገቡ"
                "OTP" -> "ወደ ${email.trim()} የተላከውን 6-አሃዝ OTP አስገብተው Verify ይበሉ"
                "SET_PIN" -> "ሁሌም ሲገቡ የሚጠቀሙበት ባለ 4 ወይም 6 አሃዝ ፓስወርድ"
                else -> "የመግቢያ ፓስወርድ/PIN ያስገቡ"
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = ProCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (mode) {
                        "REGISTER" -> {
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it; errorMsg = null },
                                label = { Text("ኢሜይል (ለOTP እና ለባክአፕ)") },
                                singleLine = true,
                                colors = proFieldColors(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = extraInfo,
                                onValueChange = { extraInfo = it; errorMsg = null },
                                label = { Text("ተጨማሪ ማስረጃ / መረጃ (የሱቅ ስም፣ ስልክ ወይም መታወቂያ)") },
                                colors = proFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = regPassword,
                                onValueChange = { regPassword = it; errorMsg = null },
                                label = { Text("የምዝገባ ፓስወርድ (ቢያንስ 6 ቁምፊ)") },
                                singleLine = true,
                                colors = proFieldColors(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it; errorMsg = null },
                                label = { Text("ፓስወርድ ያረጋግጡ (Confirm)") },
                                singleLine = true,
                                colors = proFieldColors(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Button(
                                onClick = {
                                    when {
                                        !Auth.isValidEmail(email) -> errorMsg = "ትክክለኛ ኢሜይል ያስገቡ።"
                                        extraInfo.isBlank() -> errorMsg = "ተጨማሪ ማስረጃ/መረጃ ያስገቡ።"
                                        regPassword.length < 6 -> errorMsg = "ፓስወርድ ቢያንስ 6 ቁምፊ መሆን አለበት።"
                                        regPassword != confirmPassword -> errorMsg = "ፓስወርዶቹ አይመሳሰሉም።"
                                        Auth.savePendingRegistration(ctx, email, extraInfo, regPassword) -> {
                                            sendOtpNow(email)
                                        }
                                        else -> errorMsg = "ምዝገባ ማስቀመጥ አልተቻለም።"
                                    }
                                },
                                enabled = !sendingOtp,
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (sendingOtp) "OTP በመላክ ላይ..." else "Register እና OTP ላክ",
                                    color = Color(0xFF062113),
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (Auth.isRegistrationComplete(ctx)) {
                                TextButton(onClick = { mode = "LOGIN"; errorMsg = null }) {
                                    Text("ቀድሞ የተመዘገበ መለያ አለኝ (Login)", color = NeonBlue)
                                }
                            }
                        }

                        "OTP" -> {
                            Text("የተላከበት ኢሜይል፦ ${email.trim()}", fontWeight = FontWeight.SemiBold, color = Color.White)
                            OutlinedTextField(
                                value = otpInput,
                                onValueChange = { otpInput = it; errorMsg = null },
                                label = { Text("የ6-አሃዝ OTP ኮድ ያስገቡ") },
                                singleLine = true,
                                colors = proFieldColors(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { sendOtpNow(email) },
                                    enabled = !sendingOtp
                                ) {
                                    Text(if (sendingOtp) "በመላክ ላይ..." else "OTP እንደገና ላክ", color = NeonBlue)
                                }
                                Button(
                                    onClick = {
                                        val (ok, msg) = Auth.verifyChallenge(ctx, challenge, otpInput)
                                        if (ok) {
                                            errorMsg = null
                                            otpInput = ""
                                            mode = "SET_PIN"
                                            toast(ctx, msg)
                                        } else {
                                            errorMsg = msg
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                                ) {
                                    Text("Verify", color = Color(0xFF062113), fontWeight = FontWeight.Bold)
                                }
                            }
                            TextButton(onClick = { mode = "REGISTER"; errorMsg = null }) {
                                Text("ኢሜይል / መረጃ ቀይር", color = TextMuted)
                            }
                        }

                        "SET_PIN" -> {
                            OutlinedTextField(
                                value = loginPin,
                                onValueChange = { loginPin = it; errorMsg = null },
                                label = { Text("የመግቢያ ፓስወርድ/PIN (4 ወይም 6 አሃዝ)") },
                                singleLine = true,
                                colors = proFieldColors(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = confirmPin,
                                onValueChange = { confirmPin = it; errorMsg = null },
                                label = { Text("የመግቢያ ፓስወርድ/PIN ያረጋግጡ") },
                                singleLine = true,
                                colors = proFieldColors(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    when {
                                        !Auth.isValidLoginPin(loginPin) ->
                                            errorMsg = "ባለ 4 ወይም 6 አሃዝ ቁጥር ብቻ ያስገቡ።"
                                        loginPin != confirmPin ->
                                            errorMsg = "ፓስወርዶቹ አይመሳሰሉም።"
                                        Auth.setLoginPin(ctx, loginPin) -> {
                                            errorMsg = null
                                            loginPin = ""
                                            confirmPin = ""
                                            toast(ctx, "ምዝገባ ተጠናቋል!")
                                            onUnlocked()
                                        }
                                        else -> errorMsg = "ማስቀመጥ አልተቻለም።"
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("አጠናቅቅ እና ግባ", color = Color(0xFF062113), fontWeight = FontWeight.Bold)
                            }
                        }

                        else -> {
                            Text(
                                text = "የባክአፕ ኢሜይል፦ ${Auth.savedEmail(ctx)}",
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                            OutlinedTextField(
                                value = loginPin,
                                onValueChange = { loginPin = it; errorMsg = null },
                                label = { Text("የመግቢያ ፓስወርድ/PIN (4 ወይም 6 አሃዝ)") },
                                singleLine = true,
                                colors = proFieldColors(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    if (Auth.verifyLoginPin(ctx, loginPin)) {
                                        errorMsg = null
                                        loginPin = ""
                                        onUnlocked()
                                    } else {
                                        errorMsg = "የተሳሳተ የመግቢያ ፓስወርድ/PIN ነው።"
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("ግባ", color = Color(0xFF062113), fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (errorMsg != null) {
                        Text(
                            text = errorMsg!!,
                            color = RedDark,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
