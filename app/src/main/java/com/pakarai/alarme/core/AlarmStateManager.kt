package com.pakarai.alarme.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Estado central do alarme. Vive em processo (StateFlow) E no disco (SharedPreferences),
 * para que uma morte de processo no meio do toque não mate o alarme silenciosamente:
 * ao abrir o app de novo, o estado RINGING é detectado e o toque retoma.
 */
class AlarmStateManager(context: Context) {

    private val prefs = context.getSharedPreferences("alarm_state", Context.MODE_PRIVATE)

    sealed class State {
        data object Idle : State()
        data class Ringing(val alarmId: Long, val expiresAtMs: Long) : State()
        data class Snoozing(val alarmId: Long, val remainingSnoozes: Int, val expiresAtMs: Long) : State()
    }

    private val _state = MutableStateFlow(readPersisted())
    val state: StateFlow<State> = _state

    fun isRinging(): Boolean = _state.value is State.Ringing

    fun currentAlarmId(): Long = when (val s = _state.value) {
        is State.Ringing -> s.alarmId
        is State.Snoozing -> s.alarmId
        State.Idle -> -1L
    }

    fun setRinging(alarmId: Long, durationMs: Long) {
        val s = State.Ringing(alarmId, System.currentTimeMillis() + durationMs)
        persist(s)
        _state.value = s
    }

    fun setSnoozing(alarmId: Long, remainingSnoozes: Int, untilMs: Long) {
        val s = State.Snoozing(alarmId, remainingSnoozes, untilMs)
        persist(s)
        _state.value = s
    }

    fun getSnoozeUsed(): Int = prefs.getInt(KEY_SNOOZE_USED, 0)

    fun setSnoozeUsed(n: Int) {
        prefs.edit().putInt(KEY_SNOOZE_USED, n).commit()
    }

    fun clear() {
        prefs.edit().clear().commit()
        prefs.edit().putInt(KEY_SNOOZE_USED, 0).commit()
        _state.value = State.Idle
    }

    /** Limpa um estado expirado (útil após reboot/demora). */
    fun checkExpired() {
        val cur = _state.value
        val expired = when (cur) {
            is State.Ringing -> System.currentTimeMillis() > cur.expiresAtMs
            is State.Snoozing -> System.currentTimeMillis() > cur.expiresAtMs
            State.Idle -> false
        }
        if (expired) {
            // Mantém o ID pra o fluxo de "agora toca mesmo assim" se ainda RINGING?
            // Não: expirado = fim do ciclo, mas nunca deixa silêncio de madrugada.
            clear()
        }
    }

    private fun readPersisted(): State {
        val raw = prefs.getString(KEY_STATE, "idle") ?: "idle"
        val alarmId = prefs.getLong(KEY_ALARM_ID, -1L)
        val remaining = prefs.getInt(KEY_SNOOZE_REMAINING, 0)
        val expires = prefs.getLong(KEY_EXPIRES, 0L)
        return when (raw) {
            "ringing" -> State.Ringing(alarmId, expires)
            "snoozing" -> State.Snoozing(alarmId, remaining, expires)
            else -> State.Idle
        }
    }

    private fun persist(s: State) {
        prefs.edit().apply {
            when (s) {
                is State.Ringing -> {
                    putString(KEY_STATE, "ringing")
                    putLong(KEY_ALARM_ID, s.alarmId)
                    putLong(KEY_EXPIRES, s.expiresAtMs)
                    remove(KEY_SNOOZE_REMAINING)
                }
                is State.Snoozing -> {
                    putString(KEY_STATE, "snoozing")
                    putLong(KEY_ALARM_ID, s.alarmId)
                    putInt(KEY_SNOOZE_REMAINING, s.remainingSnoozes)
                    putLong(KEY_EXPIRES, s.expiresAtMs)
                }
                State.Idle -> {
                    clear()
                }
            }
        }.commit()
    }

    private companion object {
        const val KEY_STATE = "state"
        const val KEY_ALARM_ID = "alarm_id"
        const val KEY_SNOOZE_REMAINING = "snooze_remaining"
        const val KEY_EXPIRES = "expires_at"
        const val KEY_SNOOZE_USED = "snooze_used"
    }
}

/** Longo até o fim do toque, para o serviço parar a sirene sozinho. */
const val RING_WINDOW_MS = 30 * 60 * 1000L