package com.pakarai.alarme.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.pakarai.alarme.core.BootMirror
import com.pakarai.alarme.core.Constants
import com.pakarai.alarme.core.MirrorEntry
import com.pakarai.alarme.core.PrefsBootMirror
import com.pakarai.alarme.core.SettingsManager
import com.pakarai.alarme.data.AlarmEntity
import com.pakarai.alarme.receiver.AlarmReceiver
import com.pakarai.alarme.service.Notifications
import com.pakarai.alarme.ui.MainActivity
import com.pakarai.alarme.widget.NextAlarmWidget

/**
 * Agenda alarmes com setAlarmClock() — o único tipo que a OneUI/Samsung
 * respeita 100% (é o mesmo que o Clock nativo usa). Sempre one-shot:
 * cada disparo re-agenda o próximo (mais confiável que repeating).
 */
class AlarmScheduler(
    private val context: Context,
    private val mirror: BootMirror = PrefsBootMirror(context)
) : StartupScheduler {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Android 13+ exige permissão de alarme exato; checa NA HORA do agendamento. */
    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    override fun schedule(alarm: AlarmEntity) {
        if (!alarm.enabled) return
        if (!canScheduleExact()) {
            // permissão revogada no meio da noite = alarme NUNCA mais dispararia
            // por câmera lenta: remove do espelho e avisa (rate-limited)
            mirror.remove(alarm.id)
            notifyPausedIfDue()
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
        // espelha no direct boot: após reboot, o LOCKED_BOOT_COMPLETED reagenda sem Room
        mirror.save(MirrorEntry.from(alarm))
        // widget some/atualiza na hora com o próximo alarme agendado
        NextAlarmWidget.refresh(context)
    }

    fun cancel(alarmId: Long) {
        alarmManager.cancel(pendingIntent(alarmId, Constants.ACTION_FIRE))
        cancelSnooze(alarmId)
        cancelCheck(alarmId)
        mirror.remove(alarmId)
        NextAlarmWidget.refresh(context)
    }

    /** Notificação única (rate-limited) quando a permissão de alarme exato sai. */
    private fun notifyPausedIfDue() {
        if (!Notifications.canPost(context)) return
        Notifications.createChannels(context)
        if (!SettingsManager(context).canShowPausedNotification()) return
        try {
            Notifications.notify(
                context,
                Constants.NOTIF_ID_PAUSED,
                Notifications.paused(context)
            )
        } catch (_: Exception) {
        }
    }

    /**
     * Aviso de permissão exata sem virar armadilha: o schedule antigo abria a
     * settings a CADA abertura do app — agora no máximo 1x/dia, e o banner da
     * Home + a notificação de pausa já cobrem o resto.
     */
    private fun requestExactPermission(context: Context) {
        if (!SettingsManager(context).canNudgeExactPermission()) return
        AlarmScheduler.requestExactPermission(context)
    }

    /** Cancela só o ciclo ATUAL (toque + soneca), preservando o check recém-agendado. */
    fun cancelFiring(alarmId: Long) {
        alarmManager.cancel(pendingIntent(alarmId, Constants.ACTION_FIRE))
        cancelSnooze(alarmId)
    }

    fun cancelSnooze(alarmId: Long) {
        alarmManager.cancel(pendingIntent(alarmId, Constants.ACTION_SNOOZE))
    }

    fun scheduleSnooze(alarmId: Long, afterMinutes: Int) {
        scheduleSnoozeAt(alarmId, System.currentTimeMillis() + afterMinutes * 60_000L)
    }

    /** Re-agenda a soneca para um instante exato (recupera o estado após reboot). */
    override fun scheduleSnoozeAt(alarmId: Long, triggerAtMs: Long) {
        if (!canScheduleExact()) return
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMs,
            pendingIntent(alarmId, Constants.ACTION_SNOOZE)
        )
    }

    /** Agenda o "AINDA ACORDADO?" = dispara o CheckActivity em [afterMs]. */
    override fun scheduleCheck(alarmId: Long, afterMs: Long) {
        if (!canScheduleExact()) return
        val triggerAt = System.currentTimeMillis() + afterMs.coerceAtLeast(0)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent(alarmId, Constants.ACTION_CHECK)
        )
    }

    fun cancelCheck(alarmId: Long) {
        alarmManager.cancel(pendingIntent(alarmId, Constants.ACTION_CHECK))
    }

    override fun scheduleImmediateCheck(alarmId: Long) {
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 300,
                pendingIntent(alarmId, Constants.ACTION_CHECK)
            )
        } catch (_: Exception) {
        }
    }

    override fun scheduleImmediate(alarmId: Long) {
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

        /**
         * Direct boot: reagenda do espelho DE sem tocar no Room (ainda
         * criptografado até o 1º desbloqueio).
         */
        fun scheduleFromMirror(context: Context, mirror: BootMirror) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            for (entry in mirror.list()) {
                if (!entry.enabled) continue
                val at = computeNextTrigger(entry.toAlarm(), System.currentTimeMillis())
                scheduleExactFire(context, am, entry.id, at)
            }
        }

        /** Disparo FIRE isolado num instante exato (usado no path de direct boot). */
        fun scheduleExactFire(context: Context, am: AlarmManager?, alarmId: Long, atMs: Long) {
            val manager = am ?: context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) return
            try {
                manager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(atMs, null),
                    firePendingIntent(context, alarmId)
                )
            } catch (_: Exception) {
            }
        }

        private fun firePendingIntent(context: Context, alarmId: Long): PendingIntent {
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = Constants.ACTION_FIRE
                putExtra(Constants.EXTRA_ALARM_ID, alarmId)
            }
            return PendingIntent.getBroadcast(
                context,
                (alarmId.toInt() * 31 + Constants.ACTION_FIRE.hashCode()),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}

/**
 * Próximo disparo futuro respeitando hora + dias da semana.
 * Puro e determinístico: recebe o instante de origem e o fuso explicitamente
 * (java.time, imune a DST e a mudança de hora global), sem ler relógio interno.
 */
fun computeNextTrigger(
    alarm: AlarmEntity,
    from: Long,
    zone: java.time.ZoneId = java.time.ZoneId.systemDefault()
): Long {
    val at = java.time.ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(from), zone)
    val today = at.toLocalDate()
    val hour = alarm.hour.coerceIn(0, 23)
    val minute = alarm.minute.coerceIn(0, 59)

    fun instantOn(date: java.time.LocalDate): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    if (!alarm.isRepeating()) {
        val todayAt = instantOn(today)
        return if (todayAt > from) todayAt else instantOn(today.plusDays(1))
    }

    // caminha do dia de hoje em diante até um dia marcado (sempre estritamente futuro)
    for (i in 0L..8L) {
        val d = today.plusDays(i)
        val dayBit = (d.dayOfWeek.value - 1) // MONDAY=1 → 0 ... SUNDAY → 6
        if (alarm.repeatDaysMask and (1 shl dayBit) == 0) continue
        val candidate = instantOn(d)
        if (candidate > from) return candidate
    }
    return instantOn(today.plusDays(1))
}

/** O que fazer com um ciclo pendente (soneca / "AINDA ACORDADO?") no startup. */
enum class RestoreAction {
    /** Disparo original ainda futuro → reagenda pro instante exato. */
    RESCHEDULE,

    /** Venceu dentro da janela sã → retoma/re-toca agora. */
    RE_RING,

    /** Venceu há tempo demais ou o alarme não faz mais sentido → zera o estado. */
    CLEAR,
}

/**
 * Janela em que um ciclo vencido ainda é "vivo". O que estoura isso é lixo de
 * estado (ex.: soneca de ontem) e nunca re-toca ao abrir o app.
 */
const val MISSED_WINDOW_MS = 12 * 60 * 60 * 1000L

/**
 * Decide a restauração de uma SONECA pendente.
 * [alarmUsable] = alarme ainda existe e pode tocar (habilitado ou recorrente).
 */
fun restoreSnoozeAction(expiresAtMs: Long, now: Long, alarmUsable: Boolean): RestoreAction {
    if (!alarmUsable) return RestoreAction.CLEAR
    if (expiresAtMs >= now) return RestoreAction.RESCHEDULE
    if (now - expiresAtMs > MISSED_WINDOW_MS) return RestoreAction.CLEAR
    return RestoreAction.RE_RING
}

/**
 * Decide a restauração de um "AINDA ACORDADO?" pendente.
 * [alarmExists] basta: resolver um one-shot desabilita o alarme, mas o check
 * pendente ainda precisa valer.
 */
fun restoreCheckAction(nextAtMs: Long, now: Long, alarmExists: Boolean): RestoreAction {
    if (!alarmExists) return RestoreAction.CLEAR
    if (nextAtMs >= now) return RestoreAction.RESCHEDULE
    if (now - nextAtMs > MISSED_WINDOW_MS) return RestoreAction.CLEAR
    return RestoreAction.RE_RING
}