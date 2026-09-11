package com.pakarai.alarme.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Alarme persistido. Todos os campos de configuração moram aqui:
 * volume progressivo, snooze, som, desafio, anti-fuga.
 */
@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val label: String = "Alarme",
    val hour: Int = 7,
    val minute: Int = 0,
    val enabled: Boolean = true,
    /** Bit 0=Segunda ... 6=Domingo. 0 = alarme único. */
    val repeatDaysMask: Int = 0,
    val vibrate: Boolean = true,

    /** "siren" | "airhorn" | "tone" | "ringtone" */
    val soundKind: String = "siren",
    /** Uri de toque do sistema, vazio se não usado. */
    val ringtoneUri: String = "",

    /** Volume inicial (fração do canal de alarme). */
    val volumeInitial: Float = 0.15f,
    /** Teto do volume progressivo. */
    val volumePeak: Float = 1.0f,
    /** Tempo (ms) até atingir o teto. */
    val rampMs: Int = 45_000,
    /** "linear" | "exp" | "step" */
    val rampCurve: String = "exp",
    /** Re-sobe o volume toda vez que alguém tentar abaixar. */
    val policeVolume: Boolean = true,

    /** 0 = sem soneca (modo radical). */
    val snoozeLimit: Int = 2,
    val snoozeMinutes: Int = 3,

    val mathEnabled: Boolean = true,
    /** 0=fácil 1=médio 2=difícil */
    val mathDifficulty: Int = 1,
    /** Tipo de desafio: challengeMode de ChallengeMode. "math" | "memory" | "shake" | "steps" | "qr" | "type" | "spin" | "object" */
    val challengeMode: String = "math",
    /** Fila ordenada de desafios (keys separadas por "|"). Vazia = usar [challengeMode] sozinho. */
    val challengeModes: String = "",
    /** Nº de rodadas/contas antes de desligar (math/memory/type/object). */
    val challengeRounds: Int = 1,
    /** Conteúdo do QR Code que desliga (modo "qr"). */
    val challengeQrSecret: String = "",

    /** Foto de referência do objeto cadastrado (modo "object" — caminho no filesDir). */
    val objectRefPath: String = "",
    /** Nome do objeto cadastrado ("minha escova") — vira a dica no desafio. */
    val objectRefLabel: String = "",

    /** Trava a tela do desafio com screen pinning. */
    val screenPin: Boolean = true
) {
    fun isRepeating(): Boolean = repeatDaysMask != 0

    fun matchesDayOfWeek(): Boolean {
        if (!isRepeating()) return true
        val calendarDay = java.util.Calendar.getInstance()
        // Calendar.MONDAY=2..SUNDAY=8 -> bit 0=Segunda
        var dayBit = calendarDay.get(java.util.Calendar.DAY_OF_WEEK) - 2
        if (dayBit < 0) dayBit += 7
        return repeatDaysMask and (1 shl dayBit) != 0
    }

    companion object {
        const val BIT_MON = 1 shl 0
        const val BIT_TUE = 1 shl 1
        const val BIT_WED = 1 shl 2
        const val BIT_THU = 1 shl 3
        const val BIT_FRI = 1 shl 4
        const val BIT_SAT = 1 shl 5
        const val BIT_SUN = 1 shl 6
    }
}