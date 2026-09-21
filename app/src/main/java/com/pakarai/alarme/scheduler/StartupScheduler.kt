package com.pakarai.alarme.scheduler

import com.pakarai.alarme.data.AlarmEntity

/**
 * Comandos de agendamento que o fluxo lógico pode emitir. O AlarmFlowCoordinator
 * depende SÓ desta porta — o AlarmManager fica escondido no AlarmScheduler pra
 * o fluxo ser testável em JVM sem Android.
 */
interface StartupScheduler {
    fun schedule(alarm: AlarmEntity)
    fun scheduleSnoozeAt(alarmId: Long, triggerAtMs: Long)
    fun scheduleCheck(alarmId: Long, afterMs: Long)
    fun scheduleImmediate(alarmId: Long)
    fun scheduleImmediateCheck(alarmId: Long)
}