package com.pakarai.alarme.ui.history

import com.pakarai.alarme.data.RingLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Formatação do histórico com relógio e fuso injetados: "hoje" às 23:59 vira
 * "ontem" às 00:01, e esse é exatamente o tipo de erro que só aparece às 3 da
 * manhã se o teste depender de `System.currentTimeMillis()`.
 */
class RingHistoryFormatTest {

    private val zone: ZoneId = ZoneId.of("America/Sao_Paulo")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun entry(id: Long, startedAt: Long, durationMs: Long? = 60_000L) = RingLogEntity(
        id = id,
        alarmId = 1L,
        label = "Acordar",
        startedAt = startedAt,
        startedElapsed = 1_000L,
        endedAt = durationMs?.let { startedAt + it },
        durationMs = durationMs,
        outcome = RingLogEntity.Outcome.DISMISSED
    )

    @Test
    fun mesmoDiaEhHoje() {
        val now = at(2026, 11, 7, 9, 0)
        assertEquals("hoje", RingHistoryFormat.dayLabel(at(2026, 11, 7, 6, 30), now, zone))
    }

    @Test
    fun diaAnteriorEhOntem() {
        val now = at(2026, 11, 7, 9, 0)
        assertEquals("ontem", RingHistoryFormat.dayLabel(at(2026, 11, 6, 6, 30), now, zone))
    }

    @Test
    fun doisDiasAtrasMostraDataCurta() {
        val now = at(2026, 11, 7, 9, 0)
        assertEquals("05/11", RingHistoryFormat.dayLabel(at(2026, 11, 5, 6, 30), now, zone))
    }

    @Test
    fun anoDiferenteMostraAno() {
        val now = at(2026, 11, 7, 9, 0)
        assertEquals("31/12/2025", RingHistoryFormat.dayLabel(at(2025, 12, 31, 6, 30), now, zone))
    }

    @Test
    fun viradaDaMeiaNoiteNaoEhHoje() {
        // o caso que quebra quando se compara "mesmo dia" com hora em vez de data
        val now = at(2026, 11, 7, 0, 5)
        assertEquals("ontem", RingHistoryFormat.dayLabel(at(2026, 11, 6, 23, 58), now, zone))
    }

    @Test
    fun horaSaiComDoisDigitos() {
        assertEquals("06:05", RingHistoryFormat.clockLabel(at(2026, 11, 7, 6, 5), zone))
        assertEquals("23:59", RingHistoryFormat.clockLabel(at(2026, 11, 7, 23, 59), zone))
    }

    @Test
    fun duracaoAusenteNaoEhInventada() {
        // toque sem fim registrado (processo morreu): não vira "tocou 0s"
        assertNull(RingHistoryFormat.durationLabel(null))
        assertEquals("1:23", RingHistoryFormat.durationLabel(83_000L))
        assertEquals("45s", RingHistoryFormat.durationLabel(45_000L))
    }

    @Test
    fun agrupaPorDiaPreservandoOrdem() {
        val now = at(2026, 11, 7, 9, 0)
        val items = listOf(
            entry(1, at(2026, 11, 7, 6, 30)),
            entry(2, at(2026, 11, 7, 6, 40)),
            entry(3, at(2026, 11, 6, 6, 30)),
            entry(4, at(2026, 11, 5, 6, 30))
        )
        val groups = RingHistoryFormat.groupByDay(items, now, zone)
        assertEquals(listOf("hoje", "ontem", "05/11"), groups.map { it.first })
        assertEquals(listOf(1L, 2L), groups[0].second.map { it.id })
        assertEquals(listOf(3L), groups[1].second.map { it.id })
        assertEquals(listOf(4L), groups[2].second.map { it.id })
    }
}
