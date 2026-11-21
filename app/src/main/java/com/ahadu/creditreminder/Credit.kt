package com.ahadu.creditreminder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

val PayMethods = listOf("Cash", "CBE", "Telebirr", "CBE Birr", "Bank transfer", "Other")
val PendingSoft = Color(0xFF3A3010)
val PendingDark = Color(0xFFFFC857)

fun fmtBirr(n: Long): String = String.format(Locale.US, "%,d", n) + " ብር"

fun dayText(day: Long): String {
    if (day <= 0L) return "—"
    val f = SimpleDateFormat("dd/MM/yyyy", Locale.US)
    f.timeZone = TimeZone.getTimeZone("UTC")
    return f.format(Date(day * 86_400_000L))
}

fun monthStartDay(): Long {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = Sms.today() * 86_400_000L
    cal.set(Calendar.DAY_OF_MONTH, 1)
    return cal.timeInMillis / 86_400_000L
}

fun creditGiven(d: Debtor, payments: List<PaymentEntry>): Long =
    if (d.original > 0L) d.original
    else d.amount + payments.filter { it.debtorId == d.id }.sumOf { it.amount }

fun isOverdue(d: Debtor, today: Long): Boolean =
    !d.paid && d.dueDay > 0L && d.dueDay < today

fun validPhone(p: String): Boolean {
    if (p.isBlank()) return true
    return Regex("^\\+251[79]\\d{8}$").matches(Sms.normalize(p))
}

@Composable
fun StatCard(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ProCard)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = TextMuted, fontSize = 12.sp)
            Text(value, color = color, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DashboardScreen(
    ctx: Context,
    debtors: List<Debtor>,
    customers: List<Customer>,
    onGoCredit: () -> Unit,
    onGoCustomers: () -> Unit
) {
    val payments = Store.loadPayments(ctx)
    val today = Sms.today()
    val monthStart = monthStartDay()
    val names = (debtors.map { it.name.trim().lowercase() } + customers.map { it.name.trim().lowercase() })
        .filter { it.isNotEmpty() }.toSet()
    val totalCredit = debtors.sumOf { creditGiven(it, payments) }
    val collected = payments.sumOf { it.amount } + debtors.filter { it.paid }.sumOf { it.amount }
    val outstanding = debtors.filter { !it.paid }.sumOf { it.amount }
    val overdue = debtors.filter { isOverdue(it, today) }
    val monthCredit = debtors.filter { it.createdDay > 0L && it.createdDay >= monthStart }
        .sumOf { creditGiven(it, payments) }
    val monthPaid = payments.filter { it.day > 0L && it.day >= monthStart }.sumOf { it.amount }

    Column(Modifier.fillMaxSize().background(ProBg)) {
        Header("ዳሽቦርድ", "Credit Reminder · Smart Debt Management")
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("ጠቅላላ ደንበኞች", names.size.toString(), NeonBlue, Modifier.weight(1f))
                StatCard("የተሰጠ ብድር", fmtBirr(totalCredit), Color.White, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("የተሰበሰበ", fmtBirr(collected), NeonGreen, Modifier.weight(1f))
                StatCard("ቀሪ ብድር", fmtBirr(outstanding), NeonPurple, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("ጊዜው ያለፈባቸው", overdue.size.toString(), RedDark, Modifier.weight(1f))
                StatCard("ያልተከፈሉ ሰዎች", debtors.count { !it.paid }.toString(), PendingDark, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onGoCredit,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                ) { Text("+ አዲስ ብድር", color = Color(0xFF062113), fontWeight = FontWeight.Bold) }
                OutlinedButton(onClick = onGoCustomers) { Text("👥 ደንበኞች", color = NeonBlue) }
            }
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ProCard)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("📅 የዚህ ወር ማጠቃለያ", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("አዲስ ብድር: " + fmtBirr(monthCredit), color = TextMuted, fontSize = 13.sp)
                    Text("የተሰበሰበ ክፍያ: " + fmtBirr(monthPaid), color = NeonGreen, fontSize = 13.sp)
                }
            }
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ProCard)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("⚠️ ጊዜው ያለፈባቸው", color = Color.White, fontWeight = FontWeight.SemiBold)
                    if (overdue.isEmpty()) {
                        Text("የለም", color = TextMuted, fontSize = 13.sp)
                    }
                    overdue.sortedBy { it.dueDay }.take(5).forEach { d ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(d.name, color = Color.White, fontSize = 14.sp)
                            Text(
                                fmtBirr(d.amount) + " · " + (today - d.dueDay) + " ቀን",
                                color = RedDark, fontSize = 13.sp
                            )
                        }
                    }
                }
            }
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ProCard)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("💰 የቅርብ ጊዜ ክፍያዎች", color = Color.White, fontWeight = FontWeight.SemiBold)
                    if (payments.isEmpty()) {
                        Text("እስካሁን የለም", color = TextMuted, fontSize = 13.sp)
                    }
                    payments.take(5).forEach { p ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(p.customerName, color = Color.White, fontSize = 14.sp)
                                Text(p.method + " · " + p.time, color = TextMuted, fontSize = 11.sp)
                            }
                            Text(fmtBirr(p.amount), color = NeonGreen, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DebtForm(
    initial: Debtor?,
    onDismiss: () -> Unit,
    onSubmit: (String, String, Long, Long, String) -> Boolean
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val editing = initial != null
    val dueNow = initial?.dueDay ?: 0L
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var amount by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("") }
    var note by remember { mutableStateOf(initial?.note ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ProSurface,
        title = { Text(if (editing) "ማስተካከያ" else "አዲስ ብድር", color = Color.White) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("ስም") }, singleLine = true, colors = proFieldColors()
                )
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    label = { Text("ስልክ (09...)") }, singleLine = true, colors = proFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                if (!editing) {
                    OutlinedTextField(
                        value = amount, onValueChange = { amount = it },
                        label = { Text("ብር") }, singleLine = true, colors = proFieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                OutlinedTextField(
                    value = days, onValueChange = { days = it },
                    label = {
                        Text(
                            if (dueNow > 0L) "አዲስ የመክፈያ ቀን (ከዛሬ ስንት ቀን በኋላ)፤ አሁን: " + dayText(dueNow)
                            else "የመክፈያ ቀን (ከዛሬ ስንት ቀን በኋላ)"
                        )
                    },
                    singleLine = true, colors = proFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("7", "14", "30").forEach { d ->
                        FilledTonalButton(onClick = { days = d }) { Text(d + " ቀን", fontSize = 12.sp) }
                    }
                }
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("ማስታወሻ / የዕዳው ምክንያት") }, colors = proFieldColors()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = initial?.amount ?: (amount.filter { it in '0'..'9' }.toLongOrNull() ?: 0L)
                val dn = days.filter { it in '0'..'9' }.toLongOrNull()
                val due = if (dn != null) Sms.today() + dn else dueNow
                if (name.isBlank()) {
                    toast(ctx, "ስም ያስገቡ")
                } else if (!editing && a <= 0L) {
                    toast(ctx, "ብር ያስገቡ")
                } else if (!validPhone(phone)) {
                    toast(ctx, "ስልክ ቁጥር ትክክል አይደለም (ለምሳሌ 0912345678)")
                } else {
                    if (onSubmit(name.trim(), phone.trim(), a, due, note.trim())) onDismiss()
                }
            }) { Text("አስቀምጥ", color = NeonGreen) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ተው", color = TextMuted) }
        }
    )
}

@Composable
fun CreditScreen(ctx: Context, debtors: MutableList<Debtor>) {
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        toast(ctx, if (granted) "ፈቃዱ ተሰጥቷል። እንደገና ይሞክሩ።" else "SMS ለመላክ ፈቃድ ያስፈልጋል።")
    }
    var showAdd by remember { mutableStateOf(false) }
    var confirmBulk by remember { mutableStateOf(false) }
    var payFor by remember { mutableStateOf<Debtor?>(null) }
    var smsFor by remember { mutableStateOf<Debtor?>(null) }
    var editFor by remember { mutableStateOf<Debtor?>(null) }
    var historyFor by remember { mutableStateOf<Debtor?>(null) }
    var deleteFor by remember { mutableStateOf<Debtor?>(null) }
    var sending by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(0) }
    var auto by remember { mutableStateOf(Store.getBool(ctx, "autoRemind", true)) }

    val today = Sms.today()
    val open = debtors.filter { !it.paid }
    val total = open.sumOf { it.amount }
    val overdueCount = debtors.count { isOverdue(it, today) }

    fun persist() {
        Store.save(ctx, debtors.toList())
    }

    fun hasSms(): Boolean =
        ctx.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    fun startBulk() {
        if (!hasSms()) {
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

    val shown = debtors.filter { d ->
        (query.isBlank() ||
            d.name.contains(query.trim(), ignoreCase = true) ||
            d.phone.contains(query.trim())) &&
            when (filter) {
                1 -> !d.paid
                2 -> isOverdue(d, today)
                3 -> d.paid
                else -> true
            }
    }.sortedWith(
        compareBy<Debtor>({ it.paid }, { if (it.dueDay > 0L) it.dueDay else Long.MAX_VALUE })
    )

    Column(Modifier.fillMaxSize().background(ProBg)) {
        Header("ብድር", "ያልተከፈሉ: ${open.size} · ያለፈባቸው: $overdueCount")
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ProCard)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ጠቅላላ ቀሪ ብድር", color = TextMuted, fontSize = 13.sp)
                Text(
                    fmtBirr(total),
                    color = NeonGreen, fontSize = 30.sp, fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showAdd = true },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                    ) { Text("+ አክል", color = Color(0xFF062113), fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = { confirmBulk = true },
                        enabled = !sending
                    ) { Text(if (sending) "በመላክ ላይ..." else "አሁን ላክ", color = NeonBlue) }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "በራስ-ሰር አስታዋሽ (በየ3 ቀን)",
                        color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = auto,
                        onCheckedChange = {
                            auto = it
                            Store.setBool(ctx, "autoRemind", it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonGreen, checkedTrackColor = GreenSoft
                        )
                    )
                }
            }
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("ፈልግ (ስም ወይም ስልክ)") }, singleLine = true, colors = proFieldColors(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("ሁሉም", "ያልተከፈሉ", "ያለፈባቸው", "የተከፈሉ").forEachIndexed { i, label ->
                if (filter == i) {
                    Button(
                        onClick = { filter = i },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                    ) { Text(label, color = Color(0xFF062113), fontSize = 12.sp) }
                } else {
                    OutlinedButton(onClick = { filter = i }) { Text(label, color = NeonBlue, fontSize = 12.sp) }
                }
            }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            items(shown, key = { it.id }) { d ->
                val od = isOverdue(d, today)
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
                            Text(
                                d.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                                color = Color.White, modifier = Modifier.weight(1f)
                            )
                            if (d.paid) Chip("ተከፍሏል", GreenSoft, GreenDark)
                            else if (od) Chip("ጊዜው አልፏል", RedSoft, RedDark)
                            else Chip("ያልተከፈለ", PendingSoft, PendingDark)
                        }
                        Text(
                            if (d.phone.isBlank()) "📵 ስልክ የለም" else "📞 " + d.phone,
                            color = TextMuted, fontSize = 13.sp
                        )
                        Text(
                            fmtBirr(d.amount),
                            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = NeonGreen
                        )
                        if (d.original > d.amount && d.original > 0L) {
                            Text("ከ " + fmtBirr(d.original) + " ቀሪ", color = TextMuted, fontSize = 12.sp)
                        }
                        if (d.dueDay > 0L && !d.paid) {
                            Text(
                                "የመክፈያ ቀን: " + dayText(d.dueDay),
                                color = if (od) RedDark else TextMuted, fontSize = 12.sp
                            )
                        }
                        if (d.note.isNotBlank()) {
                            Text(d.note, color = TextMuted, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (!d.paid) {
                                FilledTonalButton(onClick = { payFor = d }) { Text("ክፍያ") }
                                OutlinedButton(onClick = { smsFor = d }) { Text("📩 ላክ", color = NeonBlue) }
                            }
                            OutlinedButton(onClick = { historyFor = d }) { Text("ታሪክ", color = NeonPurple) }
                            OutlinedButton(onClick = { editFor = d }) { Text("✏️", color = NeonBlue) }
                            TextButton(onClick = { deleteFor = d }) { Text("ሰርዝ", color = RedDark) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showAdd) {
        DebtForm(
            initial = null,
            onDismiss = { showAdd = false },
            onSubmit = { name, phone, amount, due, note ->
                val norm = Sms.normalize(phone)
                val exIdx = if (phone.isBlank()) -1 else debtors.indexOfFirst {
                    !it.paid && it.phone.isNotBlank() && Sms.normalize(it.phone) == norm
                }
                if (exIdx >= 0) {
                    val ex = debtors[exIdx]
                    val prev = creditGiven(ex, Store.loadPayments(ctx))
                    debtors[exIdx] = ex.copy(
                        amount = ex.amount + amount,
                        original = prev + amount,
                        dueDay = if (due > 0L) due else ex.dueDay,
                        note = if (note.isNotBlank()) note else ex.note
                    )
                    toast(ctx, "ተመሳሳይ ስልክ ስላለ ወደ ነባሩ ብድር ተጨምሯል")
                } else {
                    debtors.add(
                        Debtor(
                            name = name, phone = phone, amount = amount,
                            createdDay = Sms.today(), dueDay = due, note = note, original = amount
                        )
                    )
                }
                persist()
                true
            }
        )
    }

    val ed = editFor
    if (ed != null) {
        DebtForm(
            initial = ed,
            onDismiss = { editFor = null },
            onSubmit = { name, phone, _, due, note ->
                val i = debtors.indexOfFirst { it.id == ed.id }
                if (i >= 0) {
                    debtors[i] = debtors[i].copy(name = name, phone = phone, dueDay = due, note = note)
                    persist()
                }
                true
            }
        )
    }

    val del = deleteFor
    if (del != null) {
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            containerColor = ProSurface,
            title = { Text("ይሰረዝ?", color = Color.White) },
            text = { Text(del.name + " ከዝርዝሩ ይወገዳል። ይህ አይመለስም።", color = Color.White) },
            confirmButton = {
                TextButton(onClick = {
                    debtors.removeAll { it.id == del.id }
                    persist()
                    deleteFor = null
                }) { Text("ሰርዝ", color = RedDark) }
            },
            dismissButton = {
                TextButton(onClick = { deleteFor = null }) { Text("ተው", color = TextMuted) }
            }
        )
    }

    val pd = payFor
    if (pd != null) {
        var txt by remember(pd.id) { mutableStateOf("") }
        var method by remember(pd.id) { mutableStateOf("Cash") }
        var ref by remember(pd.id) { mutableStateOf("") }
        var pnote by remember(pd.id) { mutableStateOf("") }
        var saving by remember(pd.id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { payFor = null },
            containerColor = ProSurface,
            title = { Text("ክፍያ: " + pd.name, color = Color.White) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("ቀሪ: " + fmtBirr(pd.amount), color = NeonGreen)
                    OutlinedTextField(
                        value = txt, onValueChange = { txt = it },
                        label = { Text("የተከፈለ ብር") }, singleLine = true, colors = proFieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    TextButton(onClick = { txt = pd.amount.toString() }) {
                        Text("ሙሉ ቀሪውን ሙላ", color = NeonBlue)
                    }
                    Text("የክፍያ ዘዴ", color = TextMuted, fontSize = 12.sp)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PayMethods.forEach { m ->
                            if (method == m) {
                                Button(
                                    onClick = { method = m },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                                ) { Text(m, color = Color(0xFF062113), fontSize = 12.sp) }
                            } else {
                                OutlinedButton(onClick = { method = m }) {
                                    Text(m, color = NeonBlue, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = ref, onValueChange = { ref = it },
                        label = { Text("የግብይት ቁጥር (አማራጭ)") }, singleLine = true, colors = proFieldColors()
                    )
                    OutlinedTextField(
                        value = pnote, onValueChange = { pnote = it },
                        label = { Text("ማስታወሻ (አማራጭ)") }, colors = proFieldColors()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val p = txt.filter { it in '0'..'9' }.toLongOrNull()
                    if (saving) {
                        // ድርብ ንክኪ ተከልክሏል
                    } else if (p == null || p <= 0L) {
                        toast(ctx, "ትክክለኛ የክፍያ መጠን ያስገቡ")
                    } else if (p > pd.amount) {
                        toast(ctx, "ክፍያው ከቀሪ ብድሩ መብለጥ አይችልም")
                    } else {
                        saving = true
                        val i = debtors.indexOfFirst { it.id == pd.id }
                        if (i >= 0) {
                            val left = pd.amount - p
                            Store.addPayment(
                                ctx,
                                PaymentEntry(
                                    debtorId = pd.id,
                                    customerName = pd.name,
                                    amount = p,
                                    time = Sms.stamp(),
                                    method = method,
                                    ref = ref.trim(),
                                    note = pnote.trim(),
                                    day = Sms.today()
                                )
                            )
                            debtors[i] = if (left == 0L) pd.copy(amount = 0L, paid = true)
                            else pd.copy(amount = left)
                            persist()
                        }
                        payFor = null
                    }
                }) { Text("አስቀምጥ", color = NeonGreen) }
            },
            dismissButton = {
                TextButton(onClick = { payFor = null }) { Text("ተው", color = TextMuted) }
            }
        )
    }

    val sd = smsFor
    if (sd != null) {
        var msg by remember(sd.id) { mutableStateOf(Sms.buildMessage(ctx, sd.amount)) }
        AlertDialog(
            onDismissRequest = { smsFor = null },
            containerColor = ProSurface,
            title = { Text("መልእክት ወደ " + sd.name, color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (sd.phone.isBlank()) "ስልክ ቁጥር የለም" else sd.phone,
                        color = if (sd.phone.isBlank()) RedDark else NeonBlue
                    )
                    OutlinedTextField(
                        value = msg, onValueChange = { msg = it },
                        label = { Text("መልእክት (ከመላክ በፊት ያርሙ)") }, colors = proFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(msg.length.toString() + " ፊደል · ወጪው ከስልክዎ ሲም ይቆረጣል", color = TextMuted, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (sd.phone.isBlank()) {
                        toast(ctx, "ስልክ ቁጥር የለም")
                    } else if (!hasSms()) {
                        permLauncher.launch(Manifest.permission.SEND_SMS)
                    } else {
                        val ok = Sms.sendSms(ctx, sd.phone, msg)
                        Store.addLog(
                            ctx,
                            LogEntry(name = sd.name, kind = "ብድር SMS", time = Sms.stamp(), ok = ok)
                        )
                        if (ok) {
                            val i = debtors.indexOfFirst { it.id == sd.id }
                            if (i >= 0) {
                                debtors[i] = debtors[i].copy(lastSentDay = Sms.today())
                                persist()
                            }
                        }
                        toast(ctx, if (ok) "ተልኳል" else "አልተላከም")
                        smsFor = null
                    }
                }) { Text("ላክ", color = NeonGreen) }
            },
            dismissButton = {
                TextButton(onClick = { smsFor = null }) { Text("ተው", color = TextMuted) }
            }
        )
    }

    val hd = historyFor
    if (hd != null) {
        val list = Store.loadPayments(ctx).filter { it.debtorId == hd.id }
        AlertDialog(
            onDismissRequest = { historyFor = null },
            containerColor = ProSurface,
            title = { Text("የክፍያ ታሪክ: " + hd.name, color = Color.White) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("ጠቅላላ የተከፈለ: " + fmtBirr(list.sumOf { it.amount }), color = NeonGreen)
                    if (list.isEmpty()) {
                        Text("ክፍያ አልተመዘገበም", color = TextMuted)
                    }
                    list.forEach { p ->
                        Column {
                            Text(fmtBirr(p.amount) + " · " + p.method, color = Color.White, fontSize = 14.sp)
                            Text(
                                p.time + (if (p.ref.isNotBlank()) " · " + p.ref else "") +
                                    (if (p.note.isNotBlank()) " · " + p.note else ""),
                                color = TextMuted, fontSize = 11.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { historyFor = null }) { Text("ዝጋ", color = NeonGreen) }
            }
        )
    }

    if (confirmBulk) {
        val due = debtors.filter {
            !it.paid && it.phone.isNotBlank() && it.amount > 0L && today - it.lastSentDay >= 3L
        }
        AlertDialog(
            onDismissRequest = { confirmBulk = false },
            containerColor = ProSurface,
            title = { Text("መልእክት ይላክ?", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${due.size} ሰዎች ይላክላቸዋል (በአንድ ጊዜ ከ8 አይበልጥም)። ወጪው ከስልክዎ ሲም ይቆረጣል።",
                        color = Color.White
                    )
                    if (due.isNotEmpty()) {
                        Text("ምሳሌ:", color = TextMuted, fontSize = 12.sp)
                        Text(Sms.buildMessage(ctx, due[0].amount), fontSize = 13.sp, color = NeonBlue)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmBulk = false
                    if (due.isEmpty()) toast(ctx, "የሚላክ የለም") else startBulk()
                }) { Text("ላክ", color = NeonGreen) }
            },
            dismissButton = {
                TextButton(onClick = { confirmBulk = false }) { Text("ተው", color = TextMuted) }
            }
        )
    }
}