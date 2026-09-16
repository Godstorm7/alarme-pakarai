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
    fun `pcm16 satura e nunca estoura`() {
        assertEquals(32767, SynthMath.toPcm16(1f).toInt())
        assertEquals(-32768, SynthMath.toPcm16(-2f).toInt())
        assertEquals(0, SynthMath.toPcm16(0f).toInt())
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