package com.pakarai.alarme.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.data.AlarmEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    val alarms: StateFlow<List<AlarmEntity>> =
        AppScope.repository.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
            }
        }
    }

    fun delete(alarm: AlarmEntity) {
        viewModelScope.launch {
            AppScope.scheduler.cancel(alarm.id)
            AppScope.repository.delete(alarm.id)
        }
    }
}