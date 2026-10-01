package com.pakarai.alarme.ui.challenge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectRoundTest {

    @Test
    fun `aguenta as tres primeiras fotos erradas`() {
        assertFalse(shouldFallbackToMath(0))
        assertFalse(shouldFallbackToMath(1))
        assertFalse(shouldFallbackToMath(2))
    }

    @Test
    fun `vira conta na terceira falha, e nao antes`() {
        assertTrue(shouldFallbackToMath(OBJECT_FALLBACK_AFTER))
        assertTrue(shouldFallbackToMath(OBJECT_FALLBACK_AFTER + 1))
    }

    @Test
    fun `placar arredonda pra inteiro e trava em 0 e 100`() {
        assertEquals(0, similarityPercent(0f))
        assertEquals(82, similarityPercent(0.8299f))
        assertEquals(100, similarityPercent(1f))
        assertEquals(100, similarityPercent(1.4f))
        assertEquals(0, similarityPercent(-0.2f))
    }

    @Test
    fun `limiar aparece como 60 por cento`() {
        assertEquals(60, thresholdPercent(0.6f))
    }
}