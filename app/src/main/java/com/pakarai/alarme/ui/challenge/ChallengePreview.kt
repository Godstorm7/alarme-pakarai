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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pakarai.alarme.data.AlarmEntity

/**
 * Prévia de um desafio dentro do editor. Roda UMA rodada real do modo escolhido
 * (mesmos composables de [ChallengeRounds]) sem tocar no estado do alarme.
 *
 * QR e OBJETO não fazem sentido "jogar" na prévia (precisam de QR impresso /
 * objeto cadastrado), então mostram uma tela explicando como funcionam.
 */
@Composable
fun ChallengePreview(
    mode: ChallengeMode,
    alarm: AlarmEntity,
    onClose: () -> Unit,
) {
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

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    when (mode) {
                        ChallengeMode.MATH -> MathRound(alarm.mathDifficulty, onInteract = {}) { onClose() }
                        ChallengeMode.MEMORY -> MemoryRound(1, onInteract = {}) { onClose() }
                        ChallengeMode.TILES -> TilesRound(
                            alarm.memoryDifficulty,
                            alarm.memorySpeedMs,
                            onInteract = {}
                        ) { onClose() }
                        ChallengeMode.TYPE -> TypeRound(onInteract = {}) { onClose() }
                        ChallengeMode.SHAKE -> ShakeRound(alarm.shakeCount, onInteract = {}) { onClose() }
                        ChallengeMode.STEPS -> StepsRound(alarm.stepCount, onInteract = {}) { onClose() }
                        ChallengeMode.SPIN -> SpinRound(alarm.spinCount, onInteract = {}) { onClose() }
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
