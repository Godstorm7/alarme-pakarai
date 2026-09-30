package com.pakarai.alarme.ui.challenge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TapTap: a meta é contagem de toques, então os toques a mais NUNCA podem
 * estourar a barra nem fazer a contagem andar para trás.
 */
class TapRoundTest {

    @Test
    fun `soma um por toque`() {
        assertEquals(1, tapAdvance(0, 100))
        assertEquals(2, tapAdvance(1, 100))
        assertEquals(99, tapAdvance(98, 100))
    }

    @Test
    fun `toques extras travam na meta`() {
        assertEquals(100, tapAdvance(100, 100))
        assertEquals(100, tapAdvance(150, 100))
    }

    @Test
    fun `meta zero nao quebra`() {
        assertEquals(0, tapAdvance(0, 0))
        assertEquals(0, tapAdvance(7, 0))
    }

    @Test
    fun `barra cheia so na meta`() {
        assertEquals(0f, tapFraction(0, 100), 0.0001f)
        assertEquals(0.5f, tapFraction(50, 100), 0.0001f)
        assertEquals(1f, tapFraction(100, 100), 0.0001f)
    }

    @Test
    fun `barra nunca passa de um nem fica negativa`() {
        assertEquals(1f, tapFraction(300, 100), 0.0001f)
        assertEquals(0f, tapFraction(-5, 100), 0.0001f)
    }

    @Test
    fun `meta zero mantem a barra cheia`() {
        // sem meta não há o que preencher: mostrar vazio seria mentira
        assertEquals(1f, tapFraction(0, 0), 0.0001f)
    }

    @Test
    fun `contagem nunca anda para tras`() {
        var atual = 0
        repeat(250) { atual = tapAdvance(atual, 100) }
        assertEquals(100, atual)
        assertTrue(tapFraction(atual, 100) <= 1f)
    }
}
