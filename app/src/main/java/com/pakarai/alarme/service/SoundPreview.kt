package com.pakarai.alarme.service

import android.content.Context

/**
 * Prévia curta dos sons no editor. Sempre para a anterior antes de tocar.
 * Usa canal de ALARME com volume reduzido — não interfere no alarme real.
 */
object SoundPreview {

    private var current: SoundSink? = null

    fun playSiren(context: Context, kind: String) {
        stop()
        val sink = SirenSink(context, kind)
        current = sink
        sink.play(previewVolume = 0.35f)
    }

    fun playRingtone(context: Context, uri: String) {
        stop()
        if (uri.isBlank()) return
        try {
            val sink = RingtoneSink(context, uri)
            current = sink
            sink.play(previewVolume = 0.5f)
        } catch (_: Exception) {
        }
    }

    fun stop() {
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
}