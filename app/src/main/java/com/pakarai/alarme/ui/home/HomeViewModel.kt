package com.pakarai.alarme.ui.home

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pakarai.alarme.AppScope
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

    val isSamsung = AppScope.appContext.packageManager
        .let { android.os.Build.MANUFACTURER }
        .contains("samsung", ignoreCase = true)

    val wizardShown = AppScope.settings.samsungWizardShown

    fun markWizardShown() {
        AppScope.settings.samsungWizardShown = true
    }

    fun toggleEnabled(alarm: AlarmEntity, enabled: Boolean) {
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

    /** Nudge "sobe o nível": aumenta a dificuldade do alarme e encerra o aviso. */
    fun bumpDifficulty(alarm: AlarmEntity) {
        viewModelScope.launch {
            AppScope.repository.upsert(com.pakarai.alarme.core.bumpDifficulty(alarm))
            AppScope.settings.resetDismissStreak()
            AppScope.settings.nudgeHidden = true
            NextAlarmWidget.refresh(AppScope.appContext)
        }
    }

    fun hideNudge() {
        AppScope.settings.nudgeHidden = true
        AppScope.settings.resetDismissStreak()
    }

    /** Apagar o alarme enquanto ele está num ciclo ativo (tocando/soneca/check) não é possível. */
    fun delete(alarm: AlarmEntity) {
        if (AppScope.stateManager.isInActiveCycle(alarm.id)) {
            Toast.makeText(
                getApplication(),
                "Não dá pra apagar o alarme enquanto ele está ativo.",
                Toast.LENGTH_SHORT
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