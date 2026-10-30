package com.pakarai.alarme.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.core.AlarmStateManager
import com.pakarai.alarme.core.Constants
import com.pakarai.alarme.core.DndBypass
import com.pakarai.alarme.core.ForegroundApp
import com.pakarai.alarme.core.GuardPolicy
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
    private var guardJob: Job? = null
    private var lastRelaunchAt = 0L
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
            val checking = stateManager.state.value as? AlarmStateManager.State.Checking
            if (checking == null) {
                stopSelf()
                return START_NOT_STICKY
            }
            // Se algum som ainda estava tocando (start repetido/atrasado), corta
            // agora: o check só existe DEPOIS de o alarme ser resolvido, então
            // deixar tocando aqui viraria som fantasma sem ninguém pra pará-lo.
            if (sound != null) {
                sound?.stop()
                sound?.release()
                sound = null
                ramp?.stop()
                ramp = null
                vibratorJob?.cancel()
                vibratorJob = null
            }
            acquireWakeLock()
            startCheckInForeground(alarmId)
            // o indicador fixo sai de cena: agora quem manda é a notificação do popup
            WakeCheckNotifier.cancel(this)
            openCheckActivity(alarmId, (Constants.CHECK_WINDOW_MS / 1000).toInt())
            startGuard(alarmId)
            // com a janela do alarme em mãos, reabre o popup já com o tempo certo
            scope.launch {
                val alarm = AppScope.repository.getById(alarmId)
                if (alarm != null) {
                    startCheckInForeground(alarmId, alarm.ackWindowSec)
                    openCheckActivity(alarmId, alarm.ackWindowSec)
                }
            }
            // encerra quando ESTE check deixar de existir: SIM na última = Idle,
            // NÃO = toque de novo, ou o SIM re-armou a próxima (índice muda) — nesse
            // caso o serviço sai de cena e quem fica na barra é o indicador do check.
            val checkIndexAtStart = checking.checkIndex
            monitorJob = scope.launch {
                stateManager.state.collectLatest { state ->
                    val sameCheck = state is AlarmStateManager.State.Checking &&
                        state.alarmId == alarmId &&
                        state.checkIndex == checkIndexAtStart
                    if (!sameCheck) cleanup()
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
        startGuard(alarmId)
        loadAndStart(alarmId, snoozeReturn)

        // DND: se a pessoa está com o "Não perturbe" ligado, o alarme é a
        // exceção — mas só durante o toque, e a devolução é garantida no
        // cleanup. Sem permissão concedida, isDndActive() nem é chamado.
        if (AppScope.settings.dndBypass) DndBypass.enter(this)

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

        // Redelivery do SO: se o processo morrer com o toque em andamento, o
        // intent é reentregue e o alarme volta. Só pedimos isso enquanto o
        // ciclo ainda é Ringing — assim que o desafio é resolvido, o cleanup
        // chama stopSelf() e a reentrega morre junto.
        val stillRinging = AppScope.stateManager.isRingingFor(alarmId)
        return if (stillRinging) START_REDELIVER_INTENT else START_NOT_STICKY
    }

    private fun usedSnoozesFrom(alarmId: Long): Int = when (val s = AppScope.stateManager.state.value) {
        is AlarmStateManager.State.Snoozing -> if (s.alarmId == alarmId) s.usedSnoozes else 0
        else -> 0
    }

    private fun openCheckActivity(alarmId: Long, windowSec: Int) {
        try {
            val i = Intent(this, CheckActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(Constants.EXTRA_ALARM_ID, alarmId)
                putExtra(Constants.EXTRA_CHECK_WINDOW_SEC, windowSec)
            }
            startActivity(i)
        } catch (_: Exception) {
        }
    }

    private fun startCheckInForeground(alarmId: Long, windowSec: Int = Constants.CHECK_WINDOW_MS.toInt() / 1000) {
        startForegroundSafe(Notifications.checking(this, alarmId, windowSec))
    }

    private fun startInForeground(alarmId: Long) {
        startForegroundSafe(Notifications.ringing(this, alarmId, null))
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

            // agora que sabemos se tem desafio, ajusta o texto da notificação
            startForegroundSafe(Notifications.ringing(this@AlarmService, alarm.id, alarm.mathEnabled))

            // abre o desafio por cima de tudo; notificação full-screen é o plano B
            try {
                val i = Intent(this@AlarmService, ChallengeActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(Constants.EXTRA_ALARM_ID, alarm.id)
                }
                startActivity(i)
            } catch (_: Exception) {
            }

            // JÁ TEM SOM TOCANDO neste ciclo? Então este é um start REPETIDO
            // (intent reentregue pela OneUI, tela bloqueada/desbloqueada, rajada).
            // Criar outro sink aqui duplicaria o áudio E deixaria o antigo órfão —
            // aí o som nunca mais parava ao resolver o desafio. Só retoma e sai.
            if (sound != null) {
                sound?.resume()
                if (alarm.vibrate && vibratorJob == null) startVibration()
                return@launch
            }

            val sink = createSoundSink(this@AlarmService, alarm)
            // Spotify só toca com device ativo: abre o app do Spotify na hora
            if (alarm.soundKind == "spotify") {
                com.pakarai.alarme.core.openSpotifyApp(this@AlarmService)
            }
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
                alarm.policeVolume || alarm.extraLoud,
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
        // nunca deixa dois laços vibrando juntos (start repetido = vibração duplicada)
        vibratorJob?.cancel()
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

    /**
     * Vigia anti-fuga SEM serviço de acessibilidade.
     *
     * O GuardService antigo exigia "Acessibilidade" ligada e, por isso, o
     * Nubank (e outros bancos) acusava o Pakarai de app nocivo e mandava
     * remover. Aqui o mesmo efeito sai do "Acesso ao uso": a cada ~700ms
     * olhamos qual pacote está na frente; se não for o nosso durante o
     * toque/checagem, reabrimos o desafio. A abertura em background é
     * permitida porque o app tem "Aparecer por cima" (SYSTEM_ALERT_WINDOW).
     *
     * Sem o acesso ao uso, nada acontece: o alarme continua tocando e a
     * notificação em tela cheia segue como plano B. Melhor degradar do que
     * inventar fuga.
     */
    private fun startGuard(alarmId: Long) {
        if (!AppScope.settings.guardUserEnabled) return
        if (!ForegroundApp.hasUsageAccess(this)) return
        guardJob?.cancel()
        lastRelaunchAt = 0L
        guardJob = scope.launch {
            while (true) {
                delay(GUARD_TICK_MS)
                val state = AppScope.stateManager.state.value
                val active = isGuardActive(state, alarmId)
                if (!active) {
                    lastRelaunchAt = 0L
                    continue
                }
                val now = SystemClock.uptimeMillis()
                val fg = ForegroundApp.currentPackage(this@AlarmService)
                if (!GuardPolicy.shouldRelaunch(fg, packageName, true, now, lastRelaunchAt)) continue
                lastRelaunchAt = now
                relaunch(state, alarmId)
            }
        }
    }

    /**
     * Ringing = vigia sempre. Checking = só depois que a janela de resposta
     * abriu: antes disso usar o telefone é legítimo (ninguém foi pego dormindo).
     */
    private fun isGuardActive(state: AlarmStateManager.State, alarmId: Long): Boolean = when (state) {
        is AlarmStateManager.State.Ringing -> state.alarmId == alarmId
        is AlarmStateManager.State.Checking ->
            state.alarmId == alarmId && state.nextAtMs <= System.currentTimeMillis()
        else -> false
    }

    /**
     * Fugiu do desafio → reabre. Fugiu do "AINDA ACORDADO?" na janela → conta
     * como não respondido: re-toca o alarme de verdade em vez de só reabrir
     * o popup.
     */
    private suspend fun relaunch(state: AlarmStateManager.State, alarmId: Long) {
        if (state is AlarmStateManager.State.Checking) {
            AppScope.stateManager.finishChecking(alarmId)
            AppScope.scheduler.cancelCheck(alarmId)
            AlarmService.start(this, alarmId)
            return
        }
        val intent = Intent(this, ChallengeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(Constants.EXTRA_ALARM_ID, alarmId)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
        }
        bounceIfStillAway(alarmId)
    }

    /**
     * `startActivity` em segundo plano é NEGADO EM SILÊNCIO pelo Android 10+
     * quando falta "Aparecer por cima" — não lança exceção, então o try/catch
     * sozinho mentiria. Aqui a gente confere de fato quem ficou na frente e,
     * se não fomos nós, derruba e republica a notificação com
     * fullScreenIntent: esse caminho o sistema respeita em app de alarme,
     * sem precisar de acessibilidade.
     */
    private suspend fun bounceIfStillAway(alarmId: Long) {
        delay(1_200)
        if (ForegroundApp.currentPackage(this) == packageName) return
        try {
            androidx.core.app.NotificationManagerCompat.from(this).cancel(Constants.SERVICE_ID_RINGING)
            startForegroundSafe(Notifications.ringing(this, alarmId, null))
        } catch (_: Exception) {
        }
    }

    private fun cleanup() {
        if (cleaning) return
        cleaning = true
        // saiu do ciclo: o indicador do check pendente não faz mais sentido
        WakeCheckNotifier.refresh(this)
        restoreDnd()
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
        guardJob?.cancel()
        guardJob = null
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

    /**
     * Devolve o DND ao estado anterior. O filtro anterior vive no
     * `DndBypass` (em disco), então isso funciona mesmo se o processo
     * anterior tenha morrido antes de devolver.
     */
    private fun restoreDnd() {
        DndBypass.restore(this)
    }

    override fun onDestroy() {
        if (!cleaning) {
            // serviço morto no meio do toque: o DND tem que voltar mesmo assim
            restoreDnd()
            sound?.stop()
            sound?.release()
            vibratorJob?.cancel()
            watchdog?.cancel()
            guardJob?.cancel()
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
        /** Intervalo do vigia (reabre o desafio em ~0,7s após a fuga). */
        private const val GUARD_TICK_MS = 700L

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