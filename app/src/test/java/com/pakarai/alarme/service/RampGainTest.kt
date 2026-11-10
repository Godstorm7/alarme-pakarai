package com.pakarai.alarme.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A rampa de volume não pode derrubar o canal de alarme do aparelho: quando o
 * ganho do track é usado, o stream do sistema fica intacto (regressão do toque
 * das 06:40, que deixava o STREAM_ALARM em 2/15 se o processo morresse).
 */
class RampGainTest {

    @Test
    fun `curva entrega inicio e fim`() {
        assertEquals(0.15f, rampValue(0, 60_000, 0.15f, 1f, "linear"), 0.001f)
        assertEquals(1f, rampValue(60_000, 60_000, 0.15f, 1f, "linear"), 0.001f)
    }

    @Test
    fun `curva nao passa do teto`() {
        assertTrue(rampValue(999_999, 60_000, 0.2f, 1f, "exp") <= 1f)
        assertTrue(rampValue(-5, 60_000, 0.2f, 1f, "step") >= 0f)
    }

    @Test
    fun `curva exponencial sobe devagar`() {
        val meio = rampValue(30_000, 60_000, 0f, 1f, "exp")
        assertTrue("meio da rampa exp deveria estar abaixo de 50%", meio < 0.5f)
    }
}
