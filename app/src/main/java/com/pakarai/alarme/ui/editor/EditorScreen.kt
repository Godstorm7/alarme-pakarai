package com.pakarai.alarme.ui.editor

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.R
import com.pakarai.alarme.core.QrGenerator
import com.pakarai.alarme.scheduler.AlarmScheduler
import com.pakarai.alarme.service.SoundPreview
import com.pakarai.alarme.ui.camera.PhotoCaptureCard
import com.pakarai.alarme.ui.challenge.ChallengeMode
import com.pakarai.alarme.ui.challenge.generateMathQuestion
import com.pakarai.alarme.ui.scan.QrScanActivity
import com.pakarai.alarme.ui.theme.PakaRaiSpacing
import com.pakarai.alarme.ui.util.formatTime
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.launch

/** Gera a imagem do QR do alarme e abre o share sheet pra imprimir (salva em Fotos). */
private fun shareQrToPrint(context: Context, secret: String) {
    if (secret.isBlank()) return
    val bmp = QrGenerator.encode(secret) ?: return
    val name = "pakarai_qr_${System.currentTimeMillis()}.png"
    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/PakaRai")
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val item = context.contentResolver.insert(collection, values) ?: return
        context.contentResolver.openOutputStream(item)?.use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        item
    } else {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "qr")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, name)
        FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
        Uri.fromFile(file)
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "QR do alarme PakaRai ($secret)")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}

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
    DisposableEffect(Unit) {
        onDispose { SoundPreview.stop() }
    }

    var showTimePicker by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf("") }
    var askUnlock by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
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
                SoundPreview.playRingtone(context, uri.toString())
                previewing = true
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
            .systemBarsPadding()
            .imePadding()
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
                TextButton(
                    onClick = { confirmDelete = true },
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

        // REPETICAO
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
                val queue = if (alarm.challengeModes.isBlank()) emptyList<ChallengeMode>()
                else ChallengeMode.queueFrom(alarm.challengeModes, alarm.challengeMode)
                var showPicker by remember { mutableStateOf(false) }

                fun setQueue(updated: List<ChallengeMode>) {
                    vm.update { a ->
                        a.copy(
                            challengeMode = updated.firstOrNull()?.key ?: a.challengeMode,
                            challengeModes = ChallengeMode.queueToString(updated)
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    "Lista de desafios (na ordem)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Adiciona um por um. Repetir o mesmo desafio = ele toca de novo na hora do alarme.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                if (queue.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "NENHUM DESAFIO AINDA — sem lista, o alarme desliga no botão.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        queue.forEachIndexed { i, m ->
                            RoundRow(i, m) { setQueue(queue.filterIndexed { idx, _ -> idx != i }) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                Button(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("ADICIONAR DESAFIO", fontWeight = FontWeight.Black)
                }

                if (queue.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    HintCard(queue)
                }

                if (showPicker) {
                    AlertDialog(
                        onDismissRequest = { showPicker = false },
                        containerColor = MaterialTheme.colorScheme.surface,
                        title = {
                            Text(
                                text = "Adicionar desafio",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black
                            )
                        },
                        text = {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ChallengeMode.entries.forEach { m ->
                                    ModeCard(
                                        mode = m,
                                        selected = false,
                                        orderIndex = -1,
                                        onClick = {
                                            setQueue(queue + m)
                                            showPicker = false
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = { showPicker = false }) {
                                Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    )
                }

                queue.distinct().forEach { mode ->
                    when (mode) {
                        ChallengeMode.MATH -> {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Dificuldade da Matemática",
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
                            Spacer(Modifier.height(10.dp))
                            MathPreviewCard(alarm.mathDifficulty)
                        }

                        ChallengeMode.SHAKE -> {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Quantas agitadas? (AGITAR)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(6.dp))
                            MovementPicker(
                                current = alarm.shakeCount,
                                presets = listOf(5, 10, 15, 20)
                            ) { n -> vm.update { it.copy(shakeCount = n) } }
                        }

                        ChallengeMode.STEPS -> {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Passos a andar (ANDAR)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(6.dp))
                            MovementPicker(
                                current = alarm.stepCount,
                                presets = listOf(10, 20, 30, 50)
                            ) { n -> vm.update { it.copy(stepCount = n) } }
                        }

                        ChallengeMode.SPIN -> {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Girar até quantos graus? (GIRAR)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(6.dp))
                            MovementPicker(
                                current = alarm.spinCount,
                                presets = listOf(45, 90, 180, 360),
                                suffix = "°"
                            ) { n -> vm.update { it.copy(spinCount = n) } }
                        }

                        ChallengeMode.QR -> {
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
                                TextButton(onClick = {
                                    shareQrToPrint(context, alarm.challengeQrSecret)
                                }) {
                                    Text(
                                        stringResource(R.string.qr_share),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        ChallengeMode.OBJECT -> {
                            Spacer(Modifier.height(16.dp))
                            ObjectRegistrationSection(
                                refPath = alarm.objectRefPath,
                                refLabel = alarm.objectRefLabel,
                                onRefPath = { path -> vm.update { it.copy(objectRefPath = path) } },
                                onRefLabel = { label -> vm.update { it.copy(objectRefLabel = label) } },
                                context = context
                            )
                        }

                        else -> {}
                    }
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
                    val previewUri = alarm.ringtoneUri.ifBlank {
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.toString() ?: ""
                    }
                    SoundPreview.playRingtone(context, previewUri)
                    previewing = true
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Som do alarme")
                    }
                    ringtoneLauncher.launch(intent)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                if (previewing) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "TOCANDO…",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = {
                        SoundPreview.stop()
                        previewing = false
                    }) {
                        Text("PARAR", color = MaterialTheme.colorScheme.primary)
                    }
                }
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
                    askUnlock = true
                } else {
                    vm.update { a -> a.copy(locked = enabled) }
                }
            }
            ToggleRow(
                "Confirmação \"AINDA ACORDADO?\"",
                alarm.ackRequired
            ) { enabled -> vm.update { a -> a.copy(ackRequired = enabled) } }
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
                    ) { vm.update { it.copy(ackSeconds = seconds) } }
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
                onDismissRequest = { confirmDelete = false },
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
                    TextButton(onClick = { confirmDelete = false; vm.delete(onDone) }) {
                        Text("Apagar", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) {
                        Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }

        if (askUnlock) {
            AlertDialog(
                onDismissRequest = { askUnlock = false },
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
                        askUnlock = false
                        vm.update { a -> a.copy(locked = false) }
                    }) {
                        Text("Desbloquear", color = MaterialTheme.colorScheme.primary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { askUnlock = false }) {
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
                    saveError = "Adiciona pelo menos um desafio na lista."
                    return@Button
                }
                if (queueForSave.any { it == ChallengeMode.OBJECT } && alarm.objectRefPath.isBlank()) {
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
