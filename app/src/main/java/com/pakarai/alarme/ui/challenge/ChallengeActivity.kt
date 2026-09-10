package com.pakarai.alarme.ui.challenge

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.core.Constants
import com.pakarai.alarme.data.AlarmEntity
import com.pakarai.alarme.ui.theme.AlarmePakaraiTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Tela de bloqueio do alarme. Roda POR CIMA da lockscreen
 * (manifest: showWhenLocked + turnScreenOn), respondível SEM desbloquear.
 * O desafio certo desliga. Screen pinning é verificado de verdade (isInLockTaskMode)
 * e avisa quando o sistema não deixou travar.
 */
class ChallengeActivity : ComponentActivity() {

    private var alarmId = -1L
    private var pinned = false

    /** true = o sistema recusou a trava de tela (ex.: Fixação de tela desligada). */
    private val _pinWarning = MutableStateFlow(false)
    val pinWarning: StateFlow<Boolean> = _pinWarning

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
            val accent by AppScope.settings.accentId.collectAsStateWithLifecycle()
            AlarmePakaraiTheme(accentId = accent) {
                val pin by pinWarning.collectAsStateWithLifecycle()
                ChallengeScreen(
                    alarmId = alarmId,
                    pinWarning = pin,
                    onRequestPin = { tryPin() }
                )
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
            tryPin()
        }
        if (isInLockTaskMode()) pinned = true
    }

    private fun tryPin() {
        if (isInLockTaskMode()) {
            _pinWarning.value = false
            pinned = true
            return
        }
        try {
            startLockTask()
            pinned = isInLockTaskMode()
            _pinWarning.value = !pinned
        } catch (_: Exception) {
            _pinWarning.value = true
        }
    }

    private fun isInLockTaskMode(): Boolean {
        return try {
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        } catch (_: Exception) {
            false
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