package com.pakarai.alarme.service

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock

/**
 * Volume progressivo CONFIGURÁVEL + policiamento.
 *
 * Trabalha direto no canal de ALARME (não toca no de mídia):
 *  - ramp: fração sobe de [initial] até [peak] em [rampMs], seguindo a curva;
 *  - police: a cada tick re-aplica o volume desejado — se o usuário tentar
 *    abaixar a sirene no shade, ela volta na hora.
 * Ao parar, restaura o volume original do canal.
 */
class RampController(
    context: Context,
    private val initialFraction: Float,
    private val peakFraction: Float,
    private val rampMs: Int,
    private val curve: String,
    private val police: Boolean,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
    private val originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)

    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread {
            val start = SystemClock.uptimeMillis()
            // arranca já no volume inicial (baixo)
            applyVolume(initialFraction)
            while (running) {
                val elapsed = SystemClock.uptimeMillis() - start
                applyVolume(rampValue(elapsed, rampMs, initialFraction, peakFraction, curve))
                try {
                    Thread.sleep(TICK_MS)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }.also { it.isDaemon = true; it.start() }
    }

    fun stop() {
        running = false
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0)
        } catch (_: Exception) {
        }
        thread?.interrupt()
    }

    private fun applyVolume(fraction: Float) {
        val target = (max * fraction).toInt().coerceIn(0, max)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        if (police || current < target) {
            if (current != target) {
                try {
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
                } catch (_: Exception) {
                }
            }
        }
    }

    companion object {
        private const val TICK_MS = 100L
        private const val STEPS = 5
    }
}

/** Fração do volume alvo em [elapsedMs], com a curva aplicada entre [initial] e [peak]. */
fun rampValue(elapsedMs: Long, rampMs: Int, initial: Float, peak: Float, curve: String): Float {
    val p = (elapsedMs.toFloat() / rampMs.coerceAtLeast(1)).coerceIn(0f, 1f)
    return initial + (peak - initial) * curveProgress(p, curve)
}

/** Deve começar em 0 e terminar em 1, com o formato de cada curva no meio. */
fun curveProgress(p: Float, curve: String): Float = when (curve) {
    "linear" -> p
    "step" -> ((p * 5).toInt().toFloat() / 5)
    else -> p * p // exp / padrão: devagar no começo, explode no fim
}

/** Percentual de volume de device (0..100) a partir de uma fração (0..1). */
fun volumePercent(fraction: Float): Int = (fraction.coerceIn(0f, 1f) * 100).toInt().coerceIn(0, 100)