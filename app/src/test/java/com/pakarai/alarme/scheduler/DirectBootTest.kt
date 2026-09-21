package com.pakarai.alarme.scheduler

import com.pakarai.alarme.core.MirrorEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase D3 — direct boot. Cobre o codec do espelho DE e as decisões puras de
 * "o que fazer com o disparo que chegou com o telefone bloqueado".
 */
class DirectBootTest {

    // ---- codec do espelho -------------------------------------------------

    @Test
    fun `MirrorEntry roundtrip preserva todos os campos`() {
        val e = MirrorEntry(id = 42, hour = 6, minute = 59, repeatMask = 42, enabled = true)
        assertEquals(e, MirrorEntry.decode(e.encoded()))
    }

    @Test
    fun `roundtrip com repeatMask zero e disabled`() {
        val e = MirrorEntry(id = 7, hour = 22, minute = 30, repeatMask = 0, enabled = false)
        assertEquals(e, MirrorEntry.decode(e.encoded()))
    }

    @Test
    fun `decode de lixo retorna null`() {
        assertNull(MirrorEntry.decode(""))
        assertNull(MirrorEntry.decode("a|b|c"))
        assertNull(MirrorEntry.decode("1|2|3|4|true|extra"))
        assertNull(MirrorEntry.decode("id|6|59|42|true"))
        assertNull(MirrorEntry.decode("1|nao|3|4|true"))
    }

    @Test
    fun `toAlarm repassa hora minuto e dias e marca habilitado`() {
        val e = MirrorEntry(id = 9, hour = 6, minute = 15, repeatMask = AlarmEntity_BITS, enabled = true)
        val alarm = e.toAlarm()
        assertEquals(9L, alarm.id)
        assertEquals(6, alarm.hour)
        assertEquals(15, alarm.minute)
        assertEquals(AlarmEntity_BITS, alarm.repeatDaysMask)
        assertTrue(alarm.enabled)
        assertTrue(alarm.isRepeating())
    }

    @Test
    fun `one-shot no espelho nao conta como recorrente`() {
        assertFalse(MirrorEntry(1, 7, 0, repeatMask = 0, enabled = true).isRepeating())
        assertTrue(MirrorEntry(1, 7, 0, repeatMask = 1, enabled = true).isRepeating())
    }

    @Test
    fun `from(AlarmEntity) capta o que importa pro reagendamento`() {
        val alarm = com.pakarai.alarme.data.AlarmEntity(
            id = 3, hour = 8, minute = 5, enabled = true,
            repeatDaysMask = 16, label = "qualquer", soundKind = "spotify"
        )
        val e = MirrorEntry.from(alarm)
        assertEquals(3L, e.id)
        assertEquals(8, e.hour)
        assertEquals(5, e.minute)
        assertEquals(16, e.repeatMask)
        assertTrue(e.enabled)
    }

    // ---- política do disparo bloqueado ------------------------------------

    @Test
    fun `disparo orfao (sem entrada) e ignorado`() {
        assertEquals(DirectBootPolicy.Outcome.Ignore, DirectBootPolicy.onFireWhileLocked(null))
    }

    @Test
    fun `disparo de one-shot e esquecido`() {
        val e = MirrorEntry(1, 7, 0, repeatMask = 0, enabled = true)
        assertEquals(DirectBootPolicy.Outcome.Forget, DirectBootPolicy.onFireWhileLocked(e))
    }

    @Test
    fun `disparo de recorrente reagenda`() {
        val e = MirrorEntry(1, 6, 59, repeatMask = 1, enabled = true)
        assertEquals(DirectBootPolicy.Outcome.Recur, DirectBootPolicy.onFireWhileLocked(e))
    }

    // ---- recuperação ao desbloquear ---------------------------------------

    @Test
    fun `dentro da janela recupera`() {
        assertTrue(DirectBootPolicy.shouldRecover(dueMs = 1000, now = 1000 + MISSED_WINDOW_MS))
        assertTrue(DirectBootPolicy.shouldRecover(dueMs = 1000, now = 1000))
    }

    @Test
    fun `fora da janela nao recupera`() {
        assertFalse(DirectBootPolicy.shouldRecover(dueMs = 1000, now = 1000 + MISSED_WINDOW_MS + 1))
    }

    private companion object {
        const val AlarmEntity_BITS = 16
    }
}