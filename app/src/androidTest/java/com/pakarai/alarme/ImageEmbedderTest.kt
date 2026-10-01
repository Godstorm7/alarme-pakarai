package com.pakarai.alarme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pakarai.alarme.core.ImageEmbedder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Valida o motor de reconhecimento offline no device real
 * (carrega o MobileNetV2 e compara embeds por cosseno).
 */
@RunWith(AndroidJUnit4::class)
class ImageEmbedderTest {

    private val ctx: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun mesmaFotoPassa() {
        assertTrue("modelo deve carregar", ImageEmbedder.ensureLoaded(ctx))
        val file = jpegFile("same_a", 12345L)
        val e = ImageEmbedder.embed(file)
        assertNotNull("embed valido", e)
        val sim = ImageEmbedder.similarity(e!!, e)
        println("SIM(mesma foto)=$sim")
        assertTrue("mesma foto → match (sim=$sim)", sim >= ImageEmbedder.MATCH_THRESHOLD)
    }

    @Test
    fun fotoDiferenteFalha() {
        assertTrue("modelo deve carregar", ImageEmbedder.ensureLoaded(ctx))
        val a = ImageEmbedder.embed(sceneFile("sun", ::sunScene))
        val b = ImageEmbedder.embed(sceneFile("stripes", ::stripesScene))
        assertNotNull("embed a", a)
        assertNotNull("embed b", b)
        println("A[0..7]=" + a!!.take(8).joinToString())
        println("B[0..7]=" + b!!.take(8).joinToString())
        val sim = ImageEmbedder.similarity(a, b)
        println("SIM(fotos diferentes)=$sim")
        assertFalse("fotos diferentes → não match (sim=$sim)", ImageEmbedder.matches(a, b))
    }

    /**
     * Invariante do "% ao vivo" contra o motor de verdade: o placar que a tela mostra
     * (referência completa x SÓ a vista normal do frame) tem de ser <= o veredito do
     * botão (referência x 8 vistas da foto). Se a tela passar do botão, ela promete
     * acerto onde o botão recusa, e o usuário enquadra até bater na parede.
     *
     * Aqui roda no device com o MobileNetV2 de verdade, não com vetores de teste.
     */
    @Test
    fun placarAoVivoNuncaPassaDoBotao() {
        assertTrue("modelo deve carregar", ImageEmbedder.ensureLoaded(ctx))
        val ref = ImageEmbedder.embedViews(sceneFile("ghost_ref", ::sunScene))
        // o "frame" da câmera entra pela MESMA porta que o analisador usa: um bitmap
        // solto (o PhotoCapture já entrega rotacionado e reduzido)
        val frameBmp = BitmapFactory.decodeFile(sceneFile("ghost_frame", ::sunScene).absolutePath)
        assertNotNull("frame decodificado", frameBmp)
        val query = ImageEmbedder.embedViews(frameBmp!!)
        assertTrue("referencia e query nao vazias", ref.isNotEmpty() && query.isNotEmpty())

        val placarDaTela = ImageEmbedder.bestSimilarity(ref, listOf(ImageEmbedder.embed(frameBmp)!!))
        val veredito = ImageEmbedder.matchScore(ref, query)
        println("PLACAR(tela)=$placarDaTela  VEREDITO(botao)=$veredito")
        assertTrue(
            "placar da tela ($placarDaTela) nao pode passar do botao ($veredito)",
            placarDaTela <= veredito + 1e-5f
        )
    }

    /** matchScore tem de bater com a condicao antiga (max entre melhor par e centroide). */
    @Test
    fun matchScoreEOMesmoVereditoDeMatchViews() {
        assertTrue("modelo deve carregar", ImageEmbedder.ensureLoaded(ctx))
        val a = ImageEmbedder.embedViews(sceneFile("ms_a", ::sunScene))
        val b = ImageEmbedder.embedViews(sceneFile("ms_b", ::stripesScene))
        val score = ImageEmbedder.matchScore(a, b)
        println("MATCHSCORE(sun vs listras)=$score")
        assertEquals(ImageEmbedder.matchesViews(a, b), score >= ImageEmbedder.MATCH_THRESHOLD)
    }

    private fun jpegFile(name: String, seed: Long): File {
        val bmp = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
        for (y in 0 until 224) {
            for (x in 0 until 224) {
                val v = (((x * 7 + y * 13 + seed) % 256).toInt())
                bmp.setPixel(
                    x, y,
                    (0xFF shl 24) or (v shl 16) or ((255 - v) shl 8) or ((v / 2 + 60) and 0xFF)
                )
            }
        }
        return File(ctx.cacheDir, "$name.jpg").also { f ->
            f.outputStream().use { os -> bmp.compress(Bitmap.CompressFormat.JPEG, 90, os) }
        }
    }

    private fun solidFile(name: String, color: Int): File {
        val bmp = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        return File(ctx.cacheDir, "$name.jpg").also { f ->
            f.outputStream().use { os -> bmp.compress(Bitmap.CompressFormat.JPEG, 90, os) }
        }
    }

    private fun sceneFile(name: String, draw: (android.graphics.Canvas) -> Unit): File {
        val bmp = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        draw(canvas)
        return File(ctx.cacheDir, "$name.jpg").also { f ->
            f.outputStream().use { os -> bmp.compress(Bitmap.CompressFormat.JPEG, 90, os) }
        }
    }

    private fun sunScene(c: android.graphics.Canvas) {
        c.drawColor(0xFF87CEEB.toInt())
        val p = android.graphics.Paint()
        p.color = 0xFFFFF200.toInt()
        c.drawCircle(112f, 112f, 70f, p)
        p.color = 0xFF228B22.toInt()
        c.drawRect(60f, 120f, 160f, 224f, p)
    }

    private fun stripesScene(c: android.graphics.Canvas) {
        c.drawColor(0xFFFFFFFF.toInt())
        val p = android.graphics.Paint()
        p.color = 0xFF000000.toInt()
        var x = -10f
        while (x < 260f) {
            c.drawRect(x, 0f, x + 28f, 224f, p)
            x += 56f
        }
        p.color = 0xFF1E90FF.toInt()
        c.drawRect(140f, 40f, 190f, 120f, p)
    }
}