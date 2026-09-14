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
        /** "AINDA ACORDADO?" aguardando resposta em [nextAtMs]. SIM encerra o ciclo. */
        data class Checking(val alarmId: Long, val nextAtMs: Long) : State()
    }

    private val _state = MutableStateFlow(readPersisted())
    val state: StateFlow<State> = _state

    fun isRinging(): Boolean = _state.value is State.Ringing

    fun isChecking(): Boolean = _state.value is State.Checking

    fun currentAlarmId(): Long = when (val s = _state.value) {
        is State.Ringing -> s.alarmId
        is State.Snoozing -> s.alarmId
        is State.Checking -> s.alarmId
        State.Idle -> -1L
    }

    /** O alarme [alarmId] está tocando AGORA? (usado pra bloquear apagar/desligar). */
    fun isRingingFor(alarmId: Long): Boolean {
        val s = _state.value
        return s is State.Ringing && s.alarmId == alarmId
    }

    /**
     * O alarme [alarmId] está num ciclo ativo (tocando, em soneca ou com
     * check pendente)? Apagar/desligar é bloqueado durante o ciclo inteiro.
     */
    fun isInActiveCycle(alarmId: Long): Boolean {
        val s = _state.value
        return when (s) {
            is State.Ringing -> s.alarmId == alarmId
            is State.Snoozing -> s.alarmId == alarmId
            is State.Checking -> s.alarmId == alarmId
            State.Idle -> false
        }
    }

    /** O alarme [alarmId] está com um "AINDA ACORDADO?" pendente? */
    fun isCheckingFor(alarmId: Long): Boolean {
        val s = _state.value
        return s is State.Checking && s.alarmId == alarmId
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

    fun setChecking(alarmId: Long, nextAtMs: Long) {
        val s = State.Checking(alarmId, nextAtMs)
        persist(s)
        _state.value = s
    }

    /** Se existe check pendente para [alarmId], encerra (alarme desativado/apagado). */
    fun finishChecking(alarmId: Long) {
        if (isCheckingFor(alarmId)) clear()
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
            // Checking expirado decide o relançamento (agendador re-toca se vencido)
            is State.Checking -> false
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
            "checking" -> State.Checking(alarmId, expires)
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
                is State.Checking -> {
                    putString(KEY_STATE, "checking")
                    putLong(KEY_ALARM_ID, s.alarmId)
                    putLong(KEY_EXPIRES, s.nextAtMs)
                    remove(KEY_SNOOZE_REMAINING)
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