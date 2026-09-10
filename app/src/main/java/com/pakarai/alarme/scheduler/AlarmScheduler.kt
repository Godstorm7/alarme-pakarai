package com.pakarai.alarme.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.core.Constants
import com.pakarai.alarme.data.AlarmEntity
import com.pakarai.alarme.receiver.AlarmReceiver
import com.pakarai.alarme.ui.MainActivity
import java.util.Calendar

/**
 * Agenda alarmes com setAlarmClock() — o único tipo que a OneUI/Samsung
 * respeita 100% (é o mesmo que o Clock nativo usa). Sempre one-shot:
 * cada disparo re-agenda o próximo (mais confiável que repeating).
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Android 13+ exige permissão de alarme exato; checa NA HORA do agendamento. */
    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun schedule(alarm: AlarmEntity) {
        if (!alarm.enabled) return
        if (!canScheduleExact()) {
            requestExactPermission(context)
            return
        }
        val triggerAt = computeNextTrigger(alarm, System.currentTimeMillis())
        val pi = pendingIntent(alarm.id, Constants.ACTION_FIRE)
        val showPi = PendingIntent.getActivity(
            context,
            requestCode(alarm.id),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // setAlarmClock: mostra ícone de alarme na barra + dispara mesmo em Doze/deep sleep
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAt, showPi),
            pi
        )
    }

    fun cancel(alarmId: Long) {
        alarmManager.cancel(pendingIntent(alarmId, Constants.ACTION_FIRE))
        cancelSnooze(alarmId)
    }

    fun cancelSnooze(alarmId: Long) {
        alarmManager.cancel(pendingIntent(alarmId, Constants.ACTION_SNOOZE))
    }

    fun scheduleSnooze(alarmId: Long, afterMinutes: Int) {
        if (!canScheduleExact()) return
        val triggerAt = System.currentTimeMillis() + afterMinutes * 60_000L
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent(alarmId, Constants.ACTION_SNOOZE)
        )
    }

    /**
     * Re-agenda todos; dispara na hora qualquer alarme que já deveria ter tocado
     * (recuperação de alarme perdido por morte de processo / reboot).
     */
    suspend fun rescheduleAllOnStartup() {
        for (alarm in AppScope.repository.getAll()) {
            if (!alarm.enabled) continue
            val now = System.currentTimeMillis()
            val next = computeNextTrigger(alarm, now)
            val missed = !alarm.isRepeating() && next - now > ONE_DAY_MS - MISSED_WINDOW_MS
            if (missed) {
                // deveria ter tocado enquanto o app não rodava → toca agora
                scheduleImmediate(alarm.id)
            } else {
                schedule(alarm)
            }
        }
    }

    private fun scheduleImmediate(alarmId: Long) {
        try {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 400,
                pendingIntent(alarmId, Constants.ACTION_FIRE)
            )
        } catch (_: Exception) {
        }
    }

    private fun pendingIntent(alarmId: Long, action: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            this.action = action
            putExtra(Constants.EXTRA_ALARM_ID, alarmId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(alarmId, action),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun requestCode(alarmId: Long, action: String = Constants.ACTION_FIRE): Int =
        (alarmId.toInt() * 31 + action.hashCode())

    companion object {
        fun requestExactPermission(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (am.canScheduleExactAlarms()) return
            val intent = Intent("android.settings.REQUEST_SCHEDULE_EXACT_ALARM").apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                val fallback = Intent("android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallback)
            }
        }

        private const val ONE_DAY_MS = 86_400_000L
        private const val MISSED_WINDOW_MS = 12 * 60 * 60 * 1000L
    }
}

/** Próximo disparo futuro respeitando hora + dias da semana. */
fun computeNextTrigger(alarm: AlarmEntity, from: Long): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = from }
    cal.set(Calendar.HOUR_OF_DAY, alarm.hour)
    cal.set(Calendar.MINUTE, alarm.minute)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)

    if (!alarm.isRepeating()) {
        val base = cal.timeInMillis
        return if (base > from) base else base + AlarmSchedulerHelper.ONE_DAY_MS
    }

    // caminha dia a dia até cair em um dia marcado
    for (i in 0..8) {
        val candidate = cal.timeInMillis + i * AlarmSchedulerHelper.ONE_DAY_MS
        if (candidate <= from) continue
        val calC = Calendar.getInstance().apply { timeInMillis = candidate }
        val dayBit = (calC.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
        if (alarm.repeatDaysMask and (1 shl dayBit) != 0) return candidate
    }
    return cal.timeInMillis + AlarmSchedulerHelper.ONE_DAY_MS
}

internal object AlarmSchedulerHelper {
    const val ONE_DAY_MS = 86_400_000L
}