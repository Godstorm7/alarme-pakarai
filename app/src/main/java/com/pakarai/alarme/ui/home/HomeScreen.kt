package com.pakarai.alarme.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.R
import com.pakarai.alarme.data.AlarmEntity
import com.pakarai.alarme.scheduler.AlarmScheduler
import com.pakarai.alarme.service.alarmSoundLabel
import com.pakarai.alarme.ui.challenge.ChallengeMode
import com.pakarai.alarme.ui.theme.PakaRaiAccent
import com.pakarai.alarme.ui.theme.PakaRaiAccents
import com.pakarai.alarme.ui.theme.PakaRaiSpacing
import com.pakarai.alarme.ui.util.computeNextTriggerForUi
import com.pakarai.alarme.ui.util.formatCountdown
import com.pakarai.alarme.ui.util.nextFireLabel
import kotlinx.coroutines.delay
import com.pakarai.alarme.ui.util.repeatDaysLabel
import java.util.Calendar

@Composable
fun HomeScreen(
    onNewAlarm: () -> Unit,
    onEditAlarm: (Long) -> Unit,
    onOpenWizard: () -> Unit,
    onOpenGuard: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val alarms by vm.alarms.collectAsStateWithLifecycle()
    val accentId by AppScope.settings.accentId.collectAsStateWithLifecycle()
    var showThemeMenu by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<AlarmEntity?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewAlarm,
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = Color.White
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Novo alarme",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // ── Cabeçalho ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PakaRaiSpacing.lg, vertical = PakaRaiSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painterResource(R.drawable.ic_clock),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.width(PakaRaiSpacing.sm))
                Text(
                    text = "PAKARAI",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.weight(1f))
                CountPill(count = alarms.size)
                IconButton(onClick = { showThemeMenu = true }) {
                    Icon(
                        Icons.Default.Palette,
                        contentDescription = "Mudar cor do app",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            if (showThemeMenu) {
                ThemeAccentDialog(
                    current = accentId,
                    onSelect = { AppScope.settings.setAccent(it) },
                    onDismiss = { showThemeMenu = false }
                )
            }

            pendingDelete?.let { alarm ->
                DeleteAlarmDialog(
                    alarm = alarm,
                    onConfirm = { vm.delete(alarm); pendingDelete = null },
                    onDismiss = { pendingDelete = null }
                )
            }

            if (vm.isSamsung && !vm.wizardShown) {
                WizardBanner(onClick = {
                    vm.markWizardShown()
                    onOpenWizard()
                })
            }

            if (vm.needsExactPermission.collectAsState().value) {
                ExactPermissionBanner(onFix = {
                    AlarmScheduler.requestExactPermission(AppScope.appContext)
                })
            }

            if (alarms.isEmpty()) {
                EmptyState(onNewAlarm)
            } else {
                HeroNextAlarm(alarms)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = PakaRaiSpacing.lg,
                        end = PakaRaiSpacing.lg,
                        bottom = 96.dp,
                        top = PakaRaiSpacing.sm
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(alarms, key = { it.id }) { alarm ->
                        AlarmCard(
                            alarm = alarm,
                            deleteBlocked = AppScope.stateManager.isInActiveCycle(alarm.id),
                            onToggle = { vm.toggleEnabled(alarm, it) },
                            onEdit = { onEditAlarm(alarm.id) },
                            onDelete = { pendingDelete = alarm }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CountPill(count: Int) {
    Text(
        text = if (count == 1) "1 alarme" else "$count alarmes",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun EmptyState(onNewAlarm: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = PakaRaiSpacing.xl)
        ) {
            Icon(
                painterResource(R.drawable.ic_clock),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(72.dp)
            )
            Spacer(Modifier.height(PakaRaiSpacing.lg))
            Text(
                text = "Nenhum alarme",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(PakaRaiSpacing.sm))
            Text(
                text = "Crie um e tente dormir tranquilo.\n(Boa sorte.)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(PakaRaiSpacing.lg))
            PrimaryCta(
                text = "Criar alarme",
                onClick = onNewAlarm
            )
        }
    }
}

@Composable
private fun PrimaryCta(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.tertiary)
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 14.dp)
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onTertiary
        )
    }
}

/** Hero com o próximo alarme (maior, com glow) — o relógio que te espera. */
@Composable
private fun HeroNextAlarm(alarms: List<AlarmEntity>) {
    val upcoming = remember(alarms) {
        alarms.asSequence()
            .filter { it.enabled }
            .map { a -> a to computeNextTriggerForUi(a, System.currentTimeMillis()) }
            .minByOrNull { it.second }
    } ?: return
    val (alarm, millis) = upcoming
    val cal = remember { Calendar.getInstance().apply { timeInMillis = millis } }
    cal.timeInMillis = millis
    val now = remember { Calendar.getInstance() }
    val rel = when {
        sameDay(cal, now) -> "HOJE"
        sameDay(cal, Calendar.getInstance().also { it.add(Calendar.DAY_OF_YEAR, 1) }) -> "AMANHÃ"
        else -> arrayOf("DOM", "SEG", "TER", "QUA", "QUI", "SEX", "SÁB")[cal.get(Calendar.DAY_OF_WEEK) - 1]
    }
    val accent = MaterialTheme.colorScheme.primary
    // contagem viva ("toca em 4h 32min") — atualiza a cada 30s enquanto a tela está visível
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(millis) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(30_000)
        }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PakaRaiSpacing.lg, vertical = PakaRaiSpacing.xs),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "PRÓXIMO ALARME",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = accent
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)),
                style = MaterialTheme.typography.displayLarge.copy(
                    shadow = Shadow(accent.copy(alpha = 0.55f), blurRadius = 28f, offset = Offset(0f, 0f))
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "$rel · " + alarm.label.uppercase().ifEmpty { "DESPERTAR" },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = formatCountdown(millis - nowMs),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = accent
            )
        }
    }
}

private fun sameDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

@Composable
private fun WizardBanner(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PakaRaiSpacing.lg, vertical = PakaRaiSpacing.sm)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(PakaRaiSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(R.drawable.ic_alert),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Sua Samsung pode MATAR o alarme",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "Toque para desbloquear bateria + autostart",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                )
            }
            Icon(
                painterResource(R.drawable.ic_arrow_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ExactPermissionBanner(onFix: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PakaRaiSpacing.lg, vertical = PakaRaiSpacing.sm)
            .clickable(onClick = onFix),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(PakaRaiSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(R.drawable.ic_alert),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "ALARMES PAUSADOS",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    "Sem permissão de alarme exato nada dispara. Toque para corrigir",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                )
            }
            Icon(
                painterResource(R.drawable.ic_arrow_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlarmCard(
    alarm: AlarmEntity,
    deleteBlocked: Boolean = false,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 14.dp)
                    .width(4.dp)
                    .height(58.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (alarm.enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "%02d:%02d".format(alarm.hour, alarm.minute),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = if (alarm.enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    if (!alarm.enabled) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "OFF",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = alarm.label.ifBlank { "DESPERTAR" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    InfoChip(repeatDaysLabel(alarm.repeatDaysMask))
                    InfoChip(alarmSoundLabel(alarm))
                    if (alarm.mathEnabled) {
                        val queue = if (alarm.challengeModes.isBlank()) emptyList<ChallengeMode>()
                        else ChallengeMode.queueFrom(alarm.challengeModes, alarm.challengeMode)
                        val tag = if (queue.isEmpty()) "SEM DESAFIO"
                        else queue.groupingBy { it }.eachCount().entries.joinToString(" ") { (m, c) ->
                            ChallengeMode.chipLabel(m) + if (c > 1) "×$c" else ""
                        }
                        InfoChip(tag)
                    }
                    if (alarm.snoozeLimit > 0) InfoChip("Zz ${alarm.snoozeMinutes}'")
                }
                Spacer(Modifier.height(10.dp))
                if (alarm.enabled) {
                    Text(
                        text = "TOCA ${nextFireLabel(alarm).uppercase()}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Switch(
                    checked = alarm.enabled,
                    onCheckedChange = if (alarm.locked) null else onToggle,
                    enabled = !alarm.locked,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = if (alarm.locked) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                Spacer(Modifier.height(8.dp))
                if (alarm.locked) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Alarme travado: não desliga nem apaga pela Home",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "TRAVADO",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    IconButton(
                        onClick = onDelete,
                        enabled = !deleteBlocked,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Apagar alarme",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = if (deleteBlocked) 0.35f else 1f
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun ThemeAccentDialog(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Cor do app",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Escolhe a cor que combina com o breu da madrugada.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PakaRaiAccents.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { accent ->
                            AccentSwatch(
                                accent = accent,
                                selected = accent.id == current,
                                onClick = { onSelect(accent.id) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("OK", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

@Composable
private fun DeleteAlarmDialog(
    alarm: AlarmEntity,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val hh = alarm.hour.toString().padStart(2, '0')
    val mm = alarm.minute.toString().padStart(2, '0')
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Apagar alarme?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
        },
        text = {
            Text(
                text = "\"${alarm.label}\" às $hh:$mm não vai mais tocar. Não tem volta.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) {
                Text("Apagar", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancelar", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

@Composable
private fun AccentSwatch(
    accent: PakaRaiAccent,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20))
                .background(accent.color),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = accent.onColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = accent.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}