package com.pakarai.alarme.core

import com.pakarai.alarme.data.RingLogEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Como o fim de um toque vira rótulo no histórico.
 *
 * O detalhe que importa: `Idle` não diz POR QUE acabou — desligar e "estourou a
 * janela" terminam no mesmo estado. Por isso o watchdog passa `perdido` na mão
 * e esta tradução só cobre os casos em que o próprio estado conta a história.
 */
class RingLogOutcomeTest {

    @Test
    fun desligarEhDesligado() {
        assertEquals(
            RingLogEntity.Outcome.DISMISSED,
            RingLog.outcomeFor(AlarmStateManager.State.Idle)
        )
    }

    @Test
    fun semEstadoTambemEhDesligado() {
        // serviço encerrando sem estado legível: o toque acabou, e "tocando"
        // seria mentira (o som parou)
        assertEquals(RingLogEntity.Outcome.DISMISSED, RingLog.outcomeFor(null))
    }

    @Test
    fun sonecaEhSoneca() {
        val state = AlarmStateManager.State.Snoozing(
            alarmId = 7L,
            remainingSnoozes = 2,
            expiresAtMs = 1_000L,
            usedSnoozes = 1
        )
        assertEquals(RingLogEntity.Outcome.SNOOZE, RingLog.outcomeFor(state))
    }

    @Test
    fun checagemEhChecagem() {
        val state = AlarmStateManager.State.Checking(alarmId = 7L, nextAtMs = 1_000L, checkIndex = 2)
        assertEquals(RingLogEntity.Outcome.CHECK, RingLog.outcomeFor(state))
    }

    @Test
    fun aindaTocandoContinuaTocando() {
        val state = AlarmStateManager.State.Ringing(alarmId = 7L, expiresAtMs = 9_999L, usedSnoozes = 0)
        assertEquals(RingLogEntity.Outcome.RINGING, RingLog.outcomeFor(state))
    }

    @Test
    fun todoResultadoTemRotuloLegivel() {
        // nenhum rótulo pode vazar a chave crua pra tela
        val outcomes = listOf(
            RingLogEntity.Outcome.RINGING,
            RingLogEntity.Outcome.DISMISSED,
            RingLogEntity.Outcome.SNOOZE,
            RingLogEntity.Outcome.CHECK,
            RingLogEntity.Outcome.MISSED,
            RingLogEntity.Outcome.INTERRUPTED
        )
        for (o in outcomes) {
            val label = RingLogEntity.Outcome.label(o)
            assertEquals(o, label.lowercase())
        }
    }
}
