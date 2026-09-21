package com.pakarai.alarme.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

/**
 * Preset da sÃ­ntese harmÃ´nica: um groove procedural (padrÃ£o de notas +
 * forma de onda + envelope) tocado a um BPM fixo, gerado 100% em runtime.
 *
 * CiÃªncia embutida (despertar com energia): pilha de harmÃ´nicos 440â€“520 Hz
 * (onda quadrada) para sair do sono profundo, arpejos melÃ³dicos 105 BPM em
 * dÃ³ maior, e variaÃ§Ã£o rÃ­tmica temporal pra nÃ£o irritar sem perder detecÃ§Ã£o.
 */
data class HarmonicPreset(
    val id: String,
    val wave: SynthWaveform,
    val bpm: Float,
    val steps: Int,
    /** Semitons rel. ao C4 por batida; `null` = pausa. */
    val pattern: List<Int?>,
    /** DuraÃ§Ã£o de cada nota em batidas. */
    val noteLenBeats: Float,
    val attackS: Float,
    val decayS: Float,
    val sustain: Float,
    val releaseS: Float,
    /** `true` = tom contÃ­nuo (sem envelope por nota) â€” PULSO/DRONADA. */
    val sustained: Boolean = false,
    /** Deslize de afinaÃ§Ã£o: comeÃ§a `glideFromSemis` acima e cai atÃ© a nota. */
    val glideFromSemis: Float = 0f,
    val glideTimeS: Float = 0.08f,
    val gain: Float,
    /** Modulador de amplitude (ex.: respiraÃ§Ã£o) â€” `(t, beatFrac) -> [0,1]`. */
    val ampMod: ((Float, Float) -> Float)? = null,
) {
    val partials: Int
        get() = when (wave) {
            SynthWaveform.SINE -> 1
            SynthWaveform.SQUARE, SynthWaveform.SAW -> 9
            SynthWaveform.BELL -> 4
        }
}

/**
 * Os 6 sons harmÃ´nicos novos (baseados na pesquisa de despertar):
 * BOM, SURTO, GALVANIZA, PULSO, ALVORADA e DRONADA.
 */
val HARMONIC_PRESETS: Map<String, HarmonicPreset> = mapOf(
    "boom" to HarmonicPreset(
        id = "boom",
        wave = SynthWaveform.SQUARE,
        bpm = 105f,
        steps = 4,
        pattern = listOf(9, 10, 11, 12), // 440 â†’ 466 â†’ 493 â†’ 523 Hz (sobe)
        noteLenBeats = 1f,
        attackS = 0.008f,
        decayS = 0.4f,
        sustain = 0.55f,
        releaseS = 0.12f,
        glideFromSemis = 8f, // "thump" descendente: comeÃ§a uma oitava alta e cai
        glideTimeS = 0.16f,
        gain = 0.6f,
    ),
    "surto" to HarmonicPreset(
        id = "surto",
        wave = SynthWaveform.SQUARE,
        bpm = 105f,
        steps = 8,
        pattern = listOf(12, 16, 19, 24, 19, 16, 19, 24), // arpejo C maior
        noteLenBeats = 0.5f,
        attackS = 0.008f,
        decayS = 0.28f,
        sustain = 0.25f,
        releaseS = 0.06f,
        gain = 0.55f,
    ),
    "galvaniza" to HarmonicPreset(
        id = "galvaniza",
        wave = SynthWaveform.BELL,
        bpm = 105f,
        steps = 8,
        pattern = listOf(12, null, 16, 12, 19, 16, 24, 19), // sinos com pausas
        noteLenBeats = 0.5f,
        attackS = 0.001f,
        decayS = 0.35f,
        sustain = 0.25f,
        releaseS = 0.45f, // sino soa
        gain = 0.6f,
    ),
    "pulso" to HarmonicPreset(
        id = "pulso",
        wave = SynthWaveform.SQUARE,
        bpm = 105f,
        steps = 4,
        pattern = listOf(9, 9, 9, 9), // 440 Hz sustido
        noteLenBeats = 1f,
        attackS = 0.02f,
        decayS = 0.2f,
        sustain = 0.8f,
        releaseS = 0.08f,
        sustained = true,
        gain = 0.6f,
        ampMod = { t, _ ->
            val breathe = 0.5f + 0.5f * kotlin.math.sin(2f * PI * 1.9f * t)
            0.35f + 0.65f * breathe
        },
    ),
    "alvorada" to HarmonicPreset(
        id = "alvorada",
        wave = SynthWaveform.SAW,
        bpm = 105f,
        steps = 8,
        pattern = listOf(0, 4, 7, 12, 7, 4, 12, 7), // C add9 subindo
        noteLenBeats = 1f,
        attackS = 0.02f,
        decayS = 0.2f,
        sustain = 0.7f,
        releaseS = 0.14f,
        glideFromSemis = 4f, // brilho ascendente em cada nota
        glideTimeS = 0.28f,
        gain = 0.5f,
    ),
    "drone" to HarmonicPreset(
        id = "drone",
        wave = SynthWaveform.SQUARE,
        bpm = 105f,
        steps = 4,
        pattern = listOf(-12, -12, -12, -12), // C3 ~130 Hz (ressonÃ¢ncia grave)
        noteLenBeats = 1f,
        attackS = 0.05f,
        decayS = 0.3f,
        sustain = 0.9f,
        releaseS = 0.1f,
        sustained = true,
        gain = 0.6f,
        ampMod = { t, beatFrac ->
            val breathe = 0.5f + 0.5f * kotlin.math.sin(2f * PI * 1.5f * t)
            val accent = 0.7f + 0.3f * (1f - beatFrac) * (1f - beatFrac)
            breathe * accent
        },
    ),
)

private const val NONE = Int.MIN_VALUE
private val PI = kotlin.math.PI.toFloat()

/**
 * Sink procedura de sÃ­ntese harmÃ´nica: canal de ALARME, 100% local,
 * baseado em [HarmonicPreset]. Mesmo contrato do [SirenSink].
 */
class HarmonicSink(
    context: Context,
    private val preset: HarmonicPreset,
) : SoundSink {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val sampleRate = 44_100
    private var track: AudioTrack? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    private val originalAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
    private var changedAlarmVolume = false

    @Synchronized
    override fun play(previewVolume: Float?) {
        if (running) return
        startRender(applyVolume = true, previewVolume)
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
        val step = 1f / sampleRate
        val beatS = 60f / preset.bpm.coerceAtLeast(1f)
        val noteLenS = beatS * preset.noteLenBeats
        val partials = preset.partials
        val scale = SynthMath.normalizeScale(preset.wave, partials)
        val masterGain = preset.gain
        val steadyNote = preset.pattern.firstOrNull { it != null } ?: 0
        var phase = 0f
        var idx = 0L
        try {
            while (running && t.playState == AudioTrack.PLAYSTATE_PLAYING) {
                for (i in buf.indices) {
                    val t0 = idx * step
                    val (stepIdx, beatFrac) = SynthMath.beatAt(t0, preset.bpm, preset.steps)
                    val stepNote = preset.pattern.getOrNull(stepIdx) ?: NONE

                    var freq = 0f
                    var env = 0f
                    if (preset.sustained) {
                        freq = SynthMath.noteFrequency(steadyNote.toFloat())
                        env = 1f
                    } else if (stepNote != NONE) {
                        val nb = (beatFrac * noteLenS).coerceAtLeast(0f)
                        env = SynthMath.adsr(
                            nb, noteLenS,
                            preset.attackS, preset.decayS, preset.sustain, preset.releaseS
                        )
                        val end = SynthMath.noteFrequency(stepNote.toFloat())
                        val start = SynthMath.noteFrequency(stepNote.toFloat() + preset.glideFromSemis)
                        val glideK = (nb / preset.glideTimeS).coerceIn(0f, 1f)
                        freq = end + (start - end) * (1f - glideK)
                    }

                    if (freq > 0f && env > 0f) {
                        phase += freq * step
                    }
                    var amp = 1f
                    preset.ampMod?.let { amp = it(t0, beatFrac).coerceIn(0f, 1f) }

                    val v = SynthMath.waveSample(preset.wave, phase - phase.toInt(), partials) *
                        scale * env * amp * masterGain
                    buf[i] = SynthMath.toPcm16(v)
                    idx++
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
}
