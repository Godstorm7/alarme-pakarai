package com.pakarai.alarme.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A síntese harmônica precisa ser pura, determinística e sem estouro. */
class SynthMathTest {

    @Test
    fun `nota do LA4 eh 440 Hz`() {
        assertEquals(440f, SynthMath.noteFrequency(9f), 0.5f)
    }

    @Test
    fun `C4 eh o fundamental e C3 uma oitava abaixo`() {
        assertEquals(SynthMath.C4_HZ, SynthMath.noteFrequency(0f), 0.01f)
        assertEquals(SynthMath.C4_HZ / 2f, SynthMath.noteFrequency(-12f), 0.05f)
        assertEquals(SynthMath.C4_HZ * 2f, SynthMath.noteFrequency(12f), 0.1f)
    }

    @Test
    fun `onda quadrada tem so imparcias impares decaindo como 1 sobre n`() {
        assertEquals(1f, SynthMath.partialAmp(SynthWaveform.SQUARE, 1), 1e-6f)
        assertEquals(0f, SynthMath.partialAmp(SynthWaveform.SQUARE, 2), 1e-6f)
        assertEquals(1f / 3f, SynthMath.partialAmp(SynthWaveform.SQUARE, 3), 1e-6f)
        assertEquals(0f, SynthMath.partialAmp(SynthWaveform.SQUARE, 4), 1e-6f)
        assertEquals(1f / 7f, SynthMath.partialAmp(SynthWaveform.SQUARE, 7), 1e-6f)
    }

    @Test
    fun `senoide tem so o fundamental`() {
        assertEquals(1f, SynthMath.partialAmp(SynthWaveform.SINE, 1), 1e-6f)
        assertEquals(0f, SynthMath.partialAmp(SynthWaveform.SINE, 5), 1e-6f)
        assertEquals(1f, SynthMath.peakCeiling(SynthWaveform.SINE, 16), 1e-6f)
    }

    @Test
    fun `sawtooth decai em todas as parciais`() {
        assertEquals(1f / 2f, SynthMath.partialAmp(SynthWaveform.SAW, 2), 1e-6f)
        assertEquals(1f / 8f, SynthMath.partialAmp(SynthWaveform.SAW, 8), 1e-6f)
    }

    @Test
    fun `soma normalizada nao estoura`() {
        SynthWaveform.entries.forEach { wave ->
            val scale = SynthMath.normalizeScale(wave, 9)
            // amostra um ciclo inteiro
            var peak = 0f
            var nan = false
            for (i in 0 until 360) {
                val s = SynthMath.waveSample(wave, i / 360f, 9) * scale
                if (s.isNaN()) nan = true
                peak = maxOf(peak, kotlin.math.abs(s))
            }
            assertFalse("NaN em $wave", nan)
            assertTrue("pico de $wave estourou: $peak", peak <= 1.2f)
            assertTrue("pico de $wave muito baixo: $peak", peak >= 0.8f)
        }
    }

    @Test
    fun `quadrada tem mais energia que senoide`() {
        val squareRms = rms(SynthWaveform.SQUARE)
        val sineRms = rms(SynthWaveform.SINE)
        assertTrue("quadrada deveria ser mais rica: $squareRms vs $sineRms", squareRms > sineRms)
    }

    @Test
    fun `envelope ADSR nasce em zero e termina em zero`() {
        val dur = 1f
        // início da nota
        assertEquals(0f, SynthMath.adsr(0f, dur, 0.1f, 0.2f, 0.5f, 0.1f), 1e-5f)
        // no meio do attack sobe
        val midAttack = SynthMath.adsr(0.05f, dur, 0.1f, 0.2f, 0.5f, 0.1f)
        assertTrue(midAttack in 0f..1f)
        // sustain
        assertEquals(0.5f, SynthMath.adsr(0.7f, dur, 0.1f, 0.2f, 0.5f, 0.1f), 1e-5f)
        // release termina em 0
        assertEquals(0f, SynthMath.adsr(1f, dur, 0.1f, 0.2f, 0.5f, 0.1f), 1e-5f)
        // nunca sai de [0,1]
        repeat(1000) { i ->
            val v = SynthMath.adsr(i / 1000f, dur, 0.1f, 0.3f, 0.4f, 0.15f)
            assertTrue("adsr fora de [0,1]: $v", v in 0f..1f)
        }
    }

    @Test
    fun `groove anda com o bpm`() {
        // 60 bpm = 1 batida por segundo, 4 passos
        val (s0, f0) = SynthMath.beatAt(0f, 60f, 4)
        assertEquals(0, s0)
        assertEquals(0f, f0, 1e-5f)
        val (s1, f1) = SynthMath.beatAt(1.5f, 60f, 4)
        assertEquals(1, s1)
        assertEquals(0.5f, f1, 1e-4f)
        // cicla
        val (s4, _) = SynthMath.beatAt(4.0f, 60f, 4)
        assertEquals(0, s4)
        // nunca estoura o índice
        repeat(200) { i ->
            val (s, f) = SynthMath.beatAt(i * 0.37f, 105f, 8)
            assertTrue("step fora", s in 0 until 8)
            assertTrue("fracao fora", f in 0f..1f)
        }
    }

    @Test
    fun `pcm16 satura suave e nunca estoura`() {
        // soft (tanh): entra perto do topo, é monotônico e nunca recorta seco
        assertTrue("1f muito baixo: ${SynthMath.toPcm16(1f)}", SynthMath.toPcm16(1f).toInt() in 25_000..32_767)
        assertTrue("-2f muito baixo: ${SynthMath.toPcm16(-2f)}", SynthMath.toPcm16(-2f).toInt() in -32_768..-25_000)
        assertEquals(0, SynthMath.toPcm16(0f).toInt())
        assertTrue(SynthMath.toPcm16(3f).toInt() > SynthMath.toPcm16(1f).toInt())
        assertTrue(SynthMath.toPcm16(-3f).toInt() < SynthMath.toPcm16(-1f).toInt())
    }

    @Test
    fun `nenhum preset estoura com a saturacao suave`() {
        // render real da síntese (mesma matemática do renderLoop) por 2 ciclos
        // do groove de cada preset: o pico nunca deve passar de 0.95.
        val step = 1f / 44_100f
        for (preset in HARMONIC_PRESETS.values) {
            val beatS = 60f / preset.bpm
            val cycleS = beatS * preset.steps * 2f
            val samples = (cycleS / step).toInt()
            val scale = SynthMath.normalizeScale(preset.wave, preset.partials)
            val noteLenS = beatS * preset.noteLenBeats
            val steady = preset.pattern.firstOrNull { it != null } ?: 0
            var phase = 0f
            var peak = 0f
            var nan = false
            for (i in 0 until samples) {
                val t0 = i * step
                val (stepIdx, beatFrac) = SynthMath.beatAt(t0, preset.bpm, preset.steps)
                val stepNote = preset.pattern.getOrNull(stepIdx) ?: Int.MIN_VALUE
                var freq = 0f
                var env = 0f
                if (preset.sustained) {
                    freq = SynthMath.noteFrequency(steady.toFloat())
                    env = 1f
                } else if (stepNote != Int.MIN_VALUE) {
                    val nb = (beatFrac * noteLenS).coerceAtLeast(0f)
                    env = SynthMath.adsr(nb, noteLenS, preset.attackS, preset.decayS, preset.sustain, preset.releaseS)
                    val end = SynthMath.noteFrequency(stepNote.toFloat())
                    val start = SynthMath.noteFrequency(stepNote.toFloat() + preset.glideFromSemis)
                    val glideK = (nb / preset.glideTimeS).coerceIn(0f, 1f)
                    freq = end + (start - end) * (1f - glideK)
                }
                if (freq > 0f && env > 0f) phase += freq * step
                var amp = 1f
                preset.ampMod?.let { amp = it(t0, beatFrac).coerceIn(0f, 1f) }
                val v = SynthMath.waveSample(preset.wave, phase - phase.toInt(), preset.partials) *
                    scale * env * amp * preset.gain
                if (v.isNaN()) nan = true
                peak = maxOf(peak, kotlin.math.abs(v))
            }
            assertFalse("NaN em ${preset.id}", nan)
            assertTrue("pico de ${preset.id} estourou: $peak", peak <= 0.95f)
        }
    }

    private fun rms(wave: SynthWaveform): Double {
        var sum = 0.0
        val n = 2048
        for (i in 0 until n) {
            val s = SynthMath.waveSample(wave, i / n.toFloat(), 9)
            sum += s.toDouble() * s.toDouble()
        }
        return kotlin.math.sqrt(sum / n)
    }
}