package com.pakarai.alarme.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Regra do "AINDA ACORDADO?" configurável: quantas checagens e quando acaba. */
class AckPolicyTest {

    @Test
    fun `uma checagem so encerra no primeiro SIM`() {
        assertNull(nextCheckIndex(1, 1))
    }

    @Test
    fun `tres checagens re-armam ate a terceira`() {
        assertEquals(2, nextCheckIndex(1, 3))
        assertEquals(3, nextCheckIndex(2, 3))
        assertNull(nextCheckIndex(3, 3))
    }

    @Test
    fun `total negativo nao repete pra sempre - para no teto`() {
        // -1 ("sempre") era beco sem saida: agora termina sozinho no teto
        assertEquals(2, nextCheckIndex(1, -1))
        assertEquals(5, nextCheckIndex(4, -1))
        assertNull(nextCheckIndex(5, -1))
        assertNull(nextCheckIndex(99, -1))
    }

    @Test
    fun `total acima do teto tambem e limitado`() {
        assertNull(nextCheckIndex(5, 99))
        assertEquals(5, nextCheckIndex(4, 99))
    }

    @Test
    fun `indice invalido e normalizado pra 1`() {
        assertEquals(2, nextCheckIndex(0, 3))
        assertEquals(2, nextCheckIndex(-5, 3))
        assertNull(nextCheckIndex(0, 1))
    }
}
