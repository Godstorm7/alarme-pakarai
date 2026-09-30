package com.pakarai.alarme.scheduler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * O aviso antecipado é agendado como um PendingIntent separado, então a conta
 * precisa ser exata: mesmo instante do alarme, minutos de antecedência certaintos,
 * e NADA agendado quando não faz sentido (desligado, ou horário já passou).
 */
class PreAlertTest {

    private val alarmAt = 1_700_000_000_000L
    private val oneMinute = 60_000L

    @Test
    fun `cinco minutos antes do alarme`() {
        assertEquals(alarmAt - 5 * oneMinute, computePreAlertAt(alarmAt, 5, now = alarmAt - 10 * oneMinute))
    }

    @Test
    fun `quinze minutos antes do alarme`() {
        assertEquals(alarmAt - 15 * oneMinute, computePreAlertAt(alarmAt, 15, now = alarmAt - 20 * oneMinute))
    }

    @Test
    fun `desligado nao agenda nada`() {
        assertNull(computePreAlertAt(alarmAt, 0, now = alarmAt - 60 * oneMinute))
    }

    @Test
    fun `valor negativo nao agenda nada`() {
        assertNull(computePreAlertAt(alarmAt, -5, now = alarmAt - 60 * oneMinute))
    }

    @Test
    fun `horario ja passou nao agenda alarme atrasado`() {
        // a pessoa abriu o app depois da hora do aviso: notificar "toca em 5 min"
        // agora seria mentira, então devolve null
        assertNull(computePreAlertAt(alarmAt, 5, now = alarmAt + oneMinute))
    }

    @Test
    fun `exatamente no horario ainda agenda`() {
        // instante limite conta como válido: um set com some atraso de ms não
        // pode engolir o aviso
        val at = computePreAlertAt(alarmAt, 5, now = alarmAt - 5 * oneMinute)
        assertEquals(alarmAt - 5 * oneMinute, at)
    }

    @Test
    fun `aviso de dois minutos nao conflita com o alarme de dois minutos atrasado`() {
        val agora = 1_700_000_500_000L
        val pre = computePreAlertAt(agora + 2 * oneMinute, 2, now = agora)
        assertEquals(agora, pre)
    }
}
