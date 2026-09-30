package com.pakarai.alarme.ui.wizard

import android.content.Context
import android.content.Intent
import android.os.Build
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.core.DndBypass
import com.pakarai.alarme.core.SetupItem
import com.pakarai.alarme.core.SetupLinks
import com.pakarai.alarme.core.SetupState
import com.pakarai.alarme.core.SetupStatus
import com.pakarai.alarme.core.SetupStatusReader
import com.pakarai.alarme.ui.theme.PakaRaiSpacing

/**
 * "ONDE FICA CADA AJUSTE" — o caminho manual, o status REAL (lido do sistema)
 * e um botão por item. Também serve de relatório ("o alarme não tocou").
 *
 * Tudo sai de [SetupStatusReader]: um lugar só decide o que é OK, o que é
 * PENDENTE e o que é MANUAL (sem API, a pessoa confere). Item em MANUAL
 * mostra o caminho escrito + copiar, em vez de um status mentiroso.
 */
@Composable
fun SettingsGuideScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }
    var persistentNotif by remember { mutableStateOf(AppScope.settings.persistentNotification) }
    var dndBypass by remember { mutableStateOf(AppScope.settings.dndBypass) }
    var copied by remember { mutableStateOf<SetupItem?>(null) }

    // a pessoa sai pro ajuste do sistema e volta: re-leemos no ON_RESUME
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // refreshKey força recomputar os status (e a lista reordena por gravidade)
    val items = remember(refreshKey) {
        SetupStatusReader.read(context, AppScope.settings.guardUserEnabled)
            .sortedByDescending { it.item.severity }
    }

    // "sem informação" também conta: esconder isso da pessoa seria o mesmo erro
    // de mentir com um status inventado, só na outra direção
    val pendingCount = items.count { it.state == SetupState.ACTION || it.state == SetupState.MANUAL }
    val unknownCount = items.count { it.state == SetupState.UNKNOWN }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .systemBarsPadding()
            .padding(horizontal = PakaRaiSpacing.lg)
    ) {
        Spacer(Modifier.height(PakaRaiSpacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ONDE FICA CADA AJUSTE",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = when {
                        pendingCount == 0 && unknownCount == 0 -> "Tudo certo por aqui"
                        pendingCount == 0 -> "$unknownCount sem informação nesta versão"
                        else -> "$pendingCount ajuste${if (pendingCount > 1) "s" else ""} pra conferir" +
                            if (unknownCount > 0) " (+$unknownCount sem info)" else ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { refreshKey++ }) {
                Text(
                    "ATUALIZAR",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Spacer(Modifier.height(PakaRaiSpacing.md))

        // Preferências do app (não são ajuste do sistema)
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Notificação do próximo alarme",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Fixa na barra de notificações.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = persistentNotif,
                        onCheckedChange = {
                            persistentNotif = it
                            AppScope.settings.persistentNotification = it
                            com.pakarai.alarme.service.NextAlarmNotifier.refresh(context)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Furar o Não perturbe no toque",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (DndBypass.hasPolicyAccess(context)) {
                                "Com o DND ligado, o alarme toca assim mesmo e o seu ajuste volta depois."
                            } else {
                                "Precisa do acesso especial abaixo pra isso funcionar."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = dndBypass,
                        onCheckedChange = {
                            dndBypass = it
                            AppScope.settings.dndBypass = it
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }

        items.forEach { status ->
            GuideCard(
                status = status,
                copied = copied == status.item,
                onOpen = { SetupLinks.open(context, status.item) },
                onCopyPath = {
                    SetupLinks.copyPath(context, status.item)
                    copied = status.item
                }
            )
        }

        Spacer(Modifier.height(20.dp))
        TextButton(onClick = { shareReport(context, items) }) {
            Text(
                "COMPARTILHAR RELATÓRIO",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black
            )
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun GuideCard(
    status: SetupStatus,
    copied: Boolean,
    onOpen: () -> Unit,
    onCopyPath: () -> Unit,
) {
    val item = status.item
    val manual = status.state == SetupState.MANUAL
    val settled = status.state == SetupState.OK || status.state == SetupState.SKIPPED
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (settled) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            }
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.why,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // MANUAL: o caminho escrito é a informação principal, não um rodapé
            if (manual) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.path,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(status.state)
                Spacer(Modifier.width(12.dp))
                if (SetupLinks.hasDirectAction(item)) {
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
                if (manual) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (copied) "COPIADO" else "COPIAR",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onCopyPath)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(state: SetupState) {
    val (label, bg, fg) = when (state) {
        SetupState.OK -> Triple(
            "ATIVO",
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            MaterialTheme.colorScheme.primary
        )
        SetupState.ACTION -> Triple(
            "PENDENTE",
            MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.error
        )
        SetupState.MANUAL -> Triple(
            "CONFIRME",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        SetupState.UNKNOWN -> Triple(
            "SEM INFO",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        SetupState.SKIPPED -> Triple(
            "DESLIGADO",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Black,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

private fun shareReport(context: Context, items: List<SetupStatus>) {
    val sb = StringBuilder()
    sb.append("Pakarai — relatório de ajustes\n")
    sb.append("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
    sb.append("Aparelho: ${Build.MANUFACTURER} ${Build.MODEL}\n\n")
    items.forEach { sb.append("• ${it.item.title}: ${labelOf(it.state)}\n") }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Pakarai — relatório")
        putExtra(Intent.EXTRA_TEXT, sb.toString())
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Compartilhar relatório").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: Exception) {
    }
}

private fun labelOf(state: SetupState): String = when (state) {
    SetupState.OK -> "ativo"
    SetupState.ACTION -> "pendente"
    SetupState.MANUAL -> "confirme no ajuste"
    SetupState.UNKNOWN -> "sem informação"
    SetupState.SKIPPED -> "desligado no app"
}
