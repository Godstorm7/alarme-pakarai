package com.pakarai.alarme.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.data.AlarmEntity

/** Saída de áudio abstrata: a rampa de volume fala com qualquer implementação. */
interface SoundSink {
    fun play(previewVolume: Float? = null)
    /**
     * Ganho proprio do track, 0..1. A rampa usa isto em vez de mexer no volume do
     * canal STREAM_ALARM do aparelho: antes ela derrubava o canal pra 2 (de 15) e,
     * se o processo morresse no meio, o aparelho inteiro ficava com o alarme baixo
     * — inclusive o despertador da Samsung.
     */
    fun setGain(fraction: Float)
    fun pause()
    fun resume()
    fun stop()
    fun release()
}

/**
 * Sirene 100% local, gerada por síntese PCM em runtime (sem assets).
 * Toca no canal de ALARME, independente do volume de mídia.
 *
 * Tipos:
 *  - "siren":   wail clássico (sobe e desce, 500–950 Hz)
 *  - "airhorn": buzina grave contínua com tremolo forte
 *  - "tone":    bip bip agudo 1 kHz
 */
class SirenSink(
    context: Context,
    kind: String,
) : SoundSink {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val sampleRate = 44_100
    private var track: AudioTrack? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    // a prévia mexe no volume do canal de alarme; restauramos o original ao fechar
    private val originalAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
    private var changedAlarmVolume = false

    private val kindParams: KindParams = when (kind) {
        "airhorn" -> KindParams(180f, 240f, 9f, 6f, 0.22f, 0.7f)
        "tone" -> KindParams(950f, 950f, 30f, 0f, 0.5f, 0.7f)
        else -> KindParams(520f, 940f, 3.4f, 5f, 0.5f, 0.7f)
    }

    @Synchronized
    override fun play(previewVolume: Float?) {
        if (running) return
        // No alarme real quem manda no volume é a rampa (RampController): o sink só
        // mexe no canal na PRÉVIA. Assim abaixar o volume no toque não silencia.
        startRender(applyVolume = previewVolume != null, previewVolume)
    }

    private fun startRender(applyVolume: Boolean, previewVolume: Float? = null) {
        running = true
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf.coerceAtLeast(4096) * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        // garante o canal de alarme alto (a rampa refina por cima).
        // previewVolume: valor só pra prévia no editor (não mexe no alarme real).
        if (applyVolume) {
            val target = previewVolume ?: 0.5f
            val newVol = (audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM) * target).toInt()
            if (newVol != audioManager.getStreamVolume(AudioManager.STREAM_ALARM)) {
                changedAlarmVolume = true
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, newVol, 0)
            }
        }
        t.play()
        thread = Thread(::renderLoop).also { it.isDaemon = true; it.start() }
    }

    override fun setGain(fraction: Float) {
        try {
            track?.setVolume(fraction.coerceIn(0f, 1f))
        } catch (_: Exception) {
        }
    }

    @Synchronized
    override fun pause() {
        running = false
        try {
            track?.stop()
        } catch (_: Exception) {
        }
        thread?.interrupt()
    }

    @Synchronized
    override fun resume() {
        if (running) return
        release()
        startRender(applyVolume = false)
    }

    private fun renderLoop() {
        val t = track ?: return
        val chunk = sampleRate / 20 // 50ms por bloco
        val buf = ShortArray(chunk)
        val kp = kindParams
        val step = 1.0 / sampleRate
        // Acumulador de fase em [0,1). Antes a fase era calculada como freq*t0:
        // com t0 crescendo em float32 a precisão degrada (o seno começa a "tremer")
        // e a cada mudança de frequência a fase dava um salto. Era isso que virava
        // chiado/estalo depois de alguns segundos de alarme.
        var phase = 0.0
        var time = 0.0
        // rampa de 12 ms nas bordas do pulso (o corte seco estalava)
        val rampS = 0.012
        try {
            while (running && t.playState == AudioTrack.PLAYSTATE_PLAYING) {
                for (i in buf.indices) {
                    val sweep = sin(2.0 * PI_D * kp.sweepRate * time)
                    // frequência central "dançando" (wail dobra/desdobra)
                    val freq = kp.baseFreq +
                        kp.sweepDepth * sweep +
                        kp.vibrato * sin(2.0 * PI_D * kp.vibratoRate * time)
                    phase += freq * step
                    phase -= floor(phase)
                    // forma de onda: seno + 25% de 2º harmônico (mais corpo)
                    var v = sin(2.0 * PI_D * phase) + 0.25 * sin(4.0 * PI_D * phase)
                    if (kp.pulse < 1f) {
                        // pulso (bip-bip): bloco ligado/desligado de 1/pulse segundos
                        val blockT = time * kp.pulse
                        val inBlock = blockT - floor(blockT)
                        val blockLenS = 1.0 / kp.pulse
                        val on = blockT.toInt() % 2 == 0
                        val rampK = (rampS / blockLenS).coerceAtMost(0.45)
                        val gate = when {
                            !on -> 0.0
                            inBlock < rampK -> inBlock / rampK
                            inBlock > 1.0 - rampK -> (1.0 - inBlock) / rampK
                            else -> 1.0
                        }
                        // smoothstep na rampa: sem quina no liga/desliga
                        v *= gate * gate * (3.0 - 2.0 * gate)
                    }
                    buf[i] = SynthMath.toPcm16((v * kp.gain * 0.7f).toFloat())
                    time += step
                }
                t.write(buf, 0, buf.size)
            }
        } catch (_: Exception) {
        }
    }

    @Synchronized
    override fun stop() {
        running = false
        thread?.interrupt()
    }

    @Synchronized
    override fun release() {
        running = false
        if (changedAlarmVolume) {
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarmVolume, 0)
            } catch (_: Exception) {
            }
            changedAlarmVolume = false
        }
        try {
            track?.stop()
        } catch (_: Exception) {
        }
        track?.release()
        track = null
        thread = null
    }

    private data class KindParams(
        val baseFreq: Float,
        val sweepDepth: Float,
        val sweepRate: Float,
        val vibrato: Float,
        val pulse: Float,
        val gain: Float,
    ) {
        val vibratoRate: Float = 22f
    }

    // tudo em Double: em Float a soma de fase perde resolução e aparece chiado
    private fun sin(x: Double): Double = kotlin.math.sin(x)
    private fun floor(x: Double): Double = kotlin.math.floor(x)
    private val PI_D = kotlin.math.PI
}

/**
 * Toca um ringtone/música do sistema via MediaPlayer em loop,
 * canal de alarme. Usado quando soundKind == "ringtone".
 */
class RingtoneSink(
    context: Context,
    uri: String,
) : SoundSink {

    private val player = MediaPlayer()
    private var ready = false

    init {
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            player.setDataSource(context, Uri.parse(uri))
            player.isLooping = true
            player.setVolume(1f, 1f)
            player.prepare()
            ready = true
        } catch (_: Exception) {
            ready = false
        }
    }

    override fun play(previewVolume: Float?) {
        if (ready) {
            try {
                player.seekTo(0)
                player.setVolume(previewVolume ?: 1f, previewVolume ?: 1f)
                player.start()
            } catch (_: Exception) {
            }
        }
    }

    override fun setGain(fraction: Float) {
        val g = fraction.coerceIn(0f, 1f)
        try {
            player.setVolume(g, g)
        } catch (_: Exception) {
        }
    }

    override fun stop() {
        if (ready) {
            try {
                if (player.isPlaying) player.pause()
            } catch (_: Exception) {
            }
        }
    }

    override fun pause() {
        if (ready) {
            try {
                if (player.isPlaying) player.pause()
            } catch (_: Exception) {
            }
        }
    }

    override fun resume() {
        if (ready) {
            try {
                if (!player.isPlaying) player.start()
            } catch (_: Exception) {
            }
        }
    }

    override fun release() {
        try {
            player.reset()
            player.release()
        } catch (_: Exception) {
        }
    }
}

/** Fábrica de sinks conforme a configuração do alarme. */
fun createSoundSink(context: Context, alarm: AlarmEntity): SoundSink {
    if (alarm.soundKind == "spotify" && alarm.spotifyUri.isNotBlank()) {
        // Toca pelo app do Spotify; se falhar, toca o som local memorizado
        // (o que a pessoa usava antes de escolher o Spotify).
        // A rampa: Web API quando o device suporta; senão canal de MÚSICA (local).
        val ramp = if (alarm.rampMs > 0) {
            SpotifyRamp(alarm.volumeInitial, alarm.volumePeak, alarm.rampMs, alarm.rampCurve)
        } else null
        val fallback = fallbackSinkFor(context, alarm.fallbackKind, alarm.fallbackUri)
        return SpotifySink(AppScope.spotifyClient, alarm.spotifyUri, fallback, ramp = ramp, context = context)
    }
    if (alarm.soundKind == "ringtone" && alarm.ringtoneUri.isNotBlank()) {
        return try {
            RingtoneSink(context, alarm.ringtoneUri)
        } catch (_: Exception) {
            createSynthSink(context, DEFAULT_SOUND)
        }
    }
    return createSynthSink(context, alarm.soundKind)
}