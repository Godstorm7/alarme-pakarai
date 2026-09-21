package com.pakarai.alarme.scheduler

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A restauração de ciclos pendentes (soneca / "AINDA ACORDADO?") no startup
 * nunca pode re-tocar algo vencido há muito tempo — senão o app dispara de
 * madrugada um alarme que devia ter tocado ontem. As regras:
 * - futuro → reagenda no instante exato;
 * - vencido dentro da janela sã → retoma (re-toca);
 * - vencido além da janela, ou sem alarme válido por trás → limpa (nunca toca).
 */
class AlarmRestoreTest {

    private val windowMs = MISSED_WINDOW_MS
    private val now = 1_700_000_000_000L

    //── Soneca ──────────────────────────────────────────────────────────────────────

    @Test
    fun `soneca futura reagenda no instante exato`() {
        val action = restoreSnoozeAction(
            expiresAtMs = now + 5 * 60 * 1000L,
            now = now,
            alarmUsable = true,
        )
        assertEquals(RestoreAction.RESCHEDULE, action)
    }

    @Test
    fun `soneca vencida dentro da janela retoma`() {
        val action = restoreSnoozeAction(
            expiresAtMs = now - 2 * 60 * 1000L,
            now = now,
            alarmUsable = true,
        )
        assertEquals(RestoreAction.RE_RING, action)
    }

    @Test
    fun `soneca vencida dentro da janela mas sem alarme válido limpa`() {
        assertEquals(
            RestoreAction.CLEAR,
            restoreSnoozeAction(
                expiresAtMs = now - 2 * 60 * 1000L,
                now = now,
                alarmUsable = false,
            )
        )
    }

    @Test
    fun `soneca vencida além da janela limpa mesmo com alarme válido`() {
        assertEquals(
            RestoreAction.CLEAR,
            restoreSnoozeAction(
                expiresAtMs = now - windowMs - 1,
                now = now,
                alarmUsable = true,
            )
        )
    }

    @Test
    fun `soneca exatamente no limite da janela ainda retoma`() {
        assertEquals(
            RestoreAction.RE_RING,
            restoreSnoozeAction(
                expiresAtMs = now - windowMs,
                now = now,
                alarmUsable = true,
            )
        )
    }

    @Test
    fun `soneca futura com alarme apagado limpa`() {
        assertEquals(
            RestoreAction.CLEAR,
            restoreSnoozeAction(
                expiresAtMs = now + 60 * 1000L,
                now = now,
                alarmUsable = false,
            )
        )
    }

    //── "AINDA ACORDADO?" ───────────────────────────────────────────────────────────

    @Test
    fun `check futuro reagenda no instante exato`() {
        assertEquals(
            RestoreAction.RESCHEDULE,
            restoreCheckAction(
                nextAtMs = now + 5 * 60 * 1000L,
                now = now,
                alarmExists = true,
            )
        )
    }

    @Test
    fun `check vencido dentro da janela retoma`() {
        assertEquals(
            RestoreAction.RE_RING,
            restoreCheckAction(
                nextAtMs = now - 30 * 1000L,
                now = now,
                alarmExists = true,
            )
        )
    }

    @Test
    fun `check vencido além da janela limpa`() {
        assertEquals(
            RestoreAction.CLEAR,
            restoreCheckAction(
                nextAtMs = now - windowMs - 1,
                now = now,
                alarmExists = true,
            )
        )
    }

    @Test
    fun `check com alarme apagado limpa mesmo futuro`() {
        assertEquals(
            RestoreAction.CLEAR,
            restoreCheckAction(
                nextAtMs = now + 60 * 1000L,
                now = now,
                alarmExists = false,
            )
        )
    }
}