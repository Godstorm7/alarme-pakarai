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

    //── matchScore: a tela mostra essa nota, o botão decide com ela ──────────

    @Test
    fun notaDeVistaUnicaIgualAReferenciaEUm() {
        val v = floatArrayOf(0.6f, 0.8f)
        assertEquals(1f, ImageEmbedder.matchScore(listOf(v), listOf(v)), 1e-6f)
    }

    @Test
    fun notaUsaOMaiorEntreMelhorParECentroide() {
        // as duas vistas da referência são muito diferentes entre si, então o centroide
        // afunda mas o melhor par acerta: matchScore tem de pegar o melhor caminho.
        val ref = listOf(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f))
        val query = listOf(floatArrayOf(1f, 0f))
        val expected = ImageEmbedder.bestSimilarity(ref, query)
        assertEquals(expected, ImageEmbedder.matchScore(ref, query), 1e-6f)
        assertTrue(ImageEmbedder.centroidSimilarity(ref, query) < expected)
    }

    @Test
    fun notaDeListaVaziaEZero() {
        assertEquals(0f, ImageEmbedder.matchScore(emptyList(), listOf(floatArrayOf(1f, 0f))), 1e-6f)
        assertEquals(0f, ImageEmbedder.matchScore(listOf(floatArrayOf(1f, 0f)), emptyList()), 1e-6f)
    }

    /**
     * Invariante que sustenta o "% ao vivo" na tela: o placar mostrado (referência
     * completa x SÓ a vista normal do frame) nunca passa do veredito do botão
     * (referência x 8 vistas da foto), porque as 8 vistas incluem a normal.
     * Se isso quebrar, a tela passa a prometer acerto onde o botão vai recusar.
     */
    @Test
    fun placarAoVivoNuncaPassaDoVereditoDoBotao() {
        val ref = listOf(
            floatArrayOf(0.9f, 0.1f),
            floatArrayOf(0.1f, 0.9f),
            floatArrayOf(0.7f, 0.7f)
        )
        val normal = floatArrayOf(0.72f, 0.69f)
        val outrasVistas = listOf(
            floatArrayOf(0.3f, 0.9f),
            floatArrayOf(0.95f, 0.05f),
            floatArrayOf(0.5f, 0.5f)
        )
        val placarDaTela = ImageEmbedder.bestSimilarity(ref, listOf(normal))
        val veredito = ImageEmbedder.matchScore(ref, listOf(normal) + outrasVistas)
        assertTrue(placarDaTela <= veredito + 1e-6f)
    }

    @Test
    fun matchesViewsSegueAPartirDaNota() {
        val ref = listOf(floatArrayOf(0.6f, 0.8f))
        assertTrue(ImageEmbedder.matchesViews(ref, listOf(floatArrayOf(0.6f, 0.8f))))
        // quase perpendicular: cosseno 0,33, abaixo do 0,6. Não usei (0,1): em 2
        // dimensões o ortogonal dá 0,8 e passa do limiar; nos 1280-d reais é que
        // objeto diferente mede ~0,5.
        assertTrue(!ImageEmbedder.matchesViews(ref, listOf(floatArrayOf(0.954f, -0.3f))))
    }
}