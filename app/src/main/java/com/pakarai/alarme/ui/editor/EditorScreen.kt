package com.pakarai.alarme.ui.editor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.clickable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.data.AlarmEntity
import com.pakarai.alarme.scheduler.AlarmScheduler
import com.pakarai.alarme.service.SoundPreview
import com.pakarai.alarme.service.alarmSoundLabel
import com.pakarai.alarme.ui.challenge.ChallengeMode
import com.pakarai.alarme.ui.theme.PakaRaiSpacing

/** Sub-tela cheia dentro do editor (missões e som) compartilhando o mesmo ViewModel. */
private enum class EditorSub { None, Missions, Audio }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    alarmId: Long,
    onBack: () -> Unit,
    onDone: () -> Unit,
    vm: EditorViewModel = viewModel(key = "editor-$alarmId"),
) {
    val context = LocalContext.current
    val alarm by vm.alarm.collectAsStateWithLifecycle()

    LaunchedEffect(alarmId) { vm.load(alarmId) }

    // para a prévia/demo se o usuário sair do editor antes do fim
    DisposableEffect(Unit) {
        onDispose { SoundPreview.stop() }
    }

    var showTimePicker by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf("") }
    var askUnlock by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var subScreen by remember { mutableStateOf(EditorSub.None) }
    val timeState = rememberTimePickerState(
        initialHour = alarm.hour,
        initialMinute = alarm.minute,
        is24Hour = true
    )

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* já salvamos via callback abaixo */ }

    fun requestPermissionsThenSave() {
        val needNotif = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needNotif) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (alarm.enabled) AlarmScheduler.requestExactPermission(context)
        vm.save(onDone)
    }

    when (subScreen) {
        EditorSub.Missions -> MissionEditorScreen(
            vm = vm,
            onBack = { subScreen = EditorSub.None }
        )
        EditorSub.Audio -> AudioEditorScreen(
            vm = vm,
            onBack = { subScreen = EditorSub.None }
        )
        EditorSub.None -> MainEditorContent(
            alarmId = alarmId,
            alarm = alarm,
            onBack = onBack,
            showTimePicker = showTimePicker,
            timeState = timeState,
            onShowTimePicker = { showTimePicker = it },
            askUnlock = askUnlock,
            onAskUnlock = { askUnlock = it },
            confirmDelete = confirmDelete,
            onConfirmDelete = { confirmDelete = it },
            saveError = saveError,
            onSaveError = { saveError = it },
            onSave = { requestPermissionsThenSave() },
            onDelete = { vm.delete(onDone) },
            onOpenMissions = { subScreen = EditorSub.Missions },
            onOpenAudio = { subScreen = EditorSub.Audio },
            update = vm::update
        )
    }
}

/** Cartão de navegação p/ as telas cheias de MISSÕES e SOM. */
private fun noQueue(alarm: AlarmEntity): List<ChallengeMode> =
    if (alarm.challengeModes.isBlank()) emptyList()
    else ChallengeMode.queueFrom(alarm.challengeModes, alarm.challengeMode)

@Composable
private fun NavCard(
    icon: ImageVector,
    title: String,
    summary: String,
    buttonLabel: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .height(48.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(buttonLabel, fontWeight = FontWeight.Black)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MainEditorContent(
    alarmId: Long,
    alarm: AlarmEntity,
    onBack: () -> Unit,
    showTimePicker: Boolean,
    timeState: androidx.compose.material3.TimePickerState,
    onShowTimePicker: (Boolean) -> Unit,
    askUnlock: Boolean,
    onAskUnlock: (Boolean) -> Unit,
    confirmDelete: Boolean,
    onConfirmDelete: (Boolean) -> Unit,
    saveError: String,
    onSaveError: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onOpenMissions: () -> Unit,
    onOpenAudio: () -> Unit,
    update: ((AlarmEntity) -> AlarmEntity) -> Unit,
) {
    val queue = noQueue(alarm)
    val missionSummary = if (!alarm.mathEnabled) "Desligar no botão (sem desafio)"
    else if (queue.isEmpty()) "Nenhum desafio ainda"
    else queue.groupingBy { it }.eachCount().entries.joinToString("   ") { (m, c) ->
        ChallengeMode.chipLabel(m) + if (c > 1) "×$c" else ""
    }

    // Toque num chip de rampa = demonstração de <6s do volume crescendo.
    val context = LocalContext.current
    fun demoRamp(a: AlarmEntity) {
        SoundPreview.playRampDemo(
            context,
            a.soundKind,
            a.ringtoneUri,
            a.spotifyUri,
            a.fallbackKind,
            a.fallbackUri,
            a.volumeInitial,
            a.volumePeak,
            a.rampMs,
            a.rampCurve,
        )
    }

    if (showTimePicker) {
        Dialog(onDismissRequest = { onShowTimePicker(false) }) {
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
                        TextButton(onClick = { onShowTimePicker(false) }) {
                            Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = {
                            update {
                                it.copy(
                                    hour = timeState.hour,
                                    minute = timeState.minute
                                )
                            }
                            onShowTimePicker(false)
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
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = PakaRaiSpacing.lg)
    ) {
        Spacer(Modifier.height(PakaRaiSpacing.md))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = PakaRaiSpacing.md)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (alarmId > 0) "EDITAR ALARME" else "NOVO ALARME",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Acorda pra valer",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (alarmId > 0) {
                TextButton(
                    onClick = { onConfirmDelete(true) },
                    enabled = !AppScope.stateManager.isInActiveCycle(alarmId)
                ) {
                    Text("Apagar", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // HORA
        TimeHeroCard(
            hour = alarm.hour,
            minute = alarm.minute,
            onClick = { onShowTimePicker(true) }
        )

        OutlinedTextField(
            value = alarm.label,
            onValueChange = { text -> update { a -> a.copy(label = text) } },
            label = { Text("Nome do alarme") },
            placeholder = { Text("ex: Prova de Física") },
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            modifier = Modifier.fillMaxWidth().padding(top = PakaRaiSpacing.md),
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
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

        // REPETICAO
        SectionShell(
            Icons.Filled.Repeat,
            "REPETIR",
            "Marcados = dias que o alarme toca. Nenhum marcado = toca todo dia."
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(7) { idx ->
                    DayChip(
                        label = listOf("SEG", "TER", "QUA", "QUI", "SEX", "SÁB", "DOM")[idx],
                        selected = alarm.repeatDaysMask and (1 shl idx) != 0,
                        onClick = {
                            update { a ->
                                a.copy(repeatDaysMask = a.repeatDaysMask xor (1 shl idx))
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TextChip("Só uma vez", alarm.repeatDaysMask == 0) {
                    update { a -> a.copy(repeatDaysMask = 0) }
                }
                TextChip("Dias úteis", alarm.repeatDaysMask == 0b0011111) {
                    update { a -> a.copy(repeatDaysMask = 0b0011111) }
                }
                TextChip("Fim de semana", alarm.repeatDaysMask == 0b1100000) {
                    update { a -> a.copy(repeatDaysMask = 0b1100000) }
                }
                TextChip("Todos", alarm.repeatDaysMask == 0b1111111) {
                    update { a -> a.copy(repeatDaysMask = 0b1111111) }
                }
            }
        }

        // MISSÕES (tela cheia)
        NavCard(
            icon = Icons.Filled.Bolt,
            title = "MISSÕES PRA DESLIGAR",
            summary = missionSummary,
            buttonLabel = "ABRIR MISSÕES",
            onClick = onOpenMissions
        )

        // SOM (tela cheia)
        NavCard(
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            title = "SOM DO ALARME",
            summary = alarmSoundLabel(alarm),
            buttonLabel = "ABRIR SOM",
            onClick = onOpenAudio
        )

        // VOLUME
        SectionShell(
            Icons.Filled.GraphicEq,
            "VOLUME CRESCENTE",
            "Começa baixo e vai até o teto. E se você abaixar o volume durante o toque, ele volta sozinho."
        ) {
            VolumeSlider(
                label = "Início: ${(alarm.volumeInitial * 100).toInt()}%",
                value = alarm.volumeInitial,
                valueRange = 0.05f..0.6f,
                onChange = { update { a -> a.copy(volumeInitial = it) } }
            )
            VolumeSlider(
                label = "Teto: ${(alarm.volumePeak * 100).toInt()}%",
                value = alarm.volumePeak,
                valueRange = 0.6f..1f,
                onChange = { update { a -> a.copy(volumePeak = it) } }
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Curva (como o volume cresce)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChoiceChip("Linear", alarm.rampCurve == "linear", Modifier.weight(1f)) { update { a -> a.copy(rampCurve = "linear").also { demoRamp(it) } } }
                ChoiceChip("Explosiva", alarm.rampCurve == "exp", Modifier.weight(1f)) { update { a -> a.copy(rampCurve = "exp").also { demoRamp(it) } } }
                ChoiceChip("Escada", alarm.rampCurve == "step", Modifier.weight(1f)) { update { a -> a.copy(rampCurve = "step").also { demoRamp(it) } } }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Tempo até atingir o teto",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TextChip("Instantâneo", alarm.rampMs == 0) { update { a -> a.copy(rampMs = 0).also { demoRamp(it) } } }
                TextChip("1 min", alarm.rampMs == 60_000) { update { a -> a.copy(rampMs = 60_000).also { demoRamp(it) } } }
                TextChip("2 min", alarm.rampMs == 120_000) { update { a -> a.copy(rampMs = 120_000).also { demoRamp(it) } } }
                TextChip("5 min", alarm.rampMs == 300_000) { update { a -> a.copy(rampMs = 300_000).also { demoRamp(it) } } }
                TextChip("15 min", alarm.rampMs == 900_000) { update { a -> a.copy(rampMs = 900_000).also { demoRamp(it) } } }
            }
        }

        // SONECA
        SectionShell(
            Icons.Filled.Bedtime,
            "SONECA",
            "A soneca é limitada de propósito: quando acaba, só levantar da cama resolve."
        ) {
            Text(
                "Máximo de sonecas",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChoiceChip("Nenhuma", alarm.snoozeLimit == 0, Modifier.weight(1f)) { update { it.copy(snoozeLimit = 0) } }
                ChoiceChip("1x", alarm.snoozeLimit == 1, Modifier.weight(1f)) { update { it.copy(snoozeLimit = 1) } }
                ChoiceChip("2x", alarm.snoozeLimit == 2, Modifier.weight(1f)) { update { it.copy(snoozeLimit = 2) } }
                ChoiceChip("3x", alarm.snoozeLimit == 3, Modifier.weight(1f)) { update { it.copy(snoozeLimit = 3) } }
            }
            if (alarm.snoozeLimit > 0) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "Duração de cada soneca",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChoiceChip("1 min", alarm.snoozeMinutes == 1, Modifier.weight(1f)) { update { it.copy(snoozeMinutes = 1) } }
                    ChoiceChip("3 min", alarm.snoozeMinutes == 3, Modifier.weight(1f)) { update { it.copy(snoozeMinutes = 3) } }
                    ChoiceChip("5 min", alarm.snoozeMinutes == 5, Modifier.weight(1f)) { update { it.copy(snoozeMinutes = 5) } }
                    ChoiceChip("10 min", alarm.snoozeMinutes == 10, Modifier.weight(1f)) { update { it.copy(snoozeMinutes = 10) } }
                }
            }
        }

        // EXTRA
        SectionShell(
            Icons.Filled.Tune,
            "EXTRA",
            "Ajustes finos do alarme."
        ) {
            ToggleRow("Vibrar junto com o som", alarm.vibrate) { enabled -> update { a -> a.copy(vibrate = enabled) } }
        }

        // PROTEÇÃO
        SectionShell(
            Icons.Filled.Lock,
            "PROTEÇÃO",
            "Travas contra a preguiça."
        ) {
            ToggleRow(
                "Cadeado (não deixa desligar ou apagar)",
                alarm.locked
            ) { enabled ->
                if (alarm.locked && !enabled) {
                    onAskUnlock(true)
                } else {
                    // travar também LIGA o alarme, pra nunca ficar destravado e desligado
                    update { a -> a.copy(locked = enabled, enabled = if (enabled) true else a.enabled) }
                }
            }
            ToggleRow(
                "Confirmação \"AINDA ACORDADO?\"",
                alarm.ackRequired
            ) { enabled -> update { a -> a.copy(ackRequired = enabled) } }
            Spacer(Modifier.height(14.dp))
            Text(
                "Perguntar de novo a cada:",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(300, 600, 900, 1800, 3600).forEach { seconds ->
                    ChoiceChip(
                        label = "${seconds / 60} min",
                        selected = alarm.ackSeconds == seconds,
                        modifier = Modifier.weight(1f)
                    ) { update { it.copy(ackSeconds = seconds) } }
                }
            }
            Text(
                text = "Depois do desafio, o alarme fica mudo e de tempos em tempos pergunta \"AINDA ACORDADO?\" por 30s, com SIM e NÃO em lugares aleatórios. SIM encerra; sem responder, o som volta e o desafio recomeça.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { onConfirmDelete(false) },
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
                        text = "\"${alarm.label}\" às ${alarm.hour}:${alarm.minute} não vai mais tocar. Não tem volta.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        onConfirmDelete(false)
                        onDelete()
                    }) {
                        Text("Apagar", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onConfirmDelete(false) }) {
                        Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }

        if (askUnlock) {
            AlertDialog(
                onDismissRequest = { onAskUnlock(false) },
                containerColor = MaterialTheme.colorScheme.surface,
                title = {
                    Text(
                        text = "Desbloquear alarme?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                },
                text = {
                    Text(
                        text = "Com o cadeado aberto já dá pra desligar e apagar este alarme. Desbloquear mesmo assim?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        onAskUnlock(false)
                        update { a -> a.copy(locked = false) }
                    }) {
                        Text("Desbloquear", color = MaterialTheme.colorScheme.primary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onAskUnlock(false) }) {
                        Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }

        Spacer(Modifier.height(PakaRaiSpacing.xl))

        Button(
            onClick = {
                val queueForSave = if (alarm.challengeModes.isBlank()) emptyList<ChallengeMode>()
                else ChallengeMode.queueFrom(alarm.challengeModes, alarm.challengeMode)
                if (alarm.mathEnabled && queueForSave.isEmpty()) {
                    onSaveError("Adiciona pelo menos um desafio na lista.")
                    return@Button
                }
                if (queueForSave.any { it == ChallengeMode.OBJECT } && alarm.objectRefPath.isBlank()) {
                    onSaveError("Cadastra a foto do objeto antes de salvar.")
                    return@Button
                }
                onSaveError("")
                onSave()
            },
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            )
        ) {
            Icon(
                imageVector = Icons.Filled.Done,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text("SALVAR ALARME", fontWeight = FontWeight.Black, fontSize = 16.sp)
        }
        if (saveError.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = saveError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(48.dp))
    }
}