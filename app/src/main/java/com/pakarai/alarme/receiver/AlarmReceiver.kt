package com.pakarai.alarme.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.core.Constants
import com.pakarai.alarme.service.AlarmService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis

/**
 * Recebe o disparo do AlarmManager. Mantido MAGRO (Android mata receiver
 * em ~10s): tudo pesado vai pro AlarmService.
 *
 * Debounce: a OneUI dispara intents antigos "em rajada" ao desbloquear —
 * sem isso o alarme duplica.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val alarmId = intent.getLongExtra(Constants.EXTRA_ALARM_ID, -1L)
        if (alarmId < 0L) return
        if (isDuplicate(alarmId, action)) return

        when (action) {
            Constants.ACTION_FIRE -> {
                AlarmService.start(context, alarmId, resume = false)
                rescheduleRecurring(context, alarmId)
            }
            Constants.ACTION_SNOOZE -> {
                // a soneca voltou: retoma o toque completo
                AlarmService.start(context, alarmId, snoozeReturn = true)
            }
        }
    }

    /** Alarme recorrente: reagenda o próximo ciclo no próprio disparo. */
    private fun rescheduleRecurring(context: Context, alarmId: Long) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarm = AppScope.repository.getById(alarmId)
                if (alarm != null && alarm.isRepeating() && alarm.enabled) {
                    AppScope.scheduler.schedule(alarm)
                }
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }

    private fun isDuplicate(alarmId: Long, action: String): Boolean {
        val key = "$alarmId|$action"
        val now = System.currentTimeMillis()
        val last = lastFire[key] ?: 0L
        if (now - last < DEBOUNCE_MS) return true
        lastFire[key] = now
        return false
    }

    companion object {
        private const val DEBOUNCE_MS = 1500L
        private val lastFire = HashMap<String, Long>()
    }
}