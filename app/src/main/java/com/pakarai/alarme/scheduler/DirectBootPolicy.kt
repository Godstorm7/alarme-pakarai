package com.pakarai.alarme.scheduler

import com.pakarai.alarme.core.MirrorEntry

/**
 * Decisões puras do direct boot (sem Android), testáveis isoladamente.
 */
object DirectBootPolicy {

    sealed class Outcome {
        /** Nada no espelho (disparo órfão): ignora. */
        data object Ignore : Outcome()

        /** Alarme recorrente: mantém no espelho e reagenda o próximo ciclo. */
        data object Recur : Outcome()

        /** Alarme único disparado: sai do espelho (não re-toca). */
        data object Forget : Outcome()
    }

    fun onFireWhileLocked(entry: MirrorEntry?): Outcome = when {
        entry == null -> Outcome.Ignore
        entry.isRepeating() -> Outcome.Recur
        else -> Outcome.Forget
    }

    /** Recupera o disparo perdido ao desbloquear apenas se ainda dentro da janela. */
    fun shouldRecover(dueMs: Long, now: Long): Boolean = now - dueMs <= MISSED_WINDOW_MS
}