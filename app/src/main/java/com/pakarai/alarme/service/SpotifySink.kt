package com.pakarai.alarme.service

import android.media.AudioManager
import android.os.SystemClock
import com.pakarai.alarme.spotify.SpotifyClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Configuração da rampa de volume do Spotify (percentual do device, via Web API). */
data class SpotifyRamp(
    val initialFraction: Float,
    val peakFraction: Float,
    val rampMs: Int,
    val curve: String,
)

/**
 * Toca uma fonte do Spotify (faixa/álbum/artista/playlist) via Web API.
 *
 * Realidade: o áudio sai do app do Spotify no canal de MÚSICA, então a rampa
 * do canal de alarme não controla ele. Se [ramp] veio preenchido, o volume do
 * device é reaplicado pela Web API a cada tick (setVolume), subindo do volume
 * inicial até o pico na mesma matemática das sirenes locais.
 *
 * Rede de segurança: se o play falhar na hora OU 15s depois nada estiver
 * tocando de fato (sem device ativo, sem Premium, token com escopo errado…),
 * dispara uma [fallback] (sirene local). Ninguém diz que desligou sem som.
 */
class SpotifySink(
    private val client: SpotifyClient,
    private val uri: String,
    private val fallback: SoundSink,
    private val confirmMs: Long = CONFIRM_MS,
    private val ramp: SpotifyRamp? = null,
    private val onFallback: (() -> Unit)? = null,
    /** Só pra rampa local no canal de mídia quando o device não aceita volume pela API. */
    private val context: android.content.Context? = null,
) : SoundSink {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var released = false
    @Volatile private var fallbackPlaying = false
    @Volatile private var useApiVolume = true
    private var rampJob: Job? = null
    private var localRamp: RampController? = null

    override fun play(previewVolume: Float?) {
        if (released) return
        val r = ramp
        scope.launch {
            val started = startOnDevice()
            // fallback imediato: nem conseguiu mandar o play (sem device, sem token…)
            if (started != true) {
                playFallback()
                return@launch
            }
            // rampa pela Web API só quando o device suporta; senão já subiu local
            if (r != null && useApiVolume) rampJob = launchRamp(r)
            // fallback defensivo: o play "deu OK" mas 15s depois nada toca
            delay(confirmMs)
            if (released) return@launch
            if (client.isPlaying() != true) {
                stopRamp()
                playFallback()
            }
        }
    }

    override fun pause() {
        stopRamp()
        stopLocalRamp()
        scope.launch { runCatching { client.pause() } }
    }

    override fun resume() {
        val r = ramp
        scope.launch {
            runCatching { client.resumePlay() }
            if (r != null) {
                if (useApiVolume) rampJob = launchRamp(r) else startLocalRamp(r)
            }
        }
    }

    override fun stop() {
        stopRamp()
        stopLocalRamp()
        stopFallback()
        scope.launch { runCatching { client.pause() } }
    }

    override fun release() {
        stopRamp()
        stopLocalRamp()
        released = true
        scope.cancel()
        stopFallback()
        runCatching { fallback.release() }
        // fire-and-forget: pausa o Spotify já que o scope principal foi encerrado
        CoroutineScope(Dispatchers.IO).launch { runCatching { client.pause() } }
    }

    /**
     * Garante um device ativo e manda o play. Retorna:
     *   true  — play aceito de verdade (ainda confirma tocar depois)
     *   false — falhou: cai no fallback
     */
    private suspend fun startOnDevice(): Boolean {
        val devices = runCatching { client.devices() }.getOrDefault(emptyList())
        val device = devices.firstOrNull { it.isActive && !it.restricted }
            ?: devices.firstOrNull { !it.restricted }
        if (device != null) {
            if (!runCatching { client.transferTo(device.id) }.getOrDefault(false)) {
                // transfer falhou (ex.: device restrito) — sem device não toca
                return false
            }
            val r = ramp
            useApiVolume = device.supportsVolume
            when {
                r == null -> runCatching { client.setVolume(100) }
                useApiVolume -> runCatching { client.setVolume(volumePercent(r.initialFraction)) }
                // device não aceita volume pela API: rampa no canal de MÚSICA (global,
                // restaurado ao parar) — é o único jeito de o som subir de verdade
                else -> startLocalRamp(r)
            }
        }
        return runCatching { client.play(uri) }.getOrDefault(false)
    }

    /** Reaplica o volume desejado no device via Web API, seguindo a curva. */
    private fun launchRamp(r: SpotifyRamp): Job = scope.launch {
        val start = SystemClock.uptimeMillis()
        while (!released && !fallbackPlaying) {
            delay(RAMP_TICK_MS)
            if (released || fallbackPlaying) return@launch
            val elapsed = SystemClock.uptimeMillis() - start
            val fraction = rampValue(elapsed, r.rampMs, r.initialFraction, r.peakFraction, r.curve)
            runCatching { client.setVolume(volumePercent(fraction)) }
        }
    }

    /** Rampa no canal de MÚSICA (fallback quando a API de volume não é suportada). */
    private fun startLocalRamp(r: SpotifyRamp) {
        val ctx = context ?: return
        if (localRamp != null) return
        val controller = RampController(
            ctx,
            r.initialFraction,
            r.peakFraction,
            r.rampMs,
            r.curve,
            police = true,
            stream = AudioManager.STREAM_MUSIC,
        )
        localRamp = controller
        controller.start()
    }

    private fun stopLocalRamp() {
        localRamp?.stop()
        localRamp = null
    }

    private fun stopRamp() {
        rampJob?.cancel()
        rampJob = null
    }

    private fun playFallback() {
        if (released || fallbackPlaying) return
        stopRamp()
        fallbackPlaying = true
        onFallback?.invoke()
        runCatching { fallback.play() }
    }

    private fun stopFallback() {
        if (!fallbackPlaying) return
        fallbackPlaying = false
        runCatching { fallback.stop() }
    }

    companion object {
        const val CONFIRM_MS = 15_000L
        private const val RAMP_TICK_MS = 200L
    }
}