package com.pakarai.alarme.ui.challenge

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.data.AlarmEntity
import com.pakarai.alarme.service.AlarmSoundControl
import com.pakarai.alarme.ui.wizard.isPinningAllowed
import kotlinx.coroutines.delay

/**
 * Tela de bloqueio do alarme com os desafios.
 * challengeModes é uma LISTA de rodadas: cada item é um desafio que resolve na
 * ordem. Repetir o mesmo = repetir o item ("math|math|memory" = 2 math + 1 memory).
 * As rodadas em si ficam em [ChallengeRounds].
 */
@Composable
fun ChallengeScreen(
    alarmId: Long,
) {
    val activity = LocalContext.current as? Activity
    var alarm by remember { mutableStateOf<AlarmEntity?>(null) }
    var loading by remember { mutableStateOf(true) }
    var step by remember { mutableIntStateOf(1) }
    var snoozeCount by remember { mutableIntStateOf(0) }
    var soundPaused by remember { mutableStateOf(false) }
    var pauseLeft by remember { mutableIntStateOf(30) }
    var runId by remember { mutableIntStateOf(0) }

    val current = alarm

    fun onInteract() {
        if (soundPaused) pauseLeft = 30
    }

    fun toggleSoundPause() {
        val next = !soundPaused
        soundPaused = next
        pauseLeft = 30
        AlarmSoundControl.handler?.invoke(next)
    }

    fun turnOff() {
        val a = current ?: return
        activity?.let { ChallengeActivity.resolve(it, a) }
    }

    LaunchedEffect(alarmId) {
        delay(200)
        val loaded = AppScope.repository.getById(alarmId)
        alarm = loaded
        snoozeCount = AppScope.stateManager.currentUsedSnoozes()
        loading = false
    }

    // screen pinning: trava a tela do desafio (sai do app = a tela fica), se o usuário habilitou
    LaunchedEffect(alarm) {
        val a = alarm ?: return@LaunchedEffect
        val act = activity ?: return@LaunchedEffect
        if (a.screenPin && isPinningAllowed(act.applicationContext)) {
            try {
                act.startLockTask()
            } catch (_: Exception) {
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // stopLockTask fora de modo pinado é um no-op; sem checagem extra
            try {
                activity?.stopLockTask()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(soundPaused) {
        while (soundPaused) {
            delay(1_000)
            pauseLeft -= 1
            if (pauseLeft <= 0) {
                soundPaused = false
                pauseLeft = 30
                AlarmSoundControl.handler?.invoke(false)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        when {
            current == null -> Text(
                text = if (loading) "Carregando..." else "Alarme não encontrado",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            !current.mathEnabled -> Column(
                modifier = Modifier.fillMaxSize().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                ChallengeHeader(
                        current.label,
                        step = 1,
                        steps = 1,
                        queueSize = 1,
                        modeLabel = "",
                        round = 1,
                        rounds = 1
                    )
                Spacer(Modifier.height(16.dp))
                BigActionButton(
                    text = "DESLIGAR",
                    onClick = { turnOff() }
                )
            }

            else -> {
                val queue = ChallengeMode.queueFrom(current.challengeModes, current.challengeMode)
                val totalSteps = queue.size

                fun nextStep() {
                    if (step < totalSteps) {
                        step += 1
                    } else {
                        turnOff()
                    }
                }

                val mode = queue[(step - 1).coerceIn(0, totalSteps - 1)]

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ChallengeHeader(
                        current.label,
                        step = step,
                        steps = totalSteps,
                        queueSize = queue.size,
                        modeLabel = mode.label,
                        round = step,
                        rounds = totalSteps
                    )
                    Spacer(Modifier.height(18.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = mode.hint,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                    Spacer(Modifier.height(20.dp))

                    key(runId, step) {
                        when (mode) {
                            ChallengeMode.MATH -> MathRound(
                                current.mathDifficulty,
                                onInteract = ::onInteract
                            ) { nextStep() }
                            ChallengeMode.TYPE -> TypeRound(onInteract = ::onInteract) { nextStep() }
                            ChallengeMode.MEMORY -> MemoryRound(
                                step,
                                onInteract = ::onInteract
                            ) { nextStep() }
                            ChallengeMode.TILES -> TilesRound(
                                current.memoryDifficulty,
                                current.memorySpeedMs,
                                onInteract = ::onInteract
                            ) { nextStep() }
                            ChallengeMode.OBJECT -> ObjectRound(
                            refPath = current.objectRefPath,
                            refLabel = current.objectRefLabel,
                            onInteract = ::onInteract
                        ) { nextStep() }
                            ChallengeMode.SHAKE -> ShakeRound(
                                current.shakeCount,
                                onInteract = ::onInteract
                            ) { nextStep() }
                            ChallengeMode.STEPS -> StepsRound(
                                current.stepCount,
                                onInteract = ::onInteract
                            ) { nextStep() }
                            ChallengeMode.SPIN -> SpinRound(
                                current.spinCount,
                                onInteract = ::onInteract
                            ) { nextStep() }
                            ChallengeMode.QR -> QrRound(
                                current.challengeQrSecret,
                                onInteract = ::onInteract
                            ) { nextStep() }
                        }
                    }

                    if (current.snoozeLimit > snoozeCount) {
                        Spacer(Modifier.height(18.dp))
                        TextButton(onClick = {
                            activity?.let {
                                ChallengeActivity.snooze(it, current)
                                it.finish()
                            }
                        }) {
                            Text(
                                if (snoozeCount == current.snoozeLimit - 1)
                                    "SONECA (ÚLTIMA!)"
                                else
                                    "SONECA (${current.snoozeMinutes}min)",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (soundPaused) {
                        Spacer(Modifier.height(18.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "SOM PAUSADO",
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Black
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Volta sozinho em ${pauseLeft}s se você não avançar o desafio.",
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(8.dp))
                                ProgressBar(fraction = pauseLeft / 30f)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
TextButton(onClick = { toggleSoundPause() }) {
                        Icon(
                            imageVector = if (soundPaused) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (soundPaused) "VOLTAR SOM" else "PAUSAR SOM",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(28.dp))
                }
            }
        }
    }
}

@Composable
private fun ChallengeHeader(
    label: String,
    step: Int,
    steps: Int,
    queueSize: Int,
    modeLabel: String,
    round: Int,
    rounds: Int,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "ALARME ATIVO",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label.uppercase().ifEmpty { "DESPERTAR" },
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        if (queueSize > 1 || rounds > 1) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    if (queueSize > 1) append("DESAFIO ${(step - 1) % queueSize + 1} de $queueSize")
                    if (queueSize > 1 && rounds > 1) append(" · ")
                    if (rounds > 1) append("RODADA $round de $rounds")
                },
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
        if (queueSize > 1) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "→ ${modeLabel.lowercase().replaceFirstChar { it.uppercase() }}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}