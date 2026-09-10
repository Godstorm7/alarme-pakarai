package com.pakarai.alarme.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

    /** Quanto maior, mais rígido. 0.78 dá folga pra ângulo e luz, sem aceitar qualquer coisa. */
    const val MATCH_THRESHOLD = 0.78f

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