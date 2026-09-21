package com.pakarai.alarme

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.UserManager
import com.pakarai.alarme.core.AlarmFlowCoordinator
import com.pakarai.alarme.core.AlarmStateManager
import com.pakarai.alarme.core.AlarmStateManager.State
import com.pakarai.alarme.core.AppJobs
import com.pakarai.alarme.core.PrefsBootMirror
import com.pakarai.alarme.core.SettingsManager
import com.pakarai.alarme.data.AlarmRepository
import com.pakarai.alarme.scheduler.AlarmScheduler
import com.pakarai.alarme.scheduler.DirectBootPolicy
import com.pakarai.alarme.service.AlarmService
import com.pakarai.alarme.spotify.SpotifyClient
import com.pakarai.alarme.spotify.SpotifyHttpClient
import com.pakarai.alarme.spotify.SpotifySession
import com.pakarai.alarme.widget.NextAlarmWidget

/**
 * DI manual — sem Hilt/Koin pra manter o build leve e robusto.
 */
object AppScope {
    lateinit var appContext: Context
    lateinit var settings: SettingsManager
    lateinit var stateManager: AlarmStateManager
    lateinit var repository: AlarmRepository
    lateinit var scheduler: AlarmScheduler
    lateinit var coordinator: AlarmFlowCoordinator
    lateinit var spotifySession: SpotifySession
    lateinit var spotifyClient: SpotifyClient

    private var initialized = false

    /** Inicialização única e determinística: chamar de novo (ex: fake/teste) quebra. */
    fun init(context: Context) {
        check(!initialized) { "AppScope.init chamado mais de uma vez" }
        initialized = true
        val app = context.applicationContext
        appContext = app
        settings = SettingsManager(app)
        stateManager = AlarmStateManager(app)
        repository = AlarmRepository.create(app)
        scheduler = AlarmScheduler(app)
        coordinator = AlarmFlowCoordinator(stateManager, repository, scheduler)
        spotifySession = SpotifySession(app, BuildConfig.SPOTIFY_CLIENT_ID, BuildConfig.SPOTIFY_REDIRECT_URI)
        spotifyClient = SpotifyHttpClient(spotifySession)
    }

    fun isManualDiReady(): Boolean = ::appContext.isInitialized
}

class AlarmeApplication : Application() {

    @Volatile
    private var startupDone = false

    override fun onCreate() {
        super.onCreate()
        val um = getSystemService(UserManager::class.java)
        if (um.isUserUnlocked) {
            onUserUnlocked()
        } else {
            // Direct boot: Room/estado moram em armazenamento criptografado,
            // inacessível agora — inicializar aqui quebraria com o telefone ainda
            // bloqueado. Espera o desbloqueio (ACTION_USER_UNLOCKED); enquanto
            // isso, a agenda é mantida pelo espelho DE (LOCKED_BOOT_COMPLETED).
            registerUnlockReceiver()
        }
    }

    private fun registerUnlockReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != Intent.ACTION_USER_UNLOCKED) return
                try {
                    unregisterReceiver(this)
                } catch (_: Exception) {
                }
                onUserUnlocked()
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(
                    receiver,
                    IntentFilter(Intent.ACTION_USER_UNLOCKED),
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                registerReceiver(receiver, IntentFilter(Intent.ACTION_USER_UNLOCKED))
            }
        } catch (_: Exception) {
        }
    }

    /** Roda na 1ª vez em que o usuário está desbloqueado neste processo. */
    private fun onUserUnlocked() {
        if (startupDone) return
        startupDone = true
        AppScope.init(this)

        // Julga estados vencidos ANTES de decidir retomar: um Ringing com validade
        // passada (teto do toque estourado) é zerado aqui, senão o app
        // re-tocaria pra sempre a cada abertura.
        AppScope.stateManager.checkExpired(System.currentTimeMillis())
        val state = AppScope.stateManager.state.value

        // Recuperação pós-morte-de-processo: se o telefone matou o app
        // NO MEIO DO TOQUE, retoma a sirene assim que possível — mas só se
        // o alarme ainda existe e está habilitado (apagado/desativado = não re-toca).
        if (state is State.Ringing) {
            val alarmId = state.alarmId
            AppJobs.launch("app-startup") {
                val alarm = AppScope.repository.getById(alarmId)
                if (alarm != null && alarm.enabled) {
                    try {
                        AlarmService.start(this@AlarmeApplication, alarmId)
                    } catch (_: Exception) {
                        // FGS pode ser bloqueado aqui; o app vai retomar ao abrir a UI
                    }
                } else {
                    AppScope.stateManager.clear()
                    AppScope.coordinator.onStartup()
                }
            }
        } else {
            // Reagenda sempre: cobre reboot silencioso e perdas de alarme
            AppJobs.launch("app-startup") {
                AppScope.coordinator.onStartup()
            }
        }
        recoverMissedDue()
        NextAlarmWidget.refresh(this)
    }

    /**
     * Disparos que caíram enquanto o telefone estava bloqueado (direct boot):
     * toca agora, se ainda dentro da janela — senão o usuário perderia o alarme
     * só porque acordou depois do reboot.
     */
    private fun recoverMissedDue() {
        AppJobs.launch("unlock-recovery") {
            try {
                val mirror = PrefsBootMirror(this@AlarmeApplication)
                val now = System.currentTimeMillis()
                for ((alarmId, dueMs) in mirror.takeMissedDue()) {
                    if (!DirectBootPolicy.shouldRecover(dueMs, now)) continue
                    val alarm = AppScope.repository.getById(alarmId)
                    if (alarm != null && alarm.enabled) {
                        try {
                            AlarmService.start(this@AlarmeApplication, alarmId)
                        } catch (_: Exception) {
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }
}