package com.pakarai.alarme.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.core.AlarmStateManager
import com.pakarai.alarme.core.Constants
import com.pakarai.alarme.core.RING_WINDOW_MS
import com.pakarai.alarme.ui.challenge.ChallengeActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground Service de mídia que toca o alarme de verdade.
 * Não é morto por swipe de recents enquanto está foreground; a notificação é
 * permanente; o fullScreenIntent joga a tela do desafio por cima de tudo.
 */
class AlarmService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var sound: SoundSink? = null
    private var ramp: RampController? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var vibratorJob: Job? = null
    private var watchdog: Job? = null
    private var cleaning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifications.createChannels(this)
        val alarmId = intent?.getLongExtra(Constants.EXTRA_ALARM_ID, -1L) ?: -1L
        val snoozeReturn = intent?.getBooleanExtra(EXTRA_SNOOZE_RETURN, false) ?: false

        if (alarmId < 0) {
            stopSelf()
            return START_NOT_STICKY
        }

        val stateManager = AppScope.stateManager
        if (stateManager.state.value !is AlarmStateManager.State.Ringing) {
            stateManager.setRinging(alarmId, RING_WINDOW_MS)
        }

        startInForeground(alarmId)
        loadAndStart(alarmId, snoozeReturn)

        // desafio resolvido (estado vira Idle) → encerra tudo
        scope.launch {
            stateManager.state.collectLatest { state ->
                if (state is AlarmStateManager.State.Idle) cleanup()
            }
        }

        // rede de segurança: para sozinho após o teto de duração
        watchdog = scope.launch {
            delay(RING_WINDOW_MS + 30_000)
            cleanup()
        }

        return START_NOT_STICKY
    }

    private fun startInForeground(alarmId: Long) {
        val notif = Notifications.ringing(this, alarmId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Constants.SERVICE_ID_RINGING,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(Constants.SERVICE_ID_RINGING, notif)
        }
    }

    private fun loadAndStart(alarmId: Long, snoozeReturn: Boolean) {
        scope.launch {
            val alarm = AppScope.repository.getById(alarmId) ?: run {
                stopSelf()
                return@launch
            }

            // abre o desafio por cima de tudo; notificação full-screen é o plano B
            try {
                val i = Intent(this@AlarmService, ChallengeActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(Constants.EXTRA_ALARM_ID, alarm.id)
                }
                startActivity(i)
            } catch (_: Exception) {
            }

            acquireWakeLock()

            val sink = createSoundSink(this@AlarmService, alarm)
            ramp = RampController(
                this@AlarmService,
                if (snoozeReturn) alarm.volumeInitial else alarm.volumeInitial,
                alarm.volumePeak,
                alarm.rampMs,
                alarm.rampCurve,
                alarm.policeVolume
            )
            ramp?.start()
            sink.play()
            sound = sink

            if (alarm.vibrate) startVibration()
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pakarai:alarm").apply {
            setReferenceCounted(false)
            acquire(RING_WINDOW_MS + 60_000L)
        }
    }

    private fun startVibration() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        vibratorJob = scope.launch {
            try {
                val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 600, 350, 600),
                        intArrayOf(0, 255, 0, 255),
                        -1
                    )
                } else {
                    VibrationEffect.createWaveform(longArrayOf(0, 600, 350, 600), -1)
                }
                while (true) {
                    vibrator.vibrate(effect)
                    delay(2000)
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun cleanup() {
        if (cleaning) return
        cleaning = true
        sound?.stop()
        sound?.release()
        sound = null
        vibratorJob?.cancel()
        vibratorJob = null
        watchdog?.cancel()
        ramp?.stop()
        ramp = null
        try {
            wakeLock?.release()
        } catch (_: Exception) {
        }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        scope.cancel()
    }

    override fun onDestroy() {
        if (!cleaning) {
            sound?.stop()
            sound?.release()
            vibratorJob?.cancel()
            watchdog?.cancel()
            ramp?.stop()
            try {
                wakeLock?.release()
            } catch (_: Exception) {
            }
        }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_SNOOZE_RETURN = "extra_snooze_return"

        fun start(context: Context, alarmId: Long, resume: Boolean = false, snoozeReturn: Boolean = false) {
            val intent = Intent(context, AlarmService::class.java).apply {
                putExtra(Constants.EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_SNOOZE_RETURN, snoozeReturn)
                putExtra("extra_resume", resume)
            }
            context.startForegroundService(intent)
        }
    }
}