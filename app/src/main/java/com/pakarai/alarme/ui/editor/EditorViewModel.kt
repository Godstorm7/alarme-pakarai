package com.pakarai.alarme.ui.editor

import android.app.Application
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
            }
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            val id = _alarm.value.id
            AppScope.scheduler.cancel(if (id > 0) id else editingId)
            AppScope.repository.delete(if (id > 0) id else editingId)
            onDone()
        }
    }
}