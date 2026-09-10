package com.pakarai.alarme.ui.editor

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pakarai.alarme.scheduler.AlarmScheduler
import com.pakarai.alarme.service.SoundPreview
import com.pakarai.alarme.ui.camera.PhotoCaptureCard
import com.pakarai.alarme.ui.challenge.ChallengeMode
import com.pakarai.alarme.ui.scan.QrScanActivity
import com.pakarai.alarme.ui.theme.PakaRaiSpacing
import com.pakarai.alarme.ui.util.formatTime
import java.io.File
import kotlinx.coroutines.delay

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

    var previewing by remember { mutableStateOf(false) }
    LaunchedEffect(previewing) {
        if (previewing) {
            delay(2_500)
            SoundPreview.stop()
            previewing = false
        }
    }
    DisposableEffect(Unit) {
        onDispose { SoundPreview.stop() }
    }

    var showTimePicker by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf("") }
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

    val qrScanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val content = result.data?.getStringExtra(QrScanActivity.RESULT_EXTRA)
            if (!content.isNullOrBlank()) {
                vm.update { a -> a.copy(challengeQrSecret = content.uppercase().take(32)) }
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
                TextButton(onClick = { vm.delete(onDone) }) {
                    Text("Apagar", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // HORA
        TimeHeroCard(
            hour = alarm.hour,
            minute = alarm.minute,
            onClick = { showTimePicker = true }
        )

        OutlinedTextField(
            value = alarm.label,
            onValueChange = { text -> vm.update { a -> a.copy(label = text) } },
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

        // REPETIÇÃO
        SectionShell(
            Icons.Filled.Repeat,
            "REPETIR",
            "Marcados = dias que o alarme toca. Nenhum marcado = toca todo dia."
        ) {
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

        // DESAFIO
        SectionShell(
            Icons.Filled.Bolt,
            "DESAFIO PRA DESLIGAR",
            "Prático demais desbloqueia até dormindo. Escolhe um desafio e usa."
        ) {
            ToggleRow("Exigir desafio na tela bloqueada", alarm.mathEnabled) {
                enabled -> vm.update { a -> a.copy(mathEnabled = enabled) }
            }

            if (alarm.mathEnabled) {
                val mode = ChallengeMode.fromKey(alarm.challengeMode)

                Spacer(Modifier.height(6.dp))
                Text(
                    "Modo de desafio",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChallengeMode.entries.forEach { m ->
                        ModeCard(
                            mode = m,
                            selected = alarm.challengeMode == m.key,
                            onClick = { vm.update { it.copy(challengeMode = m.key) } },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                HintCard(mode)

                if (mode == ChallengeMode.MATH) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Dificuldade",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChoiceChip("Fácil", alarm.mathDifficulty == 0, Modifier.weight(1f)) { vm.update { it.copy(mathDifficulty = 0) } }
                        ChoiceChip("Médio", alarm.mathDifficulty == 1, Modifier.weight(1f)) { vm.update { it.copy(mathDifficulty = 1) } }
                        ChoiceChip("Difícil", alarm.mathDifficulty == 2, Modifier.weight(1f)) { vm.update { it.copy(mathDifficulty = 2) } }
                    }
                }

                if (ChallengeMode.supportsRounds(mode)) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Nº de rodadas",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 2, 3, 5, 10).forEach { n ->
                            ChoiceChip("$n", alarm.challengeRounds == n, Modifier.weight(1f)) {
                                vm.update { it.copy(challengeRounds = n) }
                            }
                        }
                    }
                }

                if (mode == ChallengeMode.QR) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Conteúdo do QR (o segredo)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedTextField(
                        value = alarm.challengeQrSecret,
                        onValueChange = { text -> vm.update { a -> a.copy(challengeQrSecret = text.uppercase().take(32)) } },
                        placeholder = { Text("ex: ACORDA") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                    ) {
                        Text(
                            "O alarme só desliga lendo um QR com esse texto. Imprima e deixe em outro cômodo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            vm.update { it.copy(challengeQrSecret = "PAKARAI-${(1000..9999).random()}") }
                        }) {
                            Text("GERAR", color = MaterialTheme.colorScheme.primary)
                        }
                        TextButton(onClick = {
                            qrScanLauncher.launch(QrScanActivity.read(context))
                        }) {
                            Text("Ler QR", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                if (mode == ChallengeMode.OBJECT) {
                    Spacer(Modifier.height(16.dp))
                    ObjectRegistrationSection(
                        refPath = alarm.objectRefPath,
                        refLabel = alarm.objectRefLabel,
                        onRefPath = { path -> vm.update { it.copy(objectRefPath = path) } },
                        onRefLabel = { label -> vm.update { it.copy(objectRefLabel = label) } },
                        context = context
                    )
                }
            }
        }

        // SOM
        SectionShell(
            Icons.Filled.VolumeUp,
            "SOM",
            "Toque num som pra ouvir uma prévia. 'Música' deixa você escolher o som do sistema."
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChoiceChip("SIRENE", alarm.soundKind == "siren", Modifier.weight(1f)) {
                    vm.update { it.copy(soundKind = "siren", ringtoneUri = "") }
                    SoundPreview.playSiren(context, "siren")
                    previewing = true
                }
                ChoiceChip("BUZINA", alarm.soundKind == "airhorn", Modifier.weight(1f)) {
                    vm.update { it.copy(soundKind = "airhorn", ringtoneUri = "") }
                    SoundPreview.playSiren(context, "airhorn")
                    previewing = true
                }
                ChoiceChip("BIP", alarm.soundKind == "tone", Modifier.weight(1f)) {
                    vm.update { it.copy(soundKind = "tone", ringtoneUri = "") }
                    SoundPreview.playSiren(context, "tone")
                    previewing = true
                }
                ChoiceChip("MÚSICA", alarm.soundKind == "ringtone", Modifier.weight(1f)) {
                    SoundPreview.playRingtone(context, alarm.ringtoneUri)
                    previewing = true
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Som do alarme")
                    }
                    ringtoneLauncher.launch(intent)
                }
            }
            TextButton(
                onClick = {
                    if (alarm.soundKind == "ringtone" && alarm.ringtoneUri.isNotBlank()) {
                        SoundPreview.playRingtone(context, alarm.ringtoneUri)
                    } else {
                        SoundPreview.playSiren(context, alarm.soundKind)
                    }
                    previewing = true
                }
            ) {
                Text("Ouvir a prévia de novo", color = MaterialTheme.colorScheme.primary)
            }
        }

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
                onChange = { vm.update { a -> a.copy(volumeInitial = it) } }
            )
            VolumeSlider(
                label = "Teto: ${(alarm.volumePeak * 100).toInt()}%",
                value = alarm.volumePeak,
                valueRange = 0.6f..1f,
                onChange = { vm.update { a -> a.copy(volumePeak = it) } }
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
                ChoiceChip("Linear", alarm.rampCurve == "linear", Modifier.weight(1f)) { vm.update { it.copy(rampCurve = "linear") } }
                ChoiceChip("Explosiva", alarm.rampCurve == "exp", Modifier.weight(1f)) { vm.update { it.copy(rampCurve = "exp") } }
                ChoiceChip("Escada", alarm.rampCurve == "step", Modifier.weight(1f)) { vm.update { it.copy(rampCurve = "step") } }
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
                TextChip("Instantâneo", alarm.rampMs == 0) { vm.update { it.copy(rampMs = 0) } }
                TextChip("10s", alarm.rampMs == 10_000) { vm.update { it.copy(rampMs = 10_000) } }
                TextChip("30s", alarm.rampMs == 30_000) { vm.update { it.copy(rampMs = 30_000) } }
                TextChip("1 min", alarm.rampMs == 60_000) { vm.update { it.copy(rampMs = 60_000) } }
                TextChip("3 min", alarm.rampMs == 180_000) { vm.update { it.copy(rampMs = 180_000) } }
                TextChip("5 min", alarm.rampMs == 300_000) { vm.update { it.copy(rampMs = 300_000) } }
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
                ChoiceChip("Nenhuma", alarm.snoozeLimit == 0, Modifier.weight(1f)) { vm.update { it.copy(snoozeLimit = 0) } }
                ChoiceChip("1x", alarm.snoozeLimit == 1, Modifier.weight(1f)) { vm.update { it.copy(snoozeLimit = 1) } }
                ChoiceChip("2x", alarm.snoozeLimit == 2, Modifier.weight(1f)) { vm.update { it.copy(snoozeLimit = 2) } }
                ChoiceChip("3x", alarm.snoozeLimit == 3, Modifier.weight(1f)) { vm.update { it.copy(snoozeLimit = 3) } }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Duração de cada soneca",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChoiceChip("1 min", alarm.snoozeMinutes == 1, Modifier.weight(1f)) { vm.update { it.copy(snoozeMinutes = 1) } }
                ChoiceChip("3 min", alarm.snoozeMinutes == 3, Modifier.weight(1f)) { vm.update { it.copy(snoozeMinutes = 3) } }
                ChoiceChip("5 min", alarm.snoozeMinutes == 5, Modifier.weight(1f)) { vm.update { it.copy(snoozeMinutes = 5) } }
                ChoiceChip("10 min", alarm.snoozeMinutes == 10, Modifier.weight(1f)) { vm.update { it.copy(snoozeMinutes = 10) } }
            }
        }

        // EXTRA
        SectionShell(
            Icons.Filled.Tune,
            "EXTRA",
            "Ajustes finos do alarme."
        ) {
            ToggleRow("Vibrar junto com o som", alarm.vibrate) { enabled -> vm.update { a -> a.copy(vibrate = enabled) } }
            ToggleRow(
                "Prender tela (não deixa sair do desafio)",
                alarm.screenPin
            ) { enabled -> vm.update { a -> a.copy(screenPin = enabled) } }
        }

        Spacer(Modifier.height(PakaRaiSpacing.xl))

        Button(
            onClick = {
                if (alarm.challengeMode == "object" && alarm.objectRefPath.isBlank()) {
                    saveError = "Cadastra a foto do objeto antes de salvar."
                    return@Button
                }
                saveError = ""
                requestPermissionsThenSave()
            },
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
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

// HERO

@Composable
private fun TimeHeroCard(hour: Int, minute: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        ),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "HORA DO ALARME",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = formatTime(hour, minute),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "TOQUE PARA MUDAR A HORA ›",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// MODE GRID

@Composable
private fun ModeCard(
    mode: ChallengeMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val fg = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .height(96.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        else MaterialTheme.colorScheme.surface
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = mode.icon,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = mode.shortCaption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun ObjectRegistrationSection(
    refPath: String,
    refLabel: String,
    onRefPath: (String) -> Unit,
    onRefLabel: (String) -> Unit,
    context: Context,
) {
    var capturing by remember { mutableStateOf(false) }
    var refBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(refPath) {
        refBitmap = if (refPath.isNotBlank()) {
            BitmapFactory.decodeFile(refPath)?.let { it.asImageBitmap() }
        } else {
            null
        }
    }

    Text(
        text = "Foto do objeto",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = "Aponta a câmera pro seu objeto (ex.: sua escova de dente) e fotografa. É essa foto que vira a senha.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 17.sp
    )
    Spacer(Modifier.height(10.dp))

    if (capturing) {
        PhotoCaptureCard(
            targetDir = File(context.filesDir, "objects"),
            onCaptured = { file ->
                onRefPath(file.absolutePath)
                capturing = false
            },
            buttonText = "CADASTRAR FOTO",
            modifier = Modifier.fillMaxWidth()
        )
        TextButton(
            onClick = { capturing = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (refBitmap != null) {
                    Image(
                        bitmap = refBitmap!!,
                        contentDescription = "Objeto cadastrado",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Objeto cadastrado",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = "Nenhum objeto cadastrado ainda.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { capturing = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (refPath.isBlank()) "CADASTRAR FOTO" else "RECADASTRAR",
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = refLabel,
        onValueChange = onRefLabel,
        label = { Text("O que é esse objeto? (dica no alarme)") },
        placeholder = { Text("ex: minha escova") },
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
}

@Composable
private fun HintCard(mode: ChallengeMode) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = mode.hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// SECTIONS

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SectionShell(
    icon: ImageVector,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun TextChip(
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
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
        )
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
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 11.dp)
        )
    }
}

@Composable
private fun VolumeSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = valueRange,
        colors = primarySliderColors()
    )
}

@Composable
private fun primarySliderColors() = SliderDefaults.colors(
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