package com.pakarai.alarme.ui.editor

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pakarai.alarme.scheduler.AlarmScheduler
import com.pakarai.alarme.ui.theme.PakaRaiSpacing
import com.pakarai.alarme.ui.util.formatTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    alarmId: Long,
    onDone: () -> Unit,
    vm: EditorViewModel = viewModel(key = "editor-$alarmId"),
) {
    val context = LocalContext.current
    val alarm by vm.alarm.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(alarmId) { vm.load(alarmId) }

    var showTimePicker by remember { mutableStateOf(false) }
    val timeState = rememberTimePickerState(
        initialHour = alarm.hour,
        initialMinute = alarm.minute,
        is24Hour = true
    )

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* já salvamos via callback abaixo */ }
    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
                vm.setRingtone(uri.toString())
            }
        }
    }

    fun requestPermissionsThenSave() {
        val needNotif = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needNotif) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (alarm.enabled) AlarmScheduler.requestExactPermission(context)
        vm.save(onDone)
    }

    if (showTimePicker) {
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(PakaRaiSpacing.lg)) {
                    TimePicker(state = timeState)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showTimePicker = false }) {
                            Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = {
                            vm.update {
                                it.copy(
                                    hour = timeState.hour,
                                    minute = timeState.minute
                                )
                            }
                            showTimePicker = false
                        }) {
                            Text("OK", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PakaRaiSpacing.lg)
    ) {
        Spacer(Modifier.height(PakaRaiSpacing.md))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = PakaRaiSpacing.md)
        ) {
            Text(
                text = if (alarmId > 0) "EDITAR ALARME" else "NOVO ALARME",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            if (alarmId > 0) {
                TextButton(onClick = { vm.delete(onDone) }) {
                    Text("Apagar", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // ── HORA ──
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showTimePicker = true },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.large,
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "HORA DO ALARME",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = formatTime(alarm.hour, alarm.minute),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "toque para mudar a hora",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedTextField(
            value = alarm.label,
            onValueChange = { text -> vm.update { a -> a.copy(label = text) } },
            label = { Text("Nome do alarme (ex: Prova de Física)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = PakaRaiSpacing.md),
            shape = MaterialTheme.shapes.medium,
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            )
        )

        Spacer(Modifier.height(PakaRaiSpacing.lg))

        // ── REPETIÇÃO ──
        EditorSectionCard("REPETIR", "Marcados = dias que o alarme toca. Nenhum marcado = toca todo dia.") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(7) { idx ->
                    ChoiceChip(
                        label = listOf("SEG", "TER", "QUA", "QUI", "SEX", "SÁB", "DOM")[idx],
                        selected = alarm.repeatDaysMask and (1 shl idx) != 0,
                        onClick = {
                            vm.update { a ->
                                a.copy(repeatDaysMask = a.repeatDaysMask xor (1 shl idx))
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ── SOM ──
        EditorSectionCard("SOM", "O som cresce junto com o volume. 'Música' deixa você escolher o som do sistema.") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChoiceChip("SIRENE", alarm.soundKind == "siren", Modifier.weight(1f)) {
                    vm.update { it.copy(soundKind = "siren", ringtoneUri = "") }
                }
                ChoiceChip("BUZINA", alarm.soundKind == "airhorn", Modifier.weight(1f)) {
                    vm.update { it.copy(soundKind = "airhorn", ringtoneUri = "") }
                }
                ChoiceChip("BIP", alarm.soundKind == "tone", Modifier.weight(1f)) {
                    vm.update { it.copy(soundKind = "tone", ringtoneUri = "") }
                }
                ChoiceChip("MÚSICA", alarm.soundKind == "ringtone", Modifier.weight(1f)) {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Som do alarme")
                    }
                    ringtoneLauncher.launch(intent)
                }
            }
        }

        // ── VOLUME ──
        EditorSectionCard(
            "VOLUME CRESCENTE",
            "Começa baixo e vai até o teto com o passar do tempo. E se você abaixar o volume durante o toque, ele volta sozinho."
        ) {
            Text(
                "Início: ${(alarm.volumeInitial * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Slider(
                value = alarm.volumeInitial,
                onValueChange = { vm.update { a -> a.copy(volumeInitial = it) } },
                valueRange = 0.05f..0.6f,
                colors = amberSliderColors()
            )
            Text(
                "Teto: ${(alarm.volumePeak * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Slider(
                value = alarm.volumePeak,
                onValueChange = { vm.update { a -> a.copy(volumePeak = it) } },
                valueRange = 0.6f..1f,
                colors = amberSliderColors()
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Curva (como o volume cresce)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChoiceChip("Linear", alarm.rampCurve == "linear", Modifier.weight(1f)) { vm.update { it.copy(rampCurve = "linear") } }
                ChoiceChip("Explosiva", alarm.rampCurve == "exp", Modifier.weight(1f)) { vm.update { it.copy(rampCurve = "exp") } }
                ChoiceChip("Escada", alarm.rampCurve == "step", Modifier.weight(1f)) { vm.update { it.copy(rampCurve = "step") } }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Tempo até atingir o teto",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChoiceChip("Instantâneo", alarm.rampMs == 0) { vm.update { it.copy(rampMs = 0) } }
                ChoiceChip("10s", alarm.rampMs == 10_000) { vm.update { it.copy(rampMs = 10_000) } }
                ChoiceChip("30s", alarm.rampMs == 30_000) { vm.update { it.copy(rampMs = 30_000) } }
                ChoiceChip("1 min", alarm.rampMs == 60_000) { vm.update { it.copy(rampMs = 60_000) } }
                ChoiceChip("3 min", alarm.rampMs == 180_000) { vm.update { it.copy(rampMs = 180_000) } }
                ChoiceChip("5 min", alarm.rampMs == 300_000) { vm.update { it.copy(rampMs = 300_000) } }
            }
        }

        // ── SONECA ──
        EditorSectionCard("SONECA", "A soneca é limitada de propósito: quando acaba, só levantar da cama resolve.") {
            Text(
                "Máximo de sonecas",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChoiceChip("Nenhuma", alarm.snoozeLimit == 0, Modifier.weight(1f)) { vm.update { it.copy(snoozeLimit = 0) } }
                ChoiceChip("1x", alarm.snoozeLimit == 1, Modifier.weight(1f)) { vm.update { it.copy(snoozeLimit = 1) } }
                ChoiceChip("2x", alarm.snoozeLimit == 2, Modifier.weight(1f)) { vm.update { it.copy(snoozeLimit = 2) } }
                ChoiceChip("3x", alarm.snoozeLimit == 3, Modifier.weight(1f)) { vm.update { it.copy(snoozeLimit = 3) } }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Duração de cada soneca",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChoiceChip("1 min", alarm.snoozeMinutes == 1, Modifier.weight(1f)) { vm.update { it.copy(snoozeMinutes = 1) } }
                ChoiceChip("3 min", alarm.snoozeMinutes == 3, Modifier.weight(1f)) { vm.update { it.copy(snoozeMinutes = 3) } }
                ChoiceChip("5 min", alarm.snoozeMinutes == 5, Modifier.weight(1f)) { vm.update { it.copy(snoozeMinutes = 5) } }
                ChoiceChip("10 min", alarm.snoozeMinutes == 10, Modifier.weight(1f)) { vm.update { it.copy(snoozeMinutes = 10) } }
            }
        }

        // ── DESAFIO ──
        EditorSectionCard(
            "DESAFIO PRA DESLIGAR",
            "Se ativado, o alarme só desliga depois de você resolver a conta. Pra quem tem o costume de desligar dormindo."
        ) {
            ToggleRow("Exigir matemática na tela bloqueada", alarm.mathEnabled) {
                enabled -> vm.update { a -> a.copy(mathEnabled = enabled) }
            }
            if (alarm.mathEnabled) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Dificuldade",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChoiceChip("Fácil", alarm.mathDifficulty == 0, Modifier.weight(1f)) { vm.update { it.copy(mathDifficulty = 0) } }
                    ChoiceChip("Médio", alarm.mathDifficulty == 1, Modifier.weight(1f)) { vm.update { it.copy(mathDifficulty = 1) } }
                    ChoiceChip("Difícil", alarm.mathDifficulty == 2, Modifier.weight(1f)) { vm.update { it.copy(mathDifficulty = 2) } }
                }
            }
        }

        // ── EXTRA ──
        EditorSectionCard("EXTRA", "") {
            ToggleRow("Vibrar junto com o som", alarm.vibrate) { enabled -> vm.update { a -> a.copy(vibrate = enabled) } }
            ToggleRow(
                "Prender tela (não deixa sair do desafio)",
                alarm.screenPin
            ) { enabled -> vm.update { a -> a.copy(screenPin = enabled) } }
        }

        Spacer(Modifier.height(PakaRaiSpacing.xl))

        androidx.compose.material3.Button(
            onClick = { requestPermissionsThenSave() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = MaterialTheme.shapes.medium,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text("SALVAR ALARME", fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(48.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorSectionCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.dp
                )
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 11.dp)
        )
    }
}

@Composable
private fun amberSliderColors() = SliderDefaults.colors(
    thumbColor = MaterialTheme.colorScheme.primary,
    activeTrackColor = MaterialTheme.colorScheme.primary,
    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
)

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}