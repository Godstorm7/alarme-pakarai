package com.pakarai.alarme.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageEmbedderMathTest {

    @Test
    fun centroideDeVetorRepetidoEMesmoVetor() {
        val v = floatArrayOf(1f, 0f)
        val c = ImageEmbedder.meanEmbedding(listOf(v, v, v))!!
        assertEquals(1f, c[0], 1e-6f)
        assertEquals(0f, c[1], 1e-6f)
    }

    @Test
    fun centroideDeDoisVetoresEUmaMedia() {
        val c = ImageEmbedder.meanEmbedding(listOf(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)))!!
        assertEquals(0.5f, c[0], 1e-6f)
        assertEquals(0.5f, c[1], 1e-6f)
    }

    @Test
    fun listaVaziaRetornaNull() {
        assertNull(ImageEmbedder.meanEmbedding(emptyList()))
    }

    @Test
    fun centroideDeMesmoObjetoTemCossenoAlto() {
        val ref = listOf(floatArrayOf(0.8f, 0.6f), floatArrayOf(0.7f, 0.7f))
        val query = listOf(floatArrayOf(0.6f, 0.8f), floatArrayOf(0.75f, 0.66f))
        assertTrue(ImageEmbedder.centroidSimilarity(ref, query) >= 0.9f)
    }
}