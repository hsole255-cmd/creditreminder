package com.ahadu.creditreminder

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleDaily(this)
        setContent {
            MaterialTheme { App() }
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

@Composable
fun App() {
    val ctx = LocalContext.current
    val debtors = remember { mutableStateListOf<Debtor>().apply { addAll(Store.load(ctx)) } }
    var tab by remember { mutableStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) { permLauncher.launch(Manifest.permission.SEND_SMS) }

    fun persist() {
        Store.save(ctx, debtors.toList())
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("የብድር አስታዋሽ", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { tab = 0 }) { Text("ተበዳሪዎች") }
            OutlinedButton(onClick = { tab = 1 }) { Text("ቅንብር") }
        }
        Spacer(Modifier.height(12.dp))

        if (tab == 0) {
            val total = debtors.filter { !it.paid }.sumOf { it.amount }
            Text("ጠቅላላ ቀሪ ብድር: " + String.format("%,d", total) + " ብር")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showAdd = true }) { Text("+ አክል") }
                OutlinedButton(onClick = {
                    Toast.makeText(ctx, "በመላክ ላይ...", Toast.LENGTH_SHORT).show()
                    Thread {
                        val n = runReminders(ctx)
                        Handler(Looper.getMainLooper()).post {
                            debtors.clear()
                            debtors.addAll(Store.load(ctx))
                            Toast.makeText(ctx, "ተልኳል: " + n, Toast.LENGTH_LONG).show()
                        }
                    }.start()
                }) { Text("አሁን ላክ") }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                items(debtors, key = { it.id }) { d ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(d.name, style = MaterialTheme.typography.titleMedium)
                            Text(if (d.phone.isBlank()) "ስልክ የለም" else d.phone)
                            val state = if (d.paid) "ተከፍሏል" else "ያልተከፈለ"
                            Text(String.format("%,d", d.amount) + " ብር • " + state)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    val i = debtors.indexOfFirst { it.id == d.id }
                                    if (i >= 0) {
                                        debtors[i] = d.copy(paid = !d.paid)
                                        persist()
                                    }
                                }) { Text(if (d.paid) "መልስ" else "ተከፍሏል") }
                                OutlinedButton(onClick = {
                                    debtors.removeAll { it.id == d.id }
                                    persist()
                                }) { Text("ሰርዝ") }
                            }
                        }
                    }
                }
            }
        } else {
            var shop by remember { mutableStateOf(Store.getText(ctx, "shop", Sms.DEF_SHOP)) }
            var account by remember { mutableStateOf(Store.getText(ctx, "account", Sms.DEF_ACCOUNT)) }
            var template by remember { mutableStateOf(Store.getText(ctx, "template", Sms.DEF_TEMPLATE)) }
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
                label = { Text("የመልእክት ጽሑፍ") }, modifier = Modifier.fillMaxWidth()
            )
            Text("በመልእክቱ ውስጥ፦ {ሱቅ} {መጠን} {አካውንት}")
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                Store.setText(ctx, "shop", shop)
                Store.setText(ctx, "account", account)
                Store.setText(ctx, "template", template)
                Toast.makeText(ctx, "ተቀምጧል", Toast.LENGTH_SHORT).show()
            }) { Text("አስቀምጥ") }
        }
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
                    val a = amount.toLongOrNull()
                    if (name.isNotBlank() && a != null && a > 0) {
                        debtors.add(Debtor(name = name.trim(), phone = phone.trim(), amount = a))
                        persist()
                        showAdd = false
                    } else {
                        Toast.makeText(ctx, "ስም እና ብር ያስገቡ", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("አስቀምጥ") }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("ተው") }
            }
        )
    }
}
