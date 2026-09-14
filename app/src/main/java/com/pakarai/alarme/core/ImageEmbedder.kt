package com.pakarai.alarme.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Reconhecimento offline de objeto cadastrado.
 *
 * Usa o MobileNetV2 (TFLite, em assets) como extrator de descritor: qualquer
 * foto vira um vetor normalizado (logits da classificação). Compara com o
 * vetor da foto de referência por cosseno — mesma coisa = ângulo pequeno.
 *
 * Tudo roda no aparelho, sem rede nem conta.
 */
object ImageEmbedder {

    private const val MODEL_ASSET = "mobilenet_v2.tflite"
    private const val SIZE = 224
    private const val OUTPUT_DIM = 1000

    /**
     * Quanto maior, mais rígido.
     * Diminuído pra série de 0.78: logits de classificação são descritores ruins pra
     * o MESMO objeto em ângulo/luz/zoom diferente, então o teste rígido rejeitava
     * até a própria escova cadastrada. Junto com [embedViews] (multi-visão) e o
     * melhor-casamento entre as vistas, 0.5 ainda barra fotos de objetos diferentes
     * (classes distintas ficam bem mais longe, ~0.2-0.4).
     */
    const val MATCH_THRESHOLD = 0.5f

    @Volatile
    private var interpreter: org.tensorflow.lite.Interpreter? = null

    fun ensureLoaded(context: Context): Boolean = try {
        if (interpreter == null) {
            synchronized(this) {
                if (interpreter == null) {
                    val bytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
                    val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
                    buf.put(bytes)
                    buf.rewind()
                    interpreter = org.tensorflow.lite.Interpreter(buf)
                }
            }
        }
        interpreter != null
    } catch (_: Exception) {
        false
    }

    /** Descritor de uma foto em disco, ou null se falhou. */
    fun embed(imageFile: File): FloatArray? {
        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath) ?: return null
        return embed(bitmap)
    }

    /** Descritor de um bitmap, ou null se falhou. */
    fun embed(bitmap: Bitmap): FloatArray? {
        val interp = interpreter ?: return null
        return try {
            val scaled = centerCropTo(bitmap, SIZE)
            // Modelo espera NCHW [1, 3, 224, 224]: planos de canal separados,
            // não RGB interleaved por pixel.
            val input = ByteBuffer.allocateDirect(3 * SIZE * SIZE * 4)
                .order(ByteOrder.nativeOrder())
            val pixels = IntArray(SIZE * SIZE)
            scaled.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
            var c = 0
            while (c < 3) {
                var i = 0
                val shift = when (c) { 0 -> 16; 1 -> 8; else -> 0 }
                while (i < pixels.size) {
                    input.putFloat(((pixels[i] shr shift) and 0xFF) / 255f)
                    i++
                }
                c++
            }
            input.rewind()
            val outArray = Array(1) { FloatArray(OUTPUT_DIM) }
            interp.run(input, outArray)
            val vec = outArray[0]
            val norm = normSquared(vec).let { sqrt(it) }
            if (norm <= 0f) return null
            var j = 0
            while (j < vec.size) {
                vec[j] /= norm
                j++
            }
            vec
        } catch (_: Exception) {
            null
        }
    }

    /** Semelhança por cosseno (vetores já normalizados = produto escalar). */
    fun similarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        val n = minOf(a.size, b.size)
        var i = 0
        while (i < n) {
            dot += a[i] * b[i]
            i++
        }
        return dot
    }

    fun matches(reference: FloatArray, query: FloatArray): Boolean =
        similarity(reference, query) >= MATCH_THRESHOLD

    /**
     * Várias vistas de uma foto viram vetores: normal, espelhada e zooms.
     * Comparar "melhor vista vs melhor vista" segura o mesmo objeto mesmo se a
     * foto da hora tiver ângulo/luz/zoom um pouco diferente da cadastrada.
     */
    fun embedViews(imageFile: File): List<FloatArray> {
        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath) ?: return emptyList()
        val embeddings = ArrayList<FloatArray>(6)
        embed(bitmap)?.let { embeddings += it }
        val mirrored = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postScale(-1f, 1f) }, true)
        embed(mirrored)?.let { embeddings += it }
        val side = minOf(bitmap.width, bitmap.height)
        val zoom = (side * 0.15f).toInt()
        val crops = listOf(
            Bitmap.createBitmap(bitmap, zoom, zoom, side - 2 * zoom, side - 2 * zoom),
            Bitmap.createBitmap(bitmap, 0, 0, side, side),
            Bitmap.createBitmap(bitmap, bitmap.width - side, bitmap.height - side, side, side)
        )
        crops.forEach { c ->
            embed(c)?.let { embeddings += it }
            embed(Bitmap.createBitmap(c, 0, 0, c.width, c.height, Matrix().apply { postScale(-1f, 1f) }, true))?.let { embeddings += it }
        }
        return embeddings
    }

    /** Melhor cosseno entre qualquer vista de referência e qualquer vista da foto nova. */
    fun bestSimilarity(referenceViews: List<FloatArray>, queryViews: List<FloatArray>): Float {
        if (referenceViews.isEmpty() || queryViews.isEmpty()) return 0f
        var best = 0f
        referenceViews.forEach { r ->
            queryViews.forEach { q ->
                val s = similarity(r, q)
                if (s > best) best = s
            }
        }
        return best
    }

    fun matchesViews(referenceViews: List<FloatArray>, queryViews: List<FloatArray>): Boolean =
        bestSimilarity(referenceViews, queryViews) >= MATCH_THRESHOLD ||
            centroidSimilarity(referenceViews, queryViews) >= MATCH_THRESHOLD

    /**
     * Vetor médio das vistas — a soma de vetores normalizados tem magnitude menor,
     * mas a direção fica estável contra ruído de ângulo/luz. Comparar o centroide
     * da referência com o centroide da foto da hora ≈ média dos casamentos por par:
     * objeto igual mantém o cosseno alto; objetos diferentes continuam longe (a
     * média de cossenos baixos continua baixa).
     */
    fun meanEmbedding(views: List<FloatArray>): FloatArray? {
        if (views.isEmpty()) return null
        val out = FloatArray(views[0].size)
        views.forEach { v ->
            val n = minOf(out.size, v.size)
            var i = 0
            while (i < n) {
                out[i] += v[i]
                i++
            }
        }
        var i = 0
        while (i < out.size) {
            out[i] /= views.size
            i++
        }
        return out
    }

    fun centroidSimilarity(referenceViews: List<FloatArray>, queryViews: List<FloatArray>): Float {
        val r = meanEmbedding(referenceViews) ?: return 0f
        val q = meanEmbedding(queryViews) ?: return 0f
        return similarity(r, q)
    }

    private fun normSquared(v: FloatArray): Float {
        var acc = 0f
        v.forEach { acc += it * it }
        return acc
    }

    /** Crop central quadrado + resize, sem distorcer nada. */
    private fun centerCropTo(src: Bitmap, size: Int): Bitmap {
        val w = src.width
        val h = src.height
        val side = minOf(w, h)
        val left = (w - side) / 2
        val top = (h - side) / 2
        val cropped = Bitmap.createBitmap(src, left, top, side, side)
        return Bitmap.createScaledBitmap(cropped, size, size, true)
    }
}