package com.pakarai.alarme.ui.history

import com.pakarai.alarme.data.RingLogEntity
import com.pakarai.alarme.ui.util.formatDuration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Formatação do histórico de toques. Tudo aqui é função PURA com `now` e fuso
 * injetados — "hoje" às 23:59 vira "ontem" às 00:01, e isso só se testa de
 * verdade quando o relógio não vem de dentro.
 */
object RingHistoryFormat {

    /** "hoje" / "ontem" / "07/11" / "07/11/2025" (o ano só quando não é o corrente). */
    fun dayLabel(startedAt: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val day = Instant.ofEpochMilli(startedAt).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when {
            day == today -> "hoje"
            day == today.minusDays(1) -> "ontem"
            day.year == today.year -> "%02d/%02d".format(day.dayOfMonth, day.monthValue)
            else -> "%02d/%02d/%d".format(day.dayOfMonth, day.monthValue, day.year)
        }
    }

    /** Hora do toque: "06:30". */
    fun clockLabel(startedAt: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val t = Instant.ofEpochMilli(startedAt).atZone(zone).toLocalTime()
        return "%02d:%02d".format(t.hour, t.minute)
    }

    /** Quanto tempo tocou. Sem fim registrado, não inventa número. */
    fun durationLabel(durationMs: Long?): String? =
        durationMs?.let { formatDuration(it) }

    /**
     * Agrupa por dia preservando a ordem recebida (mais recente primeiro,
     * que é como a consulta entrega). Devolve pares (rótulo do dia, toques).
     */
    fun groupByDay(
        items: List<RingLogEntity>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<Pair<String, List<RingLogEntity>>> {
        val out = mutableListOf<Pair<String, MutableList<RingLogEntity>>>()
        for (item in items) {
            val label = dayLabel(item.startedAt, now, zone)
            val last = out.lastOrNull()
            if (last != null && last.first == label) last.second.add(item)
            else out.add(label to mutableListOf(item))
        }
        return out.map { it.first to it.second.toList() }
    }

    /** Data completa pro subtítulo de um toque antigo (quando o dia já não é óbvio). */
    fun fullDateLabel(startedAt: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val d: LocalDate = Instant.ofEpochMilli(startedAt).atZone(zone).toLocalDate()
        return "%02d/%02d/%d".format(d.dayOfMonth, d.monthValue, d.year)
    }
}
