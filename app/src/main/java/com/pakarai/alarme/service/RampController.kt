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
                val p = (elapsed.toFloat() / rampMs.coerceAtLeast(1)).coerceIn(0f, 1f)
                applyVolume(initialFraction + (peakFraction - initialFraction) * curveValue(p))
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

    private fun curveValue(p: Float): Float = when (curve) {
        "linear" -> p
        "step" -> ((p * STEPS).toInt().toFloat() / STEPS)
        else -> p * p // exp / padrão: devagar no começo, explode no fim
    }

    companion object {
        private const val TICK_MS = 100L
        private const val STEPS = 5
    }
}