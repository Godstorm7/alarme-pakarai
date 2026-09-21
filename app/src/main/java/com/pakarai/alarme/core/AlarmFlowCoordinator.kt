package com.pakarai.alarme.core

import com.pakarai.alarme.data.AlarmDataSource
import com.pakarai.alarme.scheduler.RestoreAction
import com.pakarai.alarme.scheduler.StartupScheduler
import com.pakarai.alarme.scheduler.restoreCheckAction
import com.pakarai.alarme.scheduler.restoreSnoozeAction

/**
 * Fluxo de startup testável: o que acontece quando o app (re)abre ou o
 * telefone reinicia. Alimentado só por portas (estado, dados, agendador) e
 * relógio injetado → roda 100% em JVM.
 *
 * Ringing fica de fora de propósito: retomar a sirene exige o AlarmService
 * (camada Android), então quem usa este coordenador faz o check de Ringing
 * ANTES e só delega o resto pra cá. Aqui garantimos:
 * 1. Estado vencido é zerado (checkExpired) antes de qualquer decisão.
 * 2. Todo alarme habilitado é re-agendado, sempre one-shot (nunca toca atrasado).
 * 3. Ciclo pendente (soneca/"AINDA ACORDADO?") é restaurado dentro da janela
 *    sã; o que venceu além dela — ou sem alarme válido — é limpo, nunca re-tocado.
 */
class AlarmFlowCoordinator(
    private val store: AlarmStateStore,
    private val dataSource: AlarmDataSource,
    private val scheduler: StartupScheduler,
    private val now: () -> Long = System::currentTimeMillis,
) {

    suspend fun onStartup() {
        store.checkExpired(now())

        for (alarm in dataSource.getAll()) {
            if (alarm.enabled) scheduler.schedule(alarm)
        }

        when (val st = store.state.value) {
            is AlarmStateManager.State.Checking -> restoreCheck(st)
            is AlarmStateManager.State.Snoozing -> restoreSnooze(st)
            else -> Unit // Idle: nada a restaurar.
        }
    }

    private suspend fun restoreSnooze(st: AlarmStateManager.State.Snoozing) {
        val alarm = dataSource.getById(st.alarmId)
        val usable = alarm != null && (alarm.enabled || alarm.isRepeating())
        val cur = now()
        when (restoreSnoozeAction(st.expiresAtMs, cur, usable)) {
            RestoreAction.RESCHEDULE -> scheduler.scheduleSnoozeAt(st.alarmId, st.expiresAtMs)
            RestoreAction.RE_RING -> scheduler.scheduleImmediate(st.alarmId)
            RestoreAction.CLEAR -> store.clear()
        }
    }

    private suspend fun restoreCheck(st: AlarmStateManager.State.Checking) {
        val alarm = dataSource.getById(st.alarmId)
        val cur = now()
        when (restoreCheckAction(st.nextAtMs, cur, alarm != null)) {
            RestoreAction.RESCHEDULE -> scheduler.scheduleCheck(st.alarmId, (st.nextAtMs - cur).coerceAtLeast(0))
            RestoreAction.RE_RING -> scheduler.scheduleImmediateCheck(st.alarmId)
            RestoreAction.CLEAR -> store.clear()
        }
    }
}