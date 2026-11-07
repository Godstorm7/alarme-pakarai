package com.pakarai.alarme.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Uma vez que o alarme TOCOU. Gravada quando o toque começa e fechada quando o
 * toque sai de cena (desligado, soneca, checagem ou estouro da janela).
 *
 * Por que duas colunas de tempo: `startedAt` (relógio de parede) é o que a tela
 * mostra, mas ele mente se a pessoa mudar o fuso ou a hora do aparelho no meio
 * do toque. A duração vem de `startedElapsed` ([android.os.SystemClock]),
 * que é monotônico e sobrevive a isso — quem toca alarme de madrugada mexe no
 * relógio, então a duração "honesta" tem que vir do outro relógio.
 */
@Entity(tableName = "ring_log", indices = [Index("startedAt")])
data class RingLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alarmId: Long,
    val label: String,
    /** Relógio de parede: quando o toque começou (o que a tela exibe). */
    val startedAt: Long,
    /** Clock monotônico: base da duração, imune a mudança de hora. */
    val startedElapsed: Long,
    val endedAt: Long? = null,
    val durationMs: Long? = null,
    /** Um de [Outcome]. */
    val outcome: String = Outcome.RINGING,
    /** Quantas sonecas já tinham sido gastas neste ciclo quando o toque começou. */
    val snoozes: Int = 0,
) {
    object Outcome {
        /** Ainda tocando (toque interrompido no meio: processo morto). */
        const val RINGING = "tocando"

        /** Desafio resolvido / alarme desligado. */
        const val DISMISSED = "desligado"

        /** Fugiu pra soneca. */
        const val SNOOZE = "soneca"

        /** Saiu do toque pra checagem "AINDA ACORDADO?". */
        const val CHECK = "checagem"

        /** Ninguém resolveu dentro da janela: o watchdog encerrou sozinho. */
        const val MISSED = "perdido"

        /** O serviço foi morto no meio do toque (deslizar dos recentes, SO matando). */
        const val INTERRUPTED = "interrompido"

        fun label(outcome: String): String = when (outcome) {
            RINGING -> "tocando"
            DISMISSED -> "desligado"
            SNOOZE -> "soneca"
            CHECK -> "checagem"
            MISSED -> "perdido"
            INTERRUPTED -> "interrompido"
            else -> outcome
        }
    }
}
