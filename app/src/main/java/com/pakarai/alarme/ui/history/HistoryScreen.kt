package com.pakarai.alarme.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.core.RingLog
import com.pakarai.alarme.data.RingLogEntity
import com.pakarai.alarme.ui.theme.PakaRaiSpacing
import kotlinx.coroutines.launch

/**
 * HISTÓRICO — as últimas vezes que o alarme tocou.
 *
 * É diagnóstico, não relatório de sono: cada linha responde "tocou, durou X e
 * terminou como Y". Sem essa resposta, "o alarme não tocou" e "tocou e eu
 * desliguei sem lembrar" ficam indistinguíveis — e a correção de um é o oposto
 * da do outro.
 *
 * Um toque sem fim registrado aparece como "tocando": é o retrato honesto de um
 * processo morto no meio (o app não inventa um fim que não viu).
 */
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val entriesFlow = remember { AppScope.ringLog.observeRecent(100) }
    val entries by entriesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var confirmClear by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        // ── cabeçalho ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PakaRaiSpacing.lg, vertical = PakaRaiSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "HISTÓRICO",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = when (entries.size) {
                        0 -> "Nada registrado ainda"
                        1 -> "1 toque registrado"
                        else -> "${entries.size} toques registrados"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (entries.isNotEmpty()) {
                IconButton(onClick = { confirmClear = true }) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = "Limpar histórico",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        if (entries.isEmpty()) {
            EmptyHistory()
        } else {
            val groups = remember(entries) { RingHistoryFormat.groupByDay(entries, System.currentTimeMillis()) }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = PakaRaiSpacing.lg,
                    end = PakaRaiSpacing.lg,
                    bottom = PakaRaiSpacing.xl
                ),
                verticalArrangement = Arrangement.spacedBy(PakaRaiSpacing.sm)
            ) {
                for ((day, dayEntries) in groups) {
                    item(key = "day-$day") { DayHeader(day, dayEntries.size) }
                    items(dayEntries, key = { it.id }) { entry -> RingRow(entry) }
                }
                item(key = "footer") {
                    Text(
                        // o histórico do app é o resumo; o log cru fica no logcat
                        // (mesma tag que o usuário pode filtrar por adb)
                        text = "Log cru: adb logcat -s ${RingLog.TAG}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = PakaRaiSpacing.lg)
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Limpar histórico?") },
            text = { Text("Some com os registros de toque. Não mexe em nenhum alarme — só no histórico.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch { AppScope.ringLog.clearAll() }
                }) {
                    Text("Limpar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun DayHeader(day: String, count: Int) {
    Text(
        text = "$day · $count",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = PakaRaiSpacing.md, bottom = PakaRaiSpacing.xs)
    )
}

@Composable
private fun RingRow(entry: RingLogEntity) {
    val accent = outcomeColor(entry.outcome)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PakaRaiSpacing.md, vertical = PakaRaiSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = RingHistoryFormat.clockLabel(entry.startedAt),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(PakaRaiSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.label.ifBlank { "Alarme" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                val details = buildList {
                    RingHistoryFormat.durationLabel(entry.durationMs)?.let { add("tocou $it") }
                    if (entry.snoozes > 0) add("soneca ${entry.snoozes}×")
                }
                Text(
                    text = if (details.isEmpty()) "sem duração registrada" else details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutcomeChip(RingLogEntity.Outcome.label(entry.outcome), accent)
        }
    }
}

@Composable
private fun OutcomeChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(50))
            .padding(horizontal = PakaRaiSpacing.sm, vertical = 4.dp)
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun outcomeColor(outcome: String): Color = when (outcome) {
    RingLogEntity.Outcome.DISMISSED -> MaterialTheme.colorScheme.primary
    RingLogEntity.Outcome.SNOOZE -> MaterialTheme.colorScheme.secondary
    RingLogEntity.Outcome.CHECK -> MaterialTheme.colorScheme.tertiary
    RingLogEntity.Outcome.MISSED,
    RingLogEntity.Outcome.INTERRUPTED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun EmptyHistory() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PakaRaiSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.height(PakaRaiSpacing.md))
        Text(
            text = "Nenhum toque registrado",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(PakaRaiSpacing.xs))
        Text(
            text = "O registro começa no próximo alarme que tocar — o app não inventa o passado.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
