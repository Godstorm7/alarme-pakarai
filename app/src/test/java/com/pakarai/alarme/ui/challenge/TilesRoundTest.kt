package com.pakarai.alarme.ui.challenge

import org.junit.Assert.assertEquals
import org.junit.Test

class TilesRoundTest {

    @Test
    fun `dificuldade fica entre 3 e 7`() {
        assertEquals(3, tilesTarget(0))
        assertEquals(3, tilesTarget(3))
        assertEquals(5, tilesTarget(5))
        assertEquals(7, tilesTarget(7))
        assertEquals(7, tilesTarget(99))
    }

    @Test
    fun `nunca acende o tabuleiro inteiro`() {
        assertEquals(3, tilesTarget(7, cells = 4))
        assertEquals(7, tilesTarget(7, cells = 8))
        assertEquals(7, tilesTarget(7, cells = 16))
    }

    @Test
    fun `memorize em segundos arredonda pra cima e no minimo 1`() {
        assertEquals(1, memorizeSeconds(0))
        assertEquals(1, memorizeSeconds(1))
        assertEquals(3, memorizeSeconds(3000))
        assertEquals(5, memorizeSeconds(4500))
        assertEquals(8, memorizeSeconds(8000))
    }
}
