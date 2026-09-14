package com.pakarai.alarme.scheduler

import com.pakarai.alarme.data.AlarmEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * O agendamento inteiro depende de [computeNextTrigger] — se ele errar, o alarme
 * toca no dia errado. Testes rodam com fuso fixo pra data ser determinística.
 */
class ComputeNextTriggerTest {

    private val tz = TimeZone.getTimeZone("GMT-03:00")

    @Before
    fun setUp() {
        TimeZone.setDefault(tz)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(TimeZone.getDefault())
    }

    private fun at(year: Int, month: Int, day: Int, h: Int, m: Int, s: Int = 0): Long {
        val c = Calendar.getInstance(tz).apply {
            clear()
            set(year, month - 1, day, h, m, s)
        }
        return c.timeInMillis
    }

    private fun alarm(hour: Int, minute: Int, mask: Int = 0) =
        AlarmEntity(id = 1, hour = hour, minute = minute, repeatDaysMask = mask)

    // 2026-09-13 é um DOMINGO
    @Test
    fun `unico alarme no futuro dispara hoje`() {
        val from = at(2026, 9, 13, 10, 0) // domingo 10:00
        val next = computeNextTrigger(alarm(22, 30), from)
        assertEquals(at(2026, 9, 13, 22, 30), next)
    }

    @Test
    fun `unico alarme no passado dispara amanha`() {
        val from = at(2026, 9, 13, 22, 0) // domingo 22:00
        val next = computeNextTrigger(alarm(7, 0), from)
        assertEquals(at(2026, 9, 14, 7, 0), next)
    }

    @Test
    fun `no instante exato vai pro proximo dia`() {
        val from = at(2026, 9, 13, 7, 0)
        val next = computeNextTrigger(alarm(7, 0), from)
        assertEquals(at(2026, 9, 14, 7, 0), next)
    }

    @Test
    fun `recorrente so segunda cai no proximo domingo`() {
        val from = at(2026, 9, 13, 12, 0) // domingo
        val next = computeNextTrigger(alarm(6, 30, AlarmEntity.BIT_MON), from)
        assertEquals(at(2026, 9, 14, 6, 30), next)
    }

    @Test
    fun `recorrente ter a sex depois do horario vai pra terca seguinte`() {
        val from = at(2026, 9, 12, 8, 0) // sábado
        val mask = AlarmEntity.BIT_TUE or AlarmEntity.BIT_WED or
            AlarmEntity.BIT_THU or AlarmEntity.BIT_FRI
        val next = computeNextTrigger(alarm(7, 0, mask), from)
        assertEquals(at(2026, 9, 15, 7, 0), next) // próxima terça
    }

    @Test
    fun `todos os dias cai amanha no mesmo horario`() {
        val from = at(2026, 9, 13, 23, 59, 30)
        val all = (0..6).fold(0) { acc, i -> acc or (1 shl i) }
        val next = computeNextTrigger(alarm(6, 15, all), from)
        assertEquals(at(2026, 9, 14, 6, 15), next)
    }
}