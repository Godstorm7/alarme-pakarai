package com.pakarai.alarme.service

import android.content.Context
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.core.openSpotifyApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Prévia curta dos sons no editor. Sempre para a anterior antes de tocar.
 * Usa canal de ALARME com volume reduzido — não interfere no alarme real.
 */
object SoundPreview {

    private var current: SoundSink? = null
    private var rampController: RampController? = null
    private var demoTimer: Thread? = null

    /** O que está tocando na demo (pra UI dar feedback). null = nada. */
    private val _demoStatus = MutableStateFlow<String?>(null)
    val demoStatus: StateFlow<String?> = _demoStatus.asStateFlow()

    fun playSiren(context: Context, kind: String) {
        stop()
        val sink = createSynthSink(context, kind)
        current = sink
        sink.play(previewVolume = 0.6f)
    }

    fun playRingtone(context: Context, uri: String) {
        stop()
        if (uri.isBlank()) return
        try {
            val sink = RingtoneSink(context, uri)
            current = sink
            sink.play(previewVolume = 0.6f)
        } catch (_: Exception) {
        }
    }

    /**
     * Demonstra a rampa de volume configurada usando o som que está definido
     * no alarme: sirene local sobe do volume inicial ao pico na curva escolhida
     * (comprimida em ~6s de prévia; Spotify ganha ~12s pra dar tempo de começar).
     * Ringtone toca na rampa do canal; Spotify toca de verdade com a rampa pela
     * Web API. Para sozinha.
     */
    fun playRampDemo(
        context: Context,
        kind: String,
        ringtoneUri: String,
        spotifyUri: String,
        fallbackKind: String,
        fallbackUri: String,
        volumeInitial: Float,
        volumePeak: Float,
        rampMs: Int,
        curve: String,
    ) {
        stop()
        val isSpotify = kind == "spotify"
        val demoMs = if (isSpotify) RAMP_DEMO_SPOTIFY_MS else RAMP_DEMO_MS
        val demoRampMs = if (rampMs <= 0) 1 else demoMs.toInt()
        val fbLabel = fallbackLabel(fallbackKind, fallbackUri)

        val sink: SoundSink = when {
            isSpotify && spotifyUri.isNotBlank() -> {
                // abre o Spotify: sem device ativo a Web API não toca
                val opened = openSpotifyApp(context)
                _demoStatus.value = if (opened) "Tocando Spotify…" else "Spotify não instalado — tocando o reserva: $fbLabel"
                SpotifySink(
                    AppScope.spotifyClient,
                    spotifyUri,
                    fallbackSinkFor(context, fallbackKind, fallbackUri),
                    ramp = SpotifyRamp(volumeInitial, volumePeak, demoRampMs, curve),
                    onFallback = {
                        _demoStatus.value =
                            "Spotify não tocou (precisa do app aberto + Premium) — tocando o reserva: $fbLabel"
                    },
                    context = context,
                )
            }
            isSpotify -> {
                _demoStatus.value = "Sem faixa escolhida — tocando o reserva: $fbLabel"
                fallbackSinkFor(context, fallbackKind, fallbackUri)
            }
            kind == "ringtone" && ringtoneUri.isNotBlank() -> {
                _demoStatus.value = "Tocando o som do sistema…"
                RingtoneSink(context, ringtoneUri)
            }
            else -> {
                _demoStatus.value = "Tocando ${soundLabel(kind)}…"
                createSynthSink(context, kind)
            }
        }
        current = sink
        // ringtone: volume interno do player cheio, quem sobe é o canal (a rampa);
        // os demais já baixam internamente e a rampa refine por cima.
        sink.play(previewVolume = if (kind == "ringtone") 1f else 0.6f)
        val controller = RampController(
            context,
            volumeInitial,
            volumePeak,
            demoRampMs,
            curve,
            police = false,
        )
        rampController = controller
        controller.start()
        demoTimer = Thread {
            try {
                Thread.sleep(demoMs)
                stop()
            } catch (_: InterruptedException) {
            }
        }.also { it.isDaemon = true; it.start() }
    }

    fun stop() {
        _demoStatus.value = null
        demoTimer?.interrupt()
        demoTimer = null
        val rc = rampController
        rampController = null
        try {
            rc?.stop()
        } catch (_: Exception) {
        }
        val s = current ?: return
        current = null
        try {
            s.stop()
        } catch (_: Exception) {
        }
        try {
            s.release()
        } catch (_: Exception) {
        }
    }

    private const val RAMP_DEMO_MS = 6_000L
    private const val RAMP_DEMO_SPOTIFY_MS = 12_000L
}
