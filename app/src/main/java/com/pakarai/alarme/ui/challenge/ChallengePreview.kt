package com.pakarai.alarme.ui.challenge

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pakarai.alarme.data.AlarmEntity
import com.pakarai.alarme.ui.editor.ChoiceChip
import com.pakarai.alarme.ui.editor.TextChip

/**
 * Prévia de um desafio dentro do editor. Roda UMA rodada real do modo escolhido
 * (mesmos composables de [ChallengeRounds]) sem tocar no estado do alarme.
 *
 * A dificuldade começa no valor do alarme e pode ser ajustada aqui pra testar.
 * QR e OBJETO não fazem sentido "jogar" na prévia (precisam de QR impresso /
 * objeto cadastrado), então mostram uma tela explicando como funcionam.
 */
@Composable
fun ChallengePreview(
    mode: ChallengeMode,
    alarm: AlarmEntity,
    onClose: () -> Unit,
) {
    var mathDiff by remember { mutableIntStateOf(alarm.mathDifficulty) }
    var tilesDiff by remember { mutableIntStateOf(alarm.memoryDifficulty) }
    var tilesSpeed by remember { mutableIntStateOf(alarm.memorySpeedMs) }
    var count by remember(mode) {
        mutableIntStateOf(
            when (mode) {
                ChallengeMode.SHAKE -> alarm.shakeCount
                ChallengeMode.STEPS -> alarm.stepCount
                ChallengeMode.SPIN -> alarm.spinCount
                else -> 0
            }
        )
    }

    BackHandler { onClose() }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, end = 12.dp, bottom = 4.dp)
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Fechar prévia",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PRÉVIA · ${mode.label}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1
                        )
                        Text(
                            text = "Toque no X pra sair",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PreviewControls(
                        mode = mode,
                        mathDiff = mathDiff, onMath = { mathDiff = it },
                        tilesDiff = tilesDiff, onTilesDiff = { tilesDiff = it },
                        tilesSpeed = tilesSpeed, onTilesSpeed = { tilesSpeed = it },
                        count = count, onCount = { count = it },
                    )

                    when (mode) {
                        ChallengeMode.MATH -> MathRound(mathDiff, onInteract = {}) { onClose() }
                        ChallengeMode.MEMORY -> MemoryRound(1, onInteract = {}) { onClose() }
                        ChallengeMode.TILES -> TilesRound(tilesDiff, tilesSpeed, onInteract = {}) { onClose() }
                        ChallengeMode.TYPE -> TypeRound(onInteract = {}) { onClose() }
                        ChallengeMode.SHAKE -> ShakeRound(count, onInteract = {}) { onClose() }
                        ChallengeMode.STEPS -> StepsRound(count, onInteract = {}) { onClose() }
                        ChallengeMode.SPIN -> SpinRound(count, onInteract = {}) { onClose() }
                        ChallengeMode.QR -> PreviewExplanation(
                            mode,
                            listOf(
                                "No editor, defina o segredo e gere o QR (botão GERAR).",
                                "Imprima ou compartilhe o QR e deixe longe da cama.",
                                "Na hora do alarme, escaneie o mesmo QR pra desligar."
                            )
                        )
                        ChallengeMode.OBJECT -> PreviewExplanation(
                            mode,
                            listOf(
                                "Toque em \"Cadastrar objeto\" e fotografe algo fácil (ex.: a escova).",
                                "Na hora do alarme, fotografe o mesmo objeto pra desligar.",
                                "O reconhecimento roda no aparelho (offline), sem internet."
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewControls(
    mode: ChallengeMode,
    mathDiff: Int, onMath: (Int) -> Unit,
    tilesDiff: Int, onTilesDiff: (Int) -> Unit,
    tilesSpeed: Int, onTilesSpeed: (Int) -> Unit,
    count: Int, onCount: (Int) -> Unit,
) {
    when (mode) {
        ChallengeMode.MATH -> {
            PreviewLabel("Dificuldade (toque pra testar)")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("Fácil" to 0, "Médio" to 1, "Difícil" to 2).forEach { (label, v) ->
                    ChoiceChip(label, mathDiff == v, Modifier.weight(1f)) { onMath(v) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        ChallengeMode.TILES -> {
            PreviewLabel("Quantos tiles acendem")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                (3..7).forEach { v ->
                    ChoiceChip("$v", tilesDiff == v, Modifier.weight(1f)) { onTilesDiff(v) }
                }
            }
            Spacer(Modifier.height(10.dp))
            PreviewLabel("Tempo pra memorizar")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("Rápido 2s" to 2000, "Normal 3s" to 3000, "Devagar 5s" to 5000).forEach { (label, v) ->
                    TextChip(label, tilesSpeed == v, Modifier.weight(1f)) { onTilesSpeed(v) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        ChallengeMode.SHAKE -> {
            PreviewLabel("Quantas agitadas")
            MovementChips(listOf(5, 10, 15, 20), count, onCount)
            Spacer(Modifier.height(16.dp))
        }

        ChallengeMode.STEPS -> {
            PreviewLabel("Passos a andar")
            MovementChips(listOf(10, 20, 30, 50), count, onCount)
            Spacer(Modifier.height(16.dp))
        }

        ChallengeMode.SPIN -> {
            PreviewLabel("Girar até quantos graus")
            MovementChips(listOf(45, 90, 180, 360), count, onCount, suffix = "°")
            Spacer(Modifier.height(16.dp))
        }

        else -> Unit
    }
}

@Composable
private fun PreviewLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
    )
}

@Composable
private fun MovementChips(
    options: List<Int>,
    current: Int,
    onPick: (Int) -> Unit,
    suffix: String = "",
) {
    val opts = if (current in options) options else (options + current).sorted()
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        opts.forEach { v ->
            TextChip("$v$suffix", current == v, Modifier.weight(1f)) { onPick(v) }
        }
    }
}

@Composable
private fun PreviewExplanation(mode: ChallengeMode, steps: List<String>) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = mode.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )
        Text(
            text = mode.label,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Text(
            text = mode.hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        steps.forEach { step ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "•",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.width(16.dp)
                )
                Text(
                    text = step,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
