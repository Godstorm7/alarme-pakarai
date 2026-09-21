package com.pakarai.alarme.service

import android.app.NotificationManager
import android.content.Context
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.core.AppJobs
import com.pakarai.alarme.scheduler.computeNextTrigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Notificação FIXA mostrando o próximo alarme na barra. Atualiza sempre que o
 * "próximo alarme" muda (mesmo hook do widget: [com.pakarai.alarme.widget.NextAlarmWidget.refresh]).
 * Desligável em Ajustes ([com.pakarai.alarme.core.SettingsManager.persistentNotification]).
 */
object NextAlarmNotifier {

    private const val NOTIF_ID = 2001

    fun refresh(context: Context) {
        val app = context.applicationContext
        if (!AppScope.isManualDiReady()) return
        if (!AppScope.settings.persistentNotification) {
            cancel(app)
            return
        }
        if (!Notifications.canPost(app)) {
            cancel(app)
            return
        }
        Notifications.createChannels(app)
        AppJobs.launch("next-alarm-notif") {
            val next = AppScope.repository.getAll()
                .asSequence()
                .filter { it.enabled }
                .mapNotNull { runCatching { computeNextTrigger(it, System.currentTimeMillis()) to it }.getOrNull() }
                .minByOrNull { it.first }
            withContext(Dispatchers.Main) {
                if (next == null) {
                    cancel(app)
                } else {
                    post(app, next.first, next.second.label)
                }
            }
        }
    }

    private fun post(context: Context, triggerMs: Long, label: String) {
        val cal = Calendar.getInstance().apply { timeInMillis = triggerMs }
        val hh = cal.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
        val mm = cal.get(Calendar.MINUTE).toString().padStart(2, '0')
        val title = "Próximo alarme: $hh:$mm"
        val text = label.ifBlank { "Alarme" }
        try {
            Notifications.notify(context, NOTIF_ID, Notifications.nextAlarm(context, title, text))
        } catch (_: Exception) {
        }
    }

    private fun cancel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        try {
            nm.cancel(NOTIF_ID)
        } catch (_: Exception) {
        }
    }
}
