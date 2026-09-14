package com.pakarai.alarme.ui.editor

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.data.AlarmEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    private val _alarm = MutableStateFlow(AlarmEntity())
    val alarm: StateFlow<AlarmEntity> = _alarm

    private var editingId = -1L
    private var loaded = false

    fun load(id: Long) {
        editingId = id
        if (id <= 0) return
        viewModelScope.launch {
            AppScope.repository.getById(id)?.let { _alarm.value = it }
            loaded = true
        }
    }

    fun update(transform: (AlarmEntity) -> AlarmEntity) {
        _alarm.value = transform(_alarm.value)
    }

    fun setRingtone(uri: String) {
        update { it.copy(soundKind = "ringtone", ringtoneUri = uri) }
    }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val alarm = _alarm.value
            if (editingId > 0) AppScope.scheduler.cancel(editingId)
            val savedId = AppScope.repository.upsert(alarm)
            if (alarm.enabled) {
                AppScope.repository.getById(savedId)?.let {
                    AppScope.scheduler.schedule(it.copy(enabled = true, id = savedId))
                }
            } else {
                AppScope.stateManager.finishChecking(savedId)
            }
            onDone()
        }
    }

    /** Apagar o alarme enquanto ele está num ciclo ativo (tocando/soneca/check) não é possível. */
    fun delete(onDone: () -> Unit) {
        val id = if (_alarm.value.id > 0) _alarm.value.id else editingId
        if (AppScope.stateManager.isInActiveCycle(id)) {
            Toast.makeText(
                getApplication(),
                "Não dá pra apagar o alarme enquanto ele está ativo.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        viewModelScope.launch {
            AppScope.scheduler.cancel(if (id > 0) id else editingId)
            AppScope.stateManager.finishChecking(id)
            AppScope.repository.delete(if (id > 0) id else editingId)
            onDone()
        }
    }
}