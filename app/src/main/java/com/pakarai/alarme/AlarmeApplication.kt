package com.pakarai.alarme

import android.app.Application
import android.content.Context
import com.pakarai.alarme.core.AlarmStateManager
import com.pakarai.alarme.core.AlarmStateManager.State
import com.pakarai.alarme.core.SettingsManager
import com.pakarai.alarme.data.AlarmRepository
import com.pakarai.alarme.scheduler.AlarmScheduler
import com.pakarai.alarme.service.AlarmService
import com.pakarai.alarme.widget.NextAlarmWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * DI manual — sem Hilt/Koin pra manter o build leve e robusto.
 */
object AppScope {
    lateinit var appContext: Context
    lateinit var settings: SettingsManager
    lateinit var stateManager: AlarmStateManager
    lateinit var repository: AlarmRepository
    lateinit var scheduler: AlarmScheduler

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
    }

    fun isManualDiReady(): Boolean = ::appContext.isInitialized
}

class AlarmeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppScope.init(this)

        // Recuperação pós-morte-de-processo: se o telefone matou o app
        // NO MEIO DO TOQUE, retoma a sirene assim que possível.
        val state = AppScope.stateManager.state.value
        var resumed = false
        if (state is State.Ringing) {
            try {
                AlarmService.start(this, state.alarmId, resume = true)
                resumed = true
            } catch (_: Exception) {
                // FGS pode ser bloqueado aqui; o app vai retomar ao abrir a UI
            }
        }
        if (!resumed) {
            // Reagenda sempre: cobre reboot silencioso e perdas de alarme
            CoroutineScope(Dispatchers.IO).launch {
                AppScope.scheduler.rescheduleAllOnStartup()
            }
        }
        NextAlarmWidget.refresh(this)
    }
}