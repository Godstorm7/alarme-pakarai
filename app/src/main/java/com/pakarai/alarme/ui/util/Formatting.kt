package com.pakarai.alarme.ui.util

import com.pakarai.alarme.data.AlarmEntity
import java.util.Calendar

private val DAY_ABBR = listOf("SEG", "TER", "QUA", "QUI", "SEX", "SÁB", "DOM")

fun formatTime(hour: Int, minute: Int): String =
    "%02d:%02d".format(hour, minute)

/** Quanto falta pro alarme, curto: "toca em 4h 32min" / "toca em 7min" / "toca em menos de 1 min". */
fun formatCountdown(remainingMs: Long): String {
    val totalMinutes = (remainingMs.coerceAtLeast(0)) / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        totalMinutes <= 0 -> "toca em menos de 1 min"
        hours > 0 -> "toca em ${hours}h ${minutes}min"
        else -> "toca em ${minutes}min"
    }
}

/** Duração curta e SEM prefixo: "52s" / "3:20" / "1h 05min". */
fun formatDuration(remainingMs: Long): String {
    val totalSec = remainingMs.coerceAtLeast(0) / 1000
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return when {
        hours > 0 -> "%dh %02dmin".format(hours, minutes)
        minutes > 0 -> "%d:%02d".format(minutes, seconds)
        else -> "${seconds}s"
    }
}

fun repeatDaysLabel(mask: Int): String {
    if (mask == 0) return "Só uma vez"
    if (mask == 0b1111111) return "Todos os dias"
    if (mask == 0b0011111) return "Dias úteis"
    if (mask == 0b1100000) return "Fim de semana"
    return DAY_ABBR.mapIndexedNotNull { i, d ->
        if (mask and (1 shl i) != 0) d else null
    }.joinToString(" · ")
}

fun nextFireLabel(alarm: AlarmEntity): String {
    val next = computeNextTriggerForUi(alarm, System.currentTimeMillis())
    val cal = Calendar.getInstance().apply { timeInMillis = next }
    val today = Calendar.getInstance()
    val rel = when {
        sameDay(cal, today) -> "Hoje"
        sameDay(cal, Calendar.getInstance().also { it.add(Calendar.DAY_OF_YEAR, 1) }) -> "Amanhã"
        else -> arrayOf("dom", "seg", "ter", "qua", "qui", "sex", "sáb")[cal.get(Calendar.DAY_OF_WEEK) - 1]
    }
    return "$rel ${formatTime(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))}"
}

private fun sameDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

/** Cópia local do cálculo (sem import circular com scheduler). */
fun computeNextTriggerForUi(alarm: AlarmEntity, from: Long): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = from }
    cal.set(Calendar.HOUR_OF_DAY, alarm.hour)
    cal.set(Calendar.MINUTE, alarm.minute)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    if (!alarm.isRepeating()) {
        val base = cal.timeInMillis
        return if (base > from) base else base + 86_400_000L
    }
    for (i in 0..8) {
        val candidate = cal.timeInMillis + i * 86_400_000L
        if (candidate <= from) continue
        val c = Calendar.getInstance().apply { timeInMillis = candidate }
        val bit = (c.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
        if (alarm.repeatDaysMask and (1 shl bit) != 0) return candidate
    }
    return cal.timeInMillis + 86_400_000L
}