package com.pakarai.alarme.ui.editor

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.data.AlarmEntity
import com.pakarai.alarme.widget.NextAlarmWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    private val _alarm = MutableStateFlow(AlarmEntity())
    val alarm: StateFlow<AlarmEntity> = _alarm

    /**
     * Falso enquanto o alarme do banco não chegou. O editor NÃO pode desenhar
     * (nem deixar editar) antes disso: ele monta primeiro com um AlarmEntity
     * default (07:00) e qualquer toque nessa janela escrevia 07:00 por cima do
     * alarme de verdade — era a "hora fixa" do seletor.
     */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    private var editingId = -1L
    private var loaded = false
    /** true quando o alarme do banco realmente veio (evita salvar em cima de um default). */
    private var loadedOk = false

    fun load(id: Long) {
        editingId = id
        if (id <= 0) {
            _ready.value = true
            return
        }
        if (loaded) { _ready.value = true; return }
        viewModelScope.launch {
            val db = AppScope.repository.getById(id)
            if (db != null) {
                _alarm.value = db
                loadedOk = true
            }
            loaded = true
            _ready.value = true
        }
    }

    /**
     * Alarme CONGELADO (tocando/soneca/"AINDA ACORDADO?" pendente) não aceita
     * nenhuma mudança — nem pelo editor aberto por engano.
     */
    private fun isFrozenNow(): Boolean {
        val id = if (_alarm.value.id > 0) _alarm.value.id else editingId
        return id > 0 && AppScope.stateManager.isFrozen(id)
    }

    fun update(transform: (AlarmEntity) -> AlarmEntity) {
        if (isFrozenNow()) return
        _alarm.value = transform(_alarm.value)
    }

    fun setRingtone(uri: String) {
        update { it.copy(soundKind = "ringtone", ringtoneUri = uri) }
    }

    fun save(onDone: () -> Unit) {
        if (isFrozenNow()) return
        // id>0 sem ter carregado = nunca sobrescrever o alarme real com o default
        if (editingId > 0 && !loadedOk) {
            onDone()
            return
        }
        viewModelScope.launch {
            val alarm = _alarm.value
            // NUNCA cancel(): ele derruba FIRE+SNOOZE+CHECK+WARMUP e mataria um
            // "AINDA ACORDADO?" pendente. Aqui só o que este alarme vai re-agendar.
            if (editingId > 0) {
                AppScope.scheduler.cancelFiring(editingId)
                AppScope.scheduler.cancelWarmup(editingId)
            }
            val savedId = AppScope.repository.upsert(alarm)
            if (alarm.enabled) {
                AppScope.repository.getById(savedId)?.let {
                    AppScope.scheduler.schedule(it.copy(enabled = true, id = savedId))
                }
            }
            NextAlarmWidget.refresh(AppScope.appContext)
            onDone()
        }
    }

    /** Apagar o alarme enquanto ele está num ciclo ativo (tocando/soneca/check) não é possível. */
    fun delete(onDone: () -> Unit) {
        val id = if (_alarm.value.id > 0) _alarm.value.id else editingId
        if (id > 0 && AppScope.stateManager.isFrozen(id)) {
            Toast.makeText(
                getApplication(),
                "Alarme ativo: não dá pra apagar enquanto ele toca ou espera o \"AINDA ACORDADO?\".",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        viewModelScope.launch {
            AppScope.scheduler.cancel(if (id > 0) id else editingId)
            AppScope.stateManager.finishChecking(id)
            AppScope.repository.delete(if (id > 0) id else editingId)
            NextAlarmWidget.refresh(AppScope.appContext)
            onDone()
        }
    }
}