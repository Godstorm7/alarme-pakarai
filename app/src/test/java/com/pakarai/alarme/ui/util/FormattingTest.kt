package com.pakarai.alarme.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {

    @Test
    fun `contagem mostra horas e minutos`() {
        assertEquals("toca em 4h 32min", formatCountdown((4 * 60 + 32) * 60_000L))
    }

    @Test
    fun `contagem so com minutos`() {
        assertEquals("toca em 7min", formatCountdown(7 * 60_000L))
    }

    @Test
    fun `contagem de hora cheia`() {
        assertEquals("toca em 2h 0min", formatCountdown(2 * 60 * 60_000L))
    }

    @Test
    fun `contagem abaixo de um minuto (e negativa) nao quebra`() {
        assertEquals("toca em menos de 1 min", formatCountdown(45_000L))
        assertEquals("toca em menos de 1 min", formatCountdown(-5L))
    }
}
