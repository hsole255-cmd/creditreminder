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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

val Teal = Color(0xFF00695C)
val Amber = Color(0xFFFFB300)
val BgColor = Color(0xFFF4F7F6)
val RedSoft = Color(0xFFFFEBEE)
val RedDark = Color(0xFFC62828)
val GreenSoft = Color(0xFFE8F5E9)
val GreenDark = Color(0xFF2E7D32)

private val Scheme = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    secondary = Amber,
    background = BgColor,
    surface = Color.White
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleDaily(this)
        setContent {
            MaterialTheme(colorScheme = Scheme) { App() }
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
fun App() {
    val ctx = LocalContext.current
    val debtors = remember { mutableStateListOf<Debtor>().apply { addAll(Store.load(ctx)) } }
    val customers = remember { mutableStateListOf<Customer>().apply { addAll(Store.loadCustomers(ctx)) } }
    var tab by remember { mutableStateOf(0) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) { permLauncher.launch(Manifest.permission.SEND_SMS) }

    val tabs = listOf("💳" to "ብድር", "👥" to "ደንበኞች", "📜" to "ታሪክ", "⚙️" to "ቅንብር")

    Scaffold(
        containerColor = BgColor,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                tabs.forEachIndexed { i, p ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(p.first, fontSize = 20.sp) },
                        label = { Text(p.second) }
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
        Modifier.fillMaxWidth().background(Teal).padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(sub, color = Color(0xFFB2DFDB), fontSize = 13.sp)
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
fun DebtorsScreen(ctx: Context, debtors: MutableList<Debtor>) {
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
        sending = true
        Thread {
            val n = runReminders(ctx)
            Handler(Looper.getMainLooper()).post {
                debtors.clear()
                debtors.addAll(Store.load(ctx))
                sending = false
                toast(ctx, "ተልኳል: " + n)
            }
        }.start()
    }

    Column(Modifier.fillMaxSize()) {
        Header("የብድር አስታዋሽ", "ያልተከፈሉ: " + open.size + " ሰዎች")
        Card(
            Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("ጠቅላላ ቀሪ ብድር", color = Color.Gray, fontSize = 13.sp)
                Text(
                    String.format("%,d", total) + " ብር",
                    color = Teal, fontSize = 32.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showAdd = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Teal)
                    ) { Text("+ አክል") }
                    OutlinedButton(
                        onClick = { confirm = true },
                        enabled = !sending
                    ) { Text(if (sending) "በመላክ ላይ..." else "አሁን ላክ") }
                }
            }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            items(debtors.sortedBy { it.paid }, key = { it.id }) { d ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(d.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            if (d.paid) Chip("ተከፍሏል", GreenSoft, GreenDark)
                            else Chip("ያልተከፈለ", RedSoft, RedDark)
                        }
                        Text(
                            if (d.phone.isBlank()) "📵 ስልክ የለም" else "📞 " + d.phone,
                            color = Color.Gray, fontSize = 13.sp
                        )
                        Text(
                            String.format("%,d", d.amount) + " ብር",
                            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Teal
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
                            }) { Text(if (d.paid) "መልስ" else "ተከፍሏል") }
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
            title = { Text("መልእክት ይላክ?") },
            text = {
                Column {
                    Text(due.size.toString() + " ሰዎች ይላክላቸዋል (በአንድ ጊዜ ከ8 አይበልጥም)። ወጪው ከስልክዎ ሲም ይቆረጣል።")
                    if (due.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("ምሳሌ:", color = Color.Gray, fontSize = 12.sp)
                        Text(Sms.buildMessage(ctx, due[0].amount), fontSize = 13.sp)
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
                }) { Text("ላክ") }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("ተው") }
            }
        )
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("አዲስ ዕዳ") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("ስም") }, singleLine = true
                    )
                    OutlinedTextField(
                        value = phone, onValueChange = { phone = it },
                        label = { Text("ስልክ (09...)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    OutlinedTextField(
                        value = amount, onValueChange = { amount = it },
                        label = { Text("ብር") }, singleLine = true,
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
                }) { Text("አስቀምጥ") }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("ተው") }
            }
        )
    }

    val pd = payFor
    if (pd != null) {
        var txt by remember(pd.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { payFor = null },
            title = { Text("ክፍያ: " + pd.name) },
            text = {
                Column {
                    Text("ቀሪ: " + String.format("%,d", pd.amount) + " ብር")
                    OutlinedTextField(
                        value = txt, onValueChange = { txt = it },
                        label = { Text("የተከፈለ ብር") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val p = txt.filter { it in '0'..'9' }.toLongOrNull()
                    if (p != null && p > 0) {
                        val i = debtors.indexOfFirst { it.id == pd.id }
                        if (i >= 0) {
                            val left = pd.amount - p
                            debtors[i] = if (left <= 0) pd.copy(paid = true) else pd.copy(amount = left)
                            persist()
                        }
                        payFor = null
                    } else {
                        toast(ctx, "ብር ያስገቡ")
                    }
                }) { Text("አስቀምጥ") }
            },
            dismissButton = {
                TextButton(onClick = { payFor = null }) { Text("ተው") }
            }
        )
    }
}

@Composable
fun CustomersScreen(ctx: Context, customers: MutableList<Customer>) {
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
        saveSettings()
        sending = true
        Thread {
            val n = runPromo(ctx, true)
            Handler(Looper.getMainLooper()).post {
                sending = false
                toast(ctx, "ተልኳል: " + n)
            }
        }.start()
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item { Header("ደንበኞች", "ጠቅላላ: " + customers.size + " ሰዎች") }
        item {
            Card(
                Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("📣 የማስታወቂያ መልእክት", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    OutlinedTextField(
                        value = promoText, onValueChange = { promoText = it },
                        label = { Text("መልእክት") }, modifier = Modifier.fillMaxWidth()
                    )
                    Text("በመልእክቱ ውስጥ፦ {ስም} {ሱቅ}", color = Color.Gray, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("በየስንት ቀን ይደገም", fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(14, 21).forEach { d ->
                            if (interval == d) {
                                Button(
                                    onClick = { interval = d },
                                    colors = ButtonDefaults.buttonColors(containerColor = Teal)
                                ) { Text(d.toString() + " ቀን") }
                            } else {
                                OutlinedButton(onClick = { interval = d }) { Text(d.toString() + " ቀን") }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("በአንድ ጊዜ የሚላክ ብዛት", fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 20).forEach { b ->
                            if (batch == b) {
                                Button(
                                    onClick = { batch = b },
                                    colors = ButtonDefaults.buttonColors(containerColor = Teal)
                                ) { Text(b.toString()) }
                            } else {
                                OutlinedButton(onClick = { batch = b }) { Text(b.toString()) }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("በራስ-ሰር ላክ")
                        Switch(checked = auto, onCheckedChange = {
                            auto = it
                            Store.setBool(ctx, "promoAuto", it)
                        })
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                saveSettings()
                                toast(ctx, "ተቀምጧል")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Teal)
                        ) { Text("አስቀምጥ") }
                        OutlinedButton(
                            onClick = { confirm = true },
                            enabled = !sending
                        ) { Text(if (sending) "በመላክ ላይ..." else "አሁን ላክ") }
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
                    colors = ButtonDefaults.buttonColors(containerColor = Teal)
                ) { Text("+ ደንበኛ") }
                OutlinedButton(onClick = { showImport = true }) { Text("📋 ዝርዝር ለጥፍ") }
            }
            Spacer(Modifier.height(8.dp))
        }
        items(customers, key = { it.id }) { cu ->
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(cu.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text(
                            if (cu.phone.isBlank()) "📵 ስልክ የለም" else cu.phone,
                            color = Color.Gray, fontSize = 13.sp
                        )
                    }
                    Switch(checked = !cu.promoOff, onCheckedChange = { on ->
                        val i = customers.indexOfFirst { it.id == cu.id }
                        if (i >= 0) {
                            customers[i] = cu.copy(promoOff = !on)
                            persist()
                        }
                    })
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
            title = { Text("ማስታወቂያ ይላክ?") },
            text = {
                Column {
                    Text("እስከ " + shown + " ሰዎች ይላክላቸዋል (ከጠቅላላ " + total + ")። ወጪው ከስልክዎ ሲም ይቆረጣል።")
                    Spacer(Modifier.height(8.dp))
                    Text("ምሳሌ:", color = Color.Gray, fontSize = 12.sp)
                    Text(
                        promoText.replace("{ስም}", "ደንበኛ")
                            .replace("{ሱቅ}", Store.getText(ctx, "shop", Sms.DEF_SHOP)),
                        fontSize = 13.sp
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
                }) { Text("ላክ") }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("ተው") }
            }
        )
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("አዲስ ደንበኛ") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("ስም") }, singleLine = true
                    )
                    OutlinedTextField(
                        value = phone, onValueChange = { phone = it },
                        label = { Text("ስልክ (09...)") }, singleLine = true,
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
                }) { Text("አስቀምጥ") }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("ተው") }
            }
        )
    }

    if (showImport) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("ዝርዝር ለጥፍ") },
            text = {
                Column {
                    Text("እያንዳንዱ ሰው በአንድ መስመር፦ ስም እና ስልክ", fontSize = 13.sp)
                    OutlinedTextField(
                        value = text, onValueChange = { text = it },
                        label = { Text("ዝርዝር") }, modifier = Modifier.fillMaxWidth()
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
                    toast(ctx, "ተጨምረዋል: " + added)
                }) { Text("አስገባ") }
            },
            dismissButton = {
                TextButton(onClick = { showImport = false }) { Text("ተው") }
            }
        )
    }
}

@Composable
fun HistoryScreen(ctx: Context) {
    val log = remember { Store.loadLog(ctx) }
    Column(Modifier.fillMaxSize()) {
        Header("የላኩ ታሪክ", "የመጨረሻዎቹ " + log.size + " መልእክቶች")
        if (log.isEmpty()) {
            Text("እስካሁን ምንም አልተላከም", color = Color.Gray, modifier = Modifier.padding(20.dp))
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            items(log) { e ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                (if (e.ok) "✅ " else "❌ ") + e.name,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(e.time, color = Color.Gray, fontSize = 12.sp)
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

    Column(Modifier.fillMaxSize()) {
        Header("ቅንብር", "የሱቅ መረጃ እና ምትኬ")
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = shop, onValueChange = { shop = it },
                label = { Text("የሱቅ ስም") }, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = account, onValueChange = { account = it },
                label = { Text("የመክፈያ አካውንት") }, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = template, onValueChange = { template = it },
                label = { Text("የብድር መልእክት ጽሑፍ") }, modifier = Modifier.fillMaxWidth()
            )
            Text("በመልእክቱ ውስጥ፦ {ሱቅ} {መጠን} {አካውንት}", color = Color.Gray, fontSize = 12.sp)
            Button(
                onClick = {
                    Store.setText(ctx, "shop", shop)
                    Store.setText(ctx, "account", account)
                    Store.setText(ctx, "template", template)
                    toast(ctx, "ተቀምጧል")
                },
                colors = ButtonDefaults.buttonColors(containerColor = Teal)
            ) { Text("አስቀምጥ") }
            Spacer(Modifier.height(12.dp))
            Text("💾 ምትኬ", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(
                "መረጃው በዚህ ስልክ ላይ ብቻ ነው የሚቀመጠው። ምትኬን ወደ Telegram ወይም Gmail ልከው ያስቀምጡ።",
                color = Color.Gray, fontSize = 13.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val i = Intent(Intent.ACTION_SEND)
                    i.type = "text/plain"
                    i.putExtra(Intent.EXTRA_TEXT, Store.backup(ctx))
                    ctx.startActivity(Intent.createChooser(i, "ምትኬ"))
                }) { Text("ምትኬ አጋራ") }
                OutlinedButton(onClick = { showRestore = true }) { Text("መልስ") }
            }
        }
    }

    if (showRestore) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRestore = false },
            title = { Text("ምትኬ መልስ") },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    label = { Text("የምትኬ ጽሑፍ ይለጥፉ") }, modifier = Modifier.fillMaxWidth()
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
                }) { Text("መልስ") }
            },
            dismissButton = {
                TextButton(onClick = { showRestore = false }) { Text("ተው") }
            }
        )
    }
}