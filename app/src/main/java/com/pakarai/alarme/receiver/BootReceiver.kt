package com.pakarai.alarme.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.core.AlarmStateManager.State
import com.pakarai.alarme.service.AlarmService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Reagenda alarmes após reboot / troca de hora / atualização do app.
 * Sem isso, todo alarme some silenciosamente quando o telefone reinicia.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED
        ) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppScope.stateManager.checkExpired()
                AppScope.scheduler.rescheduleAllOnStartup()
                val state = AppScope.stateManager.state.value
                if (state is State.Ringing) {
                    try {
                        AlarmService.start(context, state.alarmId, resume = true)
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}