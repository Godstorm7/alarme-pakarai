package com.pakarai.alarme.core

import com.pakarai.alarme.AppScope
import com.pakarai.alarme.data.AlarmEntity
import com.pakarai.alarme.service.AlarmService
import com.pakarai.alarme.service.WakeCheckNotifier
import com.pakarai.alarme.widget.NextAlarmWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Coordenador das ações de ciclo de vida de um alarme — o ÚNICO lugar onde
 * o estado, o scheduler, o serviço e o widget mudam juntos. As telas chamam
 * aqui em vez de orquestrar cada peça na mão (uma mudança = um ponto).
 */
object AlarmActions {

    /** Desligou o despertar: silencia na hora e agenda o próximo ciclo/check. */
    fun resolve(alarm: AlarmEntity) {
        // conta desligamentos seguidos DESSE alarme pro nudge "sobe o nível"
        AppScope.settings.bumpDismissStreak(alarm.id)
        // estado muda AGORA (o som para assim que o serviço observa)
        if (alarm.ackRequired) {
            val waitMs = alarm.ackSeconds.coerceAtLeast(1) * 1000L
            AppScope.stateManager.setChecking(alarm.id, System.currentTimeMillis() + waitMs, 1)
        } else {
            AppScope.stateManager.clear()
        }

        CoroutineScope(Dispatchers.IO).launch {
            // 1) encerra o ciclo ATUAL sem tocar no check (ainda não agendado)
            AppScope.scheduler.cancelFiring(alarm.id)
            if (alarm.isRepeating()) {
                AppScope.repository.getById(alarm.id)?.let { AppScope.scheduler.schedule(it) }
            } else {
                AppScope.repository.setEnabled(alarm.id, false)
            }
            // 2) agenda o check SÓ DEPOIS do cancelamento (sequencial, sem corrida)
            if (alarm.ackRequired) {
                AppScope.scheduler.scheduleCheck(alarm.id, alarm.ackSeconds.coerceAtLeast(1) * 1000L)
            }
            NextAlarmWidget.refresh(AppScope.appContext)
            WakeCheckNotifier.refresh(AppScope.appContext)
        }
    }

    /** Aplicou soneca: silencia, agenda o retorno e atualiza o widget. */
    fun snooze(alarm: AlarmEntity) {
        val sm = AppScope.stateManager
        val used = sm.currentUsedSnoozes() + 1
        val remaining = alarm.snoozeLimit - used
        sm.setSnoozing(
            alarm.id,
            remaining.coerceAtLeast(0),
            System.currentTimeMillis() + alarm.snoozeMinutes * 60_000L,
            used
        )
        AppScope.scheduler.scheduleSnooze(alarm.id, alarm.snoozeMinutes)
        WakeCheckNotifier.cancel(AppScope.appContext)
        NextAlarmWidget.refresh(AppScope.appContext)
    }

    /**
     * Respondeu "SIM" no check. Se ainda restam checagens do ciclo, RE-ARMA a
     * próxima (o alarme continua em verificação); na última, encerra de vez.
     */
    fun confirmAwake(alarmId: Long) {
        AppScope.scheduler.cancelCheck(alarmId)
        CoroutineScope(Dispatchers.IO).launch {
            val alarm = AppScope.repository.getById(alarmId)
            val index = (AppScope.stateManager.state.value as? AlarmStateManager.State.Checking)
                ?.checkIndex ?: 1
            val total = alarm?.ackChecks ?: 1
            val next = nextCheckIndex(index, total)
            if (alarm != null && alarm.ackRequired && next != null) {
                val waitMs = alarm.ackSeconds.coerceAtLeast(1) * 1000L
                AppScope.stateManager.setChecking(alarmId, System.currentTimeMillis() + waitMs, next)
                AppScope.scheduler.scheduleCheck(alarmId, waitMs)
            } else {
                AppScope.stateManager.clear()
            }
            WakeCheckNotifier.refresh(AppScope.appContext)
            NextAlarmWidget.refresh(AppScope.appContext)
        }
    }

    /** Não respondeu o check (NÃO/saiu/timeout): volta a tocar o desafio inteiro. */
    fun reRing(context: android.content.Context, alarmId: Long) {
        AppScope.scheduler.cancelCheck(alarmId)
        WakeCheckNotifier.cancel(context)
        try {
            AlarmService.start(context, alarmId)
        } catch (_: Exception) {
        }
    }
}
