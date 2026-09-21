package com.pakarai.alarme.ui.wizard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.core.SettingsManager
import com.pakarai.alarme.ui.theme.PakaRaiSpacing

/**
 * "ONDE FICA CADA AJUSTE" — mostra, pra cada permissão/acesso especial, o
 * CAMINHO manual no sistema, o status ao vivo e um botão pra abrir direto.
 * Também serve de relatório ("o alarme não tocou"): dá pra compartilhar.
 */
@Composable
fun SettingsGuideScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }
    var persistentNotif by remember { mutableStateOf(AppScope.settings.persistentNotification) }

    fun status(ok: Boolean?): Pair<String, Boolean?> = when (ok) {
        true -> "ATIVO" to true
        false -> "PENDENTE" to false
        null -> "MANUAL" to null
    }

    val granted = { perm: String ->
        context.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
    }

    // refreshKey força recomputar os status
    val items = remember(refreshKey) {
        listOf(
            GuideItem(
                "Bateria sem restrições",
                "Ajustes → Bateria e cuidados do dispositivo → Bateria → Limites de uso em 2º plano → Pakarai → Sem restrições",
                status(isBatteryIgnored(context)),
                { openBatteryExemption(context) }
            ),
            GuideItem(
                "Smart Manager (suspensão/autostart)",
                "Ajustes → Bateria e cuidados → ⋮ → Config. avançadas → Iniciar automaticamente",
                status(isBackgroundRestricted(context)?.let { !it }),
                { openSmartManager(context) }
            ),
            GuideItem(
                "Alarmes exatos",
                "Ajustes → Apps → Pakarai → Alarmes e lembretes → permitir",
                status(AppScope.scheduler.canScheduleExact()),
                { openExactAlarmSettings(context) }
            ),
            GuideItem(
                "Acessibilidade (anti-fuga)",
                "Ajustes → Acessibilidade → Apps instalados → Pakarai → Ativar",
                status(AppScope.settings.isGuardActuallyEnabled(context)),
                { openAccessibilitySettings(context) }
            ),
            GuideItem(
                "Afixar janelas (anti-fuga)",
                "Ajustes → Segurança e privacidade → Outras configurações de segurança → Afixar janelas",
                status(isPinningAllowed(context)),
                { openPinningSettings(context) }
            ),
            GuideItem(
                "Tela cheia (full-screen)",
                "Ajustes → Apps → Acesso especial → Notificações em tela cheia → Pakarai",
                status(canUseFullScreenIntent(context)),
                { openFullScreenIntentSettings(context) }
            ),
            GuideItem(
                "Aparecer por cima (popup)",
                "Ajustes → Apps → Pakarai → Aparecer por cima → permitir",
                status(isOverlayAllowed(context)),
                { openOverlaySettings(context) },
                hint = "Sem isso o \"AINDA ACORDADO?\" pode não abrir com a tela ligada."
            ),
            GuideItem(
                "Notificações",
                "Ajustes → Apps → Pakarai → Notificações → permitir",
                status(areNotificationsEnabled(context)),
                { openNotificationSettings(context) }
            ),
            GuideItem(
                "Não perturbe (DND)",
                "Ajustes → Notificações → Não perturbe → exceções/alarmes",
                status(isDndOn(context)?.let { !it }),
                { openDndSettings(context) }
            ),
            GuideItem(
                "Atividade física",
                "Ajustes → Apps → Pakarai → Permissões → Atividade física",
                status(granted(Manifest.permission.ACTIVITY_RECOGNITION)),
                { openAppPermissionSettings(context) }
            ),
            GuideItem(
                "Câmera (QR/objeto)",
                "Ajustes → Apps → Pakarai → Permissões → Câmera",
                status(granted(Manifest.permission.CAMERA)),
                { openAppPermissionSettings(context) }
            ),
            GuideItem(
                "Impedir desinstalação",
                "Ajustes → Segurança e privacidade → Outras configurações de segurança → Administradores do dispositivo",
                status(isDeviceAdminActive(context)),
                { requestDeviceAdmin(context) },
                hint = "Se não achar: desligue Bloqueador Automático → Restrições máximas. Pra remover depois, desative o admin aqui."
            ),
        )
    }

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
                    text = "Mostra o caminho e se já está ativo",
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

        // Notificação persistente (preferência do app)
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.large
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
        }

        items.forEach { item -> GuideCard(item) }

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

private data class GuideItem(
    val title: String,
    val path: String,
    val status: Pair<String, Boolean?>,
    val onOpen: () -> Unit,
    val hint: String? = null,
)

@Composable
private fun GuideCard(item: GuideItem) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                text = item.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            item.hint?.let { hint ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(item.status.first, item.status.second)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "ABRIR ›",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .clickable(onClick = item.onOpen)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun StatusPill(status: String, ok: Boolean?) {
    val bg = when (ok) {
        true -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        false -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        null -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when (ok) {
        true -> MaterialTheme.colorScheme.primary
        false -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
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

private fun shareReport(context: Context, items: List<GuideItem>) {
    val sb = StringBuilder()
    sb.append("Pakarai — relatório de ajustes\n")
    sb.append("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
    sb.append("Aparelho: ${Build.MANUFACTURER} ${Build.MODEL}\n\n")
    items.forEach { sb.append("• ${it.title}: ${it.status.first}\n") }
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
