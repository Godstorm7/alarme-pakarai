package com.pakarai.alarme.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O teto do toque só vale se o watchdog olhar pra ele de perto.
 *
 * Regressão real: com o tick de 9 min, uma janela de 30 min só era detectada no
 * quarto tick — o alarme tocou 36 min (e o histórico dizia "desligado").
 */
class RingWindowTest {

    @Test
    fun `watchdog cabe muitas vezes na janela`() {
        val ticks = RING_WINDOW_MS / RING_WATCHDOG_MS
        assertTrue("tick de $RING_WATCHDOG_MS ms na janela de $RING_WINDOW_MS ms", ticks >= 20)
    }

    @Test
    fun `janela e divisivel pelo tick`() {
        assertEquals(0L, RING_WINDOW_MS % RING_WATCHDOG_MS)
    }

    @Test
    fun `atraso maximo entre estouro e parada fica abaixo de meio minuto`() {
        assertTrue(RING_WATCHDOG_MS <= 30_000L)
    }
}
