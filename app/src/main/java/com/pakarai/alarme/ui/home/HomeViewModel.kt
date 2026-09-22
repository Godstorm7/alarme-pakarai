package com.pakarai.alarme.ui.home

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.core.AlarmStateManager
import com.pakarai.alarme.data.AlarmEntity
import com.pakarai.alarme.widget.NextAlarmWidget
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    val alarms: StateFlow<List<AlarmEntity>> =
        AppScope.repository.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Tem alarme ativo mas a permissão de alarme exato sumiu → nada jamais vai soar. */
    val needsExactPermission: StateFlow<Boolean> =
        alarms
            .map { list -> list.any { it.enabled } && !AppScope.scheduler.canScheduleExact() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** "AINDA ACORDADO?" pendente (null = nenhum) — alimenta a faixa da Home. */
    val checking: StateFlow<AlarmStateManager.State.Checking?> =
        AppScope.stateManager.state
            .map { it as? AlarmStateManager.State.Checking }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Ids congelados (tocando/soneca/check pendente). É um FLOW de propósito: ler
     * `isFrozen` direto no item do LazyColumn deixava o selo desatualizado.
     */
    val frozenIds: StateFlow<Set<Long>> =
        AppScope.stateManager.state
            .map { st ->
                when (st) {
                    is AlarmStateManager.State.Ringing -> setOf(st.alarmId)
                    is AlarmStateManager.State.Snoozing -> setOf(st.alarmId)
                    is AlarmStateManager.State.Checking -> setOf(st.alarmId)
                    AlarmStateManager.State.Idle -> emptySet()
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val isSamsung = AppScope.appContext.packageManager
        .let { android.os.Build.MANUFACTURER }
        .contains("samsung", ignoreCase = true)

    val wizardShown = AppScope.settings.samsungWizardShown

    fun markWizardShown() {
        AppScope.settings.samsungWizardShown = true
    }

    /**
     * Abre o editor — MENOS se o alarme está congelado (tocando, em soneca ou com
     * "AINDA ACORDADO?" pendente). Congelado ninguém mexe: resolve o alarme primeiro.
     */
    fun openEditor(alarm: AlarmEntity, open: () -> Unit) {
        if (AppScope.stateManager.isFrozen(alarm.id)) {
            Toast.makeText(
                getApplication(),
                "Alarme ativo: não dá pra editar enquanto ele toca ou espera o \"AINDA ACORDADO?\".",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        open()
    }

    fun toggleEnabled(alarm: AlarmEntity, enabled: Boolean) {
        // LIGAR é sempre livre (alarme travado nunca fica desligado por engano).
        // DESLIGAR é bloqueado no cadeado e durante o ciclo ativo.
        if (!enabled) {
            val blocked = when {
                alarm.locked -> "Alarme travado: liga, mas não desliga pela Home."
                AppScope.stateManager.isFrozen(alarm.id) ->
                    "Alarme ativo: resolve o desafio antes de desligar."
                else -> null
            }
            if (blocked != null) {
                Toast.makeText(getApplication(), blocked, Toast.LENGTH_LONG).show()
                return
            }
        }
        viewModelScope.launch {
            if (enabled) {
                AppScope.repository.setEnabled(alarm.id, true)
                AppScope.repository.getById(alarm.id)?.let {
                    AppScope.scheduler.schedule(it.copy(enabled = true))
                }
            } else {
                AppScope.repository.setEnabled(alarm.id, false)
                AppScope.scheduler.cancel(alarm.id)
                AppScope.stateManager.finishChecking(alarm.id)
            }
            NextAlarmWidget.refresh(AppScope.appContext)
        }
    }

    /** Nudge "sobe o nível": aumenta a dificuldade DESSE alarme e encerra o aviso. */
    fun bumpDifficulty(alarm: AlarmEntity) {
        viewModelScope.launch {
            AppScope.repository.upsert(com.pakarai.alarme.core.bumpDifficulty(alarm))
            AppScope.settings.resetDismissStreak(alarm.id)
            AppScope.settings.hideNudge(alarm.id)
            NextAlarmWidget.refresh(AppScope.appContext)
        }
    }

    fun hideNudge(alarm: AlarmEntity) {
        AppScope.settings.resetDismissStreak(alarm.id)
        AppScope.settings.hideNudge(alarm.id)
    }

    /** Apagar o alarme enquanto ele está num ciclo ativo (tocando/soneca/check) não é possível. */
    fun delete(alarm: AlarmEntity) {
        if (AppScope.stateManager.isFrozen(alarm.id)) {
            Toast.makeText(
                getApplication(),
                "Alarme ativo: não dá pra apagar enquanto ele toca ou espera o \"AINDA ACORDADO?\".",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        viewModelScope.launch {
            AppScope.scheduler.cancel(alarm.id)
            AppScope.stateManager.finishChecking(alarm.id)
            AppScope.repository.delete(alarm.id)
            NextAlarmWidget.refresh(AppScope.appContext)
        }
    }
}