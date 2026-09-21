package com.pakarai.alarme.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A rampa de volume é a única coisa que impede o alarme de explodir de manhã. */
class RampControllerTest {

    @Test
    fun `curva linear eh identidade`() {
        assertEquals(0f, curveProgress(0f, "linear"))
        assertEquals(0.5f, curveProgress(0.5f, "linear"))
        assertEquals(1f, curveProgress(1f, "linear"))
    }

    @Test
    fun `curva exp comeca devagar e explode no fim`() {
        assertEquals(0f, curveProgress(0f, "exp"))
        assertEquals(0.25f, curveProgress(0.5f, "exp"), 1e-6f)
        assertEquals(1f, curveProgress(1f, "exp"))
        // no meio da rampa ainda é baixo em relação ao linear
        assertTrue(curveProgress(0.5f, "exp") < curveProgress(0.5f, "linear"))
    }

    @Test
    fun `curva step sobe em 5 degraus`() {
        assertEquals(0f, curveProgress(0f, "step"))
        assertEquals(0.4f, curveProgress(0.5f, "step"))
        assertEquals(0.8f, curveProgress(0.99f, "step"))
        assertEquals(1f, curveProgress(1f, "step"))
    }

    @Test
    fun `rampValue respeita teto e nunca estoura`() {
        // 0ms -> volume inicial; 2x o tempo -> teto
        assertEquals(0.1f, rampValue(0, 10_000, 0.1f, 1f, "linear"), 1e-6f)
        assertEquals(0.55f, rampValue(5_000, 10_000, 0.1f, 1f, "linear"), 1e-4f)
        assertEquals(1f, rampValue(25_000, 10_000, 0.1f, 1f, "linear"), 1e-4f)
        // nunca pede mais que o teto nem menos que o inicial
        repeat(500) { i ->
            val v = rampValue(i * 100L, 45_000, 0.15f, 1f, "exp")
            assertTrue("volume $v fora do intervalo", v in 0.15f..1f)
        }
    }

    @Test
    fun `volumePercent converte fracao em percentual de device`() {
        assertEquals(0, volumePercent(0f))
        assertEquals(15, volumePercent(0.15f))
        assertEquals(50, volumePercent(0.5f))
        assertEquals(100, volumePercent(1f))
        // clamp: nunca sai de 0..100
        assertEquals(0, volumePercent(-0.5f))
        assertEquals(100, volumePercent(2f))
    }
}