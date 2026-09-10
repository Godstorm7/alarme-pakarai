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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.pakarai.alarme.data.AlarmEntity
import com.pakarai.alarme.scheduler.AlarmScheduler
import com.pakarai.alarme.ui.theme.PakaRaiSpacing
import com.pakarai.alarme.ui.util.formatTime

@OptIn(ExperimentalMaterial3Api::class)
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
        Row(verticalAlignment = Alignment.CenterVertically) {
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
        Text(
            text = formatTime(alarm.hour, alarm.minute),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showTimePicker = true }
                .padding(top = PakaRaiSpacing.xs)
        )
        Text(
            text = "TOQUE NA HORA PARA MUDAR",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = PakaRaiSpacing.md)
        )

        OutlinedTextField(
            value = alarm.label,
            onValueChange = { text -> vm.update { a -> a.copy(label = text) } },
            label = { Text("Nome do alarme") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            )
        )

        // ── DIAS ──
        SectionTitle("REPETIR")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(7) { idx ->
                OptionChip(
                    label = listOf("SEG", "TER", "QUA", "QUI", "SEX", "SÁB", "DOM")[idx],
                    selected = alarm.repeatDaysMask and (1 shl idx) != 0,
                    onClick = {
                        vm.update { a ->
                            a.copy(repeatDaysMask = a.repeatDaysMask xor (1 shl idx))
                        }
                    }
                )
            }
        }

        // ── SOM ──
        SectionTitle("SOM")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OptionChip("SIRENE", alarm.soundKind == "siren") {
                vm.update { it.copy(soundKind = "siren", ringtoneUri = "") }
            }
            OptionChip("BUZINA", alarm.soundKind == "airhorn") {
                vm.update { it.copy(soundKind = "airhorn", ringtoneUri = "") }
            }
            OptionChip("BIP", alarm.soundKind == "tone") {
                vm.update { it.copy(soundKind = "tone", ringtoneUri = "") }
            }
            OptionChip("MÚSICA", alarm.soundKind == "ringtone") {
                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Som do alarme")
                }
                ringtoneLauncher.launch(intent)
            }
        }

        // ── VOLUME PROGRESSIVO ──
        SectionTitle("VOLUME PROGRESSIVO")
        LabeledSlider(
            label = "Início: ${(alarm.volumeInitial * 100).toInt()}%",
            value = alarm.volumeInitial,
            range = 0.05f..0.6f
        ) { vm.update { a -> a.copy(volumeInitial = it) } }
        LabeledSlider(
            label = "Teto: ${(alarm.volumePeak * 100).toInt()}%",
            value = alarm.volumePeak,
            range = 0.6f..1f
        ) { vm.update { a -> a.copy(volumePeak = it) } }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OptionChip("Linear", alarm.rampCurve == "linear") { vm.update { it.copy(rampCurve = "linear") } }
            OptionChip("Explosiva", alarm.rampCurve == "exp") { vm.update { it.copy(rampCurve = "exp") } }
            OptionChip("Escada", alarm.rampCurve == "step") { vm.update { it.copy(rampCurve = "step") } }
        }
        Spacer(Modifier.height(8.dp))
        Text("Tempo até o teto", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OptionChip("10s", alarm.rampMs == 10_000) { vm.update { it.copy(rampMs = 10_000) } }
            OptionChip("30s", alarm.rampMs == 30_000) { vm.update { it.copy(rampMs = 30_000) } }
            OptionChip("1min", alarm.rampMs == 60_000) { vm.update { it.copy(rampMs = 60_000) } }
            OptionChip("3min", alarm.rampMs == 180_000) { vm.update { it.copy(rampMs = 180_000) } }
            OptionChip("5min", alarm.rampMs == 300_000) { vm.update { it.copy(rampMs = 300_000) } }
            OptionChip("Instantâneo", alarm.rampMs == 0) { vm.update { it.copy(rampMs = 0) } }
        }
        ToggleRow(
            label = "Bloqueio de volume (se você abaixar, sobe de volta)",
            checked = alarm.policeVolume
        ) { enabled -> vm.update { a -> a.copy(policeVolume = enabled) } }

        // ── SONECA ──
        SectionTitle("SONECA")
        Text("Limite", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OptionChip("Nenhuma", alarm.snoozeLimit == 0) { vm.update { it.copy(snoozeLimit = 0) } }
            OptionChip("1", alarm.snoozeLimit == 1) { vm.update { it.copy(snoozeLimit = 1) } }
            OptionChip("2", alarm.snoozeLimit == 2) { vm.update { it.copy(snoozeLimit = 2) } }
            OptionChip("3", alarm.snoozeLimit == 3) { vm.update { it.copy(snoozeLimit = 3) } }
        }
        Text("Duração", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OptionChip("1'", alarm.snoozeMinutes == 1) { vm.update { it.copy(snoozeMinutes = 1) } }
            OptionChip("3'", alarm.snoozeMinutes == 3) { vm.update { it.copy(snoozeMinutes = 3) } }
            OptionChip("5'", alarm.snoozeMinutes == 5) { vm.update { it.copy(snoozeMinutes = 5) } }
            OptionChip("10'", alarm.snoozeMinutes == 10) { vm.update { it.copy(snoozeMinutes = 10) } }
        }

        // ── DESAFIO ──
        SectionTitle("DESAFIO PRA DESLIGAR")
        ToggleRow("Matemática na tela bloqueada", alarm.mathEnabled) {
            enabled -> vm.update { a -> a.copy(mathEnabled = enabled) }
        }
        if (alarm.mathEnabled) {
            Text("Dificuldade", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OptionChip("Fácil", alarm.mathDifficulty == 0) { vm.update { it.copy(mathDifficulty = 0) } }
                OptionChip("Médio", alarm.mathDifficulty == 1) { vm.update { it.copy(mathDifficulty = 1) } }
                OptionChip("Difícil", alarm.mathDifficulty == 2) { vm.update { it.copy(mathDifficulty = 2) } }
            }
        }

        // ── OUTROS ──
        SectionTitle("ANTI-FUGA")
        ToggleRow("Vibrar", alarm.vibrate) { enabled -> vm.update { a -> a.copy(vibrate = enabled) } }
        ToggleRow("Prender tela (screen pinning)", alarm.screenPin) {
            enabled -> vm.update { a -> a.copy(screenPin = enabled) }
        }

        Spacer(Modifier.height(PakaRaiSpacing.xl))

        Button(
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

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(24.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            Modifier
                .width(4.dp)
                .height(14.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
) {
    Text(
        label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = range,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    )
}

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