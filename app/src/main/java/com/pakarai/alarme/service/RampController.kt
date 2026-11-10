package com.pakarai.alarme.service

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock

/**
 * Volume progressivo CONFIGURÁVEL + policiamento.
 *
 * Modo preferido: [gain] != null — a rampa vira ganho do próprio track de áudio
 * (AudioTrack.setVolume / MediaPlayer.setVolume) e o canal do sistema NUNCA é
 * escrito. Só se o canal estiver MUDO ele é levantado, porque aí o ganho não
 * resolve nada. É o que evita o aparelho acordar com o alarme em 2/15 depois de
 * um toque interrompido.
 *
 * Modo legado: [gain] == null (o Spotify, cujo áudio não é nosso) — aí sim
 * trabalha direto no canal:
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
    /** Canal controlado: ALARM pros sons locais; MUSIC quando é o Spotify. */
    private val stream: Int = AudioManager.STREAM_ALARM,
    /** Ganho do track (0..1). Quando presente, o canal do sistema não é escrito. */
    private val gain: ((Float) -> Unit)? = null,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val max = audioManager.getStreamMaxVolume(stream)
    private val originalVolume = audioManager.getStreamVolume(stream)

    @Volatile private var running = false
    private var thread: Thread? = null
    /** true quando achamos o canal mudo e levantamos (precisa restaurar no stop). */
    private var liftedMutedStream = false

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
        if (gain == null || liftedMutedStream) {
            try {
                audioManager.setStreamVolume(stream, originalVolume, 0)
            } catch (_: Exception) {
            }
        }
        thread?.interrupt()
    }

    private fun applyVolume(fraction: Float) {
        if (gain != null) {
            try {
                gain(fraction.coerceIn(0f, 1f))
            } catch (_: Exception) {
            }
            // Se o canal estiver mudo, ganho nenhum salva: levanta uma vez e
            // restaura no stop. Não é rampa, é só desmutar.
            if (audioManager.getStreamVolume(stream) == 0) {
                liftedMutedStream = true
                try {
                    audioManager.setStreamVolume(stream, max, 0)
                } catch (_: Exception) {
                }
            }
            return
        }
        val target = (max * fraction).toInt().coerceIn(0, max)
        val current = audioManager.getStreamVolume(stream)
        if (police || current < target) {
            if (current != target) {
                try {
                    audioManager.setStreamVolume(stream, target, 0)
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