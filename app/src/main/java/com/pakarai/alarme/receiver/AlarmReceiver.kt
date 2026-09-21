package com.pakarai.alarme.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.core.Constants
import com.pakarai.alarme.core.PrefsBootMirror
import com.pakarai.alarme.scheduler.AlarmScheduler
import com.pakarai.alarme.scheduler.DirectBootPolicy
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

        // Direct boot: disparo chegou com o telefone ainda bloqueado (criptografado).
        // O app não consegue tocar ainda — registra como perdido e reagenda o próximo
        // ciclo pelo espelho DE; a recuperação acontece no desbloqueio.
        if (!AppScope.isManualDiReady()) {
            handleLockedFire(context, alarmId)
            return
        }
        if (isDuplicate(context, alarmId, action)) return

        when (action) {
            Constants.ACTION_FIRE -> {
                AlarmService.start(context, alarmId)
                rescheduleRecurring(context, alarmId)
            }
            Constants.ACTION_SNOOZE -> {
                // a soneca voltou: retoma o toque completo
                AlarmService.start(context, alarmId, snoozeReturn = true)
            }
            Constants.ACTION_CHECK -> {
                // "AINDA ACORDADO?": abre o prompt silencioso
                AlarmService.startCheck(context, alarmId)
            }
            Constants.ACTION_WARMUP -> {
                // pré-aquecimento: reafirma o alarme exato (defensivo)
                warmup(context, alarmId)
            }
        }
    }

    /** Warmup: re-agenda o mesmo alarme (reafirma o setAlarmClock). */
    private fun warmup(context: Context, alarmId: Long) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarm = AppScope.repository.getById(alarmId)
                if (alarm != null && alarm.enabled) AppScope.scheduler.schedule(alarm)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }

    /** FIRE durante o direct boot: sem Room, só espelho DE + AlarmManager. */
    private fun handleLockedFire(context: Context, alarmId: Long) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val mirror = PrefsBootMirror(context)
                val entry = mirror.list().firstOrNull { it.id == alarmId }
                when (DirectBootPolicy.onFireWhileLocked(entry)) {
                    DirectBootPolicy.Outcome.Ignore -> {
                    }
                    else -> {
                        val known = entry ?: return@launch
                        mirror.recordMissedDue(alarmId, System.currentTimeMillis())
                        if (known.isRepeating()) {
                            AlarmScheduler.scheduleFromMirror(context, mirror)
                        } else {
                            mirror.remove(alarmId)
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                pending.finish()
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

    private fun isDuplicate(context: Context, alarmId: Long, action: String): Boolean {
        val prefs = context.applicationContext
            .getSharedPreferences("pakarai_debounce", Context.MODE_PRIVATE)
        val key = "$alarmId|$action"
        val now = System.currentTimeMillis()
        val last = prefs.getLong(key, 0L)
        if (now - last < DEBOUNCE_MS) return true
        prefs.edit().putLong(key, now).apply()
        if (prefs.all.size > MAX_KEYS) pruneStale(prefs, now)
        return false
    }

    /** Joga fora entradas antigas pra o arquivo não crescer pra sempre. */
    private fun pruneStale(prefs: android.content.SharedPreferences, now: Long) {
        val stale = prefs.all.mapNotNull { (k, v) ->
            if (v is Long && now - (v as Long) > PRUNE_AGE_MS) k else null
        }
        if (stale.isNotEmpty()) {
            val editor = prefs.edit()
            stale.forEach { editor.remove(it) }
            editor.apply()
        }
    }

    companion object {
        private const val DEBOUNCE_MS = 1500L
        // acima disso não presta pra debounce; janela é só de alguns segundos
        private const val PRUNE_AGE_MS = 60_000L * 10
        private const val MAX_KEYS = 64
    }
}