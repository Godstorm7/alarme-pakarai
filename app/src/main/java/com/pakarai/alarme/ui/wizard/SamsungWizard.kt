package com.pakarai.alarme.ui.wizard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.scheduler.AlarmScheduler

/**
 * Wizard de confiabilidade SAMSUNG / OneUI.
 * A Samsung tem DUAS proteções: o Doze do Android E o "Background usage
 * limits" (Sleeping/Deep sleeping apps). Um app de alarme precisa sair de
 * ambas pra não morrer de madrugada. Aqui o usuário é guiado com deep
 * links diretos (o único jeito de chegar nessas telas sem o usuário caçar).
 */
@Composable
fun SamsungWizardScreen(onDone: () -> Unit) {
    val context = LocalContext.current

    var batteryOk by remember {
        mutableStateOf(isBatteryIgnored(context))
    }
    var exactOk by remember {
        mutableStateOf(AppScope.scheduler.canScheduleExact())
    }
    var guardOk by remember {
        mutableStateOf(AppScope.settings.isGuardActuallyEnabled(context))
    }

    fun refresh() {
        batteryOk = isBatteryIgnored(context)
        exactOk = AppScope.scheduler.canScheduleExact()
        guardOk = AppScope.settings.isGuardActuallyEnabled(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "CONFIG SAMSUNG",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { refresh() }) {
                Text("ATUALIZAR", color = MaterialTheme.colorScheme.secondary)
            }
        }
        Text(
            text = "Sua OneUI pode MATAR o alarme de madrugada. Desbloqueie tudo abaixo (5 min, uma vez só).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))

        StepCard(
            num = 1,
            title = "Bateria: Sem restrições",
            desc = "Impede o Doze de atrasar teu alarme.",
            status = if (batteryOk) "OK" else "PENDENTE",
            onOpen = { openBatteryExemption(context) }
        )
        StepCard(
            num = 2,
            title = "Smart Manager (app nos limites)",
            desc = "Remove o PakaRai de Sleeping/Deep sleeping e libera autostart.",
            status = "-",
            onOpen = { openSmartManager(context) }
        )
        StepCard(
            num = 3,
            title = "Alarmes exatos",
            desc = "Android 13+ pede permissão separada pra alarme exato.",
            status = if (exactOk) "OK" else "PENDENTE",
            onOpen = { AlarmScheduler.requestExactPermission(context) }
        )
        StepCard(
            num = 4,
            title = "Vigilância anti-fuga",
            desc = "Ativa o serviço de acessibilidade (a tela do desafio volta se você sair).",
            status = if (guardOk) "ON" else "OFF",
            onOpen = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        )

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("CONCLUÍDO", fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun StepCard(
    num: Int,
    title: String,
    desc: String,
    status: String,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = num.toString(),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        CircleShape
                    ),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = status,
                    color = if (status == "OK" || status == "ON")
                        MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "ABRIR",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onOpen)
                )
            }
        }
    }
}

private fun isBatteryIgnored(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return try {
        pm.isIgnoringBatteryOptimizations(context.packageName)
    } catch (_: Exception) {
        false
    }
}

private fun openBatteryExemption(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        context.startActivity(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        )
    }
}

/** Deep links OEM: tentam o componente Samsung; se não achar, caem em telas genéricas. */
private fun openSmartManager(context: Context) {
    val candidates = listOf(
        ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
    )
    for (c in candidates) {
        try {
            val intent = Intent().apply {
                component = c
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (context.packageManager.resolveActivity(intent, 0) != null) {
                context.startActivity(intent)
                return
            }
        } catch (_: Exception) {
        }
    }
    // fallback: página de detalhes do app
    try {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: Exception) {
    }
}