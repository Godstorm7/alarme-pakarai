package com.pakarai.alarme.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.core.AlarmStateManager.State
import com.pakarai.alarme.core.PrefsBootMirror
import com.pakarai.alarme.scheduler.AlarmScheduler
import com.pakarai.alarme.service.AlarmService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Reagenda alarmes após reboot / troca de hora / atualização do app.
 * Sem isso, todo alarme some silenciosamente quando o telefone reinicia.
 *
 * Direct boot (telefone re-iniciado mas ainda BLOQUEADO/criptografado):
 * o Room é inacessível, então reagendamos do espelho DE. O toque que cair
 * nesse período é recuperado no desbloqueio (missed-due no alarm_state DE).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action == ACTION_LOCKED_BOOT_COMPLETED) {
            rescheduleDirectBoot(context, intent)
            return
        }
        if (action !in REGULAR_ACTIONS) return

        // QUICKBOOT_POWERON (boot rápido Samsung/MIUI) pode chegar antes do
        // desbloqueio; qualquer ação de boot com o app ainda não inicializado
        // cai no espelho DE em vez de quebrar no Room.
        if (!AppScope.isManualDiReady()) {
            rescheduleDirectBoot(context, intent)
            return
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppScope.stateManager.checkExpired(System.currentTimeMillis())
                AppScope.coordinator.onStartup()
                val state = AppScope.stateManager.state.value
                if (state is State.Ringing) {
                    try {
                        AlarmService.start(context, state.alarmId)
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }

    /** Reagenda sem tocar em armazenamento criptografado (nada de Room/AppScope). */
    private fun rescheduleDirectBoot(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AlarmScheduler.scheduleFromMirror(context, PrefsBootMirror(context))
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val ACTION_LOCKED_BOOT_COMPLETED = "android.intent.action.LOCKED_BOOT_COMPLETED"
        const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"

        val REGULAR_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            ACTION_QUICKBOOT_POWERON
        )
    }
}