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
import com.pakarai.alarme.ui.check.CheckActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Ponte para a tela do desafio pausar/retomar o som do alarme ativo.
 * O serviço registra o handler a cada toque; a tela só chama se houver.
 */
object AlarmSoundControl {
    @Volatile
    var handler: ((paused: Boolean) -> Unit)? = null
}

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
    private var monitorJob: Job? = null
    private var checkMode = false
    private var cleaning = false

    private val pauseHandler: (Boolean) -> Unit = { paused ->
        scope.launch {
            if (paused) sound?.pause() else sound?.resume()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifications.createChannels(this)
        AlarmSoundControl.handler = pauseHandler
        val alarmId = intent?.getLongExtra(Constants.EXTRA_ALARM_ID, -1L) ?: -1L
        val snoozeReturn = intent?.getBooleanExtra(EXTRA_SNOOZE_RETURN, false) ?: false
        checkMode = intent?.getBooleanExtra(EXTRA_CHECK_MODE, false) ?: false

        // o mesmo serviço pode ser reutilizado instantaneamente (NÃO/timeout re-toca
        // logo depois do check): cancela o monitor anterior e reabre o scope p/ a nova fase.
        monitorJob?.cancel()
        monitorJob = null
        cleaning = false

        if (alarmId < 0) {
            stopSelf()
            return START_NOT_STICKY
        }

        val stateManager = AppScope.stateManager
        if (checkMode) {
            // "AINDA ACORDADO?": não toca som, só hospeda o CheckActivity
            if (stateManager.state.value !is AlarmStateManager.State.Checking) {
                stopSelf()
                return START_NOT_STICKY
            }
            startCheckInForeground(alarmId)
            openCheckActivity(alarmId)
            // encerra quando o check deixar de existir (SIM=Idle, NÃO=toque de novo)
            monitorJob = scope.launch {
                stateManager.state.collectLatest { state ->
                    if (state !is AlarmStateManager.State.Checking) cleanup()
                }
            }
            return START_NOT_STICKY
        }

        if (stateManager.state.value !is AlarmStateManager.State.Ringing) {
            // preserva as sonecas já gastas quando a volta vem da soneca (ou de
            // uma recuperação pós-reboot); só começa do zero num ciclo totalmente novo
            val used = usedSnoozesFrom(alarmId)
            stateManager.setRinging(alarmId, RING_WINDOW_MS, used)
        }

        startInForeground(alarmId)
        loadAndStart(alarmId, snoozeReturn)

        // saiu do Ringing (resolveu/soneca/AINDA ACORDADO?) → encerra o toque
        monitorJob = scope.launch {
            stateManager.state.collectLatest { state ->
                if (state !is AlarmStateManager.State.Ringing) cleanup()
            }
        }

        // rede de segurança periódica: a cada ~9min confere se o ciclo ainda é
        // válido; estourou o teto → zera o estado e para sozinho. Se o ciclo já
        // saiu de Ringing, o monitorJob encerra — aqui só interceptamos o caso
        // de estado "preso" que a próxima abertura do app re-tocaria pra sempre.
        watchdog = scope.launch {
            while (true) {
                delay(WATCHDOG_PERIOD_MS)
                if (cleaning) return@launch
                val s = AppScope.stateManager.state.value
                if (s !is AlarmStateManager.State.Ringing) return@launch
                if (System.currentTimeMillis() > s.expiresAtMs) {
                    AppScope.stateManager.clear()
                    cleanup()
                    return@launch
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun usedSnoozesFrom(alarmId: Long): Int = when (val s = AppScope.stateManager.state.value) {
        is AlarmStateManager.State.Snoozing -> if (s.alarmId == alarmId) s.usedSnoozes else 0
        else -> 0
    }

    private fun openCheckActivity(alarmId: Long) {
        try {
            val i = Intent(this, CheckActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(Constants.EXTRA_ALARM_ID, alarmId)
            }
            startActivity(i)
        } catch (_: Exception) {
        }
    }

    private fun startCheckInForeground(alarmId: Long) {
        startForegroundSafe(Notifications.checking(this, alarmId))
    }

    private fun startInForeground(alarmId: Long) {
        startForegroundSafe(Notifications.ringing(this, alarmId))
    }

    /** startForeground nunca pode derrubar o toque: tenta com tipo de mídia e cai pra simples. */
    private fun startForegroundSafe(notif: android.app.Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    startForeground(
                        Constants.SERVICE_ID_RINGING,
                        notif,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                    return
                } catch (_: RuntimeException) {
                }
            }
            startForeground(Constants.SERVICE_ID_RINGING, notif)
        } catch (_: Exception) {
            // FGS bloqueada pelo sistema: o serviço pode ser encerrado, mas a tela
            // do desafio já foi aberta e o GuardService reabre se o usuário fugir.
        }
    }

    private fun loadAndStart(alarmId: Long, snoozeReturn: Boolean) {
        scope.launch {
            val alarm = AppScope.repository.getById(alarmId) ?: run {
                stopSelf()
                return@launch
            }

            // segura a CPU ANTES de abrir a tela: a activity pode demorar a
            // montar o composable, e sem o wake lock o aparelho entra no Doze
            // enquanto o desafio nem apareceu
            acquireWakeLock()

            // abre o desafio por cima de tudo; notificação full-screen é o plano B
            try {
                val i = Intent(this@AlarmService, ChallengeActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(Constants.EXTRA_ALARM_ID, alarm.id)
                }
                startActivity(i)
            } catch (_: Exception) {
            }

            val sink = createSoundSink(this@AlarmService, alarm)
            // soneca-return (ou recuperação de soneca) volta direto no volume teto:
            // quem fugiu pra soneca não merece ramp-up suave
            val fromSnooze = snoozeReturn || AppScope.stateManager.usedSnoozesFor(alarm.id) > 0
            // extra loud: força o teto em 100% e policia (não deixa abaixar)
            val peak = if (alarm.extraLoud) 1f else alarm.volumePeak
            ramp = RampController(
                this@AlarmService,
                if (fromSnooze) peak else alarm.volumeInitial,
                peak,
                alarm.rampMs,
                alarm.rampCurve,
                alarm.policeVolume || alarm.extraLoud
            )
            ramp?.start()
            sink.play()
            sound = sink
            // prevent off: o GuardService usa isso pra barrar o diálogo de desligar
            com.pakarai.alarme.core.RingGuard.preventOff = alarm.preventOff

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
        com.pakarai.alarme.core.RingGuard.preventOff = false
        if (AlarmSoundControl.handler == pauseHandler) AlarmSoundControl.handler = null
        sound?.stop()
        sound?.release()
        sound = null
        vibratorJob?.cancel()
        vibratorJob = null
        watchdog?.cancel()
        watchdog = null
        monitorJob?.cancel()
        monitorJob = null
        ramp?.stop()
        ramp = null
        try {
            wakeLock?.release()
        } catch (_: Exception) {
        }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Se o app for deslizado do Recents durante o toque, a tela do desafio
        // sai mesmo com o serviço foreground vivo. Relança na hora pra não
        // ficar som tocando sem tela pra desligar.
        val state = AppScope.stateManager.state.value
        if (state is AlarmStateManager.State.Ringing) {
            val i = Intent(this, ChallengeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(Constants.EXTRA_ALARM_ID, state.alarmId)
            }
            try {
                startActivity(i)
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        private const val EXTRA_SNOOZE_RETURN = "extra_snooze_return"
        private const val EXTRA_CHECK_MODE = "extra_check_mode"
        /** Intervalo do watchdog de segurança (bem abaixo do teto de 30min). */
        private const val WATCHDOG_PERIOD_MS = 9 * 60 * 1000L

        fun start(context: Context, alarmId: Long, snoozeReturn: Boolean = false) {
            val intent = Intent(context, AlarmService::class.java).apply {
                putExtra(Constants.EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_SNOOZE_RETURN, snoozeReturn)
            }
            try {
                context.startForegroundService(intent)
            } catch (_: Exception) {
            }
        }

        /** Abre o CheckActivity sem tocar som (hospeda o "AINDA ACORDADO?"). */
        fun startCheck(context: Context, alarmId: Long) {
            val intent = Intent(context, AlarmService::class.java).apply {
                putExtra(Constants.EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_CHECK_MODE, true)
            }
            try {
                context.startForegroundService(intent)
            } catch (_: Exception) {
            }
        }
    }
}