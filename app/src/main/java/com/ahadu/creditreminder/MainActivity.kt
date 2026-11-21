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
                0 -> DashboardScreen(ctx, debtors, customers, { tab = 1 }, { tab = 2 })
                1 -> CreditScreen(ctx, debtors)
                2 -> CustomersScreen(ctx, customers)
                3 -> HistoryScreen(ctx)
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

@Composable
fun DebtorsScreen(ctx: Context, debtors: MutableList<Debtor>) {
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        toast(ctx, if (granted) "ፈቃዱ ተሰጥቷል። እባክዎ መላኪያውን እንደገና ይጫኑ።" else "SMS ለመላክ ፈቃድ ያስፈልጋል።")
    }
    var showAdd by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var payFor by remember { mutableStateOf<Debtor?>(null) }
    var sending by remember { mutableStateOf(false) }
    val open = debtors.filter { !it.paid }
    val total = open.sumOf { it.amount }

    fun persist() {
        Store.save(ctx, debtors.toList())
    }

    fun startSend() {
        if (ctx.checkSelfPermission(Manifest.permission.SEND_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permLauncher.launch(Manifest.permission.SEND_SMS)
            return
        }
        sending = true
        Thread {
            val n = runReminders(ctx)
            Handler(Looper.getMainLooper()).post {
                debtors.clear()
                debtors.addAll(Store.load(ctx))
                sending = false
                toast(ctx, "ተልኳል: $n")
            }
        }.start()
    }

    Column(Modifier.fillMaxSize().background(ProBg)) {
        Header("የብድር አስታዋሽ", "ያልተከፈሉ: ${open.size} ሰዎች")
        Card(
            Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ProCard)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("ጠቅላላ ቀሪ ብድር", color = TextMuted, fontSize = 13.sp)
                Text(
                    String.format("%,d", total) + " ብር",
                    color = NeonGreen, fontSize = 32.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showAdd = true },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                    ) { Text("+ አክል", color = Color(0xFF062113), fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = { confirm = true },
                        enabled = !sending
                    ) { Text(if (sending) "በመላክ ላይ..." else "አሁን ላክ", color = NeonBlue) }
                }
            }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            items(debtors.sortedBy { it.paid }, key = { it.id }) { d ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = ProCard)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(d.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            if (d.paid) Chip("ተከፍሏል", GreenSoft, GreenDark)
                            else Chip("ያልተከፈለ", RedSoft, RedDark)
                        }
                        Text(
                            if (d.phone.isBlank()) "📵 ስልክ የለም" else "📞 " + d.phone,
                            color = TextMuted, fontSize = 13.sp
                        )
                        Text(
                            String.format("%,d", d.amount) + " ብር",
                            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = NeonGreen
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (!d.paid) {
                                FilledTonalButton(onClick = { payFor = d }) { Text("ክፍያ") }
                            }
                            OutlinedButton(onClick = {
                                val i = debtors.indexOfFirst { it.id == d.id }
                                if (i >= 0) {
                                    debtors[i] = d.copy(paid = !d.paid)
                                    persist()
                                }
                            }) { Text(if (d.paid) "መልስ" else "ተከፍሏል", color = NeonBlue) }
                            TextButton(onClick = {
                                debtors.removeAll { it.id == d.id }
                                persist()
                            }) { Text("ሰርዝ", color = RedDark) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (confirm) {
        val today = Sms.today()
        val due = debtors.filter {
            !it.paid && it.phone.isNotBlank() && it.amount > 0 && today - it.lastSentDay >= 3
        }
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = ProSurface,
            title = { Text("መልእክት ይላክ?", color = Color.White) },
            text = {
                Column {
                    Text(
                        "${due.size} ሰዎች ይላክላቸዋል (በአንድ ጊዜ ከ8 አይበልጥም)። ወጪው ከስልክዎ ሲም ይቆረጣል።",
                        color = Color.White
                    )
                    if (due.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("ምሳሌ:", color = TextMuted, fontSize = 12.sp)
                        Text(Sms.buildMessage(ctx, due[0].amount), fontSize = 13.sp, color = NeonBlue)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    if (due.isEmpty()) {
                        toast(ctx, "የሚላክ የለም")
                    } else {
                        startSend()
                    }
                }) { Text("ላክ", color = NeonGreen) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("ተው", color = TextMuted) }
            }
        )
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            containerColor = ProSurface,
            title = { Text("አዲስ ዕዳ", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("ስም") }, singleLine = true, colors = proFieldColors()
                    )
                    OutlinedTextField(
                        value = phone, onValueChange = { phone = it },
                        label = { Text("ስልክ (09...)") }, singleLine = true, colors = proFieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    OutlinedTextField(
                        value = amount, onValueChange = { amount = it },
                        label = { Text("ብር") }, singleLine = true, colors = proFieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val a = amount.filter { it in '0'..'9' }.toLongOrNull()
                    if (name.isNotBlank() && a != null && a > 0) {
                        debtors.add(Debtor(name = name.trim(), phone = phone.trim(), amount = a))
                        persist()
                        showAdd = false
                    } else {
                        toast(ctx, "ስም እና ብር ያስገቡ")
                    }
                }) { Text("አስቀምጥ", color = NeonGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("ተው", color = TextMuted) }
            }
        )
    }

    val pd = payFor
    if (pd != null) {
        var txt by remember(pd.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { payFor = null },
            containerColor = ProSurface,
            title = { Text("ክፍያ: ${pd.name}", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ቀሪ: ${String.format("%,d", pd.amount)} ብር", color = NeonGreen)
                    OutlinedTextField(
                        value = txt, onValueChange = { txt = it },
                        label = { Text("የተከፈለ ብር") }, singleLine = true, colors = proFieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val p = txt.filter { it in '0'..'9' }.toLongOrNull()
                    if (p != null && p > 0 && p <= pd.amount) {
                        val i = debtors.indexOfFirst { it.id == pd.id }
                        if (i >= 0) {
                            val left = pd.amount - p
                            Store.addPayment(
                                ctx,
                                PaymentEntry(
                                    debtorId = pd.id,
                                    customerName = pd.name,
                                    amount = p,
                                    time = Sms.stamp()
                                )
                            )
                            debtors[i] = if (left == 0L) pd.copy(amount = 0L, paid = true)
                            else pd.copy(amount = left)
                            persist()
                        }
                        payFor = null
                    } else {
                        toast(
                            ctx,
                            if (p != null && p > pd.amount) "ክፍያው ከቀሪ ብድሩ መብለጥ አይችልም"
                            else "ትክክለኛ የክፍያ መጠን ያስገቡ"
                        )
                    }
                }) { Text("አስቀምጥ", color = NeonGreen) }
            },
            dismissButton = {
                TextButton(onClick = { payFor = null }) { Text("ተው", color = TextMuted) }
            }
        )
    }
}

@Composable
fun CustomersScreen(ctx: Context, customers: MutableList<Customer>) {
    val promoPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        toast(ctx, if (granted) "ፈቃዱ ተሰጥቷል። መላኪያውን እንደገና ይጫኑ።" else "SMS ፈቃድ አልተሰጠም።")
    }
    var showAdd by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var promoText by remember { mutableStateOf(Store.getText(ctx, "promoText", Sms.DEF_PROMO)) }
    var interval by remember { mutableStateOf(Store.getInt(ctx, "promoInterval", 14)) }
    var batch by remember { mutableStateOf(Store.getInt(ctx, "promoBatch", 10)) }
    var auto by remember { mutableStateOf(Store.getBool(ctx, "promoAuto", false)) }
    var sending by remember { mutableStateOf(false) }

    fun persist() {
        Store.saveCustomers(ctx, customers.toList())
    }

    fun saveSettings() {
        Store.setText(ctx, "promoText", promoText)
        Store.setInt(ctx, "promoInterval", interval)
        Store.setInt(ctx, "promoBatch", batch)
    }

    fun startSend() {
        if (ctx.checkSelfPermission(Manifest.permission.SEND_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            promoPermission.launch(Manifest.permission.SEND_SMS)
            return
        }
        saveSettings()
        sending = true
        Thread {
            val n = runPromo(ctx, true)
            Handler(Looper.getMainLooper()).post {
                sending = false
                toast(ctx, "ተልኳል: $n")
            }
        }.start()
    }

    LazyColumn(Modifier.fillMaxSize().background(ProBg)) {
        item { Header("ደንበኞች", "ጠቅላላ: ${customers.size} ሰዎች") }
        item {
            Card(
                Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = ProCard)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📣 የማስታወቂያ መልእክት", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color.White)
                    OutlinedTextField(
                        value = promoText, onValueChange = { promoText = it },
                        label = { Text("መልእክት") }, colors = proFieldColors(), modifier = Modifier.fillMaxWidth()
                    )
                    Text("በመልእክቱ ውስጥ፦ {ስም} {ሱቅ}", color = TextMuted, fontSize = 12.sp)
                    Text("በየስንት ቀን ይደገም", fontSize = 13.sp, color = Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(14, 21).forEach { d ->
                            if (interval == d) {
                                Button(
                                    onClick = { interval = d },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                                ) { Text("$d ቀን", color = Color(0xFF062113), fontWeight = FontWeight.Bold) }
                            } else {
                                OutlinedButton(onClick = { interval = d }) { Text("$d ቀን", color = NeonBlue) }
                            }
                        }
                    }
                    Text("በአንድ ጊዜ የሚላክ ብዛት", fontSize = 13.sp, color = Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 20).forEach { b ->
                            if (batch == b) {
                                Button(
                                    onClick = { batch = b },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                                ) { Text("$b", color = Color(0xFF062113), fontWeight = FontWeight.Bold) }
                            } else {
                                OutlinedButton(onClick = { batch = b }) { Text("$b", color = NeonBlue) }
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("በራስ-ሰር ላክ", color = Color.White)
                        Switch(
                            checked = auto,
                            onCheckedChange = {
                                auto = it
                                Store.setBool(ctx, "promoAuto", it)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = NeonGreen, checkedTrackColor = GreenSoft)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                saveSettings()
                                toast(ctx, "ተቀምጧል")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                        ) { Text("አስቀምጥ", color = Color(0xFF062113), fontWeight = FontWeight.Bold) }
                        OutlinedButton(
                            onClick = { confirm = true },
                            enabled = !sending
                        ) { Text(if (sending) "በመላክ ላይ..." else "አሁን ላክ", color = NeonBlue) }
                    }
                }
            }
        }
        item {
            Row(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showAdd = true },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                ) { Text("+ ደንበኛ", color = Color(0xFF062113), fontWeight = FontWeight.Bold) }
                OutlinedButton(onClick = { showImport = true }) { Text("📋 ዝርዝር ለጥፍ", color = NeonBlue) }
            }
            Spacer(Modifier.height(8.dp))
        }
        items(customers, key = { it.id }) { cu ->
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = ProCard)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(cu.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color.White)
                        Text(
                            if (cu.phone.isBlank()) "📵 ስልክ የለም" else cu.phone,
                            color = TextMuted, fontSize = 13.sp
                        )
                    }
                    Switch(
                        checked = !cu.promoOff,
                        onCheckedChange = { on ->
                            val i = customers.indexOfFirst { it.id == cu.id }
                            if (i >= 0) {
                                customers[i] = cu.copy(promoOff = !on)
                                persist()
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = NeonGreen, checkedTrackColor = GreenSoft)
                    )
                    TextButton(onClick = {
                        customers.removeAll { it.id == cu.id }
                        persist()
                    }) { Text("✕", color = RedDark) }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    if (confirm) {
        val total = customers.count { !it.promoOff && it.phone.isNotBlank() }
        val shown = if (total < batch) total else batch
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = ProSurface,
            title = { Text("ማስታወቂያ ይላክ?", color = Color.White) },
            text = {
                Column {
                    Text(
                        "እስከ $shown ሰዎች ይላክላቸዋል (ከጠቅላላ $total)። ወጪው ከስልክዎ ሲም ይቆረጣል።",
                        color = Color.White
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("ምሳሌ:", color = TextMuted, fontSize = 12.sp)
                    Text(
                        promoText.replace("{ስም}", "ደንበኛ")
                            .replace("{ሱቅ}", Store.getText(ctx, "shop", Sms.DEF_SHOP)),
                        fontSize = 13.sp,
                        color = NeonBlue
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    if (total == 0) {
                        toast(ctx, "የሚላክ ደንበኛ የለም")
                    } else {
                        startSend()
                    }
                }) { Text("ላክ", color = NeonGreen) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("ተው", color = TextMuted) }
            }
        )
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            containerColor = ProSurface,
            title = { Text("አዲስ ደንበኛ", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("ስም") }, singleLine = true, colors = proFieldColors()
                    )
                    OutlinedTextField(
                        value = phone, onValueChange = { phone = it },
                        label = { Text("ስልክ (09...)") }, singleLine = true, colors = proFieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        customers.add(Customer(name = name.trim(), phone = phone.trim()))
                        persist()
                        showAdd = false
                    } else {
                        toast(ctx, "ስም ያስገቡ")
                    }
                }) { Text("አስቀምጥ", color = NeonGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("ተው", color = TextMuted) }
            }
        )
    }

    if (showImport) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImport = false },
            containerColor = ProSurface,
            title = { Text("ዝርዝር ለጥፍ", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("እያንዳንዱ ሰው በአንድ መስመር፦ ስም እና ስልክ", fontSize = 13.sp, color = TextMuted)
                    OutlinedTextField(
                        value = text, onValueChange = { text = it },
                        label = { Text("ዝርዝር") }, colors = proFieldColors(), modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = parseCustomers(text)
                    val have = customers.map { Sms.normalize(it.phone) }.toMutableSet()
                    var added = 0
                    for (p in parsed) {
                        val n = Sms.normalize(p.phone)
                        if (p.phone.isNotBlank() && n in have) continue
                        customers.add(p)
                        have.add(n)
                        added++
                    }
                    persist()
                    showImport = false
                    toast(ctx, "ተጨምረዋል: $added")
                }) { Text("አስገባ", color = NeonGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showImport = false }) { Text("ተው", color = TextMuted) }
            }
        )
    }
}

@Composable
fun HistoryScreen(ctx: Context) {
    val log = remember { Store.loadLog(ctx) }
    Column(Modifier.fillMaxSize().background(ProBg)) {
        Header("የላኩ ታሪክ", "የመጨረሻዎቹ ${log.size} መልእክቶች")
        if (log.isEmpty()) {
            Text("እስካሁን ምንም አልተላከም", color = TextMuted, modifier = Modifier.padding(20.dp))
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            items(log) { e ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = ProCard)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                (if (e.ok) "✅ " else "❌ ") + e.name,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(e.time, color = TextMuted, fontSize = 12.sp)
                        }
                        Chip(e.kind, GreenSoft, GreenDark)
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun SettingsScreen(ctx: Context) {
    var shop by remember { mutableStateOf(Store.getText(ctx, "shop", Sms.DEF_SHOP)) }
    var account by remember { mutableStateOf(Store.getText(ctx, "account", Sms.DEF_ACCOUNT)) }
    var template by remember { mutableStateOf(Store.getText(ctx, "template", Sms.DEF_TEMPLATE)) }
    var showRestore by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(ProBg)) {
        Header("ቅንብር", "የሱቅ መረጃ እና ምትኬ")
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = shop, onValueChange = { shop = it },
                label = { Text("የሱቅ ስም") }, colors = proFieldColors(), modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = account, onValueChange = { account = it },
                label = { Text("የመክፈያ አካውንት") }, colors = proFieldColors(), modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = template, onValueChange = { template = it },
                label = { Text("የብድር መልእክት ጽሑፍ") }, colors = proFieldColors(), modifier = Modifier.fillMaxWidth()
            )
            Text("በመልእክቱ ውስጥ፦ {ሱቅ} {መጠን} {አካውንት}", color = TextMuted, fontSize = 12.sp)
            Button(
                onClick = {
                    Store.setText(ctx, "shop", shop)
                    Store.setText(ctx, "account", account)
                    Store.setText(ctx, "template", template)
                    toast(ctx, "ተቀምጧል")
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
            ) { Text("አስቀምጥ", color = Color(0xFF062113), fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(12.dp))
            Text(
                "የተመዘገቡ ክፍያዎች: ${Store.loadPayments(ctx).size}",
                color = NeonGreen,
                fontWeight = FontWeight.SemiBold
            )
            if (Auth.savedEmail(ctx).isNotBlank()) {
                Text(
                    "የባክአፕ ኢሜይል: ${Auth.savedEmail(ctx)}",
                    color = NeonBlue,
                    fontSize = 13.sp
                )
            }
            Text("💾 ምትኬ", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color.White)
            Text(
                "መረጃው በዚህ ስልክ ላይ ብቻ ነው የሚቀመጠው። ምትኬን ወደ የተመዘገበ ኢሜይል፣ Telegram ወይም Gmail ልከው ያስቀምጡ።",
                color = TextMuted, fontSize = 13.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (!Auth.sendBackupToRegisteredEmail(ctx)) {
                            toast(ctx, "የተመዘገበ ኢሜይል አልተገኘም")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                ) { Text("ምትኬ ወደ ኢሜይል", color = Color(0xFF062113), fontWeight = FontWeight.Bold) }
                OutlinedButton(onClick = {
                    val i = Intent(Intent.ACTION_SEND)
                    i.type = "text/plain"
                    i.putExtra(Intent.EXTRA_TEXT, Store.backup(ctx))
                    ctx.startActivity(Intent.createChooser(i, "ምትኬ"))
                }) { Text("ምትኬ አጋራ", color = NeonBlue) }
                OutlinedButton(onClick = { showRestore = true }) { Text("መልስ", color = NeonPurple) }
            }
        }
    }

    if (showRestore) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRestore = false },
            containerColor = ProSurface,
            title = { Text("ምትኬ መልስ", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    label = { Text("የምትኬ ጽሑፍ ይለጥፉ") }, colors = proFieldColors(), modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (Store.restore(ctx, text)) {
                        showRestore = false
                        toast(ctx, "ተመልሷል። መተግበሪያውን ዘግተው እንደገና ይክፈቱ")
                    } else {
                        toast(ctx, "ጽሑፉ ትክክል አይደለም")
                    }
                }) { Text("መልስ", color = NeonGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showRestore = false }) { Text("ተው", color = TextMuted) }
            }
        )
    }
}

