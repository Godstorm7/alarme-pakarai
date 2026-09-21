package com.pakarai.alarme.service

import kotlin.math.pow
import kotlin.math.sin

/**
 * Formas de onda da síntese harmônica.
 *
 *  - `SINE`:   senóide pura (só o fundamental) — timbre "bip".
 *  - `SQUARE`: parciais ímpares 1, 1/3, 1/5… — a mais eficaz pra despertar
 *              de sono profundo (pesquisa: 440–520 Hz onda quadrada).
 *  - `SAW`:    todas as parciais 1, 1/2, 1/3… — quente e "cheia".
 *  - `BELL`:   parciais 1, .5, .35, .18 — timbre de sino/vibrafone.
 */
enum class SynthWaveform { SINE, SQUARE, SAW, BELL }

/**
 * Matemática da síntese harmônica — funções puras e determinísticas,
 * testáveis em JVM (sem dependência de Android).
 */
object SynthMath {

    /** Frequência do C4 (Dó central). */
    const val C4_HZ = 261.6256f

    /**
     * Frequência de um semitom relativo ao C4.
     * Ex.: +9 → 440 Hz (Lá4); -12 → C3.
     */
    fun noteFrequency(semitones: Float): Float =
        C4_HZ * 2f.pow(semitones / 12f)

    /** Amplitude do harmônico `h` (1-indexado) para um waveform. */
    fun partialAmp(wave: SynthWaveform, harmonic: Int): Float {
        require(harmonic >= 1) { "harmonic deve ser >= 1" }
        return when (wave) {
            SynthWaveform.SINE -> if (harmonic == 1) 1f else 0f
            SynthWaveform.SQUARE -> if (harmonic % 2 == 1) 1f / harmonic else 0f
            SynthWaveform.SAW -> 1f / harmonic
            SynthWaveform.BELL -> when (harmonic) {
                1 -> 1f
                2 -> 0.5f
                3 -> 0.35f
                4 -> 0.18f
                else -> 0f
            }
        }
    }

    /** Teto conservador do pico (soma das amplitudes absolutas das parciais). */
    fun peakCeiling(wave: SynthWaveform, partials: Int): Float {
        require(partials >= 1) { "partials deve ser >= 1" }
        var sum = 0f
        for (h in 1..partials) sum += partialAmp(wave, h)
        return sum
    }

    /**
     * Amostra do waveform em `phaseFraction` (0..1) como soma de parciais
     * senoidais. Sem normalização — use [normalizeScale] pra levar a [0,1].
     * SQUARE/SAW sofrem um rolloff em h (tilt) que amacia os harmônicos altos —
     * mais som e menos "estouro" metálico.
     */
    fun waveSample(wave: SynthWaveform, phaseFraction: Float, partials: Int): Float {
        require(partials >= 1) { "partials deve ser >= 1" }
        var v = 0f
        for (h in 1..partials) {
            val amp = partialAmp(wave, h)
            if (amp == 0f) continue
            val tilt = when (wave) {
                SynthWaveform.SQUARE -> 1f / (1f + 0.12f * (h - 1))
                SynthWaveform.SAW -> 1f / (1f + 0.16f * (h - 1))
                else -> 1f
            }
            v += amp * tilt * sin(TWO_PI * h * phaseFraction)
        }
        return v
    }

    /**
     * Fator que leva o pico do waveform pra ~0.85 com `partials` parciais.
     * Mede o pico real por amostragem (o teto Σ|amp| é conservador demais:
     * a soma de parciais defasa e o pico fica bem menor) e sobra margem
     * anti-clipping.
     */
    fun normalizeScale(wave: SynthWaveform, partials: Int): Float {
        var peak = 0f
        for (i in 0 until 720) {
            val s = waveSample(wave, i / 720f, partials)
            val a = if (s < 0f) -s else s
            if (a > peak) peak = a
        }
        return if (peak > 0f) 0.85f / peak else 1f
    }

    /**
     * Envelope ADSR [0,1] no instante `t` (segundos) de uma nota de
     * duração `noteDurS`. Nascimento e fim sempre suaves (anti-click).
     */
    fun adsr(
        t: Float,
        noteDurS: Float,
        attackS: Float,
        decayS: Float,
        sustain: Float,
        releaseS: Float,
    ): Float {
        if (t <= 0f) return 0f
        val atkEnd = attackS.coerceAtLeast(MIN_S)
        if (t < atkEnd) return (t / atkEnd).coerceIn(0f, 1f)
        val decEnd = atkEnd + decayS.coerceAtLeast(0f)
        val relStart = (noteDurS - releaseS.coerceAtLeast(0f)).coerceAtLeast(atkEnd + 0.0001f)
        val sustainLevel = sustain.coerceIn(0f, 1f)
        return when {
            t < decEnd -> {
                val k = ((t - atkEnd) / (decEnd - atkEnd).coerceAtLeast(MIN_S)).coerceIn(0f, 1f)
                1f + (sustainLevel - 1f) * k
            }
            t < relStart -> sustainLevel
            else -> {
                val k = ((t - relStart) / releaseS.coerceAtLeast(MIN_S)).coerceIn(0f, 1f)
                sustainLevel * (1f - k)
            }
        }
    }

    /**
     * Posição no groove: `step` (índice da batida atual, 0..steps-1) + fração
     * dentro da batida (0..1). Batida = 60 / bpm.
     */
    fun beatAt(elapsedSeconds: Float, bpm: Float, steps: Int): Pair<Int, Float> {
        require(steps >= 1) { "steps deve ser >= 1" }
        val beatS = 60f / bpm.coerceAtLeast(1f)
        val beatF = (elapsedSeconds / beatS).coerceAtLeast(0f)
        val step = beatF.toInt() % steps
        return step to (beatF - beatF.toInt()).coerceIn(0f, 1f)
    }

    /**
     * Converte um sample [-1,1] pra PCM16 com saturação SUAVE (tanh):
     * picos são dobrados, nunca recortados secos — o "estouro" harmonioso.
     */
    fun toPcm16(sample: Float): Short {
        val soft = kotlin.math.tanh(sample * SOFT_DRIVE)
        return (soft * 32767f).toInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
    }

    private const val MIN_S = 0.0005f
    /** Ganho pré-saturação: picos normais (~0.85) viram ~0.97; acima disso, dobrado suave. */
    private const val SOFT_DRIVE = 1.15f
    private val TWO_PI = (2.0 * kotlin.math.PI).toFloat()
}