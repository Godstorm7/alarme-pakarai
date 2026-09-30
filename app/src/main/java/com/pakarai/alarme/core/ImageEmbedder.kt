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

    /**
     * Descritor = camada penúltima (1280-d, pós-pooling global), não os 1000 logits
     * de classificação. Logit é "quanto essa imagem puxa cada classe", e como quase
     * toda foto puxa as mesmas classes de fundo, dois objetos sem relação nenhuma
     * ficam com cosseno alto. A penúltima é o vetor que a rede usa pra comparar
     * imagem com imagem — é o mesmo que a busca por similaridade do Google usa.
     */
    private const val OUTPUT_DIM = 1280

    /**
     * Quanto maior, mais rígido.
     *
     * Medido com as cenas do teste instrumentado (JPEG 90, entrada [-1,1]):
     * mesma foto 1,0 · mesmo objeto espelhado 0,99 · mesmo objeto com zoom 0,87 ·
     * objeto diferente 0,50. O 0.6 fica no meio desse vão: o vetor antigo (1000
     * logits com entrada [0,1]) dava 0,74 até pra "sol" vs "listras", ou seja,
     * praticamente nada era rejeitado.
     *
     * Cuidado: essas cenas são desenhos sintéticos, então o vão real com foto de
     * câmera é outro. O 0.6 é o piso seguro — foto do mesmo objeto que usado pra
     * cadastro o usuário aceitou com 0.5 no vetor antigo, então sobra folga.
     */
    const val MATCH_THRESHOLD = 0.6f

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
            // MobileNetV2 espera NHWC [1, 224, 224, 3] com faixa [-1, 1]
            // (o preprocess_input oficial é x/127.5 - 1). Mandar [0, 1] faz a rede
            // saturar: a saída vira quase constante e qualquer foto bate com
            // qualquer outra — era o que fazia o desafio aceitar objeto errado.
            val input = ByteBuffer.allocateDirect(3 * SIZE * SIZE * 4)
                .order(ByteOrder.nativeOrder())
            val pixels = IntArray(SIZE * SIZE)
            scaled.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
            var i = 0
            while (i < pixels.size) {
                val p = pixels[i]
                input.putFloat(((p shr 16) and 0xFF) / 127.5f - 1f)
                input.putFloat(((p shr 8) and 0xFF) / 127.5f - 1f)
                input.putFloat((p and 0xFF) / 127.5f - 1f)
                i++
            }
            input.rewind()
            // Descriptor e a PRIMEIRA saida do modelo. Se a ordem mudar no asset,
            // o run() estouraria e a foto nunca casaria com nada — melhor falhar
            // aqui do que devolver vetor de logit em silencio.
            val outShape = interp.getOutputTensor(0).shape()
            if (outShape.isEmpty() || outShape[outShape.size - 1] != OUTPUT_DIM) return null
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