package com.pakarai.alarme.service

import android.app.NotificationManager
import android.content.Context
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.core.AlarmStateManager
import com.pakarai.alarme.core.AppJobs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Indicador FIXO do "AINDA ACORDADO?" pendente: enquanto o alarme está em
 * verificação, mostra na barra a próxima checagem ("checagem 2 de 3 · 07:05")
 * com um botão CONFIRMAR. É o indicador que faltava — sem ele, o check em espera
 * ficava invisível até disparar.
 */
object WakeCheckNotifier {

    private const val NOTIF_ID = 2002

    /** Lê o estado atual e sincroniza a notificação (posta ou cancela). */
    fun refresh(context: Context) {
        val app = context.applicationContext
        if (!AppScope.isManualDiReady()) return
        val state = AppScope.stateManager.state.value
        if (state !is AlarmStateManager.State.Checking) {
            cancel(app)
            return
        }
        if (!Notifications.canPost(app)) {
            cancel(app)
            return
        }
        Notifications.createChannels(app)
        AppJobs.launch("wake-check-notif") {
            val alarm = AppScope.repository.getById(state.alarmId)
            withContext(Dispatchers.Main) {
                if (alarm == null) cancel(app) else post(app, state.nextAtMs, state.checkIndex, alarm.ackChecks)
            }
        }
    }

    private fun post(context: Context, nextAtMs: Long, index: Int, total: Int) {
        val cal = Calendar.getInstance().apply { timeInMillis = nextAtMs }
        val hh = cal.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
        val mm = cal.get(Calendar.MINUTE).toString().padStart(2, '0')
        val title = if (total > 0) "AINDA ACORDADO? · checagem $index de $total"
        else "AINDA ACORDADO? · checagem $index"
        val text = "Confirmar às $hh:$mm — sem resposta o alarme volta a tocar."
        try {
            Notifications.notify(context, NOTIF_ID, Notifications.wakeCheckPending(context, title, text))
        } catch (_: Exception) {
        }
    }

    fun cancel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        try {
            nm.cancel(NOTIF_ID)
        } catch (_: Exception) {
        }
    }
}
