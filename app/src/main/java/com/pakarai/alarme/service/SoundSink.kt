package com.pakarai.alarme.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import com.pakarai.alarme.data.AlarmEntity

/** Saída de áudio abstrata: a rampa de volume fala com qualquer implementação. */
interface SoundSink {
    fun play()
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

    private val kindParams: KindParams = when (kind) {
        "airhorn" -> KindParams(180f, 240f, 9f, 6f, 0.22f, 0.85f)
        "tone" -> KindParams(950f, 950f, 30f, 0f, 0.5f, 0.9f)
        else -> KindParams(520f, 940f, 3.4f, 5f, 0.5f, 0.9f)
    }

    @Synchronized
    override fun play() {
        if (running) return
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
        // garante o canal de alarme alto (a rampa refina por cima)
        audioManager.setStreamVolume(
            AudioManager.STREAM_ALARM,
            (audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM) * 0.5f).toInt(),
            0
        )
        t.play()
        thread = Thread(::renderLoop).also { it.isDaemon = true; it.start() }
    }

    private fun renderLoop() {
        val t = track ?: return
        val chunk = sampleRate / 20 // 50ms por bloco
        val buf = ShortArray(chunk)
        var phase = 0f
        var time = 0f
        try {
            while (running && t.playState == AudioTrack.PLAYSTATE_PLAYING) {
                val step = 1f / sampleRate
                for (i in buf.indices) {
                    val t0 = time + i * step
                    val sweep = sin(2f * PI * kindParams.sweepRate * t0)
                    // frequência central "dançando" (wail dobra/desdobra)
                    val freq = kindParams.baseFreq +
                        kindParams.sweepDepth * sweep +
                        kindParams.vibrato * sin(2f * PI * kindParams.vibratoRate * t0)
                    // forma de onda: seno + 20% de 2º harmônico (deixa com mais corpo)
                    var v = sin(2f * PI * freq * t0)
                    v += 0.25f * sin(4f * PI * freq * t0)
                    if (kindParams.pulse < 1f) {
                        // pulso (bip-bip): amplitude cortada meia onda
                        val inPulseBlock = (t0 * kindParams.pulse).toInt() % 2 == 0
                        if (!inPulseBlock) v = 0f
                    }
                    buf[i] = (v * kindParams.gain * 0.7f * Short.MAX_VALUE).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        .toShort()
                }
                t.write(buf, 0, buf.size)
                time += chunk * step
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

    private fun sin(x: Float): Float = kotlin.math.sin(x).toFloat()
    private val PI = kotlin.math.PI.toFloat()
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

    override fun play() {
        if (ready) {
            try {
                player.seekTo(0)
                player.start()
            } catch (_: Exception) {
            }
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
    if (alarm.soundKind == "ringtone" && alarm.ringtoneUri.isNotBlank()) {
        return try {
            RingtoneSink(context, alarm.ringtoneUri)
        } catch (_: Exception) {
            SirenSink(context, "siren")
        }
    }
    return SirenSink(context, alarm.soundKind)
}