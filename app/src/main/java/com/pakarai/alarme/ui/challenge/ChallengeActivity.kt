package com.pakarai.alarme.ui.challenge

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.core.Constants
import com.pakarai.alarme.data.AlarmEntity
import com.pakarai.alarme.ui.theme.AlarmePakaraiTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Tela de bloqueio do alarme. Roda POR CIMA da lockscreen
 * (manifest: showWhenLocked + turnScreenOn), respondível SEM desbloquear.
 * Resolveu a matemática → alarme para. Sem mais enrolação.
 */
class ChallengeActivity : ComponentActivity() {

    private var alarmId = -1L
    private var pinned = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        alarmId = intent.getLongExtra(Constants.EXTRA_ALARM_ID, -1L)

        setContent {
            AlarmePakaraiTheme {
                ChallengeScreen(alarmId = alarmId)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val state = AppScope.stateManager.state.value
        if (state !is com.pakarai.alarme.core.AlarmStateManager.State.Ringing) {
            finish()
            return
        }
        val alarm = runBlockingReadAlarm(alarmId)
        if (alarm?.screenPin == true) {
            try {
                startLockTask()
                pinned = true
            } catch (_: Exception) {
            }
        }
    }

    private fun runBlockingReadAlarm(id: Long): AlarmEntity? {
        return try {
            kotlinx.coroutines.runBlocking { AppScope.repository.getById(id) }
        } catch (_: Exception) {
            null
        }
    }

    private fun releasePin() {
        if (pinned) {
            try {
                stopLockTask()
            } catch (_: Exception) {
            }
            pinned = false
        }
    }

    override fun onStop() {
        releasePin()
        super.onStop()
    }

    override fun onDestroy() {
        releasePin()
        super.onDestroy()
    }

    companion object {
        /** Resolve o desafio com sucesso: para tudo e agenda o próximo ciclo. */
        fun resolve(context: Activity, alarm: AlarmEntity) {
            CoroutineScope(Dispatchers.IO).launch {
                AppScope.scheduler.cancel(alarm.id)
                if (alarm.isRepeating()) {
                    AppScope.repository.getById(alarm.id)?.let {
                        AppScope.scheduler.schedule(it)
                    }
                } else {
                    AppScope.repository.setEnabled(alarm.id, false)
                }
            }
            AppScope.stateManager.clear()
        }

        /** Aplica soneca: silencia e agenda o retorno. */
        fun snooze(context: Activity, alarm: AlarmEntity) {
            val state = AppScope.stateManager
            val used = state.getSnoozeUsed()
            val remaining = alarm.snoozeLimit - (used + 1)
            state.setSnoozeUsed(used + 1)
            val untilMs = System.currentTimeMillis() + alarm.snoozeMinutes * 60_000L
            state.setSnoozing(alarm.id, remaining.coerceAtLeast(0), untilMs)
            AppScope.scheduler.scheduleSnooze(alarm.id, alarm.snoozeMinutes)
        }
    }
}

@Composable
fun ChallengeScreen(alarmId: Long) {
    val activity = LocalContext.current as? Activity
    var alarm by remember { mutableStateOf<AlarmEntity?>(null) }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf(0) }
    var input by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var snoozeCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(alarmId) {
        delay(200)
        val loaded = AppScope.repository.getById(alarmId)
        if (loaded == null) return@LaunchedEffect
        alarm = loaded
        snoozeCount = AppScope.stateManager.getSnoozeUsed()
        val q = generateQuestion(loaded.mathDifficulty)
        question = q.first
        answer = q.second
    }

    val current = alarm
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        if (current != null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "ALARME ATIVO",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = current.label.uppercase().ifEmpty { "DESPERTAR" },
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(36.dp))

                if (current.mathEnabled) {
                    Text(
                        text = "Pra desligar, resolva:",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "$question = ?",
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.height(24.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it.filter { c -> c.isDigit() || c == '-' } },
                        label = { Text("Resposta") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    if (wrong) {
                        Text(
                            text = "NÃO. É OUTRA. ACORDA.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    BigActionButton(
                        text = "RESOLVER",
                        onClick = {
                            if (input.toIntOrNull() == answer) {
                                activity?.let { ChallengeActivity.resolve(it, current) }
                            } else {
                                wrong = true
                                val q = generateQuestion(current.mathDifficulty)
                                question = q.first
                                answer = q.second
                                input = ""
                            }
                        }
                    )
                } else {
                    Spacer(Modifier.height(16.dp))
                    BigActionButton(
                        text = "DESLIGAR",
                        onClick = { activity?.let { ChallengeActivity.resolve(it, current) } }
                    )
                }

                if (current.snoozeLimit > snoozeCount) {
                    Spacer(Modifier.height(18.dp))
                    TextButton(onClick = {
                        ChallengeActivity.snooze(activity!!, current)
                        activity?.finish()
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
            }
        } else {
            Text("Carregando...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BigActionButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black
        )
    }
}

/** Gera questão conforme dificuldade. Retorna (texto, resposta). */
private fun generateQuestion(difficulty: Int): Pair<String, Int> {
    val rnd = Random.Default
    return when (difficulty) {
        0 -> {
            val a = rnd.nextInt(5, 25)
            val b = rnd.nextInt(1, 15)
            if (rnd.nextBoolean()) "$a + $b" to a + b else "$a - $b" to a - b
        }
        1 -> {
            val a = rnd.nextInt(12, 95)
            val b = rnd.nextInt(2, 9)
            "$a × $b" to a * b
        }
        else -> {
            val a = rnd.nextInt(10, 60)
            val b = rnd.nextInt(4, 9)
            val c = rnd.nextInt(4, 9)
            "$a + $b × $c" to a + b * c
        }
    }
}