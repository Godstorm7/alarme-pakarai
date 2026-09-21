package com.pakarai.alarme.ui.editor

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pakarai.alarme.R
import com.pakarai.alarme.core.QrGenerator
import com.pakarai.alarme.ui.challenge.ChallengeMode
import com.pakarai.alarme.ui.challenge.ChallengePreview
import com.pakarai.alarme.ui.scan.QrScanActivity
import com.pakarai.alarme.ui.theme.PakaRaiSpacing
import java.io.File
import java.io.FileOutputStream

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

/**
 * Tela "MISSÕES PRA DESLIGAR": a fila de desafios (data-driven a partir de
 * [ChallengeMode.entries]), com adicionar, remover, subir/descer e configurar
 * cada modo. Compartilha o mesmo [EditorViewModel] do editor principal.
 */
@Composable
fun MissionEditorScreen(
    vm: EditorViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val alarm by vm.alarm.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }
    var previewMode by remember { mutableStateOf<ChallengeMode?>(null) }

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

    val queue = if (alarm.challengeModes.isBlank()) emptyList<ChallengeMode>()
    else ChallengeMode.queueFrom(alarm.challengeModes, alarm.challengeMode)

    fun setQueue(updated: List<ChallengeMode>) {
        vm.update { a ->
            a.copy(
                challengeMode = updated.firstOrNull()?.key ?: a.challengeMode,
                challengeModes = ChallengeMode.queueToString(updated)
            )
        }
    }

    fun moveQueue(i: Int, dir: Int) {
        val j = i + dir
        if (j < 0 || j >= queue.size) return
        val list = queue.toMutableList()
        val tmp = list[i]
        list[i] = list[j]
        list[j] = tmp
        setQueue(list)
    }

    BackHandler { onBack() }

    previewMode?.let { mode ->
        ChallengePreview(mode, alarm) { previewMode = null }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp, end = PakaRaiSpacing.lg, bottom = 4.dp)
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
                    text = "MISSÕES PRA DESLIGAR",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1
                )
                Text(
                    text = "A sequência que acorda você",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PakaRaiSpacing.lg)
        ) {
            SectionShell(
                Icons.Filled.Bolt,
                "FILA DE DESAFIOS",
                "Prático demais desbloqueia até dormindo. Monta a sequência e usa."
            ) {
                ToggleRow("Exigir desafio na tela bloqueada", alarm.mathEnabled) {
                    enabled -> vm.update { a -> a.copy(mathEnabled = enabled) }
                }

                if (!alarm.mathEnabled) return@SectionShell

                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Lista de desafios (na ordem)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Adiciona um por um e reordena com as setas. Repetir o mesmo desafio = ele toca de novo na hora do alarme.",
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
                            RoundRow(
                                index = i,
                                mode = m,
                                onUp = if (i > 0) ({ moveQueue(i, -1) }) else null,
                                onDown = if (i < queue.size - 1) ({ moveQueue(i, 1) }) else null,
                                onPreview = { previewMode = m },
                                onRemove = { setQueue(queue.filterIndexed { idx, _ -> idx != i }) }
                            )
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
                                        modifier = Modifier.fillMaxWidth(),
                                        onPreview = {
                                            previewMode = m
                                            showPicker = false
                                        }
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

                        ChallengeMode.TILES -> {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Dificuldade da memória (quantos tiles acendem)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                (3..7).forEach { n ->
                                    ChoiceChip(
                                        "$n",
                                        alarm.memoryDifficulty == n,
                                        Modifier.weight(1f)
                                    ) { vm.update { it.copy(memoryDifficulty = n) } }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "3 = mais fácil · 7 = mais difícil",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Tempo pra memorizar",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextChip("Rápido 3s", alarm.memorySpeedMs == 3000, Modifier.weight(1f)) {
                                    vm.update { it.copy(memorySpeedMs = 3000) }
                                }
                                TextChip("Normal 5s", alarm.memorySpeedMs == 5000, Modifier.weight(1f)) {
                                    vm.update { it.copy(memorySpeedMs = 5000) }
                                }
                                TextChip("Devagar 8s", alarm.memorySpeedMs == 8000, Modifier.weight(1f)) {
                                    vm.update { it.copy(memorySpeedMs = 8000) }
                                }
                            }
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
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
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
            Spacer(Modifier.height(PakaRaiSpacing.xl))
        }
    }
}