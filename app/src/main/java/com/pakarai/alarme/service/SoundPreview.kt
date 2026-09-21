package com.pakarai.alarme.service

import android.content.Context
import com.pakarai.alarme.AppScope

/**
 * Prévia curta dos sons no editor. Sempre para a anterior antes de tocar.
 * Usa canal de ALARME com volume reduzido — não interfere no alarme real.
 */
object SoundPreview {

    private var current: SoundSink? = null
    private var rampController: RampController? = null
    private var demoTimer: Thread? = null

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
     * no alarme: a sirene local sobe do volume inicial ao pico na curva
     * escolhida (comprimida em ~6s de prévia). Ringtone toca na rampa do canal
     * de alarme; Spotify toca de verdade com a rampa pela Web API. Para sozinha.
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
        // qualquer rampa configurada (1 min, 5 min…) é comprimida nos ~6s da demo;
        // Instantâneo crava no teto quase na hora.
        val demoRampMs = if (rampMs <= 0) 1 else RAMP_DEMO_MS.toInt()
        val sink: SoundSink = when {
            kind == "spotify" && spotifyUri.isNotBlank() -> SpotifySink(
                AppScope.spotifyClient,
                spotifyUri,
                fallbackSinkFor(context, fallbackKind, fallbackUri),
                ramp = SpotifyRamp(volumeInitial, volumePeak, demoRampMs, curve),
            )
            kind == "ringtone" && ringtoneUri.isNotBlank() -> RingtoneSink(context, ringtoneUri)
            else -> createSynthSink(context, kind)
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
                Thread.sleep(RAMP_DEMO_MS)
                stop()
            } catch (_: InterruptedException) {
            }
        }.also { it.isDaemon = true; it.start() }
    }

    fun stop() {
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
}