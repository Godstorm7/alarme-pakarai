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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.scheduler.AlarmScheduler
import com.pakarai.alarme.ui.theme.PakaRaiSpacing

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
            .padding(horizontal = PakaRaiSpacing.lg)
    ) {
        Spacer(Modifier.height(PakaRaiSpacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CONFIG SAMSUNG",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "5 minutos, uma única vez",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { refresh() }) {
                Text(
                    "ATUALIZAR",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Spacer(Modifier.height(PakaRaiSpacing.md))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.padding(PakaRaiSpacing.md)) {
                Text(
                    text = "POR QUE ISSO EXISTE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "A OneUI pode MATAR o alarme de madrugada: ela hiberna app que fica em background.\nDesbloqueie as 4 travas abaixo pra acordar de verdade.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(Modifier.height(PakaRaiSpacing.lg))

        StepsSummary(safe = batteryOk && exactOk && guardOk)

        Spacer(Modifier.height(PakaRaiSpacing.md))

        StepCard(
            num = 1,
            title = "Bateria: Sem restrições",
            desc = "Impede o Doze de atrasar teu alarme.",
            status = if (batteryOk) "OK" else "PENDENTE",
            onOpen = { openBatteryExemption(context) }
        )
        StepCard(
            num = 2,
            title = "Smart Manager",
            desc = "Tira o PakaRai de Sleeping/Deep sleeping e libera autostart.",
            status = "MANUAL",
            statusOk = true,
            onOpen = { openSmartManager(context) }
        )
        StepCard(
            num = 3,
            title = "Alarmes exatos",
            desc = "Android 13+ cobra permissão separada pra alarme exato.",
            status = if (exactOk) "OK" else "PENDENTE",
            onOpen = { AlarmScheduler.requestExactPermission(context) }
        )
        StepCard(
            num = 4,
            title = "Vigilância anti-fuga",
            desc = "Ativa o serviço de acessibilidade (a tela do desafio volta se você sair).",
            status = if (guardOk) "ON" else "OFF",
            statusOk = guardOk,
            onOpen = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        )

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text("CONCLUÍDO", fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun StepsSummary(safe: Boolean) {
    val color = if (safe) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.error
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (safe) "TUDO LIBERADO" else "FALTAM TRAVAS",
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun StepCard(
    num: Int,
    title: String,
    desc: String,
    status: String,
    statusOk: Boolean = false,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = num.toString().padStart(2, '0'),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(top = 9.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(status, statusOk)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "ABRIR ›",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .clickable(onClick = onOpen)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: String, ok: Boolean) {
    val bg = if (ok) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    else MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
    val fg = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Text(
        text = status,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Black,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
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