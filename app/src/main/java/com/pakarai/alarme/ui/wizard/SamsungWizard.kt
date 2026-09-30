package com.pakarai.alarme.ui.wizard

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.core.SetupItem
import com.pakarai.alarme.core.SetupLinks
import com.pakarai.alarme.core.SetupState
import com.pakarai.alarme.core.SetupStatus
import com.pakarai.alarme.core.SetupStatusReader
import com.pakarai.alarme.ui.theme.PakaRaiSpacing

/**
 * Wizard de confiabilidade SAMSUNG / OneUI.
 *
 * A Samsung tem DUAS proteções: o Doze do Android E o "Background usage limits"
 * (Sleeping/Deep sleeping apps). Um app de alarme precisa sair de ambas pra não
 * morrer de madrugada.
 *
 * Tudo vem de [SetupStatusReader] — nada de "MANUAL/OK" escrito à mão. Quando o
 * Android não expõe o estado (Fixar janelas, Smart Manager, Bloqueador
 * Automático), o passo vira "CONFIRME" com o caminho escrito pra copiar, em vez
 * de um verde falso.
 */
private val SAMSUNG_STEPS = listOf(
    SetupItem.BATTERY,
    SetupItem.BACKGROUND,
    SetupItem.EXACT_ALARM,
    SetupItem.FULL_SCREEN,
    SetupItem.NOTIFICATIONS,
    SetupItem.ACCESSIBILITY,
    SetupItem.SCREEN_PIN,
    SetupItem.ADMIN,
    SetupItem.AUTO_BLOCKER,
)

@Composable
fun SamsungWizardScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }
    var copied by remember { mutableStateOf<SetupItem?>(null) }

    // a pessoa sai pro ajuste e volta: re-leemos do sistema no ON_RESUME
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val statuses = remember(refreshKey) {
        val all = SetupStatusReader.read(context, AppScope.settings.guardUserEnabled)
        SAMSUNG_STEPS.mapNotNull { item -> all.firstOrNull { it.item == item } }
    }
    val unresolved = statuses.count { it.state == SetupState.ACTION || it.state == SetupState.MANUAL }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .systemBarsPadding()
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
                    text = "A OneUI pode MATAR o alarme de madrugada: ela hiberna app que fica em background.\nDesbloqueie as travas abaixo pra acordar de verdade.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(Modifier.height(PakaRaiSpacing.lg))

        StepsSummary(unresolved = unresolved)

        Spacer(Modifier.height(PakaRaiSpacing.md))

        statuses.forEachIndexed { index, status ->
            WizardStep(
                num = index + 1,
                status = status,
                copied = copied == status.item,
                onOpen = { SetupLinks.open(context, status.item) },
                onCopyPath = {
                    SetupLinks.copyPath(context, status.item)
                    copied = status.item
                }
            )
        }

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            )
        ) {
            Text("CONCLUÍDO", fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun StepsSummary(unresolved: Int) {
    val (label, color) = when (unresolved) {
        0 -> "TUDO LIBERADO" to MaterialTheme.colorScheme.primary
        1 -> "FALTA 1 AJUSTE" to MaterialTheme.colorScheme.error
        else -> "FALTAM $unresolved AJUSTES" to MaterialTheme.colorScheme.error
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun WizardStep(
    num: Int,
    status: SetupStatus,
    copied: Boolean,
    onOpen: () -> Unit,
    onCopyPath: () -> Unit,
) {
    val item = status.item
    val manual = status.state == SetupState.MANUAL
    val settled = status.state == SetupState.OK || status.state == SetupState.SKIPPED
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (settled) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        ),
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
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (manual) item.path else item.why,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatePill(status.state)
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
}

@Composable
private fun StatePill(state: SetupState) {
    val (label, bg, fg) = when (state) {
        SetupState.OK -> Triple(
            "OK",
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
